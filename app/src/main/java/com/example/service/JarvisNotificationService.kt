package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceNotificationItem(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

class JarvisNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isListenerConnected.value = true
        refreshNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) {
            instance = null
            _isListenerConnected.value = false
        }
    }

    fun refreshNotifications(): List<DeviceNotificationItem> {
        return try {
            val active = activeNotifications ?: emptyArray()
            val mapped = active.mapNotNull { sbn ->
                val extras = sbn.notification?.extras ?: return@mapNotNull null
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                if (title.isEmpty() && text.isEmpty()) return@mapNotNull null
                DeviceNotificationItem(
                    packageName = sbn.packageName ?: "unknown",
                    title = title,
                    text = text,
                    postedAt = sbn.postTime
                )
            }.take(20)
            _notifications.value = mapped
            mapped
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        @Volatile
        var instance: JarvisNotificationService? = null
            private set

        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

        private val _notifications = MutableStateFlow<List<DeviceNotificationItem>>(emptyList())
        val notifications: StateFlow<List<DeviceNotificationItem>> = _notifications.asStateFlow()
    }
}
