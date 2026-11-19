package com.lambda.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lambda.app.MainActivity
import java.time.ZonedDateTime

object AlarmScheduler {
    const val ACTION_FIRE = "com.lambda.app.alarm.FIRE"
    const val EXTRA_ID = "alarm_id"

    fun nextTrigger(a: AlarmItem, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        for (offset in 0..7) {
            val day = from.toLocalDate().plusDays(offset.toLong())
            val candidate = day.atTime(a.hour, a.minute).atZone(from.zone)
            if (!candidate.isAfter(from)) continue
            if (a.days.isEmpty() || a.days.contains(day.dayOfWeek.value)) return candidate
        }
        return from.plusDays(1)
    }

    private fun firePendingIntent(ctx: Context, a: AlarmItem): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, a.id)
        return PendingIntent.getBroadcast(ctx, a.id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(ctx: Context, a: AlarmItem) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTrigger(a).toInstant().toEpochMilli()
        val fire = firePendingIntent(ctx, a)
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            // setAlarmClock = exact, survives Doze, shows the alarm icon in the status bar
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
    }

    fun cancel(ctx: Context, a: AlarmItem) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(ctx, a))
    }

    fun rescheduleAll(ctx: Context) {
        AlarmStore.get(ctx).alarms.value.forEach { if (it.enabled) schedule(ctx, it) else cancel(ctx, it) }
    }

    // ---- helpers used by the UI

    fun save(ctx: Context, a: AlarmItem) {
        AlarmStore.get(ctx).update { list ->
            if (list.any { it.id == a.id }) list.map { if (it.id == a.id) a else it } else list + a
        }
        if (a.enabled) schedule(ctx, a) else cancel(ctx, a)
    }

    fun delete(ctx: Context, a: AlarmItem) {
        cancel(ctx, a)
        AlarmStore.get(ctx).update { list -> list.filter { it.id != a.id } }
    }
}
