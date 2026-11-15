package space.maxsim.ulicy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Hero(
    val id: String, val name: String, val fullName: String, val years: String,
    val street: String, val district: String, val role: String,
    val image: String, val lifeImage: String, val lifeCaption: String, val audio: String,
    val story: String, val life: List<Pair<String, String>>, val facts: List<String>
)

data class Plaque(
    val id: String, val hero: String, val lat: Double, val lon: Double,
    val where: String, val text: String, val hint: String
)

data class Stage(
    val id: String, val title: String, val where: String, val lat: Double, val lon: Double,
    val hero: String?, val letter: String, val kind: String, val intro: String,
    val how: List<String>, val q: String, val opts: List<String>, val a: Int
)

data class QuizItem(val q: String, val o: List<String>, val a: Int, val e: String)

/** Рассказ в дорогу: озвучен в ElevenLabs, играет на этапе stage. */
data class Road(val stage: String, val hint: String, val title: String, val sub: String, val audio: String, val text: List<String>)

class Content(
    val word: String, val site: String,
    val heroes: List<Hero>, val plaques: List<Plaque>,
    val stages: List<Stage>, val quiz: List<QuizItem>, val stagesJson: String,
    val road: List<Road> = emptyList()
) {
    fun hero(id: String?) = heroes.firstOrNull { it.id == id }

    companion object {
        @Volatile private var cached: Content? = null
        private val bitmaps = HashMap<String, Bitmap>()

        fun get(ctx: Context): Content = cached ?: synchronized(this) {
            cached ?: load(ctx).also { cached = it }
        }

        fun bitmap(ctx: Context, name: String): Bitmap = synchronized(bitmaps) {
            bitmaps.getOrPut(name) {
                ctx.assets.open("images/$name").use { BitmapFactory.decodeStream(it) }
            }
        }

        private fun strings(a: JSONArray) = List(a.length()) { a.getString(it) }

        private fun load(ctx: Context): Content {
            val text = ctx.assets.open("content.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)
            val heroes = root.getJSONArray("heroes").let { arr ->
                List(arr.length()) { i ->
                    val h = arr.getJSONObject(i)
                    val life = h.getJSONArray("life").let { l -> List(l.length()) { l.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } } }
                    Hero(
                        h.getString("id"), h.getString("name"), h.getString("fullName"), h.getString("years"),
                        h.getString("street"), h.getString("district"), h.getString("role"),
                        h.getString("image"), h.getString("lifeImage"), h.getString("lifeCaption"), h.getString("audio"),
                        h.getString("story"), life, strings(h.getJSONArray("facts"))
                    )
                }
            }
            val plaques = root.getJSONArray("plaques").let { arr ->
                List(arr.length()) { i ->
                    val p = arr.getJSONObject(i)
                    Plaque(p.getString("id"), p.getString("hero"), p.getDouble("lat"), p.getDouble("lon"),
                        p.getString("where"), p.getString("text"), p.getString("hint"))
                }
            }
            val stagesArr = root.getJSONArray("stages")
            val stages = List(stagesArr.length()) { i ->
                val s = stagesArr.getJSONObject(i)
                Stage(
                    s.getString("id"), s.getString("title"), s.getString("where"), s.getDouble("lat"), s.getDouble("lon"),
                    s.optString("hero").ifEmpty { null }, s.getString("letter"), s.getString("kind"), s.getString("intro"),
                    strings(s.getJSONArray("how")), s.getString("q"), strings(s.getJSONArray("opts")), s.getInt("a")
                )
            }
            val quiz = root.getJSONArray("quiz").let { arr ->
                List(arr.length()) { i ->
                    val q = arr.getJSONObject(i)
                    QuizItem(q.getString("q"), strings(q.getJSONArray("o")), q.getInt("a"), q.getString("e"))
                }
            }
            val road = try {
                val ra = org.json.JSONArray(ctx.assets.open("road.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
                List(ra.length()) { i -> ra.getJSONObject(i).let { r -> Road(r.getString("stage"), r.getString("when"), r.getString("title"), r.getString("sub"), r.getString("audio"), strings(r.getJSONArray("text"))) } }
            } catch (e: Exception) { emptyList() }
            return Content(root.getString("word"), root.getString("site"), heroes, plaques, stages, quiz, stagesArr.toString(), road)
        }
    }
}

/* ---------- география ---------- */
object Geo {
    private const val R = 6371000.0
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * atan2(sqrt(a), sqrt(1 - a))
    }
    /** направление на точку, градусы от севера по часовой */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2); val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
    fun angleDiff(a: Double, b: Double): Double { var d = (a - b) % 360; if (d > 180) d -= 360; if (d < -180) d += 360; return d }
    fun human(m: Double): String = if (m < 1000) "${(m / 10).toInt() * 10} м" else String.format("%.1f км", m / 1000).replace('.', ',')
}
