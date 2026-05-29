package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.model.Location
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    fun summarizeFahrplanResponse(raw: String): String {
        if (raw.contains("OPS_BLOCKED")) return "Response was OPS_BLOCKED."
        return try {
            val root = Json.parseToJsonElement(raw).jsonObject
            val top = root["verbindungen"]?.jsonArray?.size ?: 0
            val intervalle = root["intervalle"]?.jsonArray?.size ?: 0
            val intervalConnections = root["intervalle"]?.jsonArray.orEmpty().sumOf {
                it.jsonObject["verbindungen"]?.jsonArray?.size ?: 0
            }
            val status = root["status"]?.jsonPrimitive?.content
            "API summary: status=$status top-level verbindungen=$top intervalle=$intervalle " +
                "connectionsInIntervalle=$intervalConnections bodyLength=${raw.length}"
        } catch (_: Exception) {
            "Could not summarize response (length=${raw.length})."
        }
    }
}
