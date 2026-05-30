package de.openbahn.model

/** Largest reported delay (minutes) across all stops on the journey. */
fun Journey.maxDelayMinutes(): Int = legs.maxOfOrNull { leg ->
    maxOf(
        leg.origin.delayMinutes ?: 0,
        leg.destination.delayMinutes ?: 0,
        leg.intermediateStops.maxOfOrNull { it.delayMinutes ?: 0 } ?: 0,
    )
} ?: 0
