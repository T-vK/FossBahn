package de.openbahn.navigator.ui.search

import de.openbahn.model.Location
import de.openbahn.model.LocationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocationSearchResolverTest {
    private val berlin = Location(id = "8011160", name = "Berlin Hbf", evaNumber = "8011160")
    private val berlinOst = Location(id = "8010255", name = "Berlin Ostbahnhof", evaNumber = "8010255")
    private val poi = Location(id = "poi-1", name = "Berlin", type = LocationType.ADDRESS)

    @Test
    fun prefersStationOverPoiWhenAmbiguous() {
        val resolved = resolveLocationForSearch(
            query = "Berlin",
            selected = null,
            suggestions = emptyList(),
            recent = emptyList(),
            apiResults = listOf(poi, berlin, berlinOst),
        )
        assertEquals(berlin, resolved)
    }

    @Test
    fun exactNameMatchWins() {
        val resolved = resolveLocationForSearch(
            query = "Berlin Ostbahnhof",
            selected = null,
            suggestions = emptyList(),
            recent = emptyList(),
            apiResults = listOf(berlin, berlinOst),
        )
        assertEquals(berlinOst, resolved)
    }

    @Test
    fun returnsNullWhenMultipleStationsAndNoMatch() {
        val resolved = resolveLocationForSearch(
            query = "Hbf",
            selected = null,
            suggestions = emptyList(),
            recent = emptyList(),
            apiResults = listOf(berlin, berlinOst),
        )
        assertNull(resolved)
    }

    @Test
    fun exactAddressMatchWinsEvenWhenStationsPresent() {
        val address = Location(
            id = "A=2@O=Musterstrasse+1@X=13405000@Y=52520000@",
            name = "Musterstraße 1, Berlin",
            type = LocationType.ADDRESS,
            latitude = 52.52,
            longitude = 13.405,
        )
        val resolved = resolveLocationForSearch(
            query = "Musterstraße 1, Berlin",
            selected = null,
            suggestions = emptyList(),
            recent = emptyList(),
            apiResults = listOf(berlin, address, berlinOst),
        )
        assertEquals(address, resolved)
    }

    @Test
    fun selectedAddressIsKept() {
        val address = Location(
            id = "A=2@O=Musterstrasse+1@X=13405000@Y=52520000@",
            name = "Musterstraße 1, Berlin",
            type = LocationType.ADDRESS,
            latitude = 52.52,
            longitude = 13.405,
        )
        val resolved = resolveLocationForSearch(
            query = "Musterstraße 1, Berlin",
            selected = address,
            suggestions = emptyList(),
            recent = emptyList(),
            apiResults = emptyList(),
        )
        assertEquals(address, resolved)
    }
}
