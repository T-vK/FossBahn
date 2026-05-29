package de.openbahn.api

import de.openbahn.api.dto.DbJourneyResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JourneyResponseParseProbeTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun probeStrictDtoDecodeFailures() {
        val resources = listOf(
            "/dbweb-journey-full.json",
            "/dbweb-journey-hamburg-berlin.json",
            "/dbweb-probe-numeric_linienNummer.json",
            "/dbweb-probe-nested_verbindung.json",
            "/dbweb-probe-string_umstiege.json",
        )
        val failures = mutableListOf<String>()
        for (resource in resources) {
            val text = javaClass.getResource(resource)!!.readText()
            try {
                json.decodeFromString<DbJourneyResponse>(text)
            } catch (e: Exception) {
                failures += "$resource: ${e.message}"
            }
        }
        assertTrue(failures.isEmpty(), "DTO decode failures:\n${failures.joinToString("\n")}")
    }
}
