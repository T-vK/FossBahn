package de.openbahn.api

import de.openbahn.api.debug.FahrplanDiagnostics
import de.openbahn.model.Location
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.LocalDateTime
import java.time.ZoneId

internal object LiveApiTestSupport {
    const val HAMBURG_HBF_EVA = "8002549"
    const val BERLIN_HBF_EVA = "8011160"
    val BERLIN_ZONE: ZoneId = ZoneId.of("Europe/Berlin")

    suspend fun DbVendoClient.findStation(
        query: String,
        preferredEva: String? = null,
    ): Location? {
        val results = searchLocations(query, locale = "de")
        if (results.isEmpty()) return null
        preferredEva?.let { eva ->
            results.firstOrNull { it.evaNumber == eva }?.let { return it }
        }
        results.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return it }
        return results.first()
    }

    fun berlinDeparturePlusHours(hours: Long): LocalDateTime =
        LocalDateTime.now(BERLIN_ZONE).plusHours(hours)

    suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (e: DbApiBlockedException) {
        assumeTrue(false, "Deutsche Bahn blocked this IP (OPS_BLOCKED): ${e.message}")
        error("unreachable")
    }

    fun requireFullBahnId(location: Location, label: String) {
        check(location.id.startsWith("A=1@")) {
            "$label: expected full bahn location id from /orte, got id=${location.id}"
        }
    }

    fun assertJourneysNotEmpty(
        journeys: List<de.openbahn.model.Journey>,
        from: Location,
        to: Location,
        rawResponse: String? = null,
    ) {
        if (journeys.isNotEmpty()) return
        val hint = rawResponse?.let(::summarizeFahrplanResponse).orEmpty()
        Assertions.fail<Nothing>(
            "Expected journeys ${from.name}→${to.name} but parser returned none (UI shows " +
                "\"No connections found\"). $hint",
        )
    }

    fun summarizeFahrplanResponse(raw: String): String = FahrplanDiagnostics.summarizeFahrplanBody(raw)
}
