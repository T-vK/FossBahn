package de.openbahn.navigator.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String = "",
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

data class ReleaseApk(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val fileName: String,
)

data class ReleaseNote(
    val versionName: String,
    val body: String,
)

class GitHubReleaseClient(
    private val owner: String = "T-vK",
    private val repo: String = "FossBahn",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestApk(): ReleaseApk? = withContext(Dispatchers.IO) {
        val release = fetchJson("https://api.github.com/repos/$owner/$repo/releases/latest")
            ?.let { runCatching { json.decodeFromString<GhRelease>(it) }.getOrNull() }
            ?: return@withContext null
        release.assets
            .mapNotNull { asset -> parseApkAsset(release.tagName, asset) }
            .maxByOrNull { it.versionCode }
    }

    suspend fun fetchRecentReleases(limit: Int = 15): List<ReleaseNote> = withContext(Dispatchers.IO) {
        val body = fetchJson("https://api.github.com/repos/$owner/$repo/releases?per_page=$limit")
            ?: return@withContext emptyList()
        val releases = runCatching { json.decodeFromString<List<GhRelease>>(body) }.getOrNull()
            ?: return@withContext emptyList()
        releases.mapNotNull { release ->
            val version = release.tagName.removePrefix("v").ifBlank { return@mapNotNull null }
            ReleaseNote(versionName = version, body = release.body.trim())
        }
    }

    private fun parseApkAsset(tagName: String, asset: GhAsset): ReleaseApk? =
        parseApkFileName(asset.name, tagName)?.copy(
            downloadUrl = asset.downloadUrl,
            fileName = asset.name,
        )

    internal companion object {
        fun parseApkFileName(fileName: String, tagName: String): ReleaseApk? {
            if (!fileName.endsWith(".apk", ignoreCase = true)) return null
            val codeMatch = VERSION_CODE_IN_NAME.find(fileName) ?: return null
            val versionCode = codeMatch.groupValues[1].toIntOrNull() ?: return null
            val versionName = VERSION_NAME_IN_NAME.find(fileName)?.groupValues?.get(1)
                ?: tagName.removePrefix("v")
            return ReleaseApk(
                versionName = versionName,
                versionCode = versionCode,
                downloadUrl = "",
                fileName = fileName,
            )
        }

        private val VERSION_CODE_IN_NAME = Regex("""-(\d+)-debug\.apk$""", RegexOption.IGNORE_CASE)
        private val VERSION_NAME_IN_NAME = Regex("""OpenBahnNavigator-v([\d.]+)-\d+""", RegexOption.IGNORE_CASE)
    }

    private fun fetchJson(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OpenBahnNavigator")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

}
