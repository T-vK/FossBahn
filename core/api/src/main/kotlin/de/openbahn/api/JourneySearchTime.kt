package de.openbahn.api

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Wall-clock times for bahn.de journey search (always Europe/Berlin, no offset in JSON). */
object JourneySearchTime {
    val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

    fun nowBerlin(): LocalDateTime = LocalDateTime.now(BERLIN).truncatedTo(ChronoUnit.SECONDS)

    /**
     * bahn.de often returns `{}` or no connections for times in the past (even by seconds).
     * Use Berlin wall time, drop sub-second precision, and bump to soonest future minute.
     */
    fun forApiRequest(requested: LocalDateTime): LocalDateTime {
        val now = nowBerlin()
        val normalized = requested.withNano(0)
        return if (!normalized.isAfter(now)) {
            now.plusMinutes(1)
        } else {
            normalized
        }
    }
}
