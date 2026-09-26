package com.skydex.shared.calendar

import com.skydex.shared.calendar.SkyblockCalendar.DAY_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.EPOCH_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.HOUR_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.MONTH_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.YEAR_MILLIS
import com.skydex.shared.model.EventType
import com.skydex.shared.model.SkyblockEvent

/**
 * Computes event occurrences from [SkyblockCalendar]; no network needed. Events that depend on the mayor's perks only
 * appear when the caller passes the current term's [ActivePerks].
 */
object SkyblockEvents {
    /** Year of the Seal, Witch and Pig each come around every 12 Skyblock years; these are known years of each. */
    private const val YEAR_OF_THE_SEAL_ANCHOR = 522
    private const val YEAR_OF_THE_WITCH_ANCHOR = 524
    private const val YEAR_OF_THE_PIG_ANCHOR = 527
    private const val SPECIAL_YEAR_CYCLE = 12

    /**
     * An event that repeats every [period] millis, starting [offset] millis after the epoch
     * and lasting [duration] millis (0 for instantaneous events).
     *
     * A series with a [gate] only runs while the gate accepts the current perks, and only within their term.
     */
    private class Series(
        val type: EventType,
        val offset: Long,
        val period: Long,
        val duration: Long,
        val gate: ((ActivePerks) -> Boolean)? = null,
    )

    /** Offset from the start of a year to the start of [day] of [month] (both 1-based). */
    private fun dayOfYear(month: Int, day: Int): Long = (month - 1L) * MONTH_MILLIS + (day - 1L) * DAY_MILLIS

    private fun yearly(
        type: EventType,
        month: Int,
        firstDay: Int,
        days: Int,
        gate: ((ActivePerks) -> Boolean)? = null,
    ) = Series(type, dayOfYear(month, firstDay), YEAR_MILLIS, days * DAY_MILLIS, gate)

    /** A whole Skyblock year, every 12 years, one of which is [anchorYear]. */
    private fun everyTwelveYears(type: EventType, anchorYear: Int) =
        Series(type, (anchorYear - 1L) * YEAR_MILLIS, SPECIAL_YEAR_CYCLE * YEAR_MILLIS, YEAR_MILLIS)

    private val SERIES: List<Series> = listOf(
        // Fixed calendar dates from the Hypixel SkyBlock wiki "Calendar and Events" page.
        yearly(EventType.SPOOKY_FESTIVAL, month = 8, firstDay = 29, days = 3),
        yearly(EventType.SEASON_OF_JERRY, month = 12, firstDay = 24, days = 3),
        yearly(EventType.NEW_YEAR_CELEBRATION, month = 12, firstDay = 29, days = 3),
        yearly(EventType.TRAVELING_ZOO, month = 4, firstDay = 1, days = 3),
        yearly(EventType.TRAVELING_ZOO, month = 10, firstDay = 1, days = 3),
        // Jerry's Workshop is open all of Late Winter; Hoppity's Hunt runs through the three spring months.
        yearly(EventType.JERRYS_WORKSHOP, month = 12, firstDay = 1, days = 31),
        yearly(EventType.HOPPITYS_HUNT, month = 1, firstDay = 1, days = 93),
        // The booth opens on Summer 27th and closes when the new mayor takes office on Late Spring 27th.
        Series(
            EventType.ELECTION_OPEN,
            dayOfYear(6, 27),
            YEAR_MILLIS,
            YEAR_MILLIS - (dayOfYear(6, 27) - dayOfYear(3, 27)),
        ),
        Series(EventType.MAYOR_TERM_CHANGE, dayOfYear(3, 27), YEAR_MILLIS, 0),
        everyTwelveYears(EventType.YEAR_OF_THE_SEAL, YEAR_OF_THE_SEAL_ANCHOR),
        everyTwelveYears(EventType.YEAR_OF_THE_WITCH, YEAR_OF_THE_WITCH_ANCHOR),
        everyTwelveYears(EventType.YEAR_OF_THE_PIG, YEAR_OF_THE_PIG_ANCHOR),
        // Marina's perk: the first 3 days of every month.
        Series(EventType.FISHING_FESTIVAL, 0, MONTH_MILLIS, 3 * DAY_MILLIS) { it.fishingFestival },
    ) + listOf(2, 4, 6, 8, 10).map { month ->
        // Cole's perk: 5 Mining Fiestas a term, a week each. The term starts in Late Spring, so the clipping to the
        // term drops the Spring one before it and keeps the one in the following Spring.
        yearly(EventType.MINING_FIESTA, month = month, firstDay = 1, days = 7) { it.miningFiesta }
    } + listOf(7, 14, 21, 28).map { day ->
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

    private val SERIES_BY_TYPE: Map<EventType, List<Series>> = SERIES.groupBy { it.type }

    private val ORDER = compareBy<SkyblockEvent> { it.startsAt }.thenBy { it.type }

    /** Running at [millis] or starting after it; an instantaneous event counts only when `startsAt == millis`. */
    private fun SkyblockEvent.runsAtOrAfter(millis: Long): Boolean = endsAt > millis || startsAt == millis

    /** Occurrences of this series running at or after [fromMillis], soonest first. Finite for gated series. */
    private fun Series.occurrencesFrom(fromMillis: Long, perks: ActivePerks): Sequence<SkyblockEvent> {
        val gate = gate
        if (gate != null && !gate(perks)) return emptySequence()
        val base = EPOCH_MILLIS + offset
        // Start from the last occurrence that could still be running at fromMillis.
        var first = base + Math.floorDiv(fromMillis - base - duration, period) * period
        // Jump straight to the first start inside the term rather than walking there one period at a time.
        if (gate != null && first < perks.termStartsAt) {
            first = base - Math.floorDiv(base - perks.termStartsAt, period) * period
        }
        var starts = generateSequence(first) { it + period }
        if (gate != null) starts = starts.takeWhile { it < perks.termEndsAt }
        return starts.map { SkyblockEvent(type, it, it + duration) }.filter { it.runsAtOrAfter(fromMillis) }
    }

    /** The one extra event Foxy's "Extra Event" perk schedules in his term (Summer 22nd), if he is mayor. */
    private fun extraEvent(perks: ActivePerks): SkyblockEvent? {
        val type = perks.extraEvent ?: return null
        val start = SkyblockDate(SkyblockDate.fromMillis(perks.termStartsAt).year, 6, 22).toMillis()
        val days = if (type == EventType.MINING_FIESTA) 7 else 3
        return SkyblockEvent(type, start, start + days * DAY_MILLIS)
    }

    /**
     * Every event occurrence that is running at [nowMillis] or starts before [nowMillis] + [windowMillis],
     * sorted by [SkyblockEvent.startsAt].
     *
     * An occurrence is running if `startsAt <= now < endsAt`; an instantaneous one (bank interest) counts
     * only when `startsAt == now`. Perk-dependent events are included only as far as [perks] allows.
     */
    fun upcoming(nowMillis: Long, windowMillis: Long, perks: ActivePerks = ActivePerks.NONE): List<SkyblockEvent> {
        require(windowMillis >= 0) { "windowMillis must not be negative: $windowMillis" }
        val windowEnd = nowMillis + windowMillis
        fun inWindow(event: SkyblockEvent) = event.startsAt < windowEnd || event.startsAt == nowMillis
        val result = SERIES.flatMap { it.occurrencesFrom(nowMillis, perks).takeWhile(::inWindow).toList() } +
            listOfNotNull(extraEvent(perks)?.takeIf { it.runsAtOrAfter(nowMillis) && inWindow(it) })
        return result.sortedWith(ORDER)
    }

    /**
     * The next [count] occurrences of [type] running at or starting after [fromMillis], by the same rule as
     * [upcoming], soonest first. Perk-dependent events are only projected to the end of the current term, so there
     * may be fewer than [count].
     */
    fun occurrences(
        type: EventType,
        fromMillis: Long,
        count: Int,
        perks: ActivePerks = ActivePerks.NONE,
    ): List<SkyblockEvent> {
        require(count >= 0) { "count must not be negative: $count" }
        val result = SERIES_BY_TYPE[type].orEmpty()
            .flatMap { it.occurrencesFrom(fromMillis, perks).take(count).toList() } +
            listOfNotNull(extraEvent(perks)?.takeIf { it.type == type && it.runsAtOrAfter(fromMillis) })
        return result.sortedWith(ORDER).take(count)
    }
}
