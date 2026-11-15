package space.maxsim.ulicy

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlin.math.cos
import kotlin.math.sin

/* ======================= Главная ======================= */
@Composable
fun HomeScreen(c: Content, store: Store, nav: NavController) {
    Backdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Kicker("Екатеринбург · проект 7 «П»")
            Spacer(Modifier.height(10.dp))
            H1("Улицы Победы")
            Spacer(Modifier.height(8.dp))
            Body("Три улицы Ленинского и Академического районов, названные в честь участников Великой Отечественной войны. Квест, AR-таблички, аудиогид и викторина.")
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                c.heroes.forEach { h ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Pal.acc.copy(alpha = .35f)).clickable { nav.navigate("hero/${h.id}") }.padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        AssetImage(h.image, Modifier.fillMaxWidth().height(120.dp), gray = true)
                        Text(h.name.substringAfter(' '), color = Pal.paper, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(h.years, color = Pal.paper.copy(alpha = .6f), fontFamily = Mono, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            MenuTile("🗺", "Квест «Три улицы»", "Этап ${minOf(store.stage + 1, c.stages.size)} из ${c.stages.size} · очки: ${store.points}", main = true) { nav.navigate("quest") }
            Spacer(Modifier.height(10.dp))
            MenuTile("📍", "AR-таблички на стенах", "Повесьте мемориальную доску на стену дома · блоков ${store.blocks.size} из 3") { nav.navigate("ar?plaque=&mode=plaques") }
            Spacer(Modifier.height(10.dp))
            MenuTile("❓", "Викторина", "10 вопросов · лучший результат ${store.quizBest}/10") { nav.navigate("quiz") }
            Spacer(Modifier.height(10.dp))
            MenuTile("🏅", "Знак «Хранитель памяти»", "Собирается из блоков AR-табличек") { nav.navigate("final") }
            Spacer(Modifier.height(10.dp))
            MenuTile("ℹ", "О проекте", "Автор, источники, сайт") { nav.navigate("about") }
            Spacer(Modifier.height(24.dp))
            Text("Максим Шумилов · 7 «П» класс", color = Pal.paper.copy(alpha = .5f), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun MenuTile(icon: String, title: String, sub: String, main: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (main) Pal.acc else Pal.paper.copy(alpha = .07f))
        .border(1.dp, if (main) Color.Transparent else Pal.paper.copy(alpha = .16f), RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.paper, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(sub, color = Pal.paper.copy(alpha = .7f), fontSize = 13.sp)
        }
        Text("›", color = Pal.paper, fontSize = 26.sp)
    }
}

/* ======================= Герой ======================= */
@Composable
fun HeroScreen(c: Content, id: String, nav: NavController) {
    val h = c.hero(id) ?: return
    Backdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            TopBar(h.street, onBack = { nav.popBackStack() })
            Box(Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 20.dp)) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(.75f).fillMaxHeight(.8f).clip(RoundedCornerShape(topStart = 200.dp, topEnd = 200.dp)).background(Pal.acc))
                AssetImage(h.image, Modifier.align(Alignment.BottomCenter).fillMaxHeight(), gray = true)
            }
            Column(Modifier.padding(20.dp)) {
                Kicker(h.district)
                Spacer(Modifier.height(6.dp))
                H1(h.fullName)
                Text(h.years + " · " + h.role, color = Pal.accL, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                AudioPlayer(h.audio, "Рассказ: ${h.name}", light = false)
                Spacer(Modifier.height(18.dp))
                h.life.forEach { (t, x) ->
                    PaperCard {
                        Text(t.uppercase(), color = Pal.acc, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        Body(x, Pal.ink)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(6.dp))
                H2("А вы знали?")
                Spacer(Modifier.height(8.dp))
                h.facts.forEach { f -> Row(Modifier.padding(vertical = 4.dp)) { Text("★ ", color = Pal.gold); Body(f) } }
                Spacer(Modifier.height(14.dp))
                AssetImage(h.lifeImage, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)))
                Text(h.lifeCaption, color = Pal.paper.copy(alpha = .6f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(18.dp))
                AccentButton("📍 AR-табличка на стене", Modifier.fillMaxWidth()) { nav.navigate("ar?plaque=${h.id}&mode=plaques") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/* ======================= Квест ======================= */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QuestScreen(c: Content, store: Store, nav: NavController) {
    val ctx = LocalContext.current
    var locOn by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> locOn = r.values.any { it } }
    LaunchedEffect(Unit) { perm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) }
    val fix by rememberLocation(locOn)
    var web by remember { mutableStateOf<WebView?>(null) }
    val i = store.stage

    LaunchedEffect(web, i, store.done) {
        web?.evaluateJavascript("setStage($i, '${store.done.joinToString(",", "[", "]")}')", null)
    }
    LaunchedEffect(web, fix) { fix?.let { f -> web?.evaluateJavascript("setMe(${f.lat}, ${f.lon}, ${f.acc})", null) } }

    Backdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            TopBar("Квест · очки ${store.points}", onBack = { nav.popBackStack() })
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    c.stages.indices.forEach { k ->
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(when { k < i -> Pal.accL; k == i -> Pal.paper; else -> Pal.paper.copy(alpha = .15f) }))
                    }
                }
                Spacer(Modifier.height(10.dp))
                AndroidView(factory = { cx ->
                    WebView(cx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.evaluateJavascript("setup(${org.json.JSONObject.quote(c.stagesJson)})", null)
                                web = view
                            }
                        }
                        val html = cx.assets.open("map.html").bufferedReader().use { it.readText() }
                        loadDataWithBaseURL("https://city.maxsim.space/app/", html, "text/html", "utf-8", null)
                    }
                }, modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(18.dp)))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GhostButton("◎ Где я?") { web?.evaluateJavascript("showMe()", null) }
                    Spacer(Modifier.width(10.dp))
                    val st = c.stages.getOrNull(i)
                    Text(
                        when {
                            !locOn -> "Разрешите геолокацию"
                            fix == null -> "Ищу вас…"
                            st == null -> ""
                            else -> {
                                val d = Geo.distance(fix!!.lat, fix!!.lon, st.lat, st.lon)
                                if (d < 200) "✓ Вы на месте" else "До точки: ${Geo.human(d)}"
                            }
                        }, color = Pal.paper.copy(alpha = .8f), fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (i >= c.stages.size) FinishBlock(c, store, nav)
                else StageCard(c, store, nav, i, fix)
                Spacer(Modifier.height(30.dp))
                if (i > 0) TextButton(onClick = { store.reset() }) { Text("Начать квест заново", color = Pal.paper.copy(alpha = .5f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageCard(c: Content, store: Store, nav: NavController, i: Int, fix: Fix?) {
    val ctx = LocalContext.current
    val s = c.stages[i]
    val hero = c.hero(s.hero)
    val solved = i in store.done
    var wrong by remember(i) { mutableStateOf(setOf<Int>()) }
    PaperCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Tag(when (s.kind) { "transit" -> "Переезд"; "food" -> "Привал"; "final" -> "Финал"; else -> "Точка ${i + 1}" })
                Spacer(Modifier.height(8.dp))
                H2(s.title, Pal.ink)
                Text(s.where, color = Pal.muted, fontSize = 14.sp)
            }
            if (hero != null) AssetImage(hero.image, Modifier.size(90.dp), gray = true)
        }
        Spacer(Modifier.height(10.dp))
        Text("✉ " + s.intro, color = Pal.ink.copy(alpha = .85f), fontSize = 15.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 21.sp)
        if (hero != null) {
            Spacer(Modifier.height(12.dp))
            AudioPlayer(hero.audio, "Аудиогид: ${hero.name}")
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (s.kind == "food") Color(0xFFF3E2C4) else Color(0x0F1C0F0E)).padding(14.dp)) {
            Text(if (s.kind == "food") "🍽 Можно перекусить" else "Что делать", color = Pal.ink, fontWeight = FontWeight.Bold)
            s.how.forEachIndexed { k, t -> Text("${k + 1}. $t", color = Pal.ink, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        // кнопки действий
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val uri = if (fix != null) "https://yandex.ru/maps/?rtext=${fix.lat},${fix.lon}~${s.lat},${s.lon}&rtt=${if (s.kind == "transit") "mt" else "pd"}"
                else "https://yandex.ru/maps/?pt=${s.lon},${s.lat}&z=16&l=map"
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            }) { Text("Маршрут", color = Pal.ink) }
            if (s.kind == "food") OutlinedButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?text=кафе&ll=${s.lon},${s.lat}&z=16")))
            }) { Text("Кафе рядом", color = Pal.ink) }
            if (hero != null) {
                val got = hero.id in store.blocks
                Button(onClick = { nav.navigate("ar?plaque=${hero.id}&mode=plaques") }, colors = ButtonDefaults.buttonColors(containerColor = if (got) Pal.ok else Pal.ink)) {
                    Text(if (got) "✓ AR-табличка найдена" else "📍 Найти AR-табличку", color = Pal.paper)
                }
            }
            val near = fix != null && Geo.distance(fix.lat, fix.lon, s.lat, s.lon) < maxOf(200.0, fix.acc + 50.0)
            if (s.kind != "final") {
                val checked = s.id in store.checkins
                Button(enabled = near && !checked, onClick = { store.checkin(s.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.acc)) {
                    Text(if (checked) "✓ Отмечено +15" else "Отметиться на месте", color = Pal.paper)
                }
            }
        }
        if (hero != null) {
            Spacer(Modifier.height(10.dp))
            var open by remember(i) { mutableStateOf(false) }
            Text(if (open) "▾ История подробнее" else "▸ История подробнее", color = Pal.acc, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { open = !open }.padding(vertical = 6.dp))
            AnimatedVisibility(open) {
                Column {
                    hero.life.forEach { (t, x) ->
                        Text(t, color = Pal.acc, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        Text(x, color = Pal.ink, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Вопрос – получите букву", color = Pal.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(s.q, color = Pal.ink, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
        s.opts.forEachIndexed { k, o ->
            val isOk = solved && k == s.a
            val isBad = k in wrong
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                .background(when { isOk -> Color(0xFFDCEFE2); isBad -> Color(0xFFF6DCD9); else -> Color(0x081C0F0E) })
                .border(1.dp, when { isOk -> Pal.ok; isBad -> Pal.acc; else -> Color(0x331C0F0E) }, RoundedCornerShape(12.dp))
                .clickable(enabled = !solved) {
                    if (k == s.a) store.solve(i, wrong.isEmpty()) else wrong = wrong + k
                }.padding(14.dp)) {
                Text(o, color = Pal.ink, fontWeight = FontWeight.SemiBold)
            }
        }
        if (wrong.isNotEmpty() && !solved) Text("Не совсем. Подсказка есть на AR-табличке и в аудиогиде.", color = Pal.acc, fontSize = 14.sp)
        if (solved) {
            Spacer(Modifier.height(8.dp))
            Text("Верно! Ваша буква – «${s.letter}».", color = Pal.ok, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AccentButton(if (i == c.stages.size - 1) "К финалу →" else "Следующий этап →", Modifier.fillMaxWidth()) { store.next(c.stages.size) }
        }
    }
    Spacer(Modifier.height(12.dp))
    GlassCard {
        Text("Собранные буквы", color = Pal.paper, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Letters(c, store)
    }
}

@Composable
private fun Letters(c: Content, store: Store) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        c.stages.forEachIndexed { k, s ->
            val got = k in store.done
            Box(Modifier.size(42.dp, 50.dp).clip(RoundedCornerShape(10.dp)).background(if (got) Pal.acc else Pal.paper.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                Text(if (got) s.letter else "?", color = Pal.paper, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun FinishBlock(c: Content, store: Store, nav: NavController) {
    var word by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    if (!store.wordSolved) {
        GlassCard {
            Kicker("Финал")
            Spacer(Modifier.height(6.dp))
            H2("Сложите слово из букв")
            Spacer(Modifier.height(10.dp))
            Letters(c, store)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(word, { word = it; err = false }, label = { Text("Секретное слово") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.fillMaxWidth())
            if (err) Text("Пока не то слово. Буквы уже стоят по порядку.", color = Pal.accL, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            AccentButton("Проверить", Modifier.fillMaxWidth()) {
                if (word.trim().uppercase().replace('Ё', 'Е') == c.word) store.solveWord() else err = true
            }
        }
    } else {
        GlassCard {
            Kicker("Квест пройден")
            Spacer(Modifier.height(6.dp))
            H2("Слово «${c.word}» разгадано!")
            Body("Очки: ${store.points}. Блоков знака: ${store.blocks.size} из 3.")
            Spacer(Modifier.height(10.dp))
            AccentButton("🏅 Знак и грамота", Modifier.fillMaxWidth()) { nav.navigate("final") }
        }
    }
}

/* ======================= Знак и грамота ======================= */
@Composable
fun FinalScreen(c: Content, store: Store, nav: NavController) {
    Backdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar("Награда", onBack = { nav.popBackStack() })
            Medal(store.blocks, Modifier.size(240.dp))
            Spacer(Modifier.height(12.dp))
            H2(if (store.blocks.size >= 3) "Знак «Хранитель памяти»" else "Собрано блоков: ${store.blocks.size} из 3")
            Body(if (store.blocks.size >= 3) "Вы нашли все три AR-таблички!" else "Найдите AR-таблички на улицах Рябинина, Громова и Гризодубовой.")
            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                val badges = listOf(
                    Triple("🎯", "Знаток", store.done.size >= c.stages.size),
                    Triple("🧭", "Путешественник", store.checkins.size >= 3),
                    Triple("📍", "Искатель табличек", store.blocks.size >= 3),
                    Triple("🔐", "Разгадал слово", store.wordSolved)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    badges.forEach { (ic, t, got) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (got) Pal.acc else Pal.paper.copy(alpha = .06f)).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(ic, fontSize = 24.sp, color = if (got) Color.Unspecified else Pal.muted)
                            Text(t, color = Pal.paper.copy(alpha = if (got) 1f else .4f), fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (store.blocks.size >= 3 || store.wordSolved) {
                    var name by remember { mutableStateOf(store.name) }
                    OutlinedTextField(name, { name = it; store.updateName(it) }, label = { Text("Ваше имя для грамоты") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pal.paper).border(3.dp, Pal.acc, RoundedCornerShape(20.dp)).padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ГРАМОТА", color = Pal.acc, fontFamily = Mono, letterSpacing = 3.sp)
                        Text("Квест «Три улицы» пройден!", color = Pal.ink, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(name.ifBlank { "—" }, color = Pal.ink, fontFamily = Serif, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("за прохождение маршрута по улицам Рябинина, Громова и Гризодубовой", color = Pal.ink, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text("Очки: ${store.points}", color = Pal.acc, fontWeight = FontWeight.Bold)
                        Text("Улицы Победы · Максим Шумилов, 7 «П»", color = Pal.muted, fontSize = 12.sp)
                    }
                    Text("Сделайте скриншот, чтобы сохранить грамоту.", color = Pal.paper.copy(alpha = .6f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

/** Знак из трёх секторов: каждый сектор – блок одной AR-таблички. */
@Composable
fun Medal(blocks: Set<String>, modifier: Modifier = Modifier) {
    val ids = listOf("ryabinin", "gromov", "grizodubova")
    val colors = listOf(Color(0xFF9E2A2B), Color(0xFFB8483A), Color(0xFF7A1F20))
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 - 8f
            ids.forEachIndexed { k, id ->
                drawArc(if (id in blocks) colors[k] else Color(0xFF3A2A28), -90f + k * 120f, 120f, true,
                    topLeft = Offset(center.x - r, center.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
                val a = Math.toRadians(-90.0 + k * 120.0)
                drawLine(Pal.paper, center, Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat()), 5f)
            }
            drawCircle(Pal.gold, r, style = Stroke(12f))
            drawCircle(if (blocks.size >= 3) Pal.gold else Color(0xFF2A1A18), r * .33f)
            drawCircle(Pal.paper, r * .33f, style = Stroke(5f))
        }
        Text("★", color = if (blocks.size >= 3) Pal.acc else Pal.muted, fontSize = 44.sp)
    }
}

/* ======================= Викторина ======================= */
@Composable
fun QuizScreen(c: Content, store: Store, nav: NavController) {
    var i by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val order = remember(i) { c.quiz.getOrNull(i)?.o?.indices?.shuffled() ?: emptyList() }
    Backdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            TopBar("Викторина", onBack = { nav.popBackStack() })
            Column(Modifier.padding(20.dp)) {
                if (i < c.quiz.size) {
                    val q = c.quiz[i]
                    Text("Вопрос ${i + 1} / ${c.quiz.size} · очки $score", color = Pal.paper.copy(alpha = .7f), fontFamily = Mono, fontSize = 13.sp)
                    LinearProgressIndicator(progress = { i / c.quiz.size.toFloat() }, Modifier.fillMaxWidth().padding(vertical = 10.dp), color = Pal.accL)
                    PaperCard {
                        H2(q.q, Pal.ink)
                        Spacer(Modifier.height(10.dp))
                        order.forEach { k ->
                            val bg = when { picked == null -> Color(0x081C0F0E); k == q.a -> Color(0xFFDCEFE2); k == picked -> Color(0xFFF6DCD9); else -> Color(0x081C0F0E) }
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                                .border(1.dp, Color(0x331C0F0E), RoundedCornerShape(12.dp))
                                .clickable(enabled = picked == null) { picked = k; if (k == q.a) score++ }.padding(14.dp)) {
                                Text(q.o[k], color = Pal.ink, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (picked != null) {
                            Text((if (picked == q.a) "Верно! " else "Неверно. ") + q.e, color = Pal.ink, modifier = Modifier.padding(vertical = 10.dp))
                            AccentButton(if (i < c.quiz.size - 1) "Дальше →" else "Результат", Modifier.fillMaxWidth()) {
                                picked = null; i++; if (i >= c.quiz.size) store.quizResult(score)
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("$score / ${c.quiz.size}", color = Pal.accL, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 72.sp)
                        H2(when { score >= 9 -> "Отлично! Вы знаете героев своих улиц."; score >= 6 -> "Хорошо! Кое-что можно повторить."; else -> "Загляните в истории героев и попробуйте ещё раз." })
                        Spacer(Modifier.height(16.dp))
                        AccentButton("Пройти ещё раз", Modifier.fillMaxWidth()) { i = 0; score = 0 }
                    }
                }
            }
        }
    }
}

/* ======================= О проекте ======================= */
@Composable
fun AboutScreen(c: Content, nav: NavController) {
    val ctx = LocalContext.current
    Backdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            TopBar("О проекте", onBack = { nav.popBackStack() })
            Column(Modifier.padding(20.dp)) {
                H1("Улицы Победы")
                Spacer(Modifier.height(8.dp))
                Body("Проект Максима Шумилова, 7 «П» класс. Я узнал, какие улицы Ленинского и Академического районов Екатеринбурга названы в честь участников Великой Отечественной войны. Их оказалось три: улицы Громова, Гризодубовой и Рябинина.")
                Spacer(Modifier.height(12.dp))
                Body("Найти все улицы вручную невозможно – в городе около тысячи улиц, поэтому я использовал нейросеть Claude, а каждый факт проверил по энциклопедиям.")
                Spacer(Modifier.height(12.dp))
                Body("Источники: Википедия, Большая российская энциклопедия, «Областная газета». Озвучка: ElevenLabs. Карта: Яндекс.")
                Spacer(Modifier.height(18.dp))
                AccentButton("Открыть сайт проекта", Modifier.fillMaxWidth()) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.site))) }
            }
        }
    }
}
