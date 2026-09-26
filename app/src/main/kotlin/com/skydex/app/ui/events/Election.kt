package com.skydex.app.ui.events

import com.skydex.shared.model.Candidate
import com.skydex.shared.model.MayorStatus

/** A candidate's name and their share of the votes, floored to a whole percent. */
data class CandidateShare(val name: String, val percent: Int)

/** The mayor card's collapsible election section. [key] identifies it so expansion state resets when it changes. */
data class ElectionSection(val key: String, val title: String, val candidates: List<CandidateShare>)

/** The running election while voting is open, else the last election's results, else null. */
fun electionSection(status: MayorStatus): ElectionSection? = when {
    status.candidates.isNotEmpty() -> {
        val count = status.candidates.size
        ElectionSection(
            key = "voting-${status.votingYear}",
            title = "Election · voting open · $count ${if (count == 1) "candidate" else "candidates"}",
            candidates = candidateShares(status.candidates),
        )
    }
    status.lastElectionCandidates.isNotEmpty() -> ElectionSection(
        key = "last-${status.electionYear}",
        title = "Last election results (Year ${status.electionYear})",
        candidates = candidateShares(status.lastElectionCandidates),
    )
    else -> null
}

/** Candidates by votes, most first; percentages are floored, so they may sum to less than 100. */
fun candidateShares(candidates: List<Candidate>): List<CandidateShare> {
    val total = candidates.sumOf { it.votes }.coerceAtLeast(1)
    // Clamped in case the server ever passes negative votes through.
    return candidates.sortedByDescending { it.votes }
        .map { CandidateShare(it.name, (it.votes * 100 / total).coerceIn(0L, 100L).toInt()) }
}
