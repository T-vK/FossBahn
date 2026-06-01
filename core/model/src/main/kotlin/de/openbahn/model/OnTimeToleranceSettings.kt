package de.openbahn.model

import kotlinx.serialization.Serializable

/** How many minutes of delay still count as “on time” for stop probabilities. */
@Serializable
data class OnTimeToleranceSettings(
    val departureMinutes: Int = DEFAULT_MINUTES,
    val viaStopMinutes: Int = DEFAULT_MINUTES,
    val arrivalMinutes: Int = DEFAULT_MINUTES,
) {
    init {
        require(departureMinutes >= 0)
        require(viaStopMinutes >= 0)
        require(arrivalMinutes >= 0)
    }

    /** Tolerance for a [StopTimelinessPrediction] or UI stop row. */
    fun forStop(intermediateIndex: Int?, isArrival: Boolean): Int = when {
        intermediateIndex != null -> viaStopMinutes
        isArrival -> arrivalMinutes
        else -> departureMinutes
    }

    fun withUniform(minutes: Int): OnTimeToleranceSettings = copy(
        departureMinutes = minutes,
        viaStopMinutes = minutes,
        arrivalMinutes = minutes,
    )

    companion object {
        const val DEFAULT_MINUTES = 10
        val PRESET_MINUTES = listOf(0, 3, 5, 10, 15, 20, 30)

        fun uniform(minutes: Int): OnTimeToleranceSettings = OnTimeToleranceSettings(
            departureMinutes = minutes,
            viaStopMinutes = minutes,
            arrivalMinutes = minutes,
        )
    }
}
