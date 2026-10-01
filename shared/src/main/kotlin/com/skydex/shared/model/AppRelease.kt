package com.skydex.shared.model

import kotlinx.serialization.Serializable

/**
 * `GET /v1/app/latest`: the newest published app release (200), or 204 when there is none.
 * Older servers answer 404; the app treats that like 204.
 */
@Serializable
data class AppRelease(
    /** "1.2.3". */
    val versionName: String,
    /** [AppVersion.versionCode] of [versionName]. */
    val versionCode: Int,
    /** https://github.com/<repo>/releases/download/<tag>/skydex.apk */
    val downloadUrl: String,
    /** The release page on GitHub. */
    val releaseUrl: String,
    /** Release notes (Markdown), truncated to [MAX_NOTES_CHARS]. */
    val notes: String? = null,
    /** Epoch millis. */
    val publishedAt: Long? = null,
    val sizeBytes: Long,
    /** Lowercase hex SHA-256 of the APK. Null means the app must not install it in-app. */
    val sha256: String? = null,
) {
    companion object {
        const val ASSET_NAME = "skydex.apk"
        const val MAX_NOTES_CHARS = 4_000
    }
}
