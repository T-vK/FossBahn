package de.openbahn.api

import de.openbahn.model.Location
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Helpers for JVM integration tests against int.bahn.de (no emulator).
 */
internal object LiveApiTestSupport {
    const val HAMBURG_HBF_EVA = "8002549"
    const val BERLIN_HBF_EVA = "8011160"

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
    ) {
        if (journeys.isNotEmpty()) return
        Assertions.fail<Nothing>(
            "Expected journeys ${from.name}→${to.name} but parser returned none. " +
                "If the API responded, check intervalle/verbindungen parsing.",
        )
    }
}
