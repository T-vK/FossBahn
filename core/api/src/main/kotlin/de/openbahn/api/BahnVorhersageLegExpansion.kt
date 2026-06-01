package de.openbahn.api

import de.openbahn.model.Leg
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * One leg in the FPTF payload sent to bahnvorhersage mobile v2.
 *
 * @param sourceLegIndex index in the original [de.openbahn.model.Journey.legs] list
 */
internal data class RatingLeg(
    val leg: Leg,
    val sourceLegIndex: Int,
    val syntheticWalk: Boolean = false,
)

/**
 * bahnvorhersage [add_minimal_transfer_times_to_journey] requires a walking segment between
 * every pair of consecutive rail legs; DB often omits explicit WALK abschnitte at the same station.
 */
internal fun expandLegsForRating(legs: List<Leg>): List<RatingLeg> {
    if (legs.isEmpty()) return emptyList()
    val expanded = mutableListOf<RatingLeg>()
    legs.forEachIndexed { index, leg ->
        if (index > 0) {
            val previous = legs[index - 1]
            if (!previous.isWalking && !leg.isWalking) {
                expanded += RatingLeg(
                    leg = syntheticWalkLeg(previous, leg),
                    sourceLegIndex = index - 1,
                    syntheticWalk = true,
                )
            }
        }
        expanded += RatingLeg(leg = leg, sourceLegIndex = index)
    }
    return expanded
}

private fun syntheticWalkLeg(arrivalLeg: Leg, departureLeg: Leg): Leg {
    val origin = arrivalLeg.destination
    val destination = departureLeg.origin
    val durationMinutes = transferMinutesBetween(origin.scheduledTime, destination.scheduledTime)
    return Leg(
        origin = origin,
        destination = destination,
        isWalking = true,
        durationMinutes = durationMinutes?.toInt()?.coerceAtLeast(1),
    )
}

private val localTime = DateTimeFormatter.ISO_LOCAL_DATE_TIME

private fun transferMinutesBetween(arrivalTime: String, departureTime: String): Long? {
    if (arrivalTime.isBlank() || departureTime.isBlank()) return null
    return runCatching {
        val arr = LocalDateTime.parse(arrivalTime.take(19), localTime)
        val dep = LocalDateTime.parse(departureTime.take(19), localTime)
        java.time.Duration.between(arr, dep).toMinutes()
    }.getOrNull()
}
