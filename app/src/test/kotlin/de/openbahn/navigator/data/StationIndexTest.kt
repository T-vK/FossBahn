package de.openbahn.navigator.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StationIndexTest {

    private val stations = listOf(
        StationRecord("8002549", "Hamburg Hbf", 53.552736, 10.006909, importance = 0),
        StationRecord("8002548", "Hamburg-Altona", 53.552537, 9.934896, importance = 0),
        StationRecord("8098549", "Hamburg Hbf (S-Bahn)", 53.552809, 10.007921, importance = 1),
        StationRecord("8000238", "Lüneburg", 53.249656, 10.41989, importance = 0),
        StationRecord("8011160", "Berlin Hbf", 52.525592, 13.369545, importance = 0),
        StationRecord("8000261", "München Hbf", 48.140232, 11.558335, importance = 0),
    )

    @Test
    fun parseLine_readsTsvRow() {
        val parsed = StationIndex.parseLine("8002549\tHamburg Hbf\t53.55\t10.00\t0")
        assertEquals("8002549", parsed?.eva)
        assertEquals("Hamburg Hbf", parsed?.name)
        assertEquals(0, parsed?.importance)
    }

    @Test
    fun search_prefersLongDistanceAndPrefixMatches() {
        val index = StationIndex(stations)
        val results = index.search("Hamburg")
        assertEquals("Hamburg Hbf", results.first().name)
        assertTrue(results.any { it.name == "Hamburg-Altona" })
        assertTrue(results.indexOfFirst { it.name == "Hamburg Hbf" } <
            results.indexOfFirst { it.name == "Hamburg Hbf (S-Bahn)" })
    }

    @Test
    fun search_ranksCloserStationsHigherWhenAnchorProvided() {
        val index = StationIndex(stations)
        val ranked = index.search(
            query = "Ha",
            nearLatitude = 53.25,
            nearLongitude = 10.42,
        )
        assertEquals("Hamburg Hbf", ranked.first().name)
        assertTrue(index.search("H").isEmpty(), "queries shorter than 2 chars stay empty")
    }

    @Test
    fun resolveExact_matchesStationName() {
        val index = StationIndex(stations)
        assertEquals("8000238", index.resolveExact("Lüneburg")?.evaNumber)
        assertEquals(null, index.resolveExact("Unknown"))
    }

    @Test
    fun haversine_hamburgToLueneburgIsReasonable() {
        val km = StationIndex.haversineKm(53.552736, 10.006909, 53.249656, 10.41989)
        assertTrue(km in 35.0..55.0, "expected ~45km, got $km")
    }
}
