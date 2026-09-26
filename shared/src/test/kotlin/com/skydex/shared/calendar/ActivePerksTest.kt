package com.skydex.shared.calendar

import com.skydex.shared.model.EventType
import com.skydex.shared.model.Mayor
import com.skydex.shared.model.MayorStatus
import com.skydex.shared.model.Minister
import com.skydex.shared.model.Perk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivePerksTest {
    private val term = termBounds(515)

    private val coleFiesta = Perk(
        "Mining Fiesta",
        "Schedules 5 Mining Fiestas throughout the year! During these events gain +75☯ Mining Wisdom.",
        minister = true,
    )

    /** The shape of Hypixel's election sample (Year 516): Diaz mayor, Cole minister with Mining Fiesta. */
    private val sample = MayorStatus(
        mayor = Mayor(
            "economist",
            "Diaz",
            listOf(
                Perk("Volume Trading", "The available item quantity per Shen's Auction has been doubled."),
                Perk("Long Term Investment", "The elected minister will appear as a candidate."),
            ),
        ),
        minister = Minister("mining", "Cole", coleFiesta),
        electionYear = 515,
        termStartsAt = term.first,
        termEndsAt = term.second,
        votingYear = 516,
    )

    private fun foxy(event: String) = sample.copy(
        mayor = Mayor(
            "events",
            "Foxy",
            listOf(
                Perk("Chivalrous Carnival", "Schedules a Carnival in the hub."),
                Perk("Extra Event", "Schedules an extra $event event during the year."),
            ),
        ),
        minister = null,
    )

    @Test
    fun termBoundsRunLateSpring27thToLateSpring27th() {
        assertEquals(SkyblockDate(516, 3, 27).toMillis() to SkyblockDate(517, 3, 27).toMillis(), term)
    }

    @Test
    fun ministerPerkCounts() {
        val perks = sample.activePerks()
        assertEquals(
            ActivePerks(miningFiesta = true, termStartsAt = term.first, termEndsAt = term.second),
            perks,
        )
        assertFalse(sample.isPerkpocalypse())
    }

    @Test
    fun mayorPerkCounts() {
        val marina = sample.copy(
            mayor = Mayor("fishing", "Marina", listOf(Perk("Fishing Festival", "Start a festival each month."))),
            minister = null,
        )
        val perks = marina.activePerks()
        assertTrue(perks.fishingFestival)
        assertFalse(perks.miningFiesta)
        assertNull(perks.extraEvent)
    }

    @Test
    fun foxyExtraEventIsParsed() {
        assertEquals(EventType.MINING_FIESTA, foxy("Mining Fiesta").activePerks().extraEvent)
        assertEquals(EventType.FISHING_FESTIVAL, foxy("Fishing Festival").activePerks().extraEvent)
        assertEquals(EventType.SPOOKY_FESTIVAL, foxy("Spooky Festival").activePerks().extraEvent)
        assertNull(foxy("Mystery").activePerks().extraEvent)
        // An extra Mining Fiesta is one event, not Cole's five.
        assertFalse(foxy("Mining Fiesta").activePerks().miningFiesta)
    }

    @Test
    fun foxyAsMinisterSchedulesNothing() {
        val minister = sample.copy(minister = Minister("events", "Foxy", Perk("Extra Event", "Schedules an extra Mining Fiesta event.")))
        assertNull(minister.activePerks().extraEvent)
    }

    @Test
    fun jerryIsAPerkpocalypse() {
        val jerry = sample.copy(mayor = Mayor("jerry", "Jerry", listOf(Perk("Mining Fiesta", "Random perk"))))
        assertTrue(jerry.isPerkpocalypse())
        assertEquals(ActivePerks.NONE, jerry.activePerks())
    }
}
