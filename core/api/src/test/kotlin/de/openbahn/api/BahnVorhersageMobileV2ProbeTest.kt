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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Posts OpenBahn-shaped FPTF to bahnvorhersage mobile v2 (catches HTTP 500 regressions).
 *
 * `post_twoRailLegHamburgBerlinShape_returns200` runs on every CI build (release gate).
 * `post_dbVendoMultiLegFixture_returns200` needs `RUN_LIVE_API_TESTS=true`.
 */
@Tag("live-api")
class BahnVorhersageMobileV2ProbeTest {
  private val client = BahnVorhersageClient.createHttpClient()

  /** Hamburg→Berlin-style: two rail legs, no DB WALK abschnitt (must insert synthetic transfer). */
  @Test
  fun post_twoRailLegHamburgBerlinShape_returns200() = runBlocking {
    val journey = hamburgBerlinTwoLegJourney()
    val trips = buildTripRoutesForRating(journey)
    assertEquals(2, trips.size)
    val body = BahnVorhersageFptfMapper.buildRateRequest(listOf(journey), trips)
    val payload = Json.encodeToString(JsonElement.serializer(), body)
    assertTrue(payload.contains("\"walking\":true"), "synthetic walk required between rail legs")
    val response = client.post("${BahnVorhersageClient.DEFAULT_MOBILE_BASE_URL}/journeys") {
      contentType(ContentType.Application.Json)
      setBody(payload)
    }
    assertTrue(
      response.status.isSuccess(),
      "mobile v2 HTTP ${response.status.value}: ${response.bodyAsText().take(400)}",
    )
    assertTrue(response.bodyAsText().contains("departureDelayPrediction"))
    client.close()
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "RUN_LIVE_API_TESTS", matches = "true")
  fun post_dbVendoMultiLegFixture_returns200() = runBlocking {
    val text = javaClass.getResource("/dbweb-journey-db-vendo-real.json")!!.readText()
    val journey = JourneyResponseParser.parse(text).journeys.first()
    assertEndpointNamesMatch(journey)
    val trips = buildTripRoutesForRating(journey)
    val railLegs = journey.legs.count { !it.isWalking }
    assertEquals(railLegs, trips.size, "every rail leg needs a trip entry")
    val body = BahnVorhersageFptfMapper.buildRateRequest(listOf(journey), trips)
    val payload = Json.encodeToString(JsonElement.serializer(), body)
    val response = client.post("${BahnVorhersageClient.DEFAULT_MOBILE_BASE_URL}/journeys") {
      contentType(ContentType.Application.Json)
      setBody(payload)
    }
    val preview = response.bodyAsText().take(400)
    assertTrue(
      response.status.isSuccess(),
      "mobile v2 HTTP ${response.status.value}: $preview",
    )
    client.close()
  }

  private fun hamburgBerlinTwoLegJourney(): Journey = Journey(
    id = "gate-hh-be",
    legs = listOf(
      Leg(
        origin = StopEvent("Hamburg Hbf", id = "8002549", scheduledTime = "2026-06-01T18:00:00"),
        destination = StopEvent("Hannover Hbf", id = "8000152", scheduledTime = "2026-06-01T19:00:00"),
        lineName = "ICE 703",
      ),
      Leg(
        origin = StopEvent("Hannover Hbf", id = "8000152", scheduledTime = "2026-06-01T19:15:00"),
        destination = StopEvent("Berlin Hbf", id = "8011160", scheduledTime = "2026-06-01T20:30:00"),
        lineName = "ICE 1001",
      ),
    ),
    durationMinutes = 150,
    transfers = 1,
    departure = "2026-06-01T18:00:00",
    arrival = "2026-06-01T20:30:00",
  )

  private fun assertEndpointNamesMatch(journey: de.openbahn.model.Journey) {
    journey.legs.forEachIndexed { legIndex, leg ->
      if (leg.isWalking) return@forEachIndexed
      val tripId = ratingTripId(journey.id, legIndex, leg)
      val stops = buildTripRoutesForRating(journey)[tripId] ?: error("missing trip $tripId")
      assertTrue(
        stops.first().name == leg.origin.name,
        "leg $legIndex origin ${leg.origin.name} != trip first ${stops.first().name}",
      )
      assertTrue(
        stops.last().name == leg.destination.name,
        "leg $legIndex dest ${leg.destination.name} != trip last ${stops.last().name}",
      )
    }
  }
}
