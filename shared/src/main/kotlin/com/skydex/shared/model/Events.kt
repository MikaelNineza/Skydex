package com.skydex.shared.model

import kotlinx.serialization.Serializable

/** How often an event comes around; the app groups events by this. */
@Serializable
enum class EventCategory {
    /** At least once per 24 real hours. */
    COMMON,

    /** Less often, or only while a mayor perk is active. */
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
    TRAVELING_ZOO("Traveling Zoo", EventCategory.RARE),
    SPOOKY_FESTIVAL("Spooky Festival", EventCategory.RARE),
    SEASON_OF_JERRY("Season of Jerry", EventCategory.RARE),
    NEW_YEAR_CELEBRATION("New Year Celebration", EventCategory.RARE),
    BANK_INTEREST("Bank Interest", EventCategory.RARE),

    /** Only while Marina (or her minister perk) is in office. */
    FISHING_FESTIVAL("Fishing Festival", EventCategory.COMMON),

    /** Only while Cole (or his minister perk) is in office. */
    MINING_FIESTA("Mining Fiesta", EventCategory.RARE),
    JERRYS_WORKSHOP("Jerry's Workshop", EventCategory.RARE),
    HOPPITYS_HUNT("Hoppity's Hunt", EventCategory.RARE),
    ELECTION_OPEN("Election booth open", EventCategory.RARE),

    /** Instantaneous: the moment the newly elected mayor takes office. */
    MAYOR_TERM_CHANGE("New mayor", EventCategory.RARE),
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
