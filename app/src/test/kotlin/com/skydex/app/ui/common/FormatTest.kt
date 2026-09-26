package com.skydex.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `coins are compacted with one decimal`() {
        assertEquals("0", formatCoins(0.0))
        assertEquals("950", formatCoins(950.4))
        assertEquals("1.2K", formatCoins(1_234.0))
        assertEquals("1M", formatCoins(1_000_000.0))
        assertEquals("1.3M", formatCoins(1_250_000.0))
        assertEquals("2.5B", formatCoins(2_500_000_000.0))
        assertEquals("-1.5K", formatCoins(-1_500.0))
    }

    @Test
    fun `rounding up moves to the next suffix`() {
        assertEquals("1M", formatCoins(999_960.0))
        assertEquals("1K", formatCoins(999.96))
    }

    @Test
    fun `countdown shows hours minutes and seconds`() {
        assertEquals("1h 02m 03s", formatCountdown(3_723_000))
        assertEquals("4m 05s", formatCountdown(245_000))
        assertEquals("9s", formatCountdown(9_000))
    }

    @Test
    fun `countdown of a day or more shows days hours and minutes`() {
        assertEquals("1d 00h 00m", formatCountdown(86_400_000))
        assertEquals("2d 03h 04m", formatCountdown((2 * 86_400L + 3 * 3600 + 4 * 60 + 5) * 1000))
        assertEquals("23h 59m 59s", formatCountdown(86_399_000))
        // Rounding up a partial second can reach the next tier.
        assertEquals("1d 00h 00m", formatCountdown(86_399_001))
        assertEquals("30d 00h 00m", formatCountdown(30 * 86_400_000L))
    }

    @Test
    fun `countdown rounds partial seconds up and clamps negatives`() {
        assertEquals("10s", formatCountdown(9_001))
        assertEquals("0s", formatCountdown(-5_000))
    }

    @Test
    fun `title case capitalises the first letter`() {
        assertEquals("Farming", "farming".titleCase())
    }
}
