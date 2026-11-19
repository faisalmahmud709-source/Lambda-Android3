package com.lambda.app.ui.screens

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.lambda.app.data.DailyLogEntry
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.app.LambdaViewModel
import com.lambda.app.ui.*
import com.lambda.app.util.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as MonthStyle
import java.util.Locale
import java.time.ZoneId
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private data class DayPoint(
    val date: String, val label: String, val studyHours: Double,
    val sleep: Double?, val screen: Double?, val tasksDone: Int, val habitPct: Int
)

@Composable
fun AnalyticsScreen(vm: LambdaViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableIntStateOf(30) } // 7 | 30 | 365
    var logToDelete by remember { mutableStateOf<String?>(null) }

    val points = remember(data, range) {
        val habits = data.habits
        (range - 1 downTo 0).map { back ->
            val d = LocalDate.now().minusDays(back.toLong())
            val ds = d.toString()
            val sessionMins = data.sessions.filter { it.date == ds }.sumOf { it.minutes }
            val extra = data.dailyLogs[ds]?.extraStudyHours ?: 0.0
            val tasksDone = data.tasks.count { t ->
                t.done && t.completedAt != null &&
                    runCatching { Instant.parse(t.completedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.getOrNull() == ds
            }
            val hd = habits.count { it.log[ds] == true }
            DayPoint(
                ds, "${d.dayOfMonth}/${d.monthValue}", ((sessionMins / 60.0 + extra) * 10).roundToInt() / 10.0,
                data.dailyLogs[ds]?.sleepHours, data.dailyLogs[ds]?.screenHours, tasksDone,
                if (habits.isNotEmpty()) (hd * 100.0 / habits.size).roundToInt() else 0
            )
        }
    }
    val dates = points.map { it.date }.toSet()
    val breakdown = remember(data, range) {
        val map = linkedMapOf<String, Int>()
        data.sessions.filter { it.date in dates }.forEach { s ->
            val n = data.subjects.firstOrNull { it.id == s.subjectId }?.name ?: "General"
            map[n] = (map[n] ?: 0) + s.minutes
        }
        map.entries.sortedByDescending { it.value }
    }

    val totalHours = points.sumOf { it.studyHours }
    val avgDaily = totalHours / range
    val goalDays = points.count { it.studyHours >= data.settings.dailyStudyGoalHours }
    val tasksDone = points.sumOf { it.tasksDone }
    val sleepVals = points.mapNotNull { it.sleep }
    val screenVals = points.mapNotNull { it.screen }
    val avgSleep = if (sleepVals.isNotEmpty()) "%.1fh".format(sleepVals.average()) else "—"
    val avgScreen = if (screenVals.isNotEmpty()) "%.1fh".format(screenVals.average()) else "—"
    val avgHabit = (points.sumOf { it.habitPct } / max(1, points.size))
    val labels = points.map { it.label }

    // ---- chart series. Up to 30 days: one bar per day, every date written (day number only).
    //      One year: one bar per month (average), so the chart stays readable.
    val yearly = range > 31
    fun series(pick: (DayPoint) -> Float?, average: Boolean = true): Pair<List<Float>, List<String>> {
        if (!yearly) return points.map { pick(it) ?: 0f } to points.map { it.label.substringBefore('/') }
        val byMonth = points.groupBy { YearMonth.parse(it.date.take(7)) }.toSortedMap()
        val vals = byMonth.values.map { days ->
            val v = days.mapNotNull(pick)
            if (v.isEmpty()) 0f else if (average) v.average().toFloat() else v.sum()
        }
        return vals to byMonth.keys.map { it.month.getDisplayName(MonthStyle.SHORT, Locale.ENGLISH) }
    }
    val (studySeries, chartLabels) = series({ it.studyHours.toFloat() }, average = true)
    val (sleepSeries, _) = series({ it.sleep?.toFloat() })
    val (screenSeries, _) = series({ it.screen?.toFloat() })

    // ---- manual entry (study / screen time / sleep) for any date
    var entryDate by rememberSaveable { mutableStateOf(todayStr()) }
    var showEntryPicker by remember { mutableStateOf(false) }
    var entrySaved by remember { mutableStateOf(false) }
    val existing = data.dailyLogs[entryDate]
    fun fmtH(v: Double?): String = v?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
    var manualStudy by remember(entryDate) { mutableStateOf(fmtH(existing?.extraStudyHours)) }
    var manualScreen by remember(entryDate) { mutableStateOf(fmtH(existing?.screenHours)) }
    var manualSleep by remember(entryDate) { mutableStateOf(fmtH(existing?.sleepHours)) }
    val timerMinutes = data.sessions.filter { it.date == entryDate }.sumOf { it.minutes }
    val timerHours = timerMinutes / 60.0
    val totalStudyToday = timerHours + (manualStudy.toDoubleOrNull() ?: 0.0)
    LaunchedEffect(entrySaved) { if (entrySaved) { kotlinx.coroutines.delay(1800); entrySaved = false } }

    ScreenColumn {
        PageHeader("Patterns", "Analytics", "Log your study, sleep and screen time here. Time from the Pomodoro and Work session timers is added automatically.")

        LCard(title = "Add your numbers") {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(LC.Field)
                    .border(1.dp, LC.White10, RoundedCornerShape(10.dp)).clickable { showEntryPicker = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = LC.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(prettyDate(entryDate), color = LC.Text, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            NumberField(manualStudy, { manualStudy = it }, placeholder = "e.g. 2.5", label = "Study hours (manual, outside the timers)")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(manualScreen, { manualScreen = it }, Modifier.weight(1f), placeholder = "e.g. 3.5", label = "Screen time (h)")
                NumberField(manualSleep, { manualSleep = it }, Modifier.weight(1f), placeholder = "e.g. 7.5", label = "Sleep (h)")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "From Pomodoro & Work session (automatic): ${formatMinutes(timerMinutes)}",
                color = LC.Muted, fontSize = 11.sp
            )
            Text(
                "Total study on this day: %.1fh".format(totalStudyToday),
                color = LC.Primary, fontSize = 12.sp, fontFamily = MonoFont
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Save", {
                    val base = data.dailyLogs[entryDate] ?: DailyLogEntry(date = entryDate)
                    vm.saveDailyLog(
                        base.copy(
                            date = entryDate,
                            extraStudyHours = manualStudy.toDoubleOrNull() ?: 0.0,
                            screenHours = manualScreen.toDoubleOrNull(),
                            sleepHours = manualSleep.toDoubleOrNull()
                        )
                    )
                    entrySaved = true
                }, icon = Icons.Filled.Check)
                if (entrySaved) {
                    Spacer(Modifier.width(10.dp))
                    Text("Saved", color = LC.Emerald, fontSize = 12.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7 to "Last 7 days", 30 to "Last 30 days", 365 to "Last 1 year").forEach { (d, l) ->
                Chip(l, range == d, { range = d }, mono = false)
            }
        }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Studied", "%.1fh".format(totalHours), "avg %.1fh / day".format(avgDaily), Icons.Filled.HourglassTop, Modifier.weight(1f).fillMaxHeight())
            StatCard("Goal days", "$goalDays/$range", "hit ${data.settings.dailyStudyGoalHours}h goal", Icons.Filled.Flag, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Tasks done", "$tasksDone", "in this range", Icons.Filled.Checklist, Modifier.weight(1f).fillMaxHeight())
            StatCard("Habits", "$avgHabit%", "avg completion", Icons.Filled.LocalFireDepartment, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Avg sleep", avgSleep, "per logged day", Icons.Filled.Bedtime, Modifier.weight(1f).fillMaxHeight())
            StatCard("Avg screen", avgScreen, "per logged day", Icons.Filled.PhoneAndroid, Modifier.weight(1f).fillMaxHeight())
        }

        LCard(title = "Study hours") {
            BarChart(studySeries, chartLabels, LC.Primary, goal = data.settings.dailyStudyGoalHours.toFloat())
            Spacer(Modifier.height(6.dp))
            Text(
                "Dashed line = your daily goal (${data.settings.dailyStudyGoalHours}h)" + if (yearly) " · monthly average per day" else " · scroll sideways for earlier days",
                color = LC.Muted, fontSize = 10.sp
            )
        }

        LCard(title = "Timed study by subject") {
            if (breakdown.isEmpty()) {
                EmptyState("No timed sessions in this range", "Use the Pomodoro or Work session timers and they'll show up here.")
            } else {
                val max = breakdown.first().value.toFloat()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    breakdown.forEachIndexed { i, e ->
                        val c = LC.ChartColors[i % LC.ChartColors.size]
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                                Spacer(Modifier.width(8.dp))
                                Text(e.key, color = LC.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(formatMinutes(e.value), color = LC.Muted, fontFamily = MonoFont, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearBar(e.value / max, color = c)
                        }
                    }
                }
            }
        }

        LCard(title = "Sleep time") {
            BarChart(sleepSeries, chartLabels, LC.Indigo)
            Spacer(Modifier.height(6.dp))
            Text("Hours slept" + if (yearly) " · monthly average of logged days" else " each day", color = LC.Muted, fontSize = 10.sp)
        }

        LCard(title = "Screen time") {
            BarChart(screenSeries, chartLabels, LC.Red)
            Spacer(Modifier.height(6.dp))
            Text("Hours of screen time" + if (yearly) " · monthly average of logged days" else " each day", color = LC.Muted, fontSize = 10.sp)
        }

        LCard(title = "Habit completion trend") {
            LineChart(listOf(points.map { it.habitPct.toFloat() }), listOf(LC.Emerald), labels, fixedMax = 100f)
            Spacer(Modifier.height(6.dp))
            Text("% of your habits completed each day", color = LC.Muted, fontSize = 10.sp)
        }

        val recent = points.filter { it.sleep != null || it.screen != null || it.studyHours > 0.0 }.reversed()
        LCard(title = "Recent days") {
            if (recent.isEmpty()) {
                EmptyState("Nothing logged in this range", "Add your sleep, screen time and study above and each day will be listed here.", Icons.Filled.Assignment)
            } else {
                val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(LC.White05)
                                .clickable { entryDate = p.date }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(LocalDate.parse(p.date).format(fmt), color = LC.Text, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Sleep ${p.sleep?.let { fmtH(it) + "h" } ?: "—"} · Study ${fmtH(p.studyHours)}h · Screen ${p.screen?.let { fmtH(it) + "h" } ?: "—"}",
                                    color = LC.Muted, fontSize = 11.sp
                                )
                            }
                            if (data.dailyLogs.containsKey(p.date)) {
                                LIconButton(Icons.Filled.Delete, "Delete this day's entry", { logToDelete = p.date }, size = 30.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Tap a day to edit its numbers in “Add your numbers”.", color = LC.Muted, fontSize = 10.sp)
            }
        }
    }

    if (showEntryPicker) LDatePickerDialog(entryDate, { entryDate = it }, { showEntryPicker = false })
    logToDelete?.let { d ->
        ConfirmDialog("Delete this day's entry?", "This removes the sleep, screen time and manual study hours you logged for this date. Timer sessions are kept.", "Delete", true,
            onConfirm = { vm.deleteDailyLog(d); logToDelete = null }, onDismiss = { logToDelete = null })
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = LC.Muted, fontSize = 11.sp)
    }
}

private fun labelStep(n: Int) = max(1, ceil(n / 6.0).toInt())

@Composable
private fun BarChart(values: List<Float>, labels: List<String>, color: Color, goal: Float? = null, height: Dp = 170.dp) {
    val tm = rememberTextMeasurer()
    val axisStyle = TextStyle(color = LC.Muted, fontSize = 9.sp)
    val valueStyle = TextStyle(color = LC.Text, fontSize = 8.sp)
    val scroll = rememberScrollState()
    val slotMin = 26.dp // wide enough that every date label fits
    // open on the most recent days
    LaunchedEffect(values.size) { kotlinx.coroutines.delay(60); scroll.scrollTo(scroll.maxValue) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val needed = slotMin * values.size
        val w = if (needed > maxWidth) needed else maxWidth
        Box(Modifier.horizontalScroll(scroll)) {
            Canvas(Modifier.width(w).height(height)) {
                val n = values.size
                if (n == 0) return@Canvas
                val topPad = 14.dp.toPx()
                val bottomPad = 18.dp.toPx()
                val chartH = size.height - topPad - bottomPad
                val maxV = max(max(values.maxOrNull() ?: 0f, goal ?: 0f), 1f) * 1.1f
                val slot = size.width / n
                val barW = slot * 0.62f
                values.forEachIndexed { i, v ->
                    val h = chartH * (v / maxV)
                    drawRoundRect(
                        color, Offset(i * slot + (slot - barW) / 2, topPad + chartH - h),
                        Size(barW, max(h, 0f)), CornerRadius(3.dp.toPx())
                    )
                    if (v > 0f) {
                        val t = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)
                        val lay = tm.measure(t, valueStyle)
                        drawText(lay, topLeft = Offset(i * slot + slot / 2 - lay.size.width / 2f, topPad + chartH - h - lay.size.height - 1.dp.toPx()))
                    }
                }
                if (goal != null) {
                    val y = topPad + chartH - chartH * (goal / maxV)
                    drawLine(LC.Emerald.copy(alpha = 0.8f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
                // every label is written (day number, or month name in the yearly view)
                labels.forEachIndexed { i, l ->
                    val layout = tm.measure(l, axisStyle)
                    val x = (i * slot + slot / 2 - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(x, size.height - bottomPad + 4.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun LineChart(series: List<List<Float?>>, colors: List<Color>, labels: List<String>, fixedMax: Float? = null, height: Dp = 150.dp) {
    val tm = rememberTextMeasurer()
    val axisStyle = TextStyle(color = LC.Muted, fontSize = 9.sp)
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val n = labels.size
        if (n == 0) return@Canvas
        val topPad = 8.dp.toPx()
        val bottomPad = 16.dp.toPx()
        val chartH = size.height - topPad - bottomPad
        val maxV = fixedMax ?: max(series.flatten().filterNotNull().maxOrNull() ?: 1f, 1f) * 1.15f
        val slot = size.width / n
        // grid
        for (g in 0..3) {
            val y = topPad + chartH * g / 3f
            drawLine(Color(0x14FFFFFF), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        series.forEachIndexed { si, s ->
            val c = colors[si % colors.size]
            var prev: Offset? = null
            s.forEachIndexed { i, v ->
                if (v == null) { prev = null; return@forEachIndexed }
                val p = Offset(i * slot + slot / 2, topPad + chartH - chartH * (v / maxV))
                prev?.let { drawLine(c, it, p, 2.dp.toPx()) }
                drawCircle(c, 3.dp.toPx(), p)
                prev = p
            }
        }
        val step = labelStep(n)
        labels.forEachIndexed { i, l ->
            if (i % step == 0 || i == n - 1) {
                val layout = tm.measure(l, axisStyle)
                val x = (i * slot + slot / 2 - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, size.height - bottomPad + 3.dp.toPx()))
            }
        }
    }
}
