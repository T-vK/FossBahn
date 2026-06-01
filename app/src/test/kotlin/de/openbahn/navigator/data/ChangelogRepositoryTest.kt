package de.openbahn.navigator.data

import de.openbahn.navigator.update.ReleaseNote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChangelogRepositoryTest {
    @Test
    fun mergeReleases_prefersLongerBodyAndSortsNewestFirst() {
        val merged = ChangelogRepository.mergeReleases(
            embedded = listOf(
                ReleaseNote("0.26.0", "embedded"),
                ReleaseNote("0.25.0", "from-embedded"),
            ),
            remote = listOf(
                ReleaseNote("0.26.0", "remote longer body"),
                ReleaseNote("0.27.0", "newest"),
            ),
            cached = listOf(
                ReleaseNote("0.25.0", "from-cached"),
            ),
        )
        assertEquals(listOf("0.27.0", "0.26.0", "0.25.0"), merged.map { it.versionName })
        assertEquals("remote longer body", merged.first { it.versionName == "0.26.0" }.body)
        assertEquals("from-embedded", merged.first { it.versionName == "0.25.0" }.body)
    }

    @Test
    fun mergeReleases_handlesBuildVersions() {
        val merged = ChangelogRepository.mergeReleases(
            embedded = listOf(ReleaseNote("0.10.2", "a")),
            remote = listOf(ReleaseNote("0.10.10", "b")),
            cached = emptyList(),
        )
        assertEquals("0.10.10", merged.first().versionName)
    }

    @Test
    fun mergeReleases_sortsNewestVersionFirst() {
        val merged = ChangelogRepository.mergeReleases(
            embedded = listOf(
                ReleaseNote("0.9.0", "a"),
                ReleaseNote("0.10.1", "b"),
            ),
            remote = emptyList(),
            cached = emptyList(),
        )
        assertEquals(listOf("0.10.1", "0.9.0"), merged.map { it.versionName })
    }
}
