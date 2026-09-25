package com.skydex.shared.calendar

import com.skydex.shared.calendar.SkyblockCalendar.DAY_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.EPOCH_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.MONTH_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.YEAR_MILLIS

private const val MINUTES_PER_DAY = 24 * 60

/**
 * A moment on the Skyblock calendar, to in-game minute precision.
 *
 * [year] is 1 at [SkyblockCalendar.EPOCH_MILLIS], [month] is 1..12 (see [MONTH_NAMES]),
 * [day] is 1..31, [hour] is 0..23 and [minute] is 0..59.
 */
data class SkyblockDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int = 0,
    val minute: Int = 0,
) {
    init {
        require(month in 1..SkyblockCalendar.MONTHS_PER_YEAR) { "month out of range: $month" }
        require(day in 1..SkyblockCalendar.DAYS_PER_MONTH) { "day out of range: $day" }
        require(hour in 0..23) { "hour out of range: $hour" }
        require(minute in 0..59) { "minute out of range: $minute" }
    }

    /** Name of [month], for example "Late Winter". */
    val monthName: String get() = MONTH_NAMES[month - 1]

    /** Real time (Unix millis) at which this in-game minute starts. */
    fun toMillis(): Long {
        val dayStart = EPOCH_MILLIS + (year - 1L) * YEAR_MILLIS +
            (month - 1L) * MONTH_MILLIS + (day - 1L) * DAY_MILLIS
        // An in-game minute is 833.3 real ms; round up to the first real millisecond inside it.
        val minuteOfDay = hour * 60L + minute
        return dayStart + (minuteOfDay * DAY_MILLIS + MINUTES_PER_DAY - 1) / MINUTES_PER_DAY
    }

    /** Readable form, for example "Late Winter 24th, Year 312, 6:00 am". */
    fun format(): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val amPm = if (hour < 12) "am" else "pm"
        return "$monthName ${ordinal(day)}, Year $year, $h12:${minute.toString().padStart(2, '0')} $amPm"
    }

    override fun toString(): String = format()

    companion object {
        /** Month names in calendar order; index 0 is month 1. */
        val MONTH_NAMES: List<String> = listOf(
            "Early Spring", "Spring", "Late Spring",
            "Early Summer", "Summer", "Late Summer",
            "Early Autumn", "Autumn", "Late Autumn",
            "Early Winter", "Winter", "Late Winter",
        )

        /** The in-game minute containing real time [millis] (Unix millis). Times before the epoch give year <= 0. */
        fun fromMillis(millis: Long): SkyblockDate {
            val sinceEpoch = millis - EPOCH_MILLIS
            val year = Math.floorDiv(sinceEpoch, YEAR_MILLIS)
            val inYear = Math.floorMod(sinceEpoch, YEAR_MILLIS)
            val inMonth = inYear % MONTH_MILLIS
            val minuteOfDay = (inMonth % DAY_MILLIS) * MINUTES_PER_DAY / DAY_MILLIS
            return SkyblockDate(
                year = (year + 1).toInt(),
                month = (inYear / MONTH_MILLIS).toInt() + 1,
                day = (inMonth / DAY_MILLIS).toInt() + 1,
                hour = (minuteOfDay / 60).toInt(),
                minute = (minuteOfDay % 60).toInt(),
            )
        }

        private fun ordinal(n: Int): String = n.toString() + when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }
}
