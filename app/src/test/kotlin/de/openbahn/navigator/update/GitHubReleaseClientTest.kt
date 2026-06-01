package de.openbahn.navigator.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class GitHubReleaseClientTest {
    @Test
    fun parseApkFileName_extractsVersionCodeFromFileName() {
        val result = GitHubReleaseClient.parseApkFileName(
            fileName = "OpenBahnNavigator-v0.25.13-2513-debug.apk",
            tagName = "v0.25.13",
        )
        assertNotNull(result)
        assertEquals(2513, result!!.versionCode)
        assertEquals("0.25.13", result.versionName)
    }
}
