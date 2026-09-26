package com.skydex.shared.model

import kotlinx.serialization.Serializable

/** How often an event comes around; the app groups events by this. */
@Serializable
enum class EventCategory {
    /** At least about once a day. */
    COMMON,

    /** At least once per Skyblock year. */
    SEASONAL,

    /** Once every few Skyblock years. */
    RARE,
}

/**
 * Skyblock events whose times follow from the Skyblock calendar, in some cases combined with the current mayor's
 * perks. Names are stored in `sent_alerts.event_type`, so they must stay within 32 characters.
 */
@Serializable
enum class EventType(val displayName: String, val category: EventCategory) {
    DARK_AUCTION("Dark Auction", EventCategory.COMMON),
    JACOBS_CONTEST("Jacob's Farming Contest", EventCategory.COMMON),
    CULT_OF_THE_FALLEN_STAR("Cult of the Fallen Star", EventCategory.COMMON),
    TRAVELING_ZOO("Traveling Zoo", EventCategory.SEASONAL),
    SPOOKY_FESTIVAL("Spooky Festival", EventCategory.SEASONAL),
    SEASON_OF_JERRY("Season of Jerry", EventCategory.SEASONAL),
    NEW_YEAR_CELEBRATION("New Year Celebration", EventCategory.SEASONAL),
    BANK_INTEREST("Bank Interest", EventCategory.COMMON),

    /** Only while Marina (or her minister perk) is in office. */
    FISHING_FESTIVAL("Fishing Festival", EventCategory.COMMON),

    /** Only while Cole (or his minister perk) is in office. */
    MINING_FIESTA("Mining Fiesta", EventCategory.COMMON),
    JERRYS_WORKSHOP("Jerry's Workshop", EventCategory.SEASONAL),
    HOPPITYS_HUNT("Hoppity's Hunt", EventCategory.SEASONAL),
    ELECTION_OPEN("Election booth open", EventCategory.SEASONAL),

    /** Instantaneous: the moment the newly elected mayor takes office. */
    MAYOR_TERM_CHANGE("New mayor", EventCategory.SEASONAL),
    YEAR_OF_THE_SEAL("Year of the Seal", EventCategory.RARE),
    YEAR_OF_THE_WITCH("Year of the Witch", EventCategory.RARE),
    YEAR_OF_THE_PIG("Year of the Pig", EventCategory.RARE),
}

/** One occurrence of an event. `GET /v1/events` returns a list of these, soonest first. */
@Serializable
data class SkyblockEvent(
    val type: EventType,
    /** Unix millis. */
    val startsAt: Long,
    /** Unix millis, exclusive. Equal to [startsAt] for instantaneous events such as bank interest. */
    val endsAt: Long,
)
