package de.openbahn.navigator.navigation

import de.openbahn.model.Journey
import de.openbahn.model.RatedJourney

/** In-memory holder for journey detail navigation (avoids huge nav args). */
object JourneyNavigation {
    private var pendingJourney: Journey? = null
    private var pendingPrediction: RatedJourney? = null
    private var pendingPredictionsRequested: Boolean = false

    fun set(journey: Journey, prediction: RatedJourney? = null, predictionsRequested: Boolean = false) {
        pendingJourney = journey
        pendingPrediction = prediction
        pendingPredictionsRequested = predictionsRequested
    }

    fun consume(): JourneyDetailPayload? {
        val journey = pendingJourney ?: return null
        val payload = JourneyDetailPayload(
            journey = journey,
            prediction = pendingPrediction,
            predictionsRequested = pendingPredictionsRequested,
        )
        clear()
        return payload
    }

    fun clear() {
        pendingJourney = null
        pendingPrediction = null
        pendingPredictionsRequested = false
    }
}

data class JourneyDetailPayload(
    val journey: Journey,
    val prediction: RatedJourney?,
    val predictionsRequested: Boolean,
)
