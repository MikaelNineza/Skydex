package com.skydex.shared.calendar

import kotlin.test.Test
import kotlin.test.assertEquals

class SkyblockCalendarTest {
    @Test
    fun yearIs124RealHours() {
        assertEquals(124L * 60 * 60 * 1000, SkyblockCalendar.YEAR_MILLIS)
    }
}
