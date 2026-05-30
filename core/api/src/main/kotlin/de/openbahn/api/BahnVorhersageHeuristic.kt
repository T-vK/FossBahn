package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.TransportProduct
import de.openbahn.model.TransferPrediction

/**
 * Fallback transfer scores when the Bahn-Vorhersage API is unavailable.
 * bahnvorhersage.de does not offer a public HTTP API for third-party apps.
 */
internal object BahnVorhersageHeuristic {
    fun estimatePunctuality(journey: Journey): Double? {
        val lastLeg = journey.legs.lastOrNull() ?: return null
        val delay = lastLeg.destination.delayMinutes ?: lastLeg.origin.delayMinutes ?: 0
        val isLongHaul = lastLeg.product in setOf(TransportProduct.ICE, TransportProduct.IC_EC) ||
            lastLeg.lineName?.startsWith("ICE", ignoreCase = true) == true ||
            lastLeg.lineName?.startsWith("IC", ignoreCase = true) == true
        val base = if (isLongHaul) 0.86 else 0.80
        val penalty = when {
            delay >= 20 -> 0.45
            delay >= 15 -> 0.55
            delay >= 10 -> 0.65
            delay >= 5 -> 0.78
            delay > 0 -> 0.88
            else -> 1.0
        }
        return (base * penalty).coerceIn(0.2, 0.98)
    }

    fun estimate(journey: Journey): List<TransferPrediction> {
        if (journey.legs.size < 2) return emptyList()
        return (0 until journey.legs.lastIndex).map { index ->
            val transferMins = BahnVorhersageRequestBuilder.transferMinutesBetween(
                journey.legs[index].destination,
                journey.legs[index + 1].origin,
            )
            TransferPrediction(
                legIndex = index,
                successProbability = scoreForTransferMinutes(transferMins),
                isEstimate = true,
            )
        }
    }

    private fun scoreForTransferMinutes(transferMins: Long?): Double? {
        if (transferMins == null) return null
        return when {
            transferMins < 0 -> 0.15
            transferMins >= 25 -> 0.93
            transferMins >= 18 -> 0.85
            transferMins >= 12 -> 0.72
            transferMins >= 8 -> 0.55
            transferMins >= 5 -> 0.38
            else -> 0.22
        }
    }
}
