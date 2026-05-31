package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.TransportProduct
import de.openbahn.model.TransferPrediction

/**
 * Fallback transfer scores when the Bahn-Vorhersage API is unavailable.
 * bahnvorhersage.de does not offer a public HTTP API for third-party apps.
 */
internal object BahnVorhersageHeuristic {
    fun estimatePunctuality(journey: Journey, toleranceMinutes: Int): Double? {
        val lastLeg = journey.legs.lastOrNull() ?: return null
        val delay = lastLeg.destination.delayMinutes ?: lastLeg.origin.delayMinutes ?: 0
        if (delay > toleranceMinutes) {
            val over = delay - toleranceMinutes
            return when {
                over >= 15 -> 0.35
                over >= 10 -> 0.45
                over >= 5 -> 0.55
                else -> 0.70
            }.coerceIn(0.15, 0.75)
        }
        val isLongHaul = lastLeg.product in setOf(TransportProduct.ICE, TransportProduct.IC_EC) ||
            lastLeg.lineName?.startsWith("ICE", ignoreCase = true) == true ||
            lastLeg.lineName?.startsWith("IC", ignoreCase = true) == true
        val base = if (isLongHaul) 0.86 else 0.80
        val toleranceBonus = when {
            toleranceMinutes == 0 -> 0.92
            toleranceMinutes <= 5 -> 0.96
            else -> 1.0
        }
        return (base * toleranceBonus).coerceIn(0.2, 0.98)
    }

    fun estimate(journey: Journey, minTransferMinutes: Int? = null): List<TransferPrediction> {
        if (journey.legs.size < 2) return emptyList()
        return (0 until journey.legs.lastIndex).map { index ->
            val transferMins = BahnVorhersageRequestBuilder.transferMinutesBetween(
                journey.legs[index].destination,
                journey.legs[index + 1].origin,
            )
            TransferPrediction(
                legIndex = index,
                successProbability = scoreForTransferMinutes(transferMins, minTransferMinutes),
                isEstimate = true,
            )
        }
    }

    private fun scoreForTransferMinutes(transferMins: Long?, minTransferMinutes: Int?): Double? {
        if (transferMins == null) return null
        val slack = if (minTransferMinutes != null) transferMins - minTransferMinutes else transferMins
        return when {
            slack < 0 -> 0.12
            slack >= 25 -> 0.93
            slack >= 18 -> 0.85
            slack >= 12 -> 0.72
            slack >= 8 -> 0.55
            slack >= 5 -> 0.38
            else -> 0.22
        }
    }
}
