package de.openbahn.model

import kotlinx.serialization.Serializable

/**
 * On-time probability for a scheduled departure or arrival at a stop on a leg.
 * [intermediateIndex] is set for via stops; null for leg origin (departure) or destination (arrival).
 */
@Serializable
data class StopTimelinessPrediction(
    val legIndex: Int,
    val intermediateIndex: Int? = null,
    val isArrival: Boolean,
    val probability: Double,
    val isEstimate: Boolean = true,
)

fun RatedJourney.stopProbability(
    legIndex: Int,
    intermediateIndex: Int? = null,
    isArrival: Boolean,
): Double? = stopTimeliness
    .firstOrNull {
        it.legIndex == legIndex &&
            it.intermediateIndex == intermediateIndex &&
            it.isArrival == isArrival
    }
    ?.probability

/** True when Bahn-Vorhersage scored at least one endpoint on this rail leg. */
fun RatedJourney.hasMlTimelinessOnLeg(legIndex: Int): Boolean =
    stopTimeliness.any { it.legIndex == legIndex && !it.isEstimate }

fun RatedJourney.toleranceMinutesForStop(
    intermediateIndex: Int? = null,
    isArrival: Boolean,
): Int = effectiveOnTimeTolerance().forStop(intermediateIndex, isArrival)

fun RatedJourney.stopTimelinessIsEstimate(
    legIndex: Int,
    intermediateIndex: Int? = null,
    isArrival: Boolean,
): Boolean = stopTimeliness
    .firstOrNull {
        it.legIndex == legIndex &&
            it.intermediateIndex == intermediateIndex &&
            it.isArrival == isArrival
    }
    ?.isEstimate
    ?: true
