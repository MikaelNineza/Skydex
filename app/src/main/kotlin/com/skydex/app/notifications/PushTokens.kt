package com.skydex.app.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.skydex.app.BuildConfig
import com.skydex.app.data.local.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/** Source of this device's push token. */
interface PushTokens {
    /** False when the build has no Firebase config; push features are then hidden. */
    val isConfigured: Boolean

    suspend fun token(): String?
}

/**
 * Firebase is set up by hand from BuildConfig fields (sourced from local.properties or the environment)
 * rather than a google-services.json, so an unconfigured build simply has no push.
 */
object FirebaseSetup {
    val options: FirebaseOptions? = listOf(
        BuildConfig.FIREBASE_APP_ID,
        BuildConfig.FIREBASE_API_KEY,
        BuildConfig.FIREBASE_PROJECT_ID,
        BuildConfig.FIREBASE_SENDER_ID,
    ).takeIf { values -> values.none { it.isBlank() } }?.let { (appId, apiKey, projectId, senderId) ->
        FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
            .setGcmSenderId(senderId)
            .build()
    }

    /**
     * Call once from Application.onCreate. Auto-init is off in the manifest until this runs.
     * The token arrives in [SkydexMessagingService.onRegistered].
     */
    fun init(context: Context) {
        val options = options ?: return
        FirebaseApp.initializeApp(context, options)
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        FirebaseMessaging.getInstance().register()
    }
}

/** Reads the token [SkydexMessagingService] stored; null until Firebase has registered this device. */
@Singleton
class FirebasePushTokens @Inject constructor(private val store: SettingsStore) : PushTokens {
    override val isConfigured: Boolean get() = FirebaseSetup.options != null

    override suspend fun token(): String? = if (isConfigured) store.fcmToken() else null
}
