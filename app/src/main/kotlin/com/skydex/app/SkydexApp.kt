package com.skydex.app

import android.app.Application
import com.skydex.app.notifications.FirebaseSetup
import com.skydex.app.notifications.createEventsChannel
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SkydexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseSetup.init(this)
        createEventsChannel(this)
    }
}
