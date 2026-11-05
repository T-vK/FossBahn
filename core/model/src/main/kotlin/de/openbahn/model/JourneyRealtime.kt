package de.openbahn.model

/** Merges live stop times from [other] into this stop, keeping the richer delay information. */
fun StopEvent.withRealtimeFrom(other: StopEvent): StopEvent {
    val otherDelay = other.delayMinutes
        ?: delayMinutesFromTimes(scheduledTime, other.prognosedTime)
        ?: 0
    val thisDelay = delayMinutes ?: 0
    val otherHasPrognosis = otherDelay > 0
    val shouldMergeDelay = otherDelay > thisDelay || otherHasPrognosis
    val platformChanged = !other.platform.isNullOrBlank() && other.platform != platform
    val cancelledUpdate = other.cancelled && !cancelled
    if (!shouldMergeDelay && !platformChanged && !cancelledUpdate) return this
    return copy(
        prognosedTime = if (shouldMergeDelay) other.prognosedTime ?: prognosedTime else prognosedTime,
        delayMinutes = if (shouldMergeDelay) {
            (other.delayMinutes ?: otherDelay).takeIf { it > 0 } ?: delayMinutes
        } else {
            delayMinutes
        },
        platform = when {
            platformChanged -> other.platform
            shouldMergeDelay -> other.platform ?: platform
            else -> platform
        },
        cancelled = other.cancelled || cancelled,
    )
}

/** Applies refreshed leg times onto the search journey while preserving [id] and list keys. */
fun Journey.withRealtimeFrom(refreshed: Journey): Journey {
    if (refreshed.legs.isEmpty()) return this
    val mergedLegs = legs.mapIndexed { index, leg ->
        val ref = refreshed.legs.getOrNull(index) ?: return@mapIndexed leg
        val refStops = ref.realtimeReferenceStops()
        leg.copy(
            origin = listOf(leg.origin.withRealtimeFrom(ref.origin)).withDelaysFrom(refStops).first(),
            destination = listOf(leg.destination.withRealtimeFrom(ref.destination)).withDelaysFrom(refStops).first(),
            intermediateStops = leg.intermediateStops.withDelaysFrom(refStops),
            priorStops = leg.priorStops.withDelaysFrom(refStops),
            routeStops = when {
                leg.routeStops.isNotEmpty() -> leg.routeStops.withDelaysFrom(refStops)
                ref.routeStops.isNotEmpty() -> ref.routeStops
                else -> emptyList()
            },
            direction = ref.direction?.takeIf { it.isNotBlank() } ?: leg.direction,
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

/** Applies station-board realtime (ezZeit, gleis) onto a leg endpoint. */
fun StopEvent.withBoardRealtime(
    scheduled: String,
    prognosed: String?,
    delayMinutes: Int?,
    platform: String? = null,
): StopEvent {
    val effectiveDelay = delayMinutes
        ?: delayMinutesFromTimes(scheduledTime, prognosed)
        ?: delayMinutesFromTimes(scheduled, prognosed)
    val hasDelayUpdate = (effectiveDelay ?: 0) > 0
    val platformChanged = !platform.isNullOrBlank() && platform != this.platform
    if (!hasDelayUpdate && !platformChanged) return this
    return copy(
        prognosedTime = if (hasDelayUpdate) prognosed ?: prognosedTime else prognosedTime,
        delayMinutes = if (hasDelayUpdate && effectiveDelay != null) {
            maxOf(this.delayMinutes ?: 0, effectiveDelay).takeIf { it > 0 }
        } else {
            this.delayMinutes
        },
        platform = when {
            platformChanged -> platform
            else -> this.platform
        },
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
