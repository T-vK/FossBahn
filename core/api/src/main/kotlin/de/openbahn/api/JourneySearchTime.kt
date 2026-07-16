package de.openbahn.api

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Wall-clock times for bahn.de journey search (always Europe/Berlin, no offset in JSON). */
object JourneySearchTime {
    val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

    fun nowBerlin(): LocalDateTime = LocalDateTime.now(BERLIN).truncatedTo(ChronoUnit.SECONDS)

    /**
     * Normalizes the wall-clock time sent to bahn.de (drops sub-second precision).
     *
     * For departure searches, bahn.de often returns `{}` for times in the past (even by
     * seconds), so a past departure is bumped to the soonest future minute. Arrival searches
     * must keep the requested time verbatim: bumping it to "now" would turn an "arrive by T"
     * query into "arrive by now", returning connections that all arrive far too early.
     */
    fun forApiRequest(requested: LocalDateTime, arrivalSearch: Boolean = false): LocalDateTime {
        val normalized = requested.withNano(0)
        if (arrivalSearch) return normalized
        val now = nowBerlin()
        return if (!normalized.isAfter(now)) {
            now.plusMinutes(1)
        } else {
            normalized
        }
    }
}
