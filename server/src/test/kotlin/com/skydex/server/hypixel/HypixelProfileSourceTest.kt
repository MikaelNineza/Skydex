package com.skydex.server.hypixel

import com.skydex.shared.model.PlayerRank
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import java.util.Base64
import kotlin.test.assertTrue
import com.skydex.shared.model.ProfileSummary
import com.skydex.shared.model.SkillLevel
import com.skydex.shared.model.SlayerLevel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

private const val TESTER = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"
private const val MANGO = "a1b2c3d4-e5f6-4a1b-8c9d-0e1f2a3b4c5d"
private const val KIWI = "f0e1d2c3-b4a5-4968-8776-5a4b3c2d1e0f"

class HypixelProfileSourceTest {
    private var now = 1_700_000_000_000L

    private fun source(): Pair<MockEngine, HypixelProfileSource> {
        val (engine, http) = mockHttp { request ->
            val body = if (request.url.host == "api.hypixel.net") profilesFixture
            else """{"id":"$TESTER","name":"Tester"}"""
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return engine to HypixelProfileSource(MojangClient(http), HypixelClient(http, "key"), clock = { now })
    }

    @Test
    fun listsProfiles() = runBlocking<Unit> {
        val (_, source) = source()

        val player = source.playerProfiles("Tester")

        assertEquals(TESTER, player.uuid)
        assertEquals("Tester", player.username)
        assertEquals(
            listOf(ProfileSummary(MANGO, "Mango", null, true), ProfileSummary(KIWI, "Kiwi", "ironman", false)),
            player.profiles,
        )
    }

    @Test
    fun mapsProfileFields() = runBlocking<Unit> {
        val (_, source) = source()

        val profile = source.profile(TESTER, MANGO)

        assertEquals(MANGO, profile.profileId)
        assertEquals("Mango", profile.cuteName)
        assertEquals("Tester", profile.username)
        assertEquals(212.45, profile.skyblockLevel, 1e-9)
        assertEquals(1234567.8, profile.purse)
        assertEquals(50000000.5, profile.bankBalance)
        assertEquals(238, profile.fairySouls)
        assertEquals(Leveling.FAIRY_SOULS_TOTAL, profile.fairySoulsTotal)
        assertEquals(289, profile.fairySoulsTotal)
        // (farming 50 + combat 1.6 + taming 0) / 10; runecrafting and social are cosmetic.
        assertEquals(5.16, profile.skillAverage!!, 1e-9)
        assertEquals(now, profile.fetchedAt)
        assertEquals(
            listOf(
                SkillLevel("farming", 55172425.0, 50, 60, 0.0),
                SkillLevel("combat", 125.0, 1, 60, 0.6),
                SkillLevel("runecrafting", 94450.0, 25, 25, 1.0),
                SkillLevel("social", 150.0, 2, 25, 0.0),
                SkillLevel("taming", 0.0, 0, 60, 0.0),
            ),
            profile.skills,
        )
        assertEquals(
            listOf(
                SlayerLevel("zombie", 1_000_000, 9),
                SlayerLevel("spider", 0, 0),
                SlayerLevel("wolf", 250, 3),
                SlayerLevel("vampire", 2_400, 5),
            ),
            profile.slayers,
        )
        assertEquals(10, profile.catacombs?.level)
    }

    @Test
    fun sparseProfileFallsBackToDefaults() = runBlocking<Unit> {
        val (_, source) = source()

        // Dashes and case in ids don't matter.
        val profile = source.profile(TESTER.uppercase(), KIWI.replace("-", ""))

        assertEquals(10.5, profile.skyblockLevel)
        assertNull(profile.bankBalance)
        assertNull(profile.catacombs)
        assertEquals(0, profile.fairySouls)
        assertEquals(Leveling.FAIRY_SOULS_TOTAL, profile.fairySoulsTotal)
        assertEquals(emptyList(), profile.skills)
        assertEquals(0.0, profile.skillAverage!!, 1e-9)
    }

    @Test
    fun unknownProfileOrMemberIsNotFound() = runBlocking<Unit> {
        val (_, source) = source()
        assertFailsWith<PlayerNotFoundException> { source.profile(TESTER, "00000000000000000000000000000000") }
        assertFailsWith<PlayerNotFoundException> { source.profile("1234567890abcdef1234567890abcdee", MANGO) }
    }

    @Test
    fun cachesUntilTtlExpires() = runBlocking<Unit> {
        val (engine, source) = source()

        source.playerProfiles("Tester")
        source.playerProfiles("tester")
        source.profile(TESTER, MANGO)
        // One Mojang name lookup, one Hypixel call, one Mojang UUID lookup, one Hypixel player (rank) call.
        assertEquals(4, engine.requestHistory.size)

        now += 61_000
        source.profile(TESTER, MANGO)
        // The profile is fetched again; the rank is cached for an hour.
        assertEquals(5, engine.requestHistory.size)
        assertEquals(now, source.profile(TESTER, MANGO).fetchedAt)
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val skinUrl = "http://textures.minecraft.net/texture/tester"
    private val textures = Base64.getEncoder()
        .encodeToString("""{"textures":{"SKIN":{"url":"$skinUrl"}}}""".toByteArray())

    /** Like [source], with the `/v2/player` response from [player], a skin for Tester and a shared [HypixelClient]. */
    private fun extrasSource(
        profileTtlMillis: Long = 60_000,
        player: suspend MockRequestHandleScope.() -> HttpResponseData,
    ): Triple<MockEngine, HypixelClient, HypixelProfileSource> {
        val (engine, http) = mockHttp { request ->
            when {
                request.url.encodedPath == "/v2/player" -> player()
                request.url.host == "api.hypixel.net" -> respond(profilesFixture, headers = jsonHeaders)
                request.url.host == "textures.minecraft.net" -> respond(skin().bytes())
                else -> respond(
                    """{"id":"$TESTER","name":"Tester","properties":[{"name":"textures","value":"$textures"}]}""",
                    headers = jsonHeaders,
                )
            }
        }
        val hypixel = HypixelClient(http, "key", clock = { now })
        val source = HypixelProfileSource(
            MojangClient(http), hypixel, SkinClient(http) { now }, clock = { now }, profileTtlMillis = profileTtlMillis,
        )
        return Triple(engine, hypixel, source)
    }

    private fun MockEngine.playerRequests() = requestHistory.count { it.url.encodedPath == "/v2/player" }

    @Test
    fun rankAndFaceAreAttached() = runBlocking<Unit> {
        val (engine, _, source) = extrasSource {
            respond("""{"success":true,"player":{"newPackageRank":"MVP_PLUS","rankPlusColor":"DARK_GREEN"}}""",
                headers = jsonHeaders)
        }

        val profile = source.profile(TESTER, MANGO)

        assertEquals(PlayerRank("MVP", "#33AEC3", "+", "#00AA00"), profile.rank)
        assertEquals(64, profile.face?.size)
        // The skin is fetched over https even though Mojang handed out http.
        assertEquals(
            "https://textures.minecraft.net/texture/tester",
            engine.requestHistory.single { it.url.host == "textures.minecraft.net" }.url.toString(),
        )
        // Nothing else in the profile changes.
        assertEquals("Mango", profile.cuteName)
    }

    @Test
    fun rankFailureLeavesRankNullAndIsCachedForFiveMinutes() = runBlocking<Unit> {
        var status = HttpStatusCode.InternalServerError
        val (engine, _, source) = extrasSource {
            if (status == HttpStatusCode.OK) {
                respond("""{"success":true,"player":{"newPackageRank":"VIP"}}""", headers = jsonHeaders)
            } else {
                respond("""{"success":false}""", status, jsonHeaders)
            }
        }

        val profile = source.profile(TESTER, MANGO)
        assertNull(profile.rank)
        assertEquals(64, profile.face?.size)
        assertEquals(1, engine.playerRequests())

        now += 4 * 60_000
        status = HttpStatusCode.OK
        assertNull(source.profile(TESTER, MANGO).rank)
        assertEquals(1, engine.playerRequests())

        now += 2 * 60_000
        assertEquals(PlayerRank("VIP", "#40BB40"), source.profile(TESTER, MANGO).rank)
        assertEquals(2, engine.playerRequests())
    }

    @Test
    fun rankIsSkippedWhileRateLimited() = runBlocking<Unit> {
        var limited = true
        val (engine, hypixel, source) = extrasSource(profileTtlMillis = 60 * 60_000) {
            if (limited) respond("", HttpStatusCode.TooManyRequests, headersOf("RateLimit-Reset", "600"))
            else respond("""{"success":true,"player":{"newPackageRank":"MVP"}}""", headers = jsonHeaders)
        }

        // A 429 on the rank call doesn't fail the profile, but blocks Hypixel for ten minutes.
        assertNull(source.profile(TESTER, MANGO).rank)
        assertTrue(hypixel.isRateLimited)
        assertEquals(1, engine.playerRequests())

        // The rank failure has expired but the block hasn't: the rank isn't requested.
        limited = false
        now += 6 * 60_000
        assertTrue(hypixel.isRateLimited)
        val before = engine.requestHistory.size
        assertNull(source.profile(TESTER, MANGO).rank)
        assertEquals(before, engine.requestHistory.size)

        // Once both have passed, the rank loads.
        now += 6 * 60_000
        assertFalse(hypixel.isRateLimited)
        assertEquals(PlayerRank("MVP", "#33AEC3"), source.profile(TESTER, MANGO).rank)
        assertEquals(2, engine.playerRequests())
    }

    @Test
    fun slowRankDoesNotHoldUpTheProfile() = runBlocking<Unit> {
        val (_, _, source) = extrasSource {
            delay(30_000)
            respond("""{"success":true,"player":{"newPackageRank":"MVP"}}""", headers = jsonHeaders)
        }

        val started = System.nanoTime()
        val profile = source.profile(TESTER, MANGO)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertNull(profile.rank)
        assertEquals(64, profile.face?.size)
        assertTrue(elapsedMillis in 2_900..10_000, "took $elapsedMillis ms")
    }
}
