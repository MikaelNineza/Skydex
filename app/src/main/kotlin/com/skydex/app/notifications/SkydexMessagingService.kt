package com.skydex.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.skydex.app.MainActivity
import com.skydex.app.R
import com.skydex.app.data.local.SettingsStore
import com.skydex.app.data.repository.DeviceRepository
import com.skydex.shared.model.EventType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

const val EVENTS_CHANNEL_ID = "events"

fun createEventsChannel(context: Context) {
    val channel = NotificationChannel(EVENTS_CHANNEL_ID, "Events", NotificationManager.IMPORTANCE_DEFAULT)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/** Shows event alerts from the server and re-registers the device when its token changes. */
@AndroidEntryPoint
class SkydexMessagingService : FirebaseMessagingService() {

    @Inject lateinit var store: SettingsStore

    @Inject lateinit var devices: DeviceRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called with this device's token after [FirebaseSetup.init] registers, and again whenever it changes. */
    override fun onRegistered(token: String) {
        scope.launch {
            if (token == store.fcmToken()) return@launch
            store.setFcmToken(token)
            runCatching { devices.sync() }.onFailure { Log.w(TAG, "Re-registering with the new token failed", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val eventName = message.data["type"]?.let { type -> EventType.entries.find { it.name == type }?.displayName }
        val title = message.notification?.title ?: message.data["title"] ?: eventName ?: return
        val body = message.notification?.body ?: message.data["body"]
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, EVENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), notification)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SkydexMessaging"
    }
}
