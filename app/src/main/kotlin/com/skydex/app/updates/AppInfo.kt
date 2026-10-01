package com.skydex.app.updates

/** This build's version, from BuildConfig, and whether it checks for updates on launch. */
data class AppInfo(val versionName: String, val versionCode: Int, val checkUpdatesOnLaunch: Boolean)
