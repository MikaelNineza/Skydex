package com.skydex.app.data.remote

import com.skydex.shared.model.ApiError
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.MayorStatus
import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.SkyblockProfile
import com.skydex.shared.model.StatsHistory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Inject
import kotlinx.serialization.json.Json

/** A non-2xx response from our server, carrying its [ApiError] message when it sent one. */
class ApiException(val status: HttpStatusCode, message: String) : Exception(message)

val SkydexJson = Json { ignoreUnknownKeys = true }

/** [baseUrl] must end with a slash; request paths below are relative to it. */
fun createHttpClient(engine: HttpClientEngine, baseUrl: String) = HttpClient(engine) {
    install(ContentNegotiation) { json(SkydexJson) }
    defaultRequest { url(baseUrl) }
}

/** Our server's REST API. The app never talks to Hypixel directly. */
class SkydexApi @Inject constructor(private val client: HttpClient) {

    suspend fun player(name: String): PlayerProfiles = client.get("v1/players/${name.path()}").bodyOrThrow()

    suspend fun profile(uuid: String, profileId: String): SkyblockProfile =
        client.get("v1/players/${uuid.path()}/profiles/${profileId.path()}").bodyOrThrow()

    suspend fun history(uuid: String, profileId: String, days: Int): StatsHistory =
        client.get("v1/players/${uuid.path()}/profiles/${profileId.path()}/history") {
            parameter("days", days)
        }.bodyOrThrow()

    suspend fun mayor(): MayorStatus = client.get("v1/mayor").bodyOrThrow()

    suspend fun contests(): List<JacobContest> = client.get("v1/contests").bodyOrThrow()

    suspend fun registerDevice(installationId: String, registration: DeviceRegistration) {
        client.put("v1/devices/${installationId.path()}") {
            contentType(ContentType.Application.Json)
            setBody(registration)
        }.bodyOrThrow<Unit>()
    }

    suspend fun unregisterDevice(installationId: String) {
        val response = client.delete("v1/devices/${installationId.path()}")
        // Already gone is fine.
        if (response.status != HttpStatusCode.NotFound) response.bodyOrThrow<Unit>()
    }

    private fun String.path() = encodeURLPathPart()

    private suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
        if (!status.isSuccess()) {
            val message = runCatching { body<ApiError>().message }.getOrNull() ?: "Server error (${status.value})"
            throw ApiException(status, message)
        }
        return if (T::class == Unit::class) Unit as T else body()
    }
}
