package com.skydex.shared.calendar

/**
 * Skyblock time constants. One Skyblock year is 124 real hours, counted from a fixed epoch,
 * so event times can be computed without calling the Hypixel API.
 */
object SkyblockCalendar {
    /** Real time (Unix millis) when Skyblock Year 1, Early Spring 1 began (2019-06-11 17:55 UTC). */
    const val EPOCH_MILLIS: Long = 1_560_275_700_000L

    const val DAYS_PER_MONTH: Int = 31
    const val MONTHS_PER_YEAR: Int = 12

    /** One in-game hour: 50 real seconds. An in-game minute is not a whole number of millis (833.3 ms). */
    const val HOUR_MILLIS: Long = 50 * 1000L
    const val DAY_MILLIS: Long = 24 * HOUR_MILLIS
    const val MONTH_MILLIS: Long = DAYS_PER_MONTH * DAY_MILLIS
    const val YEAR_MILLIS: Long = MONTHS_PER_YEAR * MONTH_MILLIS
}
