package de.openbahn.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val isoLocalFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

/** Delay at final destination (prognosed vs scheduled), in whole minutes. */
fun Journey.arrivalDelayMinutes(): Int {
    val last = railLegs().lastOrNull()?.destination ?: return 0
    return last.delayMinutes
        ?: delayMinutesFromTimes(last.scheduledTime, last.prognosedTime)
        ?: 0
}

fun Journey.hasRailCancellation(): Boolean = railLegs().any { leg ->
    leg.origin.cancelled || leg.destination.cancelled ||
        leg.intermediateStops.any { it.cancelled }
}

/**
 * Counts rail connections where the next leg departs before the previous leg arrives
 * (plus [minTransferMinutes] buffer).
 */
fun Journey.missedTransferCount(minTransferMinutes: Int): Int {
    val rail = railLegs()
    if (rail.size < 2) return 0
    var missed = 0
    for (index in 0 until rail.lastIndex) {
        val arrivalIso = rail[index].destination.prognosedTime
            ?: rail[index].destination.scheduledTime
        val departureIso = rail[index + 1].origin.prognosedTime
            ?: rail[index + 1].origin.scheduledTime
        val arrival = parseIsoLocal(arrivalIso) ?: continue
        val departure = parseIsoLocal(departureIso) ?: continue
        val requiredDeparture = arrival.plusMinutes(minTransferMinutes.toLong())
        if (departure.isBefore(requiredDeparture)) missed++
    }
    return missed
}

private fun parseIsoLocal(iso: String): LocalDateTime? = runCatching {
    LocalDateTime.parse(iso.take(19), isoLocalFormatter)
}.getOrNull()
