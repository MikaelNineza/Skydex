package com.skydex.shared.calendar

/**
 * Skyblock time constants. One Skyblock year is 124 real hours, counted from a fixed epoch,
 * so event times can be computed without calling the Hypixel API.
 */
object SkyblockCalendar {
    /** Real time (Unix millis) when Skyblock Year 1, Early Spring 1 began. */
    const val EPOCH_MILLIS: Long = 1_560_275_700_000L

    const val DAY_MILLIS: Long = 20 * 60 * 1000L
    const val MONTH_MILLIS: Long = 31 * DAY_MILLIS
    const val YEAR_MILLIS: Long = 12 * MONTH_MILLIS
}
