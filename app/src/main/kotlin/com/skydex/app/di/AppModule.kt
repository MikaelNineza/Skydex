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
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.time.Clock
import javax.inject.Singleton

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
}

@Module
@InstallIn(SingletonComponent::class)
interface BindingsModule {
    @Binds
    fun pushTokens(impl: FirebasePushTokens): PushTokens
}
