package com.skydex.app.ui.profile

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankBadgeTest {
    @Test
    fun `hex colours parse as opaque`() {
        assertEquals(Color(0xFF33AEC3), parseRankColor("#33AEC3"))
        assertEquals(Color(0xFF33AEC3), parseRankColor("33aec3"))
        assertEquals(Color(0xFF000000), parseRankColor("#000000"))
        assertEquals(Color(0xFFFFFFFF), parseRankColor("#ffffff"))
    }

    @Test
    fun `anything else is rejected`() {
        for (bad in listOf("", "#", "#FFF", "#33AEC3FF", "#GGGGGG", "RED", "##33AEC3", " #33AEC3", "#-12345", "#+12345")) {
            assertNull(bad, parseRankColor(bad))
        }
    }
}
