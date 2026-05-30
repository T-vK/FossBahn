package de.openbahn.model

/** Merges live stop times from [other] into this stop, keeping the richer delay information. */
fun StopEvent.withRealtimeFrom(other: StopEvent): StopEvent {
    val otherDelay = other.delayMinutes ?: 0
    val thisDelay = delayMinutes ?: 0
    val otherHasPrognosis = other.prognosedTime != null && other.prognosedTime != scheduledTime
    val thisHasPrognosis = prognosedTime != null && prognosedTime != scheduledTime
    if (otherDelay <= thisDelay && !otherHasPrognosis) return this
    return copy(
        prognosedTime = other.prognosedTime ?: prognosedTime,
        delayMinutes = other.delayMinutes ?: delayMinutes,
        platform = other.platform ?: platform,
        cancelled = other.cancelled || cancelled,
    )
}

/** Applies refreshed leg times onto the search journey while preserving [id] and list keys. */
fun Journey.withRealtimeFrom(refreshed: Journey): Journey {
    if (refreshed.legs.isEmpty()) return this
    val mergedLegs = legs.mapIndexed { index, leg ->
        val ref = refreshed.legs.getOrNull(index) ?: return@mapIndexed leg
        leg.copy(
            origin = leg.origin.withRealtimeFrom(ref.origin),
            destination = leg.destination.withRealtimeFrom(ref.destination),
            intermediateStops = leg.intermediateStops.mapIndexed { stopIndex, stop ->
                ref.intermediateStops.getOrNull(stopIndex)?.let { stop.withRealtimeFrom(it) } ?: stop
            },
        )
    }
    val first = mergedLegs.first()
    val last = mergedLegs.last()
    return copy(
        legs = mergedLegs,
        departure = first.origin.prognosedTime ?: first.origin.scheduledTime,
        arrival = last.destination.prognosedTime ?: last.destination.scheduledTime,
        refreshToken = refreshToken ?: refreshed.refreshToken,
    )
}

/** Applies station-board realtime (ezZeit) onto a leg endpoint. */
fun StopEvent.withBoardRealtime(scheduled: String, prognosed: String?, delayMinutes: Int?): StopEvent {
    val effectiveDelay = delayMinutes ?: delayMinutesFromTimes(scheduled, prognosed) ?: return this
    if (effectiveDelay <= 0 && (prognosed == null || prognosed == scheduledTime)) return this
    return copy(
        prognosedTime = prognosed ?: prognosedTime,
        delayMinutes = maxOf(delayMinutes ?: 0, effectiveDelay).takeIf { it > 0 },
    )
}

fun delayMinutesFromTimes(scheduled: String, prognosed: String?): Int? {
    if (prognosed.isNullOrBlank() || prognosed == scheduled) return null
    val s = isoTimeToEpochMillis(scheduled) ?: return null
    val p = isoTimeToEpochMillis(prognosed) ?: return null
    return ((p - s) / 60_000).toInt().takeIf { it > 0 }
}

fun isoTimeToEpochMillis(iso: String): Long? {
    val trimmed = iso.trim()
    trimmed.toLongOrNull()?.let { raw ->
        return when {
            raw > 1_000_000_000_000L -> raw
            raw > 1_000_000_000L -> raw * 1000
            else -> null
        }
    }
    return try {
        java.time.Instant.parse(trimmed).toEpochMilli()
    } catch (_: Exception) {
        try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            java.time.LocalDateTime.parse(trimmed.take(19), formatter)
                .atZone(java.time.ZoneId.of("Europe/Berlin"))
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
