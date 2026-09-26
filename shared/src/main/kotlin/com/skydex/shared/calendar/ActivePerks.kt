package com.skydex.shared.calendar

import com.skydex.shared.model.EventType
import com.skydex.shared.model.MayorStatus

/**
 * The perks of the current term that add events to the calendar, and the term they apply to
 * ([termStartsAt] inclusive to [termEndsAt] exclusive, Unix millis).
 */
data class ActivePerks(
    val miningFiesta: Boolean = false,
    val fishingFestival: Boolean = false,
    /** The event Foxy's "Extra Event" perk schedules, if he is mayor. */
    val extraEvent: EventType? = null,
    val termStartsAt: Long = 0,
    val termEndsAt: Long = 0,
) {
    companion object {
        /** No perk-dependent events. Used when the mayor is unknown. */
        val NONE = ActivePerks()
    }
}

/** True while Jerry is mayor: his perks are random and change every few hours, so we can't predict them. */
fun MayorStatus.isPerkpocalypse(): Boolean = mayor.key == "jerry"

/** The calendar-relevant perks of the mayor and minister in office. [ActivePerks.NONE] during a Perkpocalypse. */
fun MayorStatus.activePerks(): ActivePerks {
    if (isPerkpocalypse()) return ActivePerks.NONE
    val names = mayor.perks.map { it.name } + listOfNotNull(minister?.perk?.name)
    // Only the mayor's perks count here: a Foxy minister never schedules an extra event.
    // Foxy's perk description names the event, e.g. "Schedules an extra Mining Fiesta event during the year."
    val extra = mayor.perks.find { it.name == "Extra Event" }?.description
    val extraEvent = when {
        extra == null -> null
        "Mining Fiesta" in extra -> EventType.MINING_FIESTA
        "Fishing Festival" in extra -> EventType.FISHING_FESTIVAL
        "Spooky Festival" in extra -> EventType.SPOOKY_FESTIVAL
        else -> null
    }
    return ActivePerks(
        miningFiesta = "Mining Fiesta" in names,
        fishingFestival = "Fishing Festival" in names,
        extraEvent = extraEvent,
        termStartsAt = termStartsAt,
        termEndsAt = termEndsAt,
    )
}

/**
 * Real-time bounds (Unix millis) of the term won in the election of [electionYear]: the new mayor takes office on
 * Late Spring 27th of the following year and serves until Late Spring 27th a year later.
 */
fun termBounds(electionYear: Int): Pair<Long, Long> =
    SkyblockDate(electionYear + 1, 3, 27).toMillis() to SkyblockDate(electionYear + 2, 3, 27).toMillis()
