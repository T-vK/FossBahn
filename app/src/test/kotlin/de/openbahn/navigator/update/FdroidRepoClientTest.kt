package de.openbahn.navigator.update

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class FdroidRepoClientTest {
  @Test
  fun fetchLatestApk_parsesIndexV2AndPicksHighestVersionCode() = runBlocking {
    val client = FdroidRepoClient(repoUrl = "https://example.test/fdroid/repo")
    val result = client.fetchLatestApkFromIndex(
      packageName = "de.openbahn.navigator.debug",
      indexJson = SAMPLE_INDEX,
    )
    assertNotNull(result)
    assertEquals(2602, result!!.versionCode)
    assertEquals("0.26.2", result.versionName)
    assertEquals(
      "https://example.test/fdroid/repo/de.openbahn.navigator.debug_2602.apk",
      result.downloadUrl,
    )
    assertEquals("de.openbahn.navigator.debug_2602.apk", result.fileName)
  }

  @Test
  fun fetchLatestApk_returnsNullForUnknownPackage() = runBlocking {
    val client = FdroidRepoClient(repoUrl = "https://example.test/fdroid/repo")
    val result = client.fetchLatestApkFromIndex(
      packageName = "other.app",
      indexJson = SAMPLE_INDEX,
    )
    assertEquals(null, result)
  }

  private companion object {
    const val SAMPLE_INDEX = """
      {
        "repo": { "address": "https://example.test/fdroid/repo" },
        "packages": {
          "de.openbahn.navigator.debug": {
            "versions": {
              "a": {
                "file": { "name": "/de.openbahn.navigator.debug_2601.apk" },
                "manifest": { "versionName": "0.26.1", "versionCode": 2601 }
              },
              "b": {
                "file": { "name": "/de.openbahn.navigator.debug_2602.apk" },
                "manifest": { "versionName": "0.26.2", "versionCode": 2602 }
              }
            }
          }
        }
      }
    """
  }
}
