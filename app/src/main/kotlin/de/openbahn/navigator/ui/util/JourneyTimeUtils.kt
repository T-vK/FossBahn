package de.openbahn.navigator.ui.util

import de.openbahn.model.Journey
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME

fun parseJourneyDateTime(iso: String): LocalDateTime? {
    if (iso.isBlank()) return null
    return runCatching {
        val normalized = iso.trim().let { s ->
            when {
                s.length >= 19 && s[10] == 'T' -> s.take(19)
                else -> s
            }
        }
        LocalDateTime.parse(normalized, ISO_LOCAL)
    }.getOrNull()
}

fun journeyArrivalDateTime(journey: Journey): LocalDateTime? {
    val last = journey.legs.lastOrNull()?.destination
    val iso = last?.prognosedTime?.takeIf { it.isNotBlank() }
        ?: last?.scheduledTime?.takeIf { it.isNotBlank() }
        ?: journey.arrival
    return parseJourneyDateTime(iso)
}

/** True when the journey arrived more than [graceHours] ago. */
fun isJourneyLongArrived(journey: Journey, graceHours: Long = 2, now: LocalDateTime = LocalDateTime.now()): Boolean {
    val arrival = journeyArrivalDateTime(journey) ?: return false
    return ChronoUnit.HOURS.between(arrival, now) > graceHours
}

fun isIsoLongArrived(iso: String, graceHours: Long = 2): Boolean {
    val arrival = parseJourneyDateTime(iso) ?: return false
    return ChronoUnit.HOURS.between(arrival, LocalDateTime.now()) > graceHours
}

fun localDateTimeToEpochMillis(time: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
    time.atZone(zone).toInstant().toEpochMilli()

fun epochMillisToLocalDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
