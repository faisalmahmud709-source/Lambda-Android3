package com.lambda.app.alarm

import android.content.Context
import com.lambda.app.data.LambdaJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.random.Random

/** One alarm. [days] uses java.time numbering (1 = Monday ... 7 = Sunday); empty = rings once. */
@Serializable
data class AlarmItem(
    val id: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val days: List<Int> = emptyList(),
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val soundUri: String? = null
)

@Serializable
data class AlarmFile(val alarms: List<AlarmItem> = emptyList())

/**
 * Alarms live in their own small JSON file (separate from the main app data) because the alarm
 * receiver / service must be able to read them even when the app UI is not running.
 */
class AlarmStore private constructor(context: Context) {
    private val file = File(context.filesDir, "lambda_alarms.json")
    private val _alarms = MutableStateFlow(load())
    val alarms: StateFlow<List<AlarmItem>> = _alarms.asStateFlow()

    private fun load(): List<AlarmItem> {
        if (!file.exists()) return emptyList()
        return try {
            LambdaJson.decodeFromString(AlarmFile.serializer(), file.readText()).alarms
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun update(block: (List<AlarmItem>) -> List<AlarmItem>) {
        val next = block(_alarms.value)
        _alarms.value = next
        try {
            val tmp = File(file.parentFile, "lambda_alarms.json.tmp")
            tmp.writeText(LambdaJson.encodeToString(AlarmFile.serializer(), AlarmFile(next)))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun find(id: String): AlarmItem? = _alarms.value.firstOrNull { it.id == id }

    companion object {
        @Volatile private var instance: AlarmStore? = null
        fun get(context: Context): AlarmStore =
            instance ?: synchronized(this) {
                instance ?: AlarmStore(context.applicationContext).also { instance = it }
            }
    }
}

// ------------------------------------------------------------------------------ math challenge

/** "a op b = answer" where op is '*' or '+'. */
data class MathProblem(val a: Int, val op: Char, val b: Int, val answer: Long) {
    val text: String get() = "$a ${if (op == '*') "×" else "+"} $b = ?"
    fun encode() = "$a|$op|$b|$answer"

    companion object {
        fun decode(s: String): MathProblem? {
            val p = s.split("|")
            if (p.size != 4) return null
            return MathProblem(
                p[0].toIntOrNull() ?: return null,
                p[1].firstOrNull() ?: return null,
                p[2].toIntOrNull() ?: return null,
                p[3].toLongOrNull() ?: return null
            )
        }
    }
}

object MathChallenge {
    const val COUNT = 5

    /** 3 two-digit multiplications + 2 three-digit additions, in random order. */
    fun generate(): List<MathProblem> {
        val list = mutableListOf<MathProblem>()
        repeat(3) {
            val a = Random.nextInt(12, 100)
            val b = Random.nextInt(12, 40)
            list += MathProblem(a, '*', b, a.toLong() * b)
        }
        repeat(2) {
            val a = Random.nextInt(100, 1000)
            val b = Random.nextInt(100, 1000)
            list += MathProblem(a, '+', b, a.toLong() + b)
        }
        return list.shuffled()
    }

    /** Accepts normal digits as well as Bangla (or any Unicode) digits. */
    fun parseAnswer(input: String): Long? {
        val sb = StringBuilder()
        for (c in input.trim()) {
            val d = Character.digit(c, 10)
            if (d >= 0) sb.append(d) else if (c == '-' && sb.isEmpty()) sb.append(c)
        }
        return sb.toString().toLongOrNull()
    }
}

// ------------------------------------------------------------------------------ ringing state

/** Persisted so the ring screen survives being relaunched / the process being restarted. */
object AlarmState {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("lambda_alarm_state", Context.MODE_PRIVATE)

    fun isRinging(c: Context) = prefs(c).getBoolean("ringing", false)
    fun label(c: Context) = prefs(c).getString("label", "") ?: ""
    fun vibrate(c: Context) = prefs(c).getBoolean("vibrate", true)
    fun sound(c: Context): String? = prefs(c).getString("sound", null)
    fun solved(c: Context) = prefs(c).getInt("solved", 0)
    fun setSolved(c: Context, n: Int) { prefs(c).edit().putInt("solved", n).apply() }

    fun problems(c: Context): List<MathProblem> {
        val saved = prefs(c).getString("problems", null)?.split(";")?.mapNotNull { MathProblem.decode(it) }
        return if (saved != null && saved.size == MathChallenge.COUNT) saved else MathChallenge.generate()
    }

    fun begin(c: Context, label: String, vibrate: Boolean, sound: String?) {
        val p = prefs(c)
        if (p.getBoolean("ringing", false)) return // already ringing: keep the current challenge
        p.edit()
            .putBoolean("ringing", true)
            .putString("label", label)
            .putBoolean("vibrate", vibrate)
            .putString("sound", sound)
            .putInt("solved", 0)
            .putString("problems", MathChallenge.generate().joinToString(";") { it.encode() })
            .apply()
    }

    fun end(c: Context) {
        prefs(c).edit().clear().apply()
    }
}
