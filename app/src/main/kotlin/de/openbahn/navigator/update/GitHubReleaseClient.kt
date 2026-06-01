package de.openbahn.navigator.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
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
        val response = fetchHttp("https://api.github.com/repos/$owner/$repo/releases/latest")
            ?: return@withContext null
        if (response.code !in 200..299) return@withContext null
        val release = response.body
            ?.let { runCatching { json.decodeFromString<GhRelease>(it) }.getOrNull() }
            ?: return@withContext null
        release.assets
            .mapNotNull { asset -> parseApkAsset(release.tagName, asset) }
            .maxByOrNull { it.versionCode }
    }

    suspend fun fetchRecentReleases(limit: Int = 15): List<ReleaseNote> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=$limit"
        val response = fetchHttp(url) ?: throw IOException("Could not reach GitHub")
        if (response.code !in 200..299) {
            val detail = response.body?.lineSequence()?.firstOrNull()?.take(160)
            throw IOException(
                buildString {
                    append("GitHub releases HTTP ${response.code}")
                    if (!detail.isNullOrBlank()) append(": ").append(detail)
                },
            )
        }
        val body = response.body ?: throw IOException("GitHub releases response was empty")
        val releases = runCatching { json.decodeFromString<List<GhRelease>>(body) }.getOrElse { cause ->
            throw IOException("Could not parse GitHub releases", cause)
        }
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

    private data class HttpResponse(val code: Int, val body: String?)

    private fun fetchHttp(url: String): HttpResponse? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OpenBahnNavigator")
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }
            HttpResponse(code, text)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

}
