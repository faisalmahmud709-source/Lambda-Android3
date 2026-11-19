package com.lambda.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.app.LambdaViewModel
import com.lambda.app.data.Task
import com.lambda.app.ui.*
import com.lambda.app.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(vm: LambdaViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val now by rememberNow(1000)
    val today = todayStr()

    var selectedDate by rememberSaveable { mutableStateOf(today) }
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("p2") }
    var dueTime by remember { mutableStateOf<String?>("22:00") } // null = no time
    var inboxTitle by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomTime by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    // routine import
    val context = LocalContext.current
    var routineText by remember { mutableStateOf("") }
    var routineWeeks by remember { mutableIntStateOf(0) } // 0 = no repetition
    var reading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var parsed by remember { mutableStateOf<List<ParsedTask>?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            reading = true
            scope.launch {
                val text = RoutineOcr.read(context, uri)
                reading = false
                if (text.isNullOrBlank()) {
                    Toast.makeText(context, "Couldn't read any text from that file. Try a clearer photo or PDF, or paste the routine below.", Toast.LENGTH_LONG).show()
                } else {
                    routineText = text
                    parsed = RoutineParser.parse(text, routineWeeks)
                }
            }
        }
    }

    val dayTasks = data.tasks.filter { it.date == selectedDate }
    val doneCount = dayTasks.count { it.done }
    val overdue = data.tasks.filter { !it.done && it.date != null && it.date < today }
    val inbox = data.tasks.filter { !it.done && it.date == null }

    ScreenColumn {
        PageHeader("Plan", "Daily planner", "Give every day a short list. Specify target completion times like 10 PM or 1 AM.")

        LCard {
            // date navigator
            Row(verticalAlignment = Alignment.CenterVertically) {
                LIconButton(Icons.Filled.ChevronLeft, "Previous day", { selectedDate = shiftDateStr(selectedDate, -1) })
                LIconButton(Icons.Filled.ChevronRight, "Next day", { selectedDate = shiftDateStr(selectedDate, 1) })
                Spacer(Modifier.width(6.dp))
                Row(Modifier.weight(1f).clickable { showDatePicker = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(prettyDate(selectedDate), color = LC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (selectedDate == today) { Spacer(Modifier.width(8.dp)); Badge("TODAY", LC.Primary) }
                }
                if (selectedDate != today) {
                    Text("Today", color = LC.Primary, fontSize = 12.sp, modifier = Modifier.clickable { selectedDate = today }.padding(6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // add bar
            LTextField(title, { title = it }, placeholder = "What needs to get done on this day?")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("p1" to "P1 Urgent", "p2" to "P2 High", "p3" to "P3 Normal", "p4" to "P4 Low").forEach { (k, l) ->
                    Chip(l, priority == k, { priority = k }, Modifier.weight(1f), mono = false)
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Schedule, null, tint = LC.Muted, modifier = Modifier.size(14.dp))
                Eyebrow("By when")
                Chip("10:00 PM", dueTime == "22:00", { dueTime = "22:00" })
                Chip("11:30 PM", dueTime == "23:30", { dueTime = "23:30" })
                Chip("1:00 AM", dueTime == "01:00", { dueTime = "01:00" })
                Chip("Custom…", dueTime != null && dueTime !in listOf("22:00", "23:30", "01:00"), { showCustomTime = true })
                Chip("No time", dueTime == null, { dueTime = null }, accent = LC.Red)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (dueTime != null) "Due by ${formatTime24to12(dueTime)}" else "Anytime during the day",
                color = LC.Muted, fontSize = 11.sp, fontFamily = MonoFont
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Add task", {
                if (title.isNotBlank()) {
                    vm.addTask(title, priority, selectedDate, dueTime)
                    title = ""
                }
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add, enabled = title.isNotBlank())

            Spacer(Modifier.height(14.dp))
            if (dayTasks.isEmpty()) {
                EmptyState("Nothing planned for this day", "Write down your plan or move items from your Inbox below.", Icons.Filled.CalendarMonth)
            } else {
                Row {
                    Text("$doneCount of ${dayTasks.size} completed", color = LC.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("${doneCount * 100 / dayTasks.size}%", color = LC.Primary, fontFamily = MonoFont, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                LinearBar(doneCount.toFloat() / dayTasks.size)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayTasks.forEach { t ->
                        TaskRow(
                            t, calculateTaskRemaining(t, now),
                            onToggle = { vm.toggleTask(t.id) }, onDelete = { vm.deleteTask(t.id) },
                            onTimeClick = { editing = t }, showCountdown = selectedDate == today
                        )
                    }
                }
            }
        }

        LCard(title = "Import routine", action = { Icon(Icons.Filled.UploadFile, null, tint = LC.Muted, modifier = Modifier.size(16.dp)) }) {
            Text(
                "Upload your class / exam routine as a PDF, JPG or PNG (Bangla or English), or paste it as text. Dates, weekdays and times are detected and tasks are added to the planner for you.",
                color = LC.Muted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("e.g.  12 Dec 2026  10:30 AM  Physics Exam      or      রবিবার সকাল ১০টা রসায়ন ক্লাস", color = LC.Muted, fontSize = 10.sp, fontFamily = MonoFont)
            Spacer(Modifier.height(10.dp))
            GhostButton(
                if (reading) "Reading your routine…" else "Choose routine (PDF / JPG / PNG)",
                { if (!reading) fileLauncher.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) },
                Modifier.fillMaxWidth(), icon = Icons.Filled.FileUpload
            )
            if (reading) {
                Spacer(Modifier.height(6.dp))
                LinearBar(0f, color = LC.Primary)
                Text("Recognising text on this phone — this can take a few seconds per page.", color = LC.Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            LTextField(routineText, { routineText = it }, placeholder = "…or paste your routine here", singleLine = false, minLines = 3, maxLines = 8)
            Spacer(Modifier.height(8.dp))
            FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("Repeat weekly", Modifier.align(Alignment.CenterVertically))
                Chip("None", routineWeeks == 0, { routineWeeks = 0 })
                listOf(1, 2, 4, 8).forEach { w -> Chip("$w wk", routineWeeks == w, { routineWeeks = w }) }
            }
            Text(
                if (routineWeeks == 0) "None: weekday lines are added once, with no repetition." else "Weekday-only lines repeat for $routineWeeks week${if (routineWeeks == 1) "" else "s"}.",
                color = LC.Muted, fontSize = 10.sp
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Preview tasks", {
                parsed = RoutineParser.parse(routineText, routineWeeks)
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.Search, enabled = routineText.isNotBlank())
        }

        if (overdue.isNotEmpty()) {
            LCard(title = "Overdue · ${overdue.size}", action = { Icon(Icons.Filled.Warning, null, tint = LC.Red, modifier = Modifier.size(16.dp)) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    overdue.forEach { t ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, color = LC.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                LIconButton(Icons.Filled.Delete, "Delete", { vm.deleteTask(t.id) }, size = 30.dp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Due ${t.date}" + (t.dueTime?.let { " · by ${formatTime24to12(it)}" } ?: ""),
                                    color = LC.Muted, fontSize = 11.sp, fontFamily = MonoFont, modifier = Modifier.weight(1f)
                                )
                                Text("Move to today →", color = LC.Primary, fontSize = 12.sp, modifier = Modifier.clickable { vm.moveTaskToDate(t.id, today) }.padding(4.dp))
                            }
                        }
                    }
                }
            }
        }

        LCard(title = "Inbox", action = { Icon(Icons.Filled.Inbox, null, tint = LC.Muted, modifier = Modifier.size(16.dp)) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LTextField(inboxTitle, { inboxTitle = it }, Modifier.weight(1f), placeholder = "Unscheduled task…")
                PrimaryButton("", {
                    if (inboxTitle.isNotBlank()) {
                        vm.addTask(inboxTitle, "p3", null, null)
                        inboxTitle = ""
                    }
                }, icon = Icons.Filled.Add, enabled = inboxTitle.isNotBlank(), modifier = Modifier.width(52.dp))
            }
            Spacer(Modifier.height(10.dp))
            if (inbox.isEmpty()) {
                EmptyState("Inbox zero", "Capture tasks you haven't scheduled yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    inbox.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.title, color = LC.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                "Plan for ${if (selectedDate == today) "today" else "this date"}", color = LC.Primary, fontSize = 12.sp,
                                modifier = Modifier.clickable { vm.moveTaskToDate(t.id, selectedDate) }.padding(6.dp)
                            )
                            LIconButton(Icons.Filled.Delete, "Delete", { vm.deleteTask(t.id) }, size = 30.dp)
                        }
                    }
                }
            }
        }
    }

    parsed?.let { list ->
        if (list.isEmpty()) {
            ConfirmDialog(
                "Nothing recognised", "No tasks or exams were found. Put each item on its own line with a date or weekday, e.g. \"12 Dec 2026 10:30 AM Physics Exam\".",
                "OK", false, onConfirm = { parsed = null }, onDismiss = { parsed = null }
            )
        } else {
            val exams = list.count { it.isExam }
            FormDialog(
                title = "Add ${list.size} item${if (list.size == 1) "" else "s"}?",
                confirmText = "Add to planner",
                onConfirm = {
                    val n = vm.addParsedTasks(list)
                    Toast.makeText(context, if (n == 0) "Everything was already in your planner." else "$n added to your planner.", Toast.LENGTH_SHORT).show()
                    parsed = null
                    routineText = ""
                },
                onDismiss = { parsed = null }
            ) {
                Text("$exams exam/test · ${list.size - exams} other", color = LC.Muted, fontSize = 12.sp)
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { t ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, color = LC.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (t.isExam) Badge("EXAM", LC.Red)
                            }
                            Text(
                                (t.date?.let { prettyDate(it) } ?: "Inbox (no date)") + (t.dueTime?.let { " · ${formatTime24to12(it)}" } ?: ""),
                                color = LC.Muted, fontSize = 11.sp, fontFamily = MonoFont
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) LDatePickerDialog(selectedDate, { selectedDate = it }, { showDatePicker = false })
    if (showCustomTime) {
        TimeTargetDialog("Set target time", dueTime, onSave = { dueTime = it; showCustomTime = false }, onClear = null, onDismiss = { showCustomTime = false })
    }
    editing?.let { t ->
        TimeTargetDialog(
            "Set target time", t.dueTime,
            onSave = { vm.updateTask(t.id) { x -> x.copy(dueTime = it) }; editing = null },
            onClear = { vm.updateTask(t.id) { x -> x.copy(dueTime = null) }; editing = null },
            onDismiss = { editing = null }
        )
    }
}
