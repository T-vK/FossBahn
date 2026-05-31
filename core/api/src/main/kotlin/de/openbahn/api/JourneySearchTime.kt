package de.openbahn.api

import java.time.LocalDateTime
import java.time.ZoneId

/** Wall-clock times for bahn.de journey search (always Europe/Berlin, no offset in JSON). */
object JourneySearchTime {
    val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

    fun nowBerlin(): LocalDateTime = LocalDateTime.now(BERLIN)

    /** Rejects departures more than a few minutes in the past (API often returns nothing or HTTP 422). */
    fun forApiRequest(requested: LocalDateTime): LocalDateTime {
        val now = nowBerlin()
        return if (requested.isBefore(now.minusMinutes(3))) now else requested
    }
}
