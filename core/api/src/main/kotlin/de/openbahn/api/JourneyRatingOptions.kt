package de.openbahn.api

import de.openbahn.model.OnTimeToleranceSettings

/**
 * Options that affect how journeys are scored (transfer buffer, punctuality definition).
 */
data class JourneyRatingOptions(
    /** Minimum transfer time from search filters; used for connection probabilities. */
    val minTransferMinutes: Int? = null,
    /** Per-stop-type on-time windows (heuristic display; ML uses model output). */
    val onTimeTolerance: OnTimeToleranceSettings = OnTimeToleranceSettings(),
) {
    /** Final-arrival tolerance (legacy). */
    val punctualityToleranceMinutes: Int
        get() = onTimeTolerance.arrivalMinutes

    companion object {
        const val DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES = OnTimeToleranceSettings.DEFAULT_MINUTES
    }
}
