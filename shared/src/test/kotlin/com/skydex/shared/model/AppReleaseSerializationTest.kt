package com.skydex.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppReleaseSerializationTest {
    private val release = AppRelease(
        versionName = "1.2.3",
        versionCode = 10203,
        downloadUrl = "https://github.com/MikaelNineza/Skydex/releases/download/v1.2.3/skydex.apk",
        releaseUrl = "https://github.com/MikaelNineza/Skydex/releases/tag/v1.2.3",
        notes = "Fixes",
        publishedAt = 1_790_000_000_000L,
        sizeBytes = 12_345,
        sha256 = "a".repeat(64),
    )

    @Test
    fun roundTrip() {
        assertEquals(release, Json.decodeFromString<AppRelease>(Json.encodeToString(release)))
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val decoded = Json.decodeFromString<AppRelease>(
            """{"versionName":"1.2.3","versionCode":10203,"downloadUrl":"d","releaseUrl":"r","sizeBytes":5}""",
        )
        assertNull(decoded.notes)
        assertNull(decoded.publishedAt)
        assertNull(decoded.sha256)
        assertEquals(5, decoded.sizeBytes)
    }

    @Test
    fun unknownFieldsAreIgnoredWithIgnoreUnknownKeys() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<AppRelease>(
            """{"versionName":"1.2.3","versionCode":10203,"downloadUrl":"d","releaseUrl":"r","sizeBytes":5,"future":true}""",
        )
        assertEquals("1.2.3", decoded.versionName)
    }

    @Test
    fun constants() {
        assertEquals("skydex.apk", AppRelease.ASSET_NAME)
        assertEquals(4_000, AppRelease.MAX_NOTES_CHARS)
    }
}
