package de.openbahn.api

import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * End-to-end API parsing test without network: Hamburg Hbf → Berlin Hbf journey fixture.
 */
class DbVendoJourneyIntegrationTest {
  private val journeyJson =
        checkNotNull(javaClass.getResource("/dbweb-journey-hamburg-berlin.json")).readText()

    private val hamburg = Location(id = "8002549", name = "Hamburg Hbf", evaNumber = "8002549")
    private val berlin = Location(id = "8011160", name = "Berlin Hbf", evaNumber = "8011160")

    private fun clientWithFixture(): DbVendoClient {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/angebote/fahrplan") -> respond(
                    content = journeyJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            defaultRequest {
                headers.append("Accept", "application/json")
            }
        }
        return DbVendoClient(httpClient = http)
    }

    @Test
    fun searchJourneys_parsesHamburgToBerlinFixture() = runTest {
        val client = clientWithFixture()
        val journeys = client.searchJourneys(
            hamburg,
            berlin,
            JourneySearchOptions(),
            LocalDateTime.of(2026, 5, 30, 8, 0),
        )
        assertEquals(1, journeys.size)
        val j = journeys.first()
        assertEquals("Hamburg Hbf", j.legs.first().origin.name)
        assertEquals("Berlin Hbf", j.legs.first().destination.name)
        assertEquals("7", j.legs.first().origin.platform)
        assertEquals("ICE 701", j.legs.first().lineName)
    }

    @Test
    fun searchJourneys_mapsOpsBlockedFromErrorBody() = runTest {
        val engine = MockEngine {
            respond(
                """{"status":"ERROR","code":"OPS_BLOCKED"}""",
                HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val client = DbVendoClient(httpClient = http)
        try {
            client.searchJourneys(hamburg, berlin)
            error("Expected DbApiBlockedException")
        } catch (e: DbApiBlockedException) {
            assertTrue(e.message!!.contains("blocked", ignoreCase = true))
        }
    }
}
