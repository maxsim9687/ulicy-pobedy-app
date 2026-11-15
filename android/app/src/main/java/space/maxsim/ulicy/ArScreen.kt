package space.maxsim.ulicy

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HFOV = 55.0   // примерный угол обзора камеры по горизонтали
private const val VFOV = 75.0

@Composable
fun ArScreen(c: Content, store: Store, nav: NavController, plaqueArg: String?, mode: String) {
    val ctx = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var camOk by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var locOk by remember { mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        camOk = r[Manifest.permission.CAMERA] == true || camOk
        locOk = r[Manifest.permission.ACCESS_FINE_LOCATION] == true || r[Manifest.permission.ACCESS_COARSE_LOCATION] == true || locOk
    }
    LaunchedEffect(Unit) {
        if (!camOk || !locOk) perm.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    var arFailed by remember { mutableStateOf(false) }
    val arOk = remember { arCoreSupported(ctx) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!camOk) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нужен доступ к камере", color = Pal.paper)
        }
        else if (arOk && !arFailed) WallPlaqueAr(c, store, plaqueArg, locOk) { arFailed = true }
        else {
            // запасной режим для телефонов без ARCore: компас + GPS
            CameraPreview { }
            PlaqueOverlay(c, store, plaqueArg, locOk)
        }
        // верхняя панель
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x991C0F0E)).clickable { nav.popBackStack() }, contentAlignment = Alignment.Center) {
                Text("←", color = Pal.paper, fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text("AR-ТАБЛИЧКИ", color = Pal.paper, fontFamily = Mono, letterSpacing = 2.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x991C0F0E)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun CameraPreview(onView: (PreviewView) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    AndroidView(factory = { cx ->
        PreviewView(cx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // нужно для снимка через getBitmap()
            val future = ProcessCameraProvider.getInstance(cx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Камера недоступна", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(cx))
            onView(this)
        }
    }, modifier = Modifier.fillMaxSize())
}

/* ======================= AR-таблички по GPS и компасу ======================= */
@Composable
private fun PlaqueOverlay(c: Content, store: Store, plaqueArg: String?, locOk: Boolean) {
    val ctx = LocalContext.current
    val fix by rememberLocation(locOk)
    val ori by rememberOrientation(true)
    var demo by remember { mutableStateOf(false) }
    var demoAz by remember { mutableDoubleStateOf(0.0) }
    var opened by remember { mutableStateOf<Plaque?>(null) }
    var chosen by remember { mutableStateOf(plaqueArg?.takeIf { it.isNotBlank() }) }

    // цель: выбранная табличка или ближайшая ещё не найденная
    val target = remember(chosen, fix, store.blocks) {
        c.plaques.firstOrNull { it.id == chosen }
            ?: fix?.let { f -> c.plaques.filter { it.id !in store.blocks }.minByOrNull { Geo.distance(f.lat, f.lon, it.lat, it.lon) } }
            ?: c.plaques.firstOrNull { it.id !in store.blocks } ?: c.plaques.first()
    }
    val hero = c.hero(target.hero)!!
    val dist = if (demo) 6.0 else fix?.let { Geo.distance(it.lat, it.lon, target.lat, target.lon) } ?: Double.MAX_VALUE
    val bearing = if (demo) demoAz else fix?.let { Geo.bearing(it.lat, it.lon, target.lat, target.lon) } ?: 0.0
    val delta = Geo.angleDiff(bearing, ori.azimuth)
    val acc = fix?.acc?.toDouble() ?: 999.0
    val canSee = demo || dist <= 150
    val near = demo || dist <= maxOf(40.0, acc + 15)
    val centered = abs(delta) < 20

    val pulse by rememberInfiniteTransition(label = "p").animateFloat(1f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        if (canSee && ori.ok) {
            val widthDp = (260.0 * 20.0 / maxOf(dist, 6.0)).coerceIn(120.0, 300.0)
            val x = w / 2 + (delta / HFOV * w).toFloat()
            val y = h * 0.55f - (ori.pitch / VFOV * h).toFloat()
            val wPx = with(density) { widthDp.dp.toPx() }
            if (abs(delta) < HFOV) {
                Box(Modifier.offset { IntOffset((x - wPx / 2).roundToInt(), (y - wPx * 0.6f).roundToInt()) }
                    .width(widthDp.dp)
                    .graphicsLayer { rotationY = (delta * 0.6).toFloat(); cameraDistance = 12f * density.density }
                    .scale(if (near && centered) pulse else 1f)
                    .clickable(enabled = near) { chosen = target.id; opened = target }) {
                    PlaqueBitmapView(target, hero)
                }
            }
        }
        // подсказки
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC1C0F0E)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hero.street, color = Pal.paper, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val status = when {
                !locOk && !demo -> "Разрешите геолокацию"
                fix == null && !demo -> "Ищу GPS…"
                !ori.ok -> "Нет компаса – включите демо-режим"
                demo -> "Демо-режим: табличка перед вами"
                dist > 150 -> "До таблички ${Geo.human(dist)} – подойдите ближе"
                !centered -> if (delta > 0) "Повернитесь направо →" else "← Повернитесь налево"
                !near -> "Видите табличку? Подойдите ближе: ${Geo.human(dist)}"
                else -> "Нажмите на табличку!"
            }
            Text(status, color = if (near && centered && canSee) Pal.gold else Pal.paper.copy(alpha = .85f), fontSize = 14.sp, textAlign = TextAlign.Center)
            if (fix != null && !demo) Text("точность GPS ±${fix!!.acc.roundToInt()} м", color = Pal.paper.copy(alpha = .5f), fontSize = 11.sp)
        }
        // стрелка-компас к цели
        if (!demo && fix != null && ori.ok) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(56.dp).clip(CircleShape).background(Color(0xAA1C0F0E))
                .border(2.dp, Pal.gold, CircleShape), contentAlignment = Alignment.Center) {
                Text("↑", color = Pal.gold, fontSize = 30.sp, modifier = Modifier.graphicsLayer { rotationZ = delta.toFloat() })
            }
        }
        // нижняя панель
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE1C0F0E)))).padding(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.plaques.forEach { p ->
                    val got = p.id in store.blocks
                    val sel = p.id == target.id
                    Text((if (got) "✓ " else "") + c.hero(p.hero)!!.street, color = Pal.paper, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Pal.acc else Color(0x55F4EFE7))
                            .clickable { chosen = p.id }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { demo = !demo; demoAz = ori.azimuth }, colors = ButtonDefaults.buttonColors(containerColor = if (demo) Pal.gold else Pal.acc)) {
                    Text(if (demo) "Выключить демо" else "Демо-режим", color = Pal.ink)
                }
                if (fix != null) OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?rtext=${fix!!.lat},${fix!!.lon}~${target.lat},${target.lon}&rtt=pd")))
                }) { Text("Маршрут", color = Pal.paper) }
                Text("Блоков: ${store.blocks.size}/3", color = Pal.gold, modifier = Modifier.align(Alignment.CenterVertically))
            }
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

/** Виртуальная мемориальная доска: «гранит» с золотыми буквами. */
@Composable
fun PlaqueView(p: Plaque, h: Hero, compact: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF3A3330), Color(0xFF1E1A19), Color(0xFF2E2826))))
        .border(3.dp, Pal.gold.copy(alpha = .8f), RoundedCornerShape(6.dp)).padding(if (compact) 8.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        AssetImage(h.image, Modifier.height(if (compact) 54.dp else 96.dp).graphicsLayer { alpha = .85f }, gray = true)
        Spacer(Modifier.height(4.dp))
        Text("В ЧЕСТЬ", color = Pal.gold, fontFamily = Mono, fontSize = if (compact) 8.sp else 10.sp, letterSpacing = 2.sp)
        Text(h.fullName.uppercase(), color = Pal.gold, fontFamily = Serif, fontWeight = FontWeight.Bold,
            fontSize = if (compact) 11.sp else 15.sp, textAlign = TextAlign.Center, lineHeight = if (compact) 13.sp else 18.sp)
        Text(h.years, color = Pal.gold.copy(alpha = .85f), fontFamily = Serif, fontSize = if (compact) 10.sp else 13.sp)
        if (!compact) {
            Spacer(Modifier.height(6.dp))
            Text(p.text, color = Pal.gold.copy(alpha = .9f), fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
        }
    }
}

fun saveImage(ctx: android.content.Context, bmp: Bitmap) {
    try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ulicy_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UlicyPobedy")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        Toast.makeText(ctx, "Фото сохранено в галерею", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show()
    }
}
