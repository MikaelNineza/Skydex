package com.skydex.shared.calendar

import com.skydex.shared.calendar.SkyblockCalendar.DAY_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.EPOCH_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.HOUR_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.MONTH_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.YEAR_MILLIS
import com.skydex.shared.model.EventType
import com.skydex.shared.model.SkyblockEvent

/** Computes event occurrences from [SkyblockCalendar]; no network needed. */
object SkyblockEvents {
    /**
     * An event that repeats every [period] millis, starting [offset] millis after the epoch
     * and lasting [duration] millis (0 for instantaneous events).
     */
    private class Series(val type: EventType, val offset: Long, val period: Long, val duration: Long)

    /** Offset from the start of a year to the start of [day] of [month] (both 1-based). */
    private fun dayOfYear(month: Int, day: Int): Long = (month - 1L) * MONTH_MILLIS + (day - 1L) * DAY_MILLIS

    private fun yearly(type: EventType, month: Int, firstDay: Int, days: Int) =
        Series(type, dayOfYear(month, firstDay), YEAR_MILLIS, days * DAY_MILLIS)

    private val SERIES: List<Series> = listOf(
        // Fixed calendar dates from the Hypixel SkyBlock wiki "Calendar and Events" page.
        yearly(EventType.SPOOKY_FESTIVAL, month = 8, firstDay = 29, days = 3),
        yearly(EventType.SEASON_OF_JERRY, month = 12, firstDay = 24, days = 3),
        yearly(EventType.NEW_YEAR_CELEBRATION, month = 12, firstDay = 29, days = 3),
        yearly(EventType.TRAVELING_ZOO, month = 4, firstDay = 1, days = 3),
        yearly(EventType.TRAVELING_ZOO, month = 10, firstDay = 1, days = 3),
    ) + listOf(7, 14, 21, 28).map { day ->
        // Days 7, 14, 21 and 28 of every month, 00:00-06:00 in-game (5 real minutes).
        Series(EventType.CULT_OF_THE_FALLEN_STAR, (day - 1L) * DAY_MILLIS, MONTH_MILLIS, 6 * HOUR_MILLIS)
    } + listOf(
        // Every 3 Skyblock days, lasting one Skyblock day (20 real minutes). Starts on day 2 of the year and
        // every third day after, i.e. xx:15 real time; matches the contest times published by api.elitebot.dev.
        Series(EventType.JACOBS_CONTEST, DAY_MILLIS, 3 * DAY_MILLIS, DAY_MILLIS),
        // Every 3 Skyblock days at in-game midnight, i.e. xx:55 real time (wiki: "every 3 SkyBlock days at
        // midnight, usually occurring at the 55-minute mark"). No source states a length; it depends on bidding,
        // so we assume 5 real minutes, roughly until the next real hour.
        Series(EventType.DARK_AUCTION, 0, 3 * DAY_MILLIS, 5 * 60 * 1000L),
        // Paid at the end of every season (every 31 real hours), i.e. at the start of Early Spring, Early Summer,
        // Early Autumn and Early Winter. Phase checked against inventivetalent's bank interest timer API.
        Series(EventType.BANK_INTEREST, 0, 3 * MONTH_MILLIS, 0),
    )

    /**
     * Every event occurrence that is running at [nowMillis] or starts before [nowMillis] + [windowMillis],
     * sorted by [SkyblockEvent.startsAt].
     *
     * An occurrence is running if `startsAt <= now < endsAt`; an instantaneous one (bank interest) counts
     * only when `startsAt == now`.
     */
    fun upcoming(nowMillis: Long, windowMillis: Long): List<SkyblockEvent> {
        require(windowMillis >= 0) { "windowMillis must not be negative: $windowMillis" }
        val windowEnd = nowMillis + windowMillis
        val result = mutableListOf<SkyblockEvent>()
        for (series in SERIES) {
            val base = EPOCH_MILLIS + series.offset
            // Start from the last occurrence that could still be running at nowMillis.
            var start = base + Math.floorDiv(nowMillis - base - series.duration, series.period) * series.period
            while (start < windowEnd || start == nowMillis) {
                val end = start + series.duration
                if (end > nowMillis || start == nowMillis) result += SkyblockEvent(series.type, start, end)
                start += series.period
            }
        }
        return result.sortedWith(compareBy<SkyblockEvent> { it.startsAt }.thenBy { it.type })
    }
}
