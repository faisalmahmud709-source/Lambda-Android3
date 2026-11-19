package com.lambda.app.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.app.alarm.AlarmItem
import com.lambda.app.alarm.AlarmScheduler
import com.lambda.app.alarm.AlarmService
import com.lambda.app.alarm.AlarmStore
import com.lambda.app.alarm.MathChallenge
import com.lambda.app.ui.*
import com.lambda.app.util.formatTime24to12
import java.time.Duration
import java.time.ZonedDateTime

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S") // index 0 = Monday (value 1)
private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun timeStr(a: AlarmItem) = "%02d:%02d".format(a.hour, a.minute)

private fun daysSummary(days: List<Int>): String = when {
    days.isEmpty() -> "Once"
    days.size == 7 -> "Every day"
    days.sorted() == listOf(1, 2, 3, 4, 5) -> "Weekdays"
    days.sorted() == listOf(6, 7) -> "Weekends"
    else -> days.sorted().joinToString(" ") { DAY_NAMES[it - 1] }
}

private fun ringsIn(a: AlarmItem): String {
    val d = Duration.between(ZonedDateTime.now(), AlarmScheduler.nextTrigger(a))
    val mins = d.toMinutes().coerceAtLeast(0)
    val days = mins / (24 * 60)
    val hours = (mins % (24 * 60)) / 60
    val m = mins % 60
    return when {
        days > 0 -> "Rings in ${days}d ${hours}h"
        hours > 0 -> "Rings in ${hours}h ${m}m"
        else -> "Rings in ${m}m"
    }
}

private fun soundName(ctx: Context, uri: String?): String {
    if (uri == null) return "Default alarm sound"
    return runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(uri))?.getTitle(ctx) }.getOrNull() ?: "Custom sound"
}

// ---------------------------------------------------------------- permission helpers

private fun notificationsGranted(c: Context) =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun overlayGranted(c: Context) = Settings.canDrawOverlays(c)

private fun fullScreenGranted(c: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 34) (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent() else true

private fun batteryGranted(c: Context) =
    (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

private fun openSettings(c: Context, vararg intents: Intent) {
    for (i in intents) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmScreen() {
    val ctx = LocalContext.current
    val store = remember { AlarmStore.get(ctx) }
    val alarms by store.alarms.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<AlarmItem?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<AlarmItem?>(null) }

    // permissions are re-checked every time the user comes back from a system settings page
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notif = remember(tick) { notificationsGranted(ctx) }
    val overlay = remember(tick) { overlayGranted(ctx) }
    val fullScreen = remember(tick) { fullScreenGranted(ctx) }
    val battery = remember(tick) { batteryGranted(ctx) }
    val allGranted = notif && overlay && fullScreen && battery

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri: Uri? = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        draft = draft?.copy(soundUri = uri?.toString())
    }

    // ask for the notification permission as soon as the Alarm page is opened
    LaunchedEffect(Unit) {
        if (!notificationsGranted(ctx) && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val pkg = Uri.parse("package:${ctx.packageName}")

    ScreenColumn {
        PageHeader(
            "Wake up", "Alarm",
            "Set alarms like any alarm app, with one difference: there is no snooze. When it rings, you must solve ${MathChallenge.COUNT} math problems to stop it."
        )

        if (!allGranted) {
            LCard(title = "Permissions needed", action = { Icon(Icons.Filled.Warning, null, tint = LC.Amber, modifier = Modifier.size(16.dp)) }) {
                Text("For the alarm to ring reliably (even with the screen off or the phone locked), please allow:", color = LC.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!notif) PermRow("Notifications", "Show the alarm on the lock screen") {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!fullScreen) PermRow("Full-screen alerts", "Open the alarm screen over the lock screen") {
                        if (Build.VERSION.SDK_INT >= 34) openSettings(ctx, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
                    }
                    if (!overlay) PermRow("Display over other apps", "Bring the alarm screen back if it is hidden") {
                        openSettings(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
                    }
                    if (!battery) PermRow("Ignore battery optimisation", "Stops the phone from delaying the alarm") {
                        openSettings(
                            ctx,
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                }
            }
        }

        PrimaryButton("Add alarm", {
            draft = AlarmItem(id = "alm-${System.currentTimeMillis()}", hour = 7, minute = 0)
            isNew = true
        }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)

        if (alarms.isEmpty()) {
            LCard { EmptyState("No alarms yet", "Tap \"Add alarm\" to create your first one.", Icons.Filled.Alarm) }
        } else {
            alarms.sortedWith(compareBy<AlarmItem>({ it.hour }, { it.minute })).forEach { a ->
                LCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                formatTime24to12(timeStr(a)), fontFamily = MonoFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp,
                                color = if (a.enabled) LC.Text else LC.Muted
                            )
                            Text(
                                buildString {
                                    append(daysSummary(a.days))
                                    if (a.label.isNotBlank()) append(" · ").append(a.label)
                                },
                                color = LC.Muted, fontSize = 12.sp
                            )
                            if (a.enabled) Text(ringsIn(a), color = LC.Primary, fontSize = 11.sp, fontFamily = MonoFont)
                        }
                        Switch(
                            checked = a.enabled,
                            onCheckedChange = { on -> AlarmScheduler.save(ctx, a.copy(enabled = on)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = LC.OnPrimary, checkedTrackColor = LC.Primary)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        GhostButton("Edit", { draft = a; isNew = false }, icon = Icons.Filled.Edit)
                        Spacer(Modifier.width(8.dp))
                        GhostButton("Delete", { toDelete = a }, icon = Icons.Filled.Delete, color = LC.Red, borderColor = LC.Red.copy(alpha = 0.4f))
                    }
                }
            }
        }

        LCard(title = "How it works") {
            Text("• No snooze button and no dismiss button.", color = LC.Muted, fontSize = 12.sp)
            Text("• ${MathChallenge.COUNT} problems: two-digit multiplication and three-digit addition.", color = LC.Muted, fontSize = 12.sp)
            Text("• Volume buttons, the Back button and Home do not stop it. Only solving the problems does.", color = LC.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            GhostButton("Test the alarm now", { AlarmService.start(ctx, "Test alarm", true, null) }, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
        }
    }

    // ---- editor
    draft?.let { d ->
        FormDialog(
            title = if (isNew) "New alarm" else "Edit alarm",
            confirmText = "Save",
            onConfirm = {
                AlarmScheduler.save(ctx, d.copy(enabled = true))
                draft = null
            },
            onDismiss = { draft = null }
        ) {
            GhostButton(formatTime24to12(timeStr(d)), { showTime = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Schedule)
            LTextField(d.label, { draft = d.copy(label = it.take(40)) }, placeholder = "Label (optional)", label = "Label")
            Eyebrow("Repeat")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DAY_LETTERS.forEachIndexed { i, l ->
                    val day = i + 1
                    Chip(l, d.days.contains(day), {
                        draft = d.copy(days = if (d.days.contains(day)) d.days - day else (d.days + day).sorted())
                    })
                }
            }
            Text(daysSummary(d.days), color = LC.Muted, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrate", color = LC.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = d.vibrate, onCheckedChange = { draft = d.copy(vibrate = it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = LC.OnPrimary, checkedTrackColor = LC.Primary)
                )
            }
            GhostButton("Sound: ${soundName(ctx, d.soundUri)}", {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, d.soundUri?.let { Uri.parse(it) })
                runCatching { soundLauncher.launch(i) }
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.MusicNote)
        }
        if (showTime) {
            TimeTargetDialog(
                "Alarm time", timeStr(d),
                onSave = { t ->
                    val p = t.split(":")
                    draft = d.copy(hour = p[0].toIntOrNull() ?: d.hour, minute = p.getOrNull(1)?.toIntOrNull() ?: d.minute)
                    showTime = false
                },
                onClear = null, onDismiss = { showTime = false }
            )
        }
    }

    toDelete?.let { a ->
        ConfirmDialog("Delete this alarm?", "${formatTime24to12(timeStr(a))} ${a.label}".trim(), "Delete", true,
            onConfirm = { AlarmScheduler.delete(ctx, a); toDelete = null }, onDismiss = { toDelete = null })
    }
}

@Composable
private fun PermRow(title: String, desc: String, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(LC.White05).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LC.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = LC.Muted, fontSize = 11.sp)
        }
        GhostButton("Allow", onGrant)
    }
}
