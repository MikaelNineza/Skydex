package com.skydex.shared.model

/**
 * A release version MAJOR.MINOR.PATCH; minor and patch are below 100 so versionCode orders like the version.
 *
 * `app/build.gradle.kts` and `.github/workflows/release.yml` repeat the pattern and formula; keep them in sync.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    init {
        require(major in 0..MAX_MAJOR && minor in 0..99 && patch in 0..99) { "Version out of range: $major.$minor.$patch" }
    }

    /** major*10000 + minor*100 + patch. */
    val versionCode: Int get() = major * 10_000 + minor * 100 + patch

    val name: String get() = "$major.$minor.$patch"

    override fun compareTo(other: AppVersion): Int = versionCode.compareTo(other.versionCode)

    override fun toString(): String = name

    companion object {
        /** Keeps versionCode <= 99_999_999, well within Int and Play's 2_100_000_000. */
        const val MAX_MAJOR = 9_999

        private val PATTERN = Regex("""v?(0|[1-9]\d{0,3})\.(0|[1-9]\d?)\.(0|[1-9]\d?)""")

        /** Parses "1.2.3" or "v1.2.3"; null for anything else, including leading zeros and 0.0.0 (versionCode must be >= 1). */
        fun parse(text: String): AppVersion? {
            val (major, minor, patch) = PATTERN.matchEntire(text)?.destructured ?: return null
            return AppVersion(major.toInt(), minor.toInt(), patch.toInt()).takeIf { it.versionCode >= 1 }
        }
    }
}
