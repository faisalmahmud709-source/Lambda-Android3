package com.lambda.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by AlarmManager at the exact alarm time. Not exported: only this app can trigger it. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
        val store = AlarmStore.get(context)
        val alarm = store.find(id) ?: return
        if (!alarm.enabled) return

        AlarmService.start(context, alarm.label, alarm.vibrate, alarm.soundUri)

        if (alarm.days.isEmpty()) {
            // one-time alarm: switch it off in the list
            store.update { list -> list.map { if (it.id == id) it.copy(enabled = false) else it } }
        } else {
            AlarmScheduler.schedule(context, alarm) // queue the next repeat
        }
    }
}

/** Re-arms every alarm after a reboot, an app update or a clock / time-zone change. */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> AlarmScheduler.rescheduleAll(context)
        }
    }
}
