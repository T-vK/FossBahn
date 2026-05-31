package de.openbahn.api

import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TripRouteFetcherTest {
    @Test
    fun fetchFullLegRoute_prefersFahrtOverSegment() = runTest {
        val fahrtJson = javaClass.getResource("/dbweb-fahrt-rb31.json")!!.readText()
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/reiseloesung/fahrt") -> respond(
                    content = fahrtJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> error("Unexpected: ${request.url}")
            }
        }
        val client = DbVendoClient(
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            },
        )
        val leg = Leg(
            origin = StopEvent("Uelzen", scheduledTime = "2026-06-15T10:15:00"),
            destination = StopEvent("Wieren", scheduledTime = "2026-06-15T11:05:00"),
            routeStops = listOf(
                StopEvent("Uelzen", scheduledTime = "2026-06-15T10:15:00"),
                StopEvent("Wieren", scheduledTime = "2026-06-15T11:05:00"),
            ),
            lineName = "RB 31",
            lineDetail = "81633",
            tripId = "2|#ZB#RB    31#ZE#81633#",
        )
        val route = client.fetchFullLegRoute(leg)
        assertEquals(6, route.size)
        assertEquals("Celle", route.first().name)
        assertEquals("Salzwedel", route.last().name)
    }

    @Test
    fun fetchFullLegRoute_fallsBackToBoardWhenFahrtEmpty() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/reiseloesung/fahrt") -> respond(
                    content = """{"halte":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                request.url.encodedPath.endsWith("/reiseloesung/ankuenfte") -> respond(
                    content = """{"ankuenfte":[{"journeyId":"trip-1","ueber":["Alpha"],"zeit":"2026-06-15T10:00:00"}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                request.url.encodedPath.endsWith("/reiseloesung/abfahrten") -> respond(
                    content = """{"abfahrten":[{"journeyId":"trip-1","ueber":["Omega"],"zeit":"2026-06-15T10:00:00"}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> error("Unexpected: ${request.url}")
            }
        }
        val client = DbVendoClient(
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            },
        )
        val leg = Leg(
            origin = StopEvent("Beta", id = "8000001", scheduledTime = "2026-06-15T10:00:00"),
            destination = StopEvent("Gamma", scheduledTime = "2026-06-15T11:00:00"),
            routeStops = listOf(
                StopEvent("Beta", scheduledTime = "2026-06-15T10:00:00"),
                StopEvent("Gamma", scheduledTime = "2026-06-15T11:00:00"),
            ),
            tripId = "trip-1",
            lineDetail = "123",
        )
        val route = client.fetchFullLegRoute(leg)
        assertTrue(route.size >= 4)
        assertEquals("Alpha", route.first().name)
        assertEquals("Omega", route.last().name)
    }
}
