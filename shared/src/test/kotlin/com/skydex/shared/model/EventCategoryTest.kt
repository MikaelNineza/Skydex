package com.skydex.shared.model

import com.skydex.shared.calendar.SkyblockCalendar.MONTH_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.YEAR_MILLIS
import com.skydex.shared.calendar.ActivePerks
import com.skydex.shared.calendar.SkyblockDate
import com.skydex.shared.calendar.SkyblockEvents
import com.skydex.shared.calendar.termBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val REAL_DAY = 24 * 60 * 60 * 1000L

class EventCategoryTest {
    @Test
    fun everyEventTypeHasItsDecidedCategory() {
        // Hard-coded on purpose: a new EventType fails here until someone decides its category.
        val expected = mapOf(
            EventType.DARK_AUCTION to EventCategory.COMMON,
            EventType.JACOBS_CONTEST to EventCategory.COMMON,
            EventType.CULT_OF_THE_FALLEN_STAR to EventCategory.COMMON,
            EventType.FISHING_FESTIVAL to EventCategory.COMMON,
            EventType.MINING_FIESTA to EventCategory.COMMON,
            EventType.BANK_INTEREST to EventCategory.COMMON,
            EventType.TRAVELING_ZOO to EventCategory.SEASONAL,
            EventType.SPOOKY_FESTIVAL to EventCategory.SEASONAL,
            EventType.SEASON_OF_JERRY to EventCategory.SEASONAL,
            EventType.NEW_YEAR_CELEBRATION to EventCategory.SEASONAL,
            EventType.JERRYS_WORKSHOP to EventCategory.SEASONAL,
            EventType.HOPPITYS_HUNT to EventCategory.SEASONAL,
            EventType.ELECTION_OPEN to EventCategory.SEASONAL,
            EventType.MAYOR_TERM_CHANGE to EventCategory.SEASONAL,
            EventType.YEAR_OF_THE_SEAL to EventCategory.RARE,
            EventType.YEAR_OF_THE_WITCH to EventCategory.RARE,
            EventType.YEAR_OF_THE_PIG to EventCategory.RARE,
        )
        assertEquals(expected, EventType.entries.associateWith { it.category })
    }

    private fun typesIn(category: EventCategory) = EventType.entries.filter { it.category == category }

    /** Starts of [type] in `[from, until)`. */
    private fun startsIn(type: EventType, from: Long, until: Long, perks: ActivePerks = ActivePerks.NONE) =
        SkyblockEvents.occurrences(type, from, 1000, perks).map { it.startsAt }.filter { it in from until until }

    @Test
    fun ungatedCommonEventsComeAboutDaily() {
        val from = SkyblockDate(300, 1, 1).toMillis() + 12_345
        // Bank interest is paid once a Skyblock season (31 real hours); the user still counts it as daily-ish.
        val maxGap = mapOf(EventType.BANK_INTEREST to 3 * MONTH_MILLIS).withDefault { REAL_DAY }
        val ungated = typesIn(EventCategory.COMMON) - setOf(EventType.MINING_FIESTA, EventType.FISHING_FESTIVAL)
        assertEquals(
            setOf(EventType.DARK_AUCTION, EventType.JACOBS_CONTEST, EventType.CULT_OF_THE_FALLEN_STAR, EventType.BANK_INTEREST),
            ungated.toSet(),
        )
        for (type in ungated) {
            // Slide a window across two Skyblock years in steps that don't line up with any period.
            var t = from
            while (t < from + 2 * YEAR_MILLIS) {
                val gap = maxGap.getValue(type)
                assertTrue(startsIn(type, t, t + gap).isNotEmpty(), "$type does not start within $gap ms of $t")
                t += 7 * 60 * 1000L + 13
            }
        }
    }

    @Test
    fun gatedCommonEventsComeSeveralTimesInTheirTerm() {
        val (start, end) = termBounds(300)
        val perks = ActivePerks(miningFiesta = true, fishingFestival = true, termStartsAt = start, termEndsAt = end)
        assertEquals(5, startsIn(EventType.MINING_FIESTA, start, end, perks).size)
        assertEquals(12, startsIn(EventType.FISHING_FESTIVAL, start, end, perks).size)
    }

    @Test
    fun seasonalEventsStartEverySkyblockYear() {
        for (year in 300..312) {
            val from = SkyblockDate(year, 1, 1).toMillis()
            for (type in typesIn(EventCategory.SEASONAL)) {
                val n = startsIn(type, from, from + YEAR_MILLIS).size
                assertTrue(n in 1..2, "$type starts $n times in year $year")
            }
        }
    }

    @Test
    fun rareEventsStartOnceInTwelveYears() {
        val from = SkyblockDate(300, 1, 1).toMillis()
        for (type in typesIn(EventCategory.RARE)) {
            assertEquals(1, startsIn(type, from, from + 12 * YEAR_MILLIS).size, "$type")
            // So some Skyblock years have none of it.
            val yearsWith = (300 until 312).count { y ->
                val s = SkyblockDate(y, 1, 1).toMillis()
                startsIn(type, s, s + YEAR_MILLIS).isNotEmpty()
            }
            assertEquals(1, yearsWith, "$type")
        }
    }
}
