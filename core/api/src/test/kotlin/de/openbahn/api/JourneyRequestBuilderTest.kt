package de.openbahn.api

import de.openbahn.model.AccessibilityFilter
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import de.openbahn.model.TransportProduct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JourneyRequestBuilderTest {
    private val berlin = Location(id = "8011160", name = "Berlin Hbf")
    private val munich = Location(id = "8000261", name = "München Hbf")

    @Test
    fun `builds request with transport products`() {
        val body = JourneyRequestBuilder.build(
            berlin,
            munich,
            JourneySearchOptions(products = setOf(TransportProduct.ICE, TransportProduct.IC_EC)),
            LocalDateTime.of(2026, 5, 30, 10, 0),
        )
        val products = body["produktgattungen"]!!.toString()
        assertTrue(products.contains("ICE"))
        assertTrue(products.contains("EC_IC"))
    }

    @Test
    fun `builds deutschland ticket filters`() {
        val body = JourneyRequestBuilder.build(
            berlin,
            munich,
            JourneySearchOptions(
                deutschlandTicketConnectionsOnly = true,
                bikeCarriage = true,
                directOnly = true,
            ),
            LocalDateTime.now(),
        )
        assertEquals(true, body["deutschlandTicketVorhanden"]?.toString()?.toBooleanStrictOrNull())
        assertEquals(true, body["nurDeutschlandTicketVerbindungen"]?.toString()?.toBooleanStrictOrNull())
        assertEquals(true, body["bikeCarriage"]?.toString()?.toBooleanStrictOrNull())
        assertEquals(0, body["maxUmstiege"]?.toString()?.toIntOrNull())
    }

    @Test
    fun `prefers full bahn location id for journey request`() {
        val from = Location(
            id = "A=1@O=Berlin Hbf@X=1@Y=2@L=8011160@",
            name = "Berlin Hbf",
            evaNumber = "8011160",
        )
        val to = Location(id = "8000261", name = "München Hbf", evaNumber = "8000261")
        val body = JourneyRequestBuilder.build(from, to, JourneySearchOptions(), LocalDateTime.now())
        assertEquals(from.id, body["abfahrtsHalt"]?.toString()?.trim('"'))
        assertEquals("A=1@L=8000261@", body["ankunftsHalt"]?.toString()?.trim('"'))
    }

    @Test
    fun `includes travellers and class for dbweb`() {
        val body = JourneyRequestBuilder.build(
            berlin,
            munich,
            JourneySearchOptions(),
            LocalDateTime.now(),
        )
        assertEquals("KLASSE_2", body["klasse"]?.toString()?.trim('"'))
        assertTrue(body["reisende"].toString().contains("ERWACHSENER"))
    }

    @Test
    fun `builds accessibility filter`() {
        val body = JourneyRequestBuilder.build(
            berlin,
            munich,
            JourneySearchOptions(accessibility = AccessibilityFilter.WHEELCHAIR),
            LocalDateTime.now(),
        )
        assertEquals("ROLLSTUHL", body["barrierefrei"]?.toString()?.trim('"'))
    }
}
