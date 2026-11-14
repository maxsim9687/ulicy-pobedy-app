package space.maxsim.ulicy

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Прогресс квеста: этап, решённые этапы, блоки знака, очки. Хранится в SharedPreferences. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("quest", Context.MODE_PRIVATE)

    var stage by mutableStateOf(p.getInt("stage", 0)); private set
    var done by mutableStateOf(p.getString("done", "")!!.split(',').filter { it.isNotEmpty() }.map { it.toInt() }.toSet()); private set
    var blocks by mutableStateOf(p.getStringSet("blocks", emptySet())!!.toSet()); private set
    var checkins by mutableStateOf(p.getStringSet("checkins", emptySet())!!.toSet()); private set
    var points by mutableStateOf(p.getInt("points", 0)); private set
    var wordSolved by mutableStateOf(p.getBoolean("word", false)); private set
    var name by mutableStateOf(p.getString("name", "")!!); private set
    var quizBest by mutableStateOf(p.getInt("quizBest", 0)); private set

    private fun persist() {
        p.edit()
            .putInt("stage", stage).putString("done", done.joinToString(","))
            .putStringSet("blocks", blocks).putStringSet("checkins", checkins)
            .putInt("points", points).putBoolean("word", wordSolved).putString("name", name)
            .putInt("quizBest", quizBest).apply()
    }

    fun solve(index: Int, firstTry: Boolean) {
        if (index !in done) { done = done + index; points += if (firstTry) 10 else 5; persist() }
    }
    fun next(total: Int) { stage = (stage + 1).coerceAtMost(total); persist() }
    fun addBlock(id: String): Boolean {
        if (id in blocks) return false
        blocks = blocks + id; points += 20; persist(); return true
    }
    fun checkin(id: String): Boolean {
        if (id in checkins) return false
        checkins = checkins + id; points += 15; persist(); return true
    }
    fun solveWord() { if (!wordSolved) { wordSolved = true; points += 10; persist() } }
    fun updateName(v: String) { name = v; persist() }
    fun quizResult(score: Int) { if (score > quizBest) { quizBest = score; persist() } }
    fun reset() {
        stage = 0; done = emptySet(); blocks = emptySet(); checkins = emptySet(); points = 0; wordSolved = false
        persist()
    }
}
