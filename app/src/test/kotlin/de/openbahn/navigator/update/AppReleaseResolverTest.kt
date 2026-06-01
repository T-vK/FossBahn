package de.openbahn.navigator.update

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppReleaseResolverTest {
  @Test
  fun fetchLatestApk_returnsNewestAcrossFdroidAndGitHub() = runBlocking {
    val fdroid = mockk<FdroidRepoClient>()
    val github = mockk<GitHubReleaseClient>()
    coEvery { fdroid.fetchLatestApk("de.openbahn.navigator.debug") } returns ReleaseApk(
      versionName = "0.26.1",
      versionCode = 2601,
      downloadUrl = "https://example.test/fdroid/de.openbahn.navigator.debug_2601.apk",
      fileName = "de.openbahn.navigator.debug_2601.apk",
    )
    coEvery { github.fetchLatestApk() } returns ReleaseApk(
      versionName = "0.26.2",
      versionCode = 2602,
      downloadUrl = "https://github.test/OpenBahnNavigator-v0.26.2-2602-debug.apk",
      fileName = "OpenBahnNavigator-v0.26.2-2602-debug.apk",
    )
    val resolver = AppReleaseResolver(fdroid, github)
    val latest = resolver.fetchLatestApk("de.openbahn.navigator.debug")
    assertEquals(2602, latest?.versionCode)
    assertEquals("OpenBahnNavigator-v0.26.2-2602-debug.apk", latest?.fileName)
  }
}
