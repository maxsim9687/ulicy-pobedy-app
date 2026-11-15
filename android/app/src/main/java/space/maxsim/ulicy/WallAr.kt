package space.maxsim.ulicy

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.PixelCopy
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Поддерживает ли телефон ARCore (Сервисы Google Play для AR). */
fun arCoreSupported(ctx: Context): Boolean = try {
    val a = ArCoreApk.getInstance().checkAvailability(ctx)
    a.isSupported || a.isTransient
} catch (e: Throwable) { false }

private class FrameHolder { var frame: Frame? = null }

/* =====================================================================
   AR-табличка на стене (ARCore). Телефон находит вертикальную плоскость –
   стену дома, забор, стену класса. По нажатию на стену туда «прикручивается»
   объёмная гранитная доска с бронзовой рамкой и четырьмя болтами.
   Она держится на месте, когда вы ходите вокруг, и освещается как
   настоящая (оценка освещения ARCore).
   ===================================================================== */
@Composable
fun WallPlaqueAr(c: Content, store: Store, plaqueArg: String?, locOk: Boolean, onFail: () -> Unit) {
    val ctx = LocalContext.current
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val childNodes = rememberNodes()
    val holder = remember { FrameHolder() }
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }

    val fix by rememberLocation(locOk)
    val ori by rememberOrientation(true)
    var demo by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(plaqueArg?.takeIf { it.isNotBlank() }) }
    var opened by remember { mutableStateOf<Plaque?>(null) }
    var placedId by remember { mutableStateOf<String?>(null) }
    var wallSeen by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val target = remember(chosen, fix, store.blocks) {
        c.plaques.firstOrNull { it.id == chosen }
            ?: fix?.let { f -> c.plaques.filter { it.id !in store.blocks }.minByOrNull { Geo.distance(f.lat, f.lon, it.lat, it.lon) } }
            ?: c.plaques.firstOrNull { it.id !in store.blocks } ?: c.plaques.first()
    }
    val hero = c.hero(target.hero)!!
    val dist = fix?.let { Geo.distance(it.lat, it.lon, target.lat, target.lon) }
    val acc = fix?.acc?.toDouble() ?: 999.0
    val near = demo || (dist != null && dist <= maxOf(40.0, acc + 15))
    val bearing = fix?.let { Geo.bearing(it.lat, it.lon, target.lat, target.lon) } ?: 0.0
    val delta = Geo.angleDiff(bearing, ori.azimuth)

    val targetState = rememberUpdatedState(target)
    val nearState = rememberUpdatedState(near)

    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(2600); message = null } }

    fun clearPlaque() {
        val old = childNodes.toList(); childNodes.clear(); old.forEach { it.destroy() }
        placedId = null
    }
    LaunchedEffect(target.id) { if (placedId != null && placedId != target.id) clearPlaque() }

    fun placeAt(x: Float, y: Float) {
        val frame = holder.frame ?: return
        val session = sceneView?.session ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) { message = "Подождите, камера ещё осматривается"; return }
        val hits = frame.hitTest(x, y)
        var floorHit = false
        val wall: HitResult? = hits.firstOrNull { h ->
            val t = h.trackable
            val ok = when (t) {
                is Plane -> t.type == Plane.Type.VERTICAL && t.isPoseInPolygon(h.hitPose)
                is DepthPoint -> true
                is Point -> t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                else -> false
            }
            val ny = h.hitPose.getTransformedAxis(1, 1f)[1]
            if (ok && abs(ny) > 0.55f) floorHit = true
            ok && abs(ny) <= 0.55f
        }
        if (wall == null) {
            message = if (floorHit) "Это пол или стол – наведите на стену" else "Стена не найдена – медленно поведите телефоном вдоль стены"
            return
        }
        val p = wall.hitPose
        val n = p.getTransformedAxis(1, 1f)
        var nx = n[0]; var nz = n[2]
        val cam = frame.camera.pose
        // нормаль стены должна смотреть на нас
        if (nx * (cam.tx() - p.tx()) + nz * (cam.tz() - p.tz()) < 0) { nx = -nx; nz = -nz }
        val len = sqrt(nx * nx + nz * nz).takeIf { it > 1e-3f } ?: return
        nx /= len; nz /= len
        val yaw = atan2(nx, nz)
        val anchor = try {
            session.createAnchor(Pose(floatArrayOf(p.tx(), p.ty(), p.tz()), floatArrayOf(0f, sin(yaw / 2), 0f, cos(yaw / 2))))
        } catch (e: Exception) { message = "Не удалось закрепить, попробуйте ещё раз"; return }
        clearPlaque()
        val t = targetState.value
        val bmp = plaqueBitmap(ctx, t, c.hero(t.hero)!!)
        childNodes += buildPlaque(engine, materialLoader, anchor, bmp)
        placedId = t.id
    }

    fun isPlaque(n: Node?): Boolean {
        var cur = n
        while (cur != null) { if (cur.name == "plaque") return true; cur = cur.parent }
        return false
    }

    Box(Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            childNodes = childNodes,
            planeRenderer = true,
            sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.focusMode = Config.FocusMode.AUTO
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
            },
            onSessionFailed = { onFail() },
            onSessionUpdated = { session, frame ->
                holder.frame = frame
                val tr = frame.camera.trackingState == TrackingState.TRACKING
                if (tr != tracking) tracking = tr
                if (!wallSeen) {
                    val w = session.getAllTrackables(Plane::class.java).any { it.type == Plane.Type.VERTICAL && it.trackingState == TrackingState.TRACKING }
                    if (w) wallSeen = true
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { e, node ->
                    if (isPlaque(node)) {
                        if (nearState.value) opened = targetState.value
                        else message = "Чтобы получить блок, подойдите к улице (или включите демо-режим)"
                    } else placeAt(e.x, e.y)
                }
            ),
            onViewCreated = { sceneView = this }
        )

        // верхняя подсказка
        Column(Modifier.align(Alignment.TopCenter).padding(top = 70.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC1C0F0E)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hero.street, color = Pal.paper, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val status = when {
                !tracking -> "Медленно поводите телефоном – камера осматривается"
                placedId == null && !wallSeen -> "Наведите камеру на стену дома и медленно ведите вдоль неё"
                placedId == null -> "Стена найдена! Нажмите на неё – там появится табличка"
                !near && dist != null -> "Табличка висит. Блок можно получить у самой улицы: ${Geo.human(dist)}"
                !near -> "Табличка висит. Блок – у самой улицы или в демо-режиме"
                else -> "Нажмите на табличку, чтобы прочитать и получить блок"
            }
            Text(status, color = if (placedId != null && near) Pal.gold else Pal.paper.copy(alpha = .9f), fontSize = 14.sp, textAlign = TextAlign.Center)
            if (fix != null && !demo) Text("GPS ±${fix!!.acc.roundToInt()} м" + (dist?.let { " · до улицы ${Geo.human(it)}" } ?: ""), color = Pal.paper.copy(alpha = .55f), fontSize = 11.sp)
        }
        message?.let {
            Text(it, color = Pal.paper, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(50)).background(Pal.acc).padding(horizontal = 16.dp, vertical = 10.dp))
        }
        // стрелка к улице, если далеко
        if (!demo && fix != null && ori.ok && !near) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(56.dp).clip(CircleShape).background(Color(0xAA1C0F0E))
                .border(2.dp, Pal.gold, CircleShape), contentAlignment = Alignment.Center) {
                Text("↑", color = Pal.gold, fontSize = 30.sp, modifier = Modifier.graphicsLayer { rotationZ = delta.toFloat() })
            }
        }
        // нижняя панель
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE1C0F0E)))).padding(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.plaques.forEach { p ->
                    val got = p.id in store.blocks
                    Text((if (got) "✓ " else "") + c.hero(p.hero)!!.street, color = Pal.paper, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (p.id == target.id) Pal.acc else Color(0x55F4EFE7))
                            .clickable { chosen = p.id }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { demo = !demo }, colors = ButtonDefaults.buttonColors(containerColor = if (demo) Pal.gold else Pal.acc)) {
                    Text(if (demo) "Демо: вкл" else "Демо", color = Pal.ink)
                }
                if (fix != null && !near) OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?rtext=${fix!!.lat},${fix!!.lon}~${target.lat},${target.lon}&rtt=pd")))
                }) { Text("Маршрут", color = Pal.paper) }
                Spacer(Modifier.weight(1f))
                // снимок кадра AR: камера + объёмная табличка
                Box(Modifier.size(62.dp).clip(CircleShape).background(if (placedId != null) Pal.acc else Color(0x55F4EFE7)).border(4.dp, Pal.paper, CircleShape)
                    .clickable(enabled = placedId != null) { sceneView?.let { snapshot(ctx, it, hero) } }, contentAlignment = Alignment.Center) {
                    Text("📷", fontSize = 24.sp)
                }
            }
            Text("Блоков знака: ${store.blocks.size} из 3 · табличку можно перевесить – нажмите на другое место стены",
                color = Pal.paper.copy(alpha = .7f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }

    opened?.let { p ->
        val hh = c.hero(p.hero)!!
        val isNew = remember(p.id) { store.addBlock(p.id) }
        AlertDialog(onDismissRequest = { opened = null }, containerColor = Pal.paper,
            confirmButton = { TextButton(onClick = { opened = null }) { Text("Дальше", color = Pal.acc, fontWeight = FontWeight.Bold) } },
            title = { Text(hh.fullName, color = Pal.ink, fontFamily = Serif, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(hh.years + " · " + p.where, color = Pal.acc, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(p.text, color = Pal.ink, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(p.hint, color = Pal.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3E2C4)).padding(10.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(if (isNew) "🧩 Блок знака получен! +20 очков" else "Этот блок у вас уже есть", color = Pal.ok, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Medal(store.blocks, Modifier.size(140.dp).align(Alignment.CenterHorizontally))
                }
            })
    }
}

/** Объёмная доска: бронзовая рама, гранитная плита, лицевая гравировка и 4 болта. */
private fun buildPlaque(engine: Engine, ml: MaterialLoader, anchor: Anchor, face: Bitmap): AnchorNode {
    val root = AnchorNode(engine, anchor).apply {
        name = "plaque"; isPositionEditable = false; isRotationEditable = false; isScaleEditable = false
    }
    val w = 0.60f; val h = 0.42f; val d = 0.035f
    val granite = ml.createColorInstance(Color(0xFF1E1A19), metallic = 0f, roughness = 0.32f)
    val bronze = ml.createColorInstance(Color(0xFFB08A4A), metallic = 1f, roughness = 0.35f)
    val brass = ml.createColorInstance(Color(0xFFD8B86A), metallic = 1f, roughness = 0.22f)
    // тонкая бронзовая рама прямо на стене
    root.addChildNode(CubeNode(engine, size = Size(w + 0.028f, h + 0.028f, 0.01f), center = Position(0f, 0f, 0.005f), materialInstance = bronze).apply { name = "plaque-part" })
    // гранитная плита
    root.addChildNode(CubeNode(engine, size = Size(w, h, d), center = Position(0f, 0f, 0.008f + d / 2), materialInstance = granite).apply { name = "plaque-part" })
    // лицевая сторона с гравировкой
    root.addChildNode(ImageNode(ml, face, size = Size(w - 0.004f, h - 0.004f), center = Position(0f, 0f, 0.0085f + d), normal = Direction(z = 1f)).apply { name = "plaque-part" })
    // болты по углам
    val bx = w / 2 - 0.032f; val by = h / 2 - 0.032f
    listOf(bx to by, -bx to by, bx to -by, -bx to -by).forEach { (x, y) ->
        root.addChildNode(CylinderNode(engine, radius = 0.0115f, height = 0.009f, materialInstance = brass).apply {
            name = "plaque-part"; position = Position(x, y, 0.008f + d + 0.0045f); rotation = Rotation(x = 90f)
        })
    }
    return root
}

/** Рисует лицевую сторону доски: гранит с крапинками, золотые буквы с тенью гравировки. */
fun plaqueBitmap(ctx: Context, p: Plaque, h: Hero): Bitmap {
    val W = 1200; val H = 840
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(0xFF2E2826.toInt(), 0xFF161312.toInt(), 0xFF26211F.toInt()), null, Shader.TileMode.CLAMP)
    })
    val rnd = java.util.Random(p.id.hashCode().toLong())
    val sp = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(6000) {
        val l = 70 + rnd.nextInt(90)
        sp.color = android.graphics.Color.argb(12 + rnd.nextInt(40), l, l, l)
        cv.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H, 0.5f + rnd.nextFloat() * 2.3f, sp)
    }
    val gold = 0xFFDDB86C.toInt()
    val shadow = 0xAA000000.toInt()
    val fr = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = gold; strokeWidth = 5f }
    cv.drawRect(90f, 34f, W - 90f, H - 34f, fr)
    fr.strokeWidth = 2f; cv.drawRect(104f, 48f, W - 104f, H - 48f, fr)

    // портрет «золотом»
    val photo = Content.bitmap(ctx, h.image)
    val k = minOf(340f / photo.width, 600f / photo.height)
    val pw = photo.width * k; val ph = photo.height * k
    val pr = RectF(130f + (340f - pw) / 2, 120f + (600f - ph) / 2, 130f + (340f + pw) / 2, 120f + (600f + ph) / 2)
    val cm = ColorMatrix(floatArrayOf(
        0.26f, 0.51f, 0.10f, 0f, 22f,
        0.21f, 0.42f, 0.08f, 0f, 12f,
        0.12f, 0.24f, 0.05f, 0f, 0f,
        0f, 0f, 0f, 0.92f, 0f))
    cv.drawBitmap(photo, null, pr, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })

    fun block(text: String, x: Float, y: Float, width: Int, size: Float, face: Typeface, maxLines: Int, spacing: Float = 0f): Float {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; letterSpacing = spacing }
        val lay = StaticLayout.Builder.obtain(text, 0, text.length, tp, width).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.05f).build()
        cv.save(); cv.translate(x + 2.5f, y + 2.5f); tp.color = shadow; lay.draw(cv); cv.restore()
        cv.save(); cv.translate(x, y); tp.color = gold; lay.draw(cv); cv.restore()
        return y + lay.height
    }
    val tx = 500f; val tw = 590
    var y = 90f
    y = block("В ЧЕСТЬ", tx, y, tw, 30f, Typeface.MONOSPACE, 1, 0.3f) + 14f
    y = block(h.fullName.uppercase(), tx, y, tw, 54f, Typeface.create(Typeface.SERIF, Typeface.BOLD), 3) + 8f
    y = block(h.years, tx, y, tw, 40f, Typeface.SERIF, 1) + 18f
    cv.drawRect(tx + tw / 2 - 120f, y, tx + tw / 2 + 120f, y + 3f, Paint().apply { color = gold })
    y += 22f
    y = block(h.street.uppercase(), tx, y, tw, 32f, Typeface.create(Typeface.SERIF, Typeface.BOLD), 1, 0.08f) + 16f
    val left = (H - 70f - y).coerceAtLeast(40f)
    block(p.text, tx, y, tw, 27f, Typeface.SERIF, (left / 31f).toInt().coerceIn(1, 7))
    return bmp
}

private fun snapshot(ctx: Context, v: ARSceneView, hero: Hero) {
    if (v.width == 0 || v.height == 0) return
    val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(v, bmp, { r ->
            if (r == PixelCopy.SUCCESS) {
                val cv = Canvas(bmp)
                val s = bmp.width / 1080f
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4EFE7.toInt(); textSize = 36f * s; typeface = Typeface.SERIF }
                val label = "${hero.street} · Улицы Победы"
                cv.drawRect(0f, bmp.height - 100f * s, bmp.width.toFloat(), bmp.height.toFloat(), Paint().apply { color = 0xAA1C0F0E.toInt() })
                cv.drawText(label, 36f * s, bmp.height - 38f * s, tp)
                saveImage(ctx, bmp)
            } else Toast.makeText(ctx, "Не удалось сделать снимок", Toast.LENGTH_SHORT).show()
        }, Handler(Looper.getMainLooper()))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не удалось сделать снимок", Toast.LENGTH_SHORT).show()
    }
}
