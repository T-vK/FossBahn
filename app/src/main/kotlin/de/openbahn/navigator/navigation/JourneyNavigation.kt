package de.openbahn.navigator.navigation

import de.openbahn.model.Journey
import de.openbahn.model.RatedJourney

/** In-memory holder for journey detail navigation (avoids huge nav args). */
object JourneyNavigation {
    private var pendingJourney: Journey? = null
    private var pendingPrediction: RatedJourney? = null
    private var pendingPredictionsRequested: Boolean = false
    private var pendingMinTransferMinutes: Int? = null

    fun set(
        journey: Journey,
        prediction: RatedJourney? = null,
        predictionsRequested: Boolean = false,
        minTransferMinutes: Int? = null,
    ) {
        pendingJourney = journey
        pendingPrediction = prediction
        pendingPredictionsRequested = predictionsRequested
        pendingMinTransferMinutes = minTransferMinutes
    }

    fun consume(): JourneyDetailPayload? {
        val journey = pendingJourney ?: return null
        val payload = JourneyDetailPayload(
            journey = journey,
            prediction = pendingPrediction,
            predictionsRequested = pendingPredictionsRequested,
            minTransferMinutes = pendingMinTransferMinutes,
        )
        clear()
        return payload
    }

    fun clear() {
        pendingJourney = null
        pendingPrediction = null
        pendingPredictionsRequested = false
        pendingMinTransferMinutes = null
    }
}

data class JourneyDetailPayload(
    val journey: Journey,
    val prediction: RatedJourney?,
    val predictionsRequested: Boolean,
    val minTransferMinutes: Int? = null,
)
