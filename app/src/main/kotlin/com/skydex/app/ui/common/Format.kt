package com.skydex.app.ui.common

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val COIN_SUFFIXES = listOf("", "K", "M", "B", "T")

/** Compact coin amount: 950 -> "950", 1_234 -> "1.2K", 1_250_000 -> "1.3M", 2e9 -> "2B". */
fun formatCoins(coins: Double): String {
    var scaled = abs(coins)
    var tier = 0
    // Round before comparing so 999_960 becomes "1M" rather than "1000K".
    while (tier < COIN_SUFFIXES.lastIndex && roundTenths(scaled) >= 1000) {
        scaled /= 1000
        tier++
    }
    val rounded = if (tier == 0) scaled.roundToLong().toDouble() else roundTenths(scaled)
    val number = if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", rounded)
    }
    val sign = if (coins < 0 && rounded != 0.0) "-" else ""
    return sign + number + COIN_SUFFIXES[tier]
}

private fun roundTenths(value: Double) = (value * 10).roundToLong() / 10.0

/** Time until an event: "1h 02m 03s", "4m 05s", "9s". Negative durations show as "0s". */
fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.ROOT, "%dm %02ds", minutes, seconds)
        else -> "${seconds}s"
    }
}

/** "farming" -> "Farming". */
fun String.titleCase(): String = replaceFirstChar { it.titlecase(Locale.ROOT) }
