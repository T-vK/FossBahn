package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
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
 * Posts real OpenBahn-shaped FPTF to bahnvorhersage mobile v2 (catches HTTP 500 regressions).
 *
 * Run: `RUN_LIVE_API_TESTS=true ./gradlew :core:api:test`
 */
@Tag("live-api")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_API_TESTS", matches = "true")
class BahnVorhersageMobileV2ProbeTest {
  private val client = BahnVorhersageClient.createHttpClient()

  @Test
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
