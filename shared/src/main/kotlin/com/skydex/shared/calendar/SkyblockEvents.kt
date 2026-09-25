package com.skydex.shared.calendar

import com.skydex.shared.model.SkyblockEvent

/** Computes event occurrences from [SkyblockCalendar]; no network needed. */
object SkyblockEvents {
    /**
     * Every event occurrence that is running at [nowMillis] or starts before [nowMillis] + [windowMillis],
     * sorted by [SkyblockEvent.startsAt].
     */
    fun upcoming(nowMillis: Long, windowMillis: Long): List<SkyblockEvent> = TODO("calendar task")
}
