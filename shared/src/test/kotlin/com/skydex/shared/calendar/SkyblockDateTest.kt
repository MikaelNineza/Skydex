package com.skydex.shared.calendar

import com.skydex.shared.calendar.SkyblockCalendar.DAY_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.EPOCH_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.HOUR_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.MONTH_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.YEAR_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SkyblockDateTest {
    @Test
    fun epochIsYearOneEarlySpringFirst() {
        assertEquals(SkyblockDate(1, 1, 1, 0, 0), SkyblockDate.fromMillis(EPOCH_MILLIS))
        assertEquals(EPOCH_MILLIS, SkyblockDate(1, 1, 1).toMillis())
    }

    @Test
    fun realWorldAnchor() {
        // api.elitebot.dev reports Year 516's first Jacob's contest at 1790172900 (Unix seconds), on Early Spring 2.
        assertEquals(SkyblockDate(516, 1, 2), SkyblockDate.fromMillis(1_790_172_900_000L))
        // inventivetalent's bank interest timer estimated interest at 1790394900000, the start of Early Autumn.
        assertEquals(SkyblockDate(516, 7, 1), SkyblockDate.fromMillis(1_790_394_900_000L))
    }

    @Test
    fun everyMinuteRoundTrips() {
        for (hour in 0..23) for (minute in 0..59) {
            val date = SkyblockDate(312, 12, 24, hour, minute)
            val millis = date.toMillis()
            assertEquals(date, SkyblockDate.fromMillis(millis))
            // toMillis is the first millisecond of the minute.
            assertTrue(SkyblockDate.fromMillis(millis - 1) != date)
        }
    }

    @Test
    fun millisRoundTripToStartOfMinute() {
        var t = EPOCH_MILLIS + 5 * YEAR_MILLIS + 123_457L
        repeat(2000) {
            val start = SkyblockDate.fromMillis(t).toMillis()
            assertTrue(start <= t && t - start < 834, "t=$t start=$start")
            t += 7_919L
        }
    }

    @Test
    fun hoursAreFiftySeconds() {
        assertEquals(SkyblockDate(1, 1, 1, 6, 0).toMillis(), EPOCH_MILLIS + 6 * HOUR_MILLIS)
        assertEquals(SkyblockDate(1, 1, 1, 6, 0), SkyblockDate.fromMillis(EPOCH_MILLIS + 5 * 60 * 1000L))
    }

    @Test
    fun monthBoundary() {
        val lastMinuteOfEarlySpring = SkyblockDate.fromMillis(EPOCH_MILLIS + MONTH_MILLIS - 1)
        assertEquals(SkyblockDate(1, 1, 31, 23, 59), lastMinuteOfEarlySpring)
        assertEquals(SkyblockDate(1, 2, 1), SkyblockDate.fromMillis(EPOCH_MILLIS + MONTH_MILLIS))
        assertEquals(SkyblockDate(1, 1, 31), SkyblockDate.fromMillis(EPOCH_MILLIS + 30 * DAY_MILLIS))
    }

    @Test
    fun yearRollover() {
        val newYear = EPOCH_MILLIS + 311 * YEAR_MILLIS
        assertEquals(SkyblockDate(311, 12, 31, 23, 59), SkyblockDate.fromMillis(newYear - 1))
        assertEquals(SkyblockDate(312, 1, 1), SkyblockDate.fromMillis(newYear))
        assertEquals(newYear, SkyblockDate(312, 1, 1).toMillis())
    }

    @Test
    fun beforeEpochGivesYearZero() {
        assertEquals(SkyblockDate(0, 12, 31, 23, 59), SkyblockDate.fromMillis(EPOCH_MILLIS - 1))
    }

    @Test
    fun monthNames() {
        assertEquals(12, SkyblockDate.MONTH_NAMES.size)
        assertEquals("Early Spring", SkyblockDate(1, 1, 1).monthName)
        assertEquals("Autumn", SkyblockDate(1, 8, 1).monthName)
        assertEquals("Late Winter", SkyblockDate(1, 12, 1).monthName)
    }

    @Test
    fun format() {
        assertEquals("Late Winter 24th, Year 312, 6:00 am", SkyblockDate(312, 12, 24, 6, 0).format())
        assertEquals("Early Spring 1st, Year 1, 12:00 am", SkyblockDate(1, 1, 1).format())
        assertEquals("Summer 22nd, Year 5, 12:05 pm", SkyblockDate(5, 5, 22, 12, 5).format())
        assertEquals("Autumn 13th, Year 5, 11:59 pm", SkyblockDate(5, 8, 13, 23, 59).format())
        assertEquals("Autumn 31st, Year 5, 1:00 pm", SkyblockDate(5, 8, 31, 13, 0).format())
    }

    @Test
    fun rejectsOutOfRangeFields() {
        assertFailsWith<IllegalArgumentException> { SkyblockDate(1, 13, 1) }
        assertFailsWith<IllegalArgumentException> { SkyblockDate(1, 1, 32) }
        assertFailsWith<IllegalArgumentException> { SkyblockDate(1, 1, 1, 24, 0) }
        assertFailsWith<IllegalArgumentException> { SkyblockDate(1, 1, 1, 0, 60) }
    }
}
