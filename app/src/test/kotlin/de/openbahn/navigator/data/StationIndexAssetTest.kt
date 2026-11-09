package de.openbahn.navigator.data

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Loads the shipped `stations.tsv` from the app module source tree so CI catches
 * a broken/empty offline index without needing an Android instrumentation context.
 */
class StationIndexAssetTest {

    private fun loadBundledStations(): List<StationRecord> {
        val candidates = listOf(
            File("src/main/assets/openbahn/stations.tsv"),
            File("app/src/main/assets/openbahn/stations.tsv"),
        )
        val file = candidates.firstOrNull { it.isFile }
        assumeTrue(file != null, "stations.tsv asset not found from ${File(".").absolutePath}")
        return file!!.readLines(Charsets.UTF_8)
            .mapNotNull { StationIndex.parseLine(it) }
    }

    @Test
    fun bundledStations_coverMajorGermanHubs() {
        val stations = loadBundledStations()
        assertTrue(stations.size >= 6000, "expected full DB dump, got ${stations.size}")
        val byName = stations.associateBy { it.name }
        assertEquals("8002549", byName.getValue("Hamburg Hbf").eva)
        assertEquals("8011160", byName.getValue("Berlin Hbf").eva)
        assertEquals("8000261", byName.getValue("München Hbf").eva)
        assertEquals("8000238", byName.getValue("Lüneburg").eva)
        assertEquals(0, byName.getValue("Hamburg Hbf").importance)
    }

    @Test
    fun bundledIndex_searchIsInstantAndPrefersLongDistance() {
        val index = StationIndex(loadBundledStations())
        val hamburg = index.search("Hamburg")
        assertTrue(hamburg.isNotEmpty())
        assertEquals("Hamburg Hbf", hamburg.first().name)
        assertEquals("8002549", hamburg.first().evaNumber)

        val lueneburg = index.resolveExact("Lüneburg")
        assertEquals("8000238", lueneburg?.evaNumber)

        // Anchor near Lüneburg must still put the long-distance hub first.
        val nearLueneburg = index.search("Hamburg", nearLatitude = 53.25, nearLongitude = 10.42)
        assertEquals("Hamburg Hbf", nearLueneburg.first().name)
        assertTrue(
            nearLueneburg.any { it.name == "Hamburg-Altona" } ||
                nearLueneburg.any { it.name.contains("S-Bahn") },
        )
    }

    @Test
    fun bundledIndex_doesNotFalseMatchStreetAddresses() {
        val index = StationIndex(loadBundledStations())
        assertEquals(null, index.resolveExact("Musterstraße 1, Berlin"))
        assertTrue(index.search("Musterstraße 1").isEmpty())
    }
}
