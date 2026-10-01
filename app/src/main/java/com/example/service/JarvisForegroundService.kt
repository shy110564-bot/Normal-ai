package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Always-on Foreground Service (START_STICKY) allowing JARVIS v5.0
 * to remain active in the background and when the phone is locked.
 */
class JarvisForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_FOREGROUND) {
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: "Ji… sun rahi hun 💕 ('JARVIS' bolo)"
        val notification = buildNotification(statusText)
        runCatching {
            startForeground(NOTIFICATION_ID, notification)
            _isRunning.value = true
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Always-On Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps JARVIS active in background and on lock screen (Crafted by AK EXPLOITS)"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("◉ JARVIS v5.0 — Active")
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_always_on_channel"
        private const val NOTIFICATION_ID = 5001
        const val ACTION_STOP_FOREGROUND = "com.example.action.STOP_JARVIS_FG"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context, statusText: String = "Ji… sun rahi hun 💕 ('JARVIS' bolo)") {
            val intent = Intent(context, JarvisForegroundService::class.java).apply {
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java).apply {
                action = ACTION_STOP_FOREGROUND
            }
            runCatching { context.startService(intent) }
        }
    }
}
