package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        ).journeys
        assertEquals(1, journeys.size)
        val j = journeys.first()
        assertEquals("Hamburg Hbf", j.legs.first().origin.name)
        assertEquals("Berlin Hbf", j.legs.first().destination.name)
        assertEquals("7", j.legs.first().origin.platform)
        assertEquals("ICE 701", j.legs.first().lineName)
    }

    @Test
    fun journeyResponseParser_readsHamburgBerlinFixture() {
        val text = journeyJson
        val journeys = JourneyResponseParser.parse(text).journeys
        assertEquals(1, journeys.size)
        assertEquals("Hamburg Hbf", journeys.first().legs.first().origin.name)
        assertEquals("Berlin Hbf", journeys.first().legs.last().destination.name)
    }

    private fun capturingClient(response: String, sentTimes: MutableList<String>): DbVendoClient {
        val engine = MockEngine { request ->
            val body = (request.body as TextContent).text
            Regex("\"anfrageZeitpunkt\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?.let { sentTimes += it }
            respond(
                content = response,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        return DbVendoClient(httpClient = http)
    }

    @Test
    fun searchJourneys_emptyResponse_doesNotOverrideChosenFutureDeparture() = runTest {
        val sentTimes = mutableListOf<String>()
        val client = capturingClient("{}", sentTimes)
        val future = JourneySearchTime.nowBerlin().plusHours(6).withSecond(0).withNano(0)

        client.searchJourneys(hamburg, berlin, JourneySearchOptions(), future)

        val expected = future.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        // Regression: a future departure that yields no connections must not be silently
        // re-queried at "now + 3 min" (which surfaced current-time results instead).
        assertEquals(listOf(expected), sentTimes)
    }

    @Test
    fun searchJourneys_emptyResponse_doesNotOverrideChosenArrivalTime() = runTest {
        val sentTimes = mutableListOf<String>()
        val client = capturingClient("{}", sentTimes)
        val arrival = JourneySearchTime.nowBerlin().plusHours(6).withSecond(0).withNano(0)

        client.searchJourneys(hamburg, berlin, JourneySearchOptions(arrivalSearch = true), arrival)

        val expected = arrival.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        // Arrival "arrive by T" must stay T, never be shifted to the current time.
        assertEquals(listOf(expected), sentTimes)
    }

    @Test
    fun searchJourneys_emptyResponse_stillRetriesBumpedPastDeparture() = runTest {
        val sentTimes = mutableListOf<String>()
        val client = capturingClient("{}", sentTimes)
        val past = JourneySearchTime.nowBerlin().minusHours(2)

        client.searchJourneys(hamburg, berlin, JourneySearchOptions(), past)

        // A past departure is nudged to the future; if that is still empty we retry once more.
        assertEquals(2, sentTimes.size)
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
