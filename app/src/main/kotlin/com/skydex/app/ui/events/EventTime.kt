package com.skydex.app.ui.events

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Formatters for event times: with and without the year, and date-only ones for estimated events. All must use
 * [zone], which also decides whether a time falls in the current year.
 */
class EventTimeFormat(
    val dateTime: DateTimeFormatter,
    val dateTimeWithYear: DateTimeFormatter,
    val date: DateTimeFormatter,
    val dateWithYear: DateTimeFormatter,
    val zone: ZoneId,
)

/**
 * The real date and time this card counts down to (its end while it's running), e.g. "Sat 3 Oct, 14:35". Adds the
 * year when it isn't this year (at [now]), and leaves out the time of day for estimated events.
 */
fun EventCard.localTime(format: EventTimeFormat, now: Instant): String {
    val at = Instant.ofEpochMilli(if (happeningNow) next.endsAt else next.startsAt)
    val sameYear = at.atZone(format.zone).year == now.atZone(format.zone).year
    val formatter = when {
        type.estimated -> if (sameYear) format.date else format.dateWithYear
        else -> if (sameYear) format.dateTime else format.dateTimeWithYear
    }
    return formatter.format(at)
}

/** Weekday, day, month and time in the device's locale, 12/24-hour setting and time zone. */
@Composable
fun rememberEventTimeFormat(): EventTimeFormat {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24h = DateFormat.is24HourFormat(context)
    val zone = ZoneId.systemDefault()
    return remember(locale, is24h, zone) {
        val time = if (is24h) "HHmm" else "hmma"
        val dateTimeFallback = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        val dateFallback = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        EventTimeFormat(
            dateTime = bestFormatter(locale, "EEEdMMM$time", dateTimeFallback, zone),
            dateTimeWithYear = bestFormatter(locale, "yEEEdMMM$time", dateTimeFallback, zone),
            date = bestFormatter(locale, "EEEdMMM", dateFallback, zone),
            dateWithYear = bestFormatter(locale, "yEEEdMMM", dateFallback, zone),
            zone = zone,
        )
    }
}

/** The locale's best pattern for [skeleton], or [fallback] if Android's pattern isn't one java.time can parse. */
private fun bestFormatter(locale: Locale, skeleton: String, fallback: DateTimeFormatter, zone: ZoneId): DateTimeFormatter =
    runCatching { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
        .getOrElse { fallback.withLocale(locale) }
        .withZone(zone)
