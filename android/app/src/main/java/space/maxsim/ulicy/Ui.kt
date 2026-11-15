package space.maxsim.ulicy

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

object Pal {
    val ink = Color(0xFF1C0F0E)
    val paper = Color(0xFFF4EFE7)
    val acc = Color(0xFF9E2A2B)
    val accL = Color(0xFFEC9A96)
    val gold = Color(0xFFD9B56A)
    val ok = Color(0xFF3F8A5C)
    val muted = Color(0xFF8A7A75)
}

val Serif = FontFamily.Serif
val Mono = FontFamily.Monospace

@Composable
fun UlicyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.acc, onPrimary = Pal.paper, secondary = Pal.accL,
            background = Pal.ink, surface = Color(0xFF2A1A18), onBackground = Pal.paper, onSurface = Pal.paper
        ),
        content = content
    )
}

@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0xFF5A1C1C), Pal.ink, Color(0xFF0A0505)),
                radius = 1600f
            )
        ), content = content
    )
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Pal.paper) }
        else Spacer(Modifier.width(12.dp))
        Text(title.uppercase(), color = Pal.paper.copy(alpha = .8f), fontFamily = Mono, fontSize = 13.sp, letterSpacing = 2.sp)
    }
}

@Composable
fun Kicker(text: String) = Text(text.uppercase(), color = Pal.accL, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp)

@Composable
fun H1(text: String, color: Color = Pal.paper) = Text(text, color = color, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)

@Composable
fun H2(text: String, color: Color = Pal.paper) = Text(text, color = color, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp)

@Composable
fun Body(text: String, color: Color = Pal.paper.copy(alpha = .88f)) = Text(text, color = color, fontSize = 16.sp, lineHeight = 23.sp)

@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pal.paper).padding(18.dp), content = content)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    var m = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Pal.paper.copy(alpha = .07f))
        .border(1.dp, Pal.paper.copy(alpha = .18f), RoundedCornerShape(18.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(16.dp), content = content)
}

@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Pal.acc, contentColor = Pal.paper)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, dark: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dark) Pal.paper else Pal.ink)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Tag(text: String, color: Color = Pal.acc) {
    Text(text.uppercase(), color = Pal.paper, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp))
}

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun AssetImage(name: String, modifier: Modifier = Modifier, gray: Boolean = false, scale: ContentScale = ContentScale.Fit) {
    val ctx = LocalContext.current
    val bmp = remember(name) { Content.bitmap(ctx, name).asImageBitmap() }
    Image(bmp, null, modifier, contentScale = scale, colorFilter = if (gray) grayscale else null)
}

/* ---------- аудиоплеер для записей ElevenLabs ---------- */
@Composable
fun AudioPlayer(file: String, title: String, light: Boolean = true) {
    val ctx = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    DisposableEffect(file) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { if (it.duration > 0) progress = it.currentPosition.toFloat() / it.duration }
            delay(250)
        }
    }
    val bg = if (light) Color(0xFFEFE1DC) else Pal.acc.copy(alpha = .25f)
    val fg = if (light) Pal.ink else Pal.paper
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Pal.acc).clickable {
            val p = player ?: openPlayer(ctx, file).also { mp ->
                mp.setOnCompletionListener { playing = false; progress = 1f }
                player = mp
            }
            if (playing) { p.pause(); playing = false } else { p.start(); playing = true }
        }, contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Пауза" else "Слушать", tint = Pal.paper)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("озвучка ElevenLabs", color = fg.copy(alpha = .6f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Pal.acc, trackColor = fg.copy(alpha = .15f), gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

private fun openPlayer(ctx: Context, file: String): MediaPlayer {
    val mp = MediaPlayer()
    ctx.assets.openFd("audio/$file").use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
    mp.prepare()
    return mp
}
