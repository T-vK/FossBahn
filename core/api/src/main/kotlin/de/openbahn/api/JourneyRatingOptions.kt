package de.openbahn.api

/**
 * Options that affect how journeys are scored (transfer buffer, punctuality definition).
 */
data class JourneyRatingOptions(
    /** Minimum transfer time from search filters; used for connection probabilities. */
    val minTransferMinutes: Int? = null,
    /** Max acceptable arrival delay in minutes (0 = on time to the minute). */
    val punctualityToleranceMinutes: Int = DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES,
) {
    init {
        require(punctualityToleranceMinutes >= 0) { "punctualityToleranceMinutes must be >= 0" }
    }

    companion object {
        const val DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES = 10
    }
}
