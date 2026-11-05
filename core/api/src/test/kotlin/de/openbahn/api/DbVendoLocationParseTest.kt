package de.openbahn.api

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
import org.junit.jupiter.api.assertThrows

/**
 * Regression for GitHub #39: HTML "Access Denied" from a WAF must not crash Search
 * via an uncaught JsonDecodingException in [DbVendoClient.searchLocations].
 */
class DbVendoLocationParseTest {

    private val berlinOrtJson = """
        [{"id":"A=1@O=Berlin Hbf@X=13369549@Y=52525589@U=80@L=8011160@","name":"Berlin Hbf","extId":"8011160","type":"ST"}]
    """.trimIndent()

    private val accessDeniedHtml = """
        <HTML><HEAD>
        <TITLE>Access Denied</TITLE>
        </HEAD><BODY>
        <H1>Access Denied</H1>
        You don't have permission to access this server.
        </BODY></HTML>
    """.trimIndent()

    private fun clientReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ): DbVendoClient {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, contentType),
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
    fun searchLocations_htmlAccessDenied_throwsBlockedNotCrash() = runTest {
        val client = clientReturning(accessDeniedHtml, HttpStatusCode.Forbidden, contentType = "text/html")
        val error = assertThrows<DbApiBlockedException> {
            client.searchLocations("Munich")
        }
        assertTrue(error.message!!.contains("blocked", ignoreCase = true))
    }

    @Test
    fun searchLocationsNearby_htmlAccessDenied_throwsBlockedNotCrash() = runTest {
        val client = clientReturning(accessDeniedHtml, contentType = "text/html")
        assertThrows<DbApiBlockedException> {
            client.searchLocationsNearby(48.140, 11.558)
        }
    }

    @Test
    fun searchLocations_opsBlockedJson_throwsBlocked() = runTest {
        val client = clientReturning("""{"status":"ERROR","code":"OPS_BLOCKED"}""")
        assertThrows<DbApiBlockedException> {
            client.searchLocations("Berlin")
        }
    }

    @Test
    fun searchLocations_parsesOrtList() = runTest {
        val locations = clientReturning(berlinOrtJson).searchLocations("Berlin")
        assertEquals(1, locations.size)
        assertEquals("Berlin Hbf", locations.first().name)
        assertEquals("8011160", locations.first().evaNumber)
    }

    @Test
    fun searchLocations_garbageText_throwsParseNotCrash() = runTest {
        val client = clientReturning("not-json-and-not-html")
        assertThrows<DbParseException> {
            client.searchLocations("Berlin")
        }
    }
}
