package com.skydex.server.notifications

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Sends pushes through Firebase Cloud Messaging using the service account JSON at [credentialsPath]. */
class FcmPushSender(credentialsPath: String) : PushSender {
    private val messaging: FirebaseMessaging = run {
        val credentials = File(credentialsPath).inputStream().use { GoogleCredentials.fromStream(it) }
        val app = FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build())
        FirebaseMessaging.getInstance(app)
    }

    override suspend fun send(fcmToken: String, alert: EventAlert): PushResult {
        val message = Message.builder()
            .setToken(fcmToken)
            .setNotification(Notification.builder().setTitle(alert.title).setBody(alert.body).build())
            .putData("eventType", alert.event.type.name)
            .putData("startsAt", alert.event.startsAt.toString())
            .putData("endsAt", alert.event.endsAt.toString())
            // Alerts are time-sensitive, so ask FCM not to batch them while the device dozes.
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .build()
        return try {
            withContext(Dispatchers.IO) { messaging.send(message) }
            PushResult.SENT
        } catch (e: FirebaseMessagingException) {
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED) PushResult.UNREGISTERED else throw e
        }
    }
}
