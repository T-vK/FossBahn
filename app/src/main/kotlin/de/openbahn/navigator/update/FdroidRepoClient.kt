package de.openbahn.navigator.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the custom F-Droid repository index (same source as the in-app repo link).
 * CI publishes debug APKs here; they may appear before or without a matching GitHub Release.
 */
class FdroidRepoClient(
    private val repoUrl: String = DEFAULT_REPO_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestApk(packageName: String): ReleaseApk? = withContext(Dispatchers.IO) {
        val indexUrl = "${repoUrl.trimEnd('/')}/index-v2.json"
        val body = fetchText(indexUrl) ?: return@withContext null
        parseLatestApk(packageName, body)
    }

    /** Visible for unit tests without network. */
    internal fun fetchLatestApkFromIndex(packageName: String, indexJson: String): ReleaseApk? =
        parseLatestApk(packageName, indexJson)

    private fun parseLatestApk(packageName: String, indexJson: String): ReleaseApk? {
        val index = runCatching { json.decodeFromString<FdroidIndexV2>(indexJson) }.getOrNull()
            ?: return null
        val packageEntry = index.packages[packageName] ?: return null
        val repoBase = index.repo.address.trimEnd('/')
        val latest = packageEntry.versions.values.maxByOrNull { it.manifest.versionCode } ?: return null
        val apkPath = latest.file.name.trimStart('/')
        return ReleaseApk(
            versionName = latest.manifest.versionName,
            versionCode = latest.manifest.versionCode,
            downloadUrl = "$repoBase/$apkPath",
            fileName = apkPath.substringAfterLast('/'),
        )
    }

    @Serializable
    private data class FdroidIndexV2(
        val repo: FdroidRepo,
        val packages: Map<String, FdroidPackage> = emptyMap(),
    )

    @Serializable
    private data class FdroidRepo(val address: String)

    @Serializable
    private data class FdroidPackage(val versions: Map<String, FdroidVersion> = emptyMap())

    @Serializable
    private data class FdroidVersion(
        val file: FdroidFile,
        val manifest: FdroidManifest,
    )

    @Serializable
    private data class FdroidFile(val name: String)

    @Serializable
    private data class FdroidManifest(
        @SerialName("versionName") val versionName: String,
        @SerialName("versionCode") val versionCode: Int,
    )

    private fun fetchText(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OpenBahnNavigator")
            // Repo index updates on every publish; avoid stale CDN/browser caches.
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_REPO_URL = "https://t-vk.github.io/FossBahn/fdroid/repo"
    }
}
