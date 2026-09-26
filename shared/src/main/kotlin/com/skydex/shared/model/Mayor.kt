package com.skydex.shared.model

import kotlinx.serialization.Serializable

/** A mayor or minister perk. The server strips Minecraft § colour codes from [description]. */
@Serializable
data class Perk(
    val name: String,
    val description: String,
    /** True if a candidate keeps this perk when elected minister. */
    val minister: Boolean = false,
)

@Serializable
data class Candidate(
    /** Hypixel's mayor id, e.g. "mining". */
    val key: String,
    val name: String,
    val perks: List<Perk>,
    val votes: Long,
)

@Serializable
data class Minister(
    val key: String,
    val name: String,
    val perk: Perk,
)

@Serializable
data class Mayor(
    /** Hypixel's mayor id, e.g. "economist"; "jerry" during a Perkpocalypse. */
    val key: String,
    val name: String,
    val perks: List<Perk>,
)

/** `GET /v1/mayor`: who is in office, and the running election if voting is open. */
@Serializable
data class MayorStatus(
    val mayor: Mayor,
    val minister: Minister? = null,
    /** Skyblock year of the election the current mayor won. */
    val electionYear: Int,
    /** Unix millis. */
    val termStartsAt: Long,
    /** Unix millis, exclusive. */
    val termEndsAt: Long,
    /** Skyblock year of the open election, or null when the booth is closed. */
    val votingYear: Int? = null,
    /** Candidates of the open election; empty when the booth is closed. */
    val candidates: List<Candidate> = emptyList(),
    /** Final results of election [electionYear] (the one the mayor won), in Hypixel's order; empty from older servers. */
    val lastElectionCandidates: List<Candidate> = emptyList(),
)
