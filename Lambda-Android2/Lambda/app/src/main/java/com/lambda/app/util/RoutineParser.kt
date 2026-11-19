package com.lambda.app.util

import java.time.DayOfWeek
import java.time.LocalDate

/** One task/exam found in an uploaded or pasted routine. */
data class ParsedTask(
    val title: String,
    val date: String?,      // yyyy-MM-dd, null = goes to the Inbox
    val dueTime: String?,   // HH:mm
    val priority: String,   // p1 = exam / test, p3 = normal
    val isExam: Boolean
)

/**
 * Turns a plain-text routine (English or Bangla, typed or from a .txt/.csv file) into planner tasks.
 *
 * Understands, per line (any combination, any order):
 *  - dates: 2026-12-05, 5/12/2026, 5 Dec 2026, Dec 5, ৫ ডিসেম্বর ২০২৬, ০৫/১২/২০২৬ ...
 *  - weekdays: Sunday, Sun, রবিবার ...  (repeated weekly for [weeks] weeks)
 *  - times: 10:30 AM, 4pm, 14:00, সকাল ১০টা, বিকাল ৪:৩০, রাত ৯টা ...
 *  - exams/tests: exam, test, quiz, viva, final, পরীক্ষা, টেস্ট, কুইজ ... (high priority)
 * A line that contains only a date or weekday works as a heading for the lines below it.
 */
object RoutineParser {

    private val monthWords: List<Pair<String, Int>> = listOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "sept" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "জানুয়ারি" to 1, "জানুয়ারী" to 1, "ফেব্রুয়ারি" to 2, "ফেব্রুয়ারী" to 2, "মার্চ" to 3, "এপ্রিল" to 4, "মে" to 5,
        "জুন" to 6, "জুলাই" to 7, "আগস্ট" to 8, "সেপ্টেম্বর" to 9, "অক্টোবর" to 10, "নভেম্বর" to 11, "ডিসেম্বর" to 12
    ).sortedByDescending { it.first.length }

    private val weekdayWords: List<Pair<String, DayOfWeek>> = listOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY, "tues" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "weds" to DayOfWeek.WEDNESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "thurs" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
        "সোমবার" to DayOfWeek.MONDAY, "মঙ্গলবার" to DayOfWeek.TUESDAY, "বুধবার" to DayOfWeek.WEDNESDAY,
        "বৃহস্পতিবার" to DayOfWeek.THURSDAY, "শুক্রবার" to DayOfWeek.FRIDAY, "শনিবার" to DayOfWeek.SATURDAY,
        "রবিবার" to DayOfWeek.SUNDAY
    ).sortedByDescending { it.first.length }

    private val opt = setOf(RegexOption.IGNORE_CASE)
    private val monthAlt = monthWords.joinToString("|") { Regex.escape(it.first) }
    private val weekdayAlt = weekdayWords.joinToString("|") { Regex.escape(it.first) }

    private val reIso = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
    private val reNumeric = Regex("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?!\\d)")
    private val reDayMonth = Regex("(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s*(?:of\\s+)?($monthAlt)(?![\\p{L}])\\.?(?:\\s*,?\\s*(\\d{4}))?", opt)
    private val reMonthDay = Regex("(?<![\\p{L}])($monthAlt)(?![\\p{L}])\\.?\\s+(\\d{1,2})(?!\\d)(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?", opt)
    private val reWeekday = Regex("(?<![\\p{L}])($weekdayAlt)(?![\\p{L}])", opt)

    private val reAmPm = Regex("(?<!\\d)(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![\\p{L}])", opt)
    private val bnPeriod = "সকাল|ভোর|দুপুর|বিকাল|বিকেল|সন্ধ্যা|সন্ধ্যায়|রাত|রাতে"
    private val reBnPeriod = Regex("($bnPeriod)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:টা|টায়)?")
    private val reBnTa = Regex("(?<!\\d)(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:টা|টায়)")
    private val re24 = Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)")

    private val reExam = Regex(
        "(?<![\\p{L}])(?:exam|test|quiz|viva|midterm|mid-term|final|assessment)|পরীক্ষা|টেস্ট|কুইজ|ভাইভা", opt
    )
    private val headerWords = setOf(
        "date", "day", "time", "subject", "task", "tasks", "title", "class", "topic", "exam", "routine", "schedule", "name"
    )
    private val reLeadingNumber = Regex("^\\d+[.)]\\s+")

    fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val d = Character.digit(c, 10)
            if (d >= 0 && !c.isLetter()) sb.append(('0' + d)) else sb.append(c)
        }
        return sb.toString()
    }

    private data class Found<T>(val value: T, val range: IntRange)

    private fun fixYear(y: Int) = if (y < 100) 2000 + y else y

    private fun makeDate(y: Int, m: Int, d: Int): LocalDate? =
        runCatching { LocalDate.of(y, m, d) }.getOrNull()

    /** A date written without a year means the nearest sensible one (not far in the past). */
    private fun withoutYear(m: Int, d: Int, today: LocalDate): LocalDate? {
        val thisYear = makeDate(today.year, m, d) ?: return null
        return if (thisYear.isBefore(today.minusDays(30))) makeDate(today.year + 1, m, d) else thisYear
    }

    private fun findDate(s: String, today: LocalDate): Found<LocalDate>? {
        reIso.find(s)?.let { m ->
            makeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return Found(it, m.range) }
        }
        reNumeric.find(s)?.let { m ->
            var d = m.groupValues[1].toInt()
            var mo = m.groupValues[2].toInt()
            if (mo > 12 && d <= 12) { val t = d; d = mo; mo = t } // tolerate month-first
            makeDate(fixYear(m.groupValues[3].toInt()), mo, d)?.let { return Found(it, m.range) }
        }
        reDayMonth.find(s)?.let { m ->
            val mo = monthWords.first { it.first.equals(m.groupValues[2], ignoreCase = true) }.second
            val d = m.groupValues[1].toInt()
            val y = m.groupValues[3]
            val date = if (y.isNotEmpty()) makeDate(y.toInt(), mo, d) else withoutYear(mo, d, today)
            if (date != null) return Found(date, m.range)
        }
        reMonthDay.find(s)?.let { m ->
            val mo = monthWords.first { it.first.equals(m.groupValues[1], ignoreCase = true) }.second
            val d = m.groupValues[2].toInt()
            val y = m.groupValues[3]
            val date = if (y.isNotEmpty()) makeDate(y.toInt(), mo, d) else withoutYear(mo, d, today)
            if (date != null) return Found(date, m.range)
        }
        return null
    }

    private fun findWeekday(s: String): Found<DayOfWeek>? {
        val m = reWeekday.find(s) ?: return null
        val dow = weekdayWords.first { it.first.equals(m.groupValues[1], ignoreCase = true) }.second
        return Found(dow, m.range)
    }

    private fun hhmm(h: Int, m: Int) = "%02d:%02d".format(h, m)

    private fun findTime(s: String): Found<String>? {
        reAmPm.find(s)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h in 1..12 && min in 0..59) {
                val pm = m.groupValues[3].lowercase().startsWith("p")
                h %= 12
                if (pm) h += 12
                return Found(hhmm(h, min), m.range)
            }
        }
        reBnPeriod.find(s)?.let { m ->
            var h = m.groupValues[2].toInt()
            val min = m.groupValues[3].ifEmpty { "0" }.toInt()
            if (h in 0..12 && min in 0..59) {
                when (m.groupValues[1]) {
                    "সকাল", "ভোর" -> if (h == 12) h = 0
                    "দুপুর" -> if (h in 1..5) h += 12 // 12 stays 12, 1-5 = early afternoon
                    "বিকাল", "বিকেল" -> if (h < 12) h += 12
                    "সন্ধ্যা", "সন্ধ্যায়" -> if (h < 12) h += 12
                    else -> { // রাত / রাতে
                        if (h == 12) h = 0 else if (h in 5..11) h += 12
                    }
                }
                return Found(hhmm(h % 24, min), m.range)
            }
        }
        re24.find(s)?.let { m ->
            return Found(hhmm(m.groupValues[1].toInt(), m.groupValues[2].toInt()), m.range)
        }
        reBnTa.find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h in 0..23 && min in 0..59) return Found(hhmm(h, min), m.range)
        }
        return null
    }

    private fun removeRange(s: String, r: IntRange): String =
        s.substring(0, r.first) + " " + s.substring(r.last + 1)

    private fun cleanTitle(raw: String): String {
        var t = raw
        // leftover time-range glue such as "- 11:30" or "to"
        t = t.replace(Regex("\\s(?:-|–|—|to|থেকে|হতে)\\s", opt), " ")
        t = t.replace(Regex("\\s+"), " ")
        return t.trim(' ', '-', '–', '—', ':', ',', '.', '(', ')', '[', ']', '/', '*', '•', '·', '@')
    }

    private fun nextOccurrence(from: LocalDate, dow: DayOfWeek): LocalDate {
        var d = from
        while (d.dayOfWeek != dow) d = d.plusDays(1)
        return d
    }

    /**
     * @param weeks how many weeks a weekday-only line (e.g. "Sunday 10 AM Physics") is repeated;
     *              0 = no repetition (added once, on its next occurrence).
     */
    fun parse(text: String, weeks: Int = 0, today: LocalDate = LocalDate.now()): List<ParsedTask> {
        val out = mutableListOf<ParsedTask>()
        var ctxDate: LocalDate? = null
        var ctxDay: DayOfWeek? = null

        for (rawLine in text.replace("\uFEFF", "").lines()) {
            var s = normalizeDigits(rawLine)
                .replace('\t', ' ').replace('|', ' ').replace(';', ' ').replace(',', ' ')
                .trim()
            if (s.isEmpty()) continue
            s = s.trimStart('-', '*', '•', '·', '#', ' ')
            s = reLeadingNumber.replace(s, "")

            val isExam = reExam.containsMatchIn(s)

            var date: LocalDate? = null
            var dow: DayOfWeek? = null
            findDate(s, today)?.let { date = it.value; s = removeRange(s, it.range) }
            if (date == null) findWeekday(s)?.let { dow = it.value; s = removeRange(s, it.range) }
            var time: String? = null
            findTime(s)?.let { time = it.value; s = removeRange(s, it.range) }
            // a second time in the same line (end of a range) is dropped from the title
            findTime(s)?.let { s = removeRange(s, it.range) }

            val title = cleanTitle(s)
            if (date == null && dow == null && time == null &&
                title.lowercase().split(' ').all { it in headerWords }
            ) continue // table header such as "Date Time Subject"
            if (title.isEmpty()) {
                // heading line: applies to the lines below
                if (date != null) { ctxDate = date; ctxDay = null }
                else if (dow != null) { ctxDay = dow; ctxDate = null }
                continue
            }

            val prio = if (isExam) "p1" else "p3"
            val effDate = date
            val effDay = if (effDate == null) (dow ?: if (ctxDate == null) ctxDay else null) else null
            val baseDate = effDate ?: ctxDate

            when {
                baseDate != null -> out += ParsedTask(title, baseDate.toString(), time, prio, isExam)
                effDay != null -> {
                    val first = nextOccurrence(today, effDay)
                    for (k in 0 until maxOf(1, weeks.coerceIn(0, 12))) {
                        out += ParsedTask(title, first.plusWeeks(k.toLong()).toString(), time, prio, isExam)
                    }
                }
                else -> out += ParsedTask(title, null, time, prio, isExam)
            }
        }
        return out.distinct()
    }
}
