package com.skydex.app.ui.events

import com.skydex.shared.model.EventType
import com.skydex.shared.model.SkyblockEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class EventTimeTest {
    /** Explicit UK patterns: the Composable picks the device locale's best pattern, which JVM tests can't. */
    private fun format(zone: ZoneId) = EventTimeFormat(
        dateTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.UK).withZone(zone),
        dateTimeWithYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.UK).withZone(zone),
        date = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK).withZone(zone),
        dateWithYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK).withZone(zone),
        zone = zone,
    )

    private val edmonton = format(ZoneId.of("America/Edmonton"))
    private val utc = format(ZoneOffset.UTC)
    private val now = Instant.parse("2026-09-30T12:00:00Z")

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun card(type: EventType, starts: String, ends: String, happeningNow: Boolean = false) =
        EventCard(type, SkyblockEvent(type, at(starts), at(ends)), happeningNow, status = "")

    @Test
    fun `upcoming events show their local start time without the year`() {
        val auction = card(EventType.DARK_AUCTION, "2026-10-03T20:35:00Z", "2026-10-03T20:40:00Z")

        assertEquals("Sat 3 Oct, 14:35", auction.localTime(edmonton, now))
        // Same instant, other zone.
        assertEquals("Sat 3 Oct, 20:35", auction.localTime(utc, now))
    }

    @Test
    fun `running events show when they end`() {
        val running = card(EventType.JACOBS_CONTEST, "2026-10-03T20:00:00Z", "2026-10-03T21:00:00Z", happeningNow = true)

        assertEquals("Sat 3 Oct, 15:00", running.localTime(edmonton, Instant.parse("2026-10-03T20:30:00Z")))
        // Not running: its start.
        assertEquals("Sat 3 Oct, 14:00", running.copy(happeningNow = false).localTime(edmonton, now))
    }

    @Test
    fun `dates in another year include the year`() {
        val century = card(EventType.CENTURY_CELEBRATION, "2027-12-01T13:55:00Z", "2027-12-06T17:55:00Z")

        assertEquals("Wed 1 Dec 2027, 06:55", century.localTime(edmonton, now))
        assertEquals("Wed 1 Dec 2027, 13:55", century.localTime(utc, now))
    }

    @Test
    fun `the year is decided in the local zone`() {
        // 05:00 UTC on New Year's Day is still New Year's Eve in Edmonton, where "now" is also 2026.
        val lateEvening = card(EventType.DARK_AUCTION, "2027-01-01T05:00:00Z", "2027-01-01T05:05:00Z")
        val nowIn2026 = Instant.parse("2026-12-31T20:00:00Z")

        assertEquals("Thu 31 Dec, 22:00", lateEvening.localTime(edmonton, nowIn2026))
        assertEquals("Fri 1 Jan 2027, 05:00", lateEvening.localTime(utc, nowIn2026))
    }

    @Test
    fun `estimated events show the date only`() {
        val anniversary = card(EventType.SKYBLOCK_ANNIVERSARY, "2027-06-11T00:00:00Z", "2027-06-18T00:00:00Z")

        assertEquals("Fri 11 Jun 2027", anniversary.localTime(utc, now))
        // Midnight UTC is still the evening before in Edmonton.
        assertEquals("Thu 10 Jun 2027", anniversary.localTime(edmonton, now))
        // In its own year there's no year, and while it runs it shows its end date.
        assertEquals("Fri 11 Jun", anniversary.localTime(utc, Instant.parse("2027-01-01T00:00:00Z")))
        val running = anniversary.copy(happeningNow = true)
        assertEquals("Fri 18 Jun", running.localTime(utc, Instant.parse("2027-06-12T00:00:00Z")))
    }
}
