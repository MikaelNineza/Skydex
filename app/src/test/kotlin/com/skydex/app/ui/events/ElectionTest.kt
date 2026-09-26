package com.skydex.app.ui.events

import com.skydex.shared.model.Candidate
import com.skydex.shared.model.Mayor
import com.skydex.shared.model.MayorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElectionTest {
    private fun candidate(name: String, votes: Long) = Candidate(name.lowercase(), name, emptyList(), votes)

    /** Final results of election 515, in Hypixel's order. */
    private val year515 = listOf(
        candidate("Cole", 407934),
        candidate("Diana", 178521),
        candidate("Paul", 125538),
        candidate("Diaz", 529099),
        candidate("Foxy", 71618),
    )

    private fun status(
        votingYear: Int? = null,
        candidates: List<Candidate> = emptyList(),
        last: List<Candidate> = emptyList(),
        electionYear: Int = 515,
    ) = MayorStatus(
        mayor = Mayor("economist", "Diaz", emptyList()),
        electionYear = electionYear,
        termStartsAt = 1,
        termEndsAt = 2,
        votingYear = votingYear,
        candidates = candidates,
        lastElectionCandidates = last,
    )

    @Test
    fun `an open election is titled with its candidate count`() {
        val section = electionSection(status(516, year515))!!

        assertEquals("Election · voting open · 5 candidates", section.title)
        assertEquals(5, section.candidates.size)
    }

    @Test
    fun `a lone candidate is singular`() {
        assertEquals(
            "Election · voting open · 1 candidate",
            electionSection(status(516, listOf(candidate("Diaz", 3))))!!.title,
        )
    }

    @Test
    fun `a closed booth shows the last election's results`() {
        val section = electionSection(status(last = year515))!!

        assertEquals("Last election results (Year 515)", section.title)
        assertEquals("Diaz", section.candidates.first().name)
    }

    @Test
    fun `no election data means no section`() {
        assertNull(electionSection(status()))
    }

    @Test
    fun `an open election wins over the last results`() {
        val section = electionSection(status(516, listOf(candidate("Diana", 1)), year515))!!

        assertEquals("Election · voting open · 1 candidate", section.title)
        assertEquals(listOf(CandidateShare("Diana", 100)), section.candidates)
    }

    @Test
    fun `shares are sorted by votes with floored percentages`() {
        // Total 1,312,710: Diaz 40.3%, Cole 31.07%, Diana 13.6%, Paul 9.56%, Foxy 5.46%.
        assertEquals(
            listOf(
                CandidateShare("Diaz", 40),
                CandidateShare("Cole", 31),
                CandidateShare("Diana", 13),
                CandidateShare("Paul", 9),
                CandidateShare("Foxy", 5),
            ),
            candidateShares(year515),
        )
    }

    @Test
    fun `zero votes give zero percent instead of dividing by zero`() {
        assertEquals(
            listOf(CandidateShare("Cole", 0), CandidateShare("Diaz", 0)),
            candidateShares(listOf(candidate("Cole", 0), candidate("Diaz", 0))),
        )
        assertEquals(emptyList<CandidateShare>(), candidateShares(emptyList()))
    }

    @Test
    fun `percentages stay within 0 to 100 for odd votes`() {
        val shares = candidateShares(listOf(candidate("Up", 10), candidate("Down", -5)))

        assertEquals(listOf(CandidateShare("Up", 100), CandidateShare("Down", 0)), shares)
    }

    @Test
    fun `keys differ by state and year`() {
        val open = electionSection(status(516, year515))!!.key
        val closed = electionSection(status(last = year515))!!.key
        val nextClosed = electionSection(status(last = year515, electionYear = 516))!!.key
        val nextOpen = electionSection(status(517, year515))!!.key

        assertNotEquals(open, closed)
        assertNotEquals(closed, nextClosed)
        assertNotEquals(open, nextOpen)
        assertEquals(4, setOf(open, closed, nextClosed, nextOpen).size)
    }
}
