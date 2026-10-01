package com.skydex.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.skydex.app.BuildConfig
import com.skydex.app.data.remote.createHttpClient
import com.skydex.app.notifications.FirebasePushTokens
import com.skydex.app.notifications.PushTokens
import com.skydex.app.updates.ApkInstaller
import com.skydex.app.updates.AppInfo
import com.skydex.app.updates.PackageInstallerApkInstaller
import com.skydex.app.updates.createDownloadClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A scope that lives as long as the app process, for work that must outlive a screen (update downloads). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** The [HttpClient] for APK downloads from GitHub (no base URL, no automatic redirects). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadClient

/** The cache directory downloaded updates go to. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdatesDir

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun httpClient(): HttpClient = createHttpClient(OkHttp.create(), BuildConfig.BASE_URL)

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    @DownloadClient
    fun downloadClient(): HttpClient = createDownloadClient(OkHttp.create())

    @Provides
    @UpdatesDir
    fun updatesDir(@ApplicationContext context: Context): File = File(context.cacheDir, "updates")

    @Provides
    fun appInfo(): AppInfo =
        AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.CHECK_UPDATES_ON_LAUNCH)
}

@Module
@InstallIn(SingletonComponent::class)
interface BindingsModule {
    @Binds
    fun pushTokens(impl: FirebasePushTokens): PushTokens

    @Binds
    fun apkInstaller(impl: PackageInstallerApkInstaller): ApkInstaller
}
