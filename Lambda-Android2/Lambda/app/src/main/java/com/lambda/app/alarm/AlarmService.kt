package com.lambda.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps the alarm ringing. It has NO snooze / dismiss action:
 * it only stops once the ring screen reports that all math problems were solved
 * (see [AlarmRingActivity]). A 1.5 s watchdog re-forces the volume, restarts the sound if it
 * was interrupted and brings the ring screen back if it was hidden.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val watchdog = object : Runnable {
        override fun run() {
            if (!AlarmState.isRinging(this@AlarmService)) {
                stopSelf()
                return
            }
            forceVolume()
            if (player?.isPlaying != true) startSound()
            if (wakeLock?.isHeld != true) acquireWakeLock()
            if (!AlarmRingActivity.visible) showRingScreen()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            AlarmState.begin(
                this,
                intent.getStringExtra(EXTRA_LABEL) ?: "",
                intent.getBooleanExtra(EXTRA_VIBRATE, true),
                intent.getStringExtra(EXTRA_SOUND)
            )
        } else if (!AlarmState.isRinging(this)) {
            // restarted by the system after a kill, but nothing is ringing any more
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        if (!running) {
            running = true
            acquireWakeLock()
            startSound()
            startVibration()
            handler.removeCallbacks(watchdog)
            handler.postDelayed(watchdog, 1500)
        }
        showRingScreen()
        return START_STICKY
    }

    // ------------------------------------------------------------------ notification

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Alarm ringing", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while an alarm is ringing"
                setSound(null, null) // the sound is played by the service itself
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            this, 1, AlarmRingActivity.intent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = AlarmState.label(this).ifBlank { "Alarm" }
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Solve ${MathChallenge.COUNT} math problems to stop the alarm")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
        ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    // ------------------------------------------------------------------ ring screen

    private fun showRingScreen() {
        try {
            startActivity(AlarmRingActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Background launches can be blocked by Android; the full-screen notification covers that.
        }
    }

    // ------------------------------------------------------------------ sound / vibration / volume

    private fun startSound() {
        runCatching { player?.release() }
        player = null
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val candidates = listOfNotNull(
            AlarmState.sound(this)?.let { runCatching { Uri.parse(it) }.getOrNull() },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (e: Exception) {
                runCatching { mp.release() }
            }
        }
    }

    private fun startVibration() {
        if (!AlarmState.vibrate(this)) return
        val v = ContextCompat.getSystemService(this, Vibrator::class.java) ?: return
        vibrator = v
        try {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
        } catch (_: Exception) {
        }
    }

    /** Pressing the volume buttons can't silence the alarm: the alarm stream is pushed back to max. */
    private fun forceVolume() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lambda:alarm").also { wakeLock = it }
            if (!wl.isHeld) wl.acquire(60 * 60 * 1000L)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        running = false
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        runCatching { vibrator?.cancel() }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID) }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "lambda_alarm_ring"
        private const val NOTIF_ID = 7001
        private const val ACTION_START = "com.lambda.app.alarm.START"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_VIBRATE = "vibrate"
        private const val EXTRA_SOUND = "sound"

        fun start(ctx: Context, label: String, vibrate: Boolean, sound: String?) {
            val i = Intent(ctx, AlarmService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_LABEL, label).putExtra(EXTRA_VIBRATE, vibrate).putExtra(EXTRA_SOUND, sound)
            ContextCompat.startForegroundService(ctx, i)
        }

        /** Called by the ring screen after the last problem was solved. */
        fun stop(ctx: Context) {
            AlarmState.end(ctx)
            ctx.stopService(Intent(ctx, AlarmService::class.java))
        }
    }
}
