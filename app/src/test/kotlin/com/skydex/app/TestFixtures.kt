package com.skydex.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.skydex.app.data.local.Selection
import com.skydex.app.data.local.SettingsStore
import com.skydex.app.data.remote.SkydexApi
import com.skydex.app.data.remote.SkydexJson
import com.skydex.app.data.remote.createHttpClient
import com.skydex.app.notifications.PushTokens
import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.ProfileSummary
import com.skydex.shared.model.SkillLevel
import com.skydex.shared.model.SkyblockProfile
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Routes Dispatchers.Main (and so viewModelScope) to a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** A real DataStore in a temp folder; call [close] when done. */
class TestStore(folder: TemporaryFolder) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val store = SettingsStore(
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.newFolder(), "test.preferences_pb") },
    )

    fun close() = scope.cancel()
}

/** A [SkydexApi] backed by Ktor's MockEngine; set [handler] per test and inspect [requests]. */
class FakeServer {
    val requests = mutableListOf<HttpRequestData>()
    var handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respondJson("""{"message":"Not found"}""", HttpStatusCode.NotFound)
    }
    val api = SkydexApi(
        createHttpClient(
            MockEngine { request ->
                requests += request
                handler(request)
            },
            "http://test/",
        ),
    )
}

fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

inline fun <reified T> MockRequestHandleScope.respondJson(value: T) = respondJson(SkydexJson.encodeToString(value))

class FakePushTokens(override val isConfigured: Boolean = true, private val token: String? = "fcm-token") :
    PushTokens {
    override suspend fun token() = token
}

val samplePlayer = PlayerProfiles(
    uuid = "abc123",
    username = "Technoblade",
    profiles = listOf(
        ProfileSummary(profileId = "p1", cuteName = "Mango", selected = true),
        ProfileSummary(profileId = "p2", cuteName = "Kiwi", gameMode = "ironman", selected = false),
    ),
)

val sampleSelection = Selection(uuid = "abc123", username = "Technoblade", profileId = "p1", cuteName = "Mango")

val sampleProfile = SkyblockProfile(
    profileId = "p1",
    cuteName = "Mango",
    uuid = "abc123",
    username = "Technoblade",
    skyblockLevel = 212.45,
    purse = 1_250_000.0,
    bankBalance = null,
    fairySouls = 240,
    skills = listOf(SkillLevel("farming", 1e7, 40, 60, 0.5)),
    slayers = emptyList(),
    fetchedAt = 1_700_000_000_000L,
)
