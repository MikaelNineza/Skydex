package com.skydex.shared.model

import kotlinx.serialization.Serializable

/** Recurring Skyblock events whose times follow from the Skyblock calendar alone. */
@Serializable
enum class EventType(val displayName: String) {
    DARK_AUCTION("Dark Auction"),
    JACOBS_CONTEST("Jacob's Farming Contest"),
    CULT_OF_THE_FALLEN_STAR("Cult of the Fallen Star"),
    TRAVELING_ZOO("Traveling Zoo"),
    SPOOKY_FESTIVAL("Spooky Festival"),
    SEASON_OF_JERRY("Season of Jerry"),
    NEW_YEAR_CELEBRATION("New Year Celebration"),
    BANK_INTEREST("Bank Interest"),
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
