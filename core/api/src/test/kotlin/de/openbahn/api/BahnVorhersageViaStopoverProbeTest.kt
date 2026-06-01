package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/** Explores mobile v2 response shape when trip stopovers include intermediate stations. */
@Tag("live-api")
class BahnVorhersageViaStopoverProbeTest {
    private val client = BahnVorhersageClient.createHttpClient()

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_API_TESTS", matches = "true")
    fun post_legWithIntermediateStopover_returns200AndMayIncludeStopoverPredictions() = runBlocking {
        val text = javaClass.getResource("/dbweb-journey-intermediate-halte.json")!!.readText()
        val journey = JourneyResponseParser.parse(text).journeys.first()
        val leg = journey.legs.first { !it.isWalking }
        require(leg.intermediateStops.isNotEmpty()) { "fixture needs intermediate stops" }
        val tripId = ratingTripId(journey.id, 0, leg)
        val route = listOf(leg.origin) + leg.intermediateStops + listOf(leg.destination)
        val trips = mapOf(tripId to ensureLegEndpoints(route, leg))
        assertTrue(trips.values.first().size >= 3)
        val body = BahnVorhersageFptfMapper.buildRateRequest(listOf(journey), trips)
        val payload = Json.encodeToString(JsonElement.serializer(), body)
        assertTrue(payload.contains("Hannover Hbf"), payload)
        val response = client.post("${BahnVorhersageClient.DEFAULT_MOBILE_BASE_URL}/journeys") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val responseText = response.bodyAsText()
        assertTrue(
            response.status.isSuccess(),
            "HTTP ${response.status.value}: ${responseText.take(500)}",
        )
        // Document for mapper work: stopover-level predictions if present
        println("via-probe response snippet: ${responseText.take(4000)}")
        client.close()
    }
}
