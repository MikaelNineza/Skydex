package com.skydex.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppVersionTest {
    @Test
    fun parsesValidVersions() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(0, 0, 1), AppVersion.parse("0.0.1"))
        assertEquals(AppVersion(9999, 99, 99), AppVersion.parse("9999.99.99"))
        assertEquals(AppVersion(10, 0, 0), AppVersion.parse("10.0.0"))
    }

    @Test
    fun rejectsInvalidVersions() {
        val bad = listOf(
            "", "1.2", "1.2.3.4", "01.2.3", "1.02.3", "1.2.03", "1.100.0", "1.2.100", "10000.0.0", "1.2.3-beta",
            "V1.2.3", " 1.2.3", "1.2.3 ", "vv1.2.3", "v0.0.0", "0.0.0", "-1.2.3", "1..3", "a.b.c",
        )
        for (text in bad) assertNull(AppVersion.parse(text), "'$text' should not parse")
    }

    @Test
    fun constructorValidatesRanges() {
        assertFailsWith<IllegalArgumentException> { AppVersion(1, 100, 0) }
        assertFailsWith<IllegalArgumentException> { AppVersion(1, 0, 100) }
        assertFailsWith<IllegalArgumentException> { AppVersion(10_000, 0, 0) }
        assertFailsWith<IllegalArgumentException> { AppVersion(-1, 0, 0) }
    }

    @Test
    fun versionCodeFormula() {
        assertEquals(10203, AppVersion(1, 2, 3).versionCode)
        assertEquals(1, AppVersion.parse("0.0.1")!!.versionCode)
        assertEquals(10000, AppVersion.parse("1.0.0")!!.versionCode)
        assertEquals(99_999_999, AppVersion.parse("9999.99.99")!!.versionCode)
    }

    @Test
    fun orderingByVersionMatchesOrderingByVersionCode() {
        val grid = buildList {
            for (major in listOf(0, 1, 2, 10, 9999)) for (minor in listOf(0, 1, 9, 10, 99)) for (patch in listOf(0, 1, 9, 10, 99)) {
                if (major + minor + patch > 0) add(AppVersion(major, minor, patch))
            }
        }
        val lexicographic = compareBy<AppVersion>({ it.major }, { it.minor }, { it.patch })
        for (a in grid) for (b in grid) {
            assertEquals(
                lexicographic.compare(a, b).coerceIn(-1, 1),
                a.versionCode.compareTo(b.versionCode).coerceIn(-1, 1),
                "$a vs $b",
            )
            assertEquals(lexicographic.compare(a, b).coerceIn(-1, 1), a.compareTo(b).coerceIn(-1, 1), "$a vs $b")
        }
    }

    @Test
    fun nameRoundTripsThroughParse() {
        for (version in listOf(AppVersion(0, 0, 1), AppVersion(1, 2, 3), AppVersion(10, 0, 99), AppVersion(9999, 99, 99))) {
            assertEquals(version, AppVersion.parse(version.name))
            assertEquals(version, AppVersion.parse("v${version.name}"))
            assertEquals(version.name, version.toString())
        }
    }
}
