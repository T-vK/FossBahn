package de.openbahn.navigator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.openbahn.navigator.update.GitHubReleaseClient
import de.openbahn.navigator.update.ReleaseNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
data class ChangelogLoadResult(
    val releases: List<ReleaseNote>,
    val error: String? = null,
    val fromCache: Boolean = false,
)

class ChangelogRepository(
    private val context: Context,
    private val userPreferences: UserPreferencesRepository,
    private val gitHubReleaseClient: GitHubReleaseClient = GitHubReleaseClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): ChangelogLoadResult = withContext(Dispatchers.IO) {
        val embedded = loadEmbedded()
        val cached = loadCached()

        val remoteResult = runCatching { gitHubReleaseClient.fetchRecentReleases() }
        val remote = remoteResult.getOrNull()

        if (!remote.isNullOrEmpty()) {
            saveCached(remote)
            return@withContext ChangelogLoadResult(
                releases = mergeReleases(embedded, remote, cached),
                error = null,
                fromCache = false,
            )
        }

        val fallback = mergeReleases(embedded, emptyList(), cached)
        if (fallback.isNotEmpty()) {
            return@withContext ChangelogLoadResult(
                releases = fallback,
                error = remoteResult.exceptionOrNull()?.message,
                fromCache = cached.isNotEmpty() && embedded.isEmpty(),
            )
        }

        ChangelogLoadResult(
            releases = emptyList(),
            error = remoteResult.exceptionOrNull()?.message ?: "No changelog available",
        )
    }

    fun loadEmbedded(): List<ReleaseNote> = runCatching {
        context.assets.open(ASSET_PATH).use { stream ->
            val file = json.decodeFromString<ChangelogAssetFile>(stream.bufferedReader().readText())
            file.releases.map { ReleaseNote(it.versionName, it.body) }
        }
    }.getOrElse { emptyList() }

    private suspend fun loadCached(): List<ReleaseNote> {
        val raw = userPreferences.changelogCacheJson.first() ?: return emptyList()
        return runCatching {
            json.decodeFromString<ChangelogAssetFile>(raw).releases
                .map { ReleaseNote(it.versionName, it.body) }
        }.getOrElse { emptyList() }
    }

    private suspend fun saveCached(releases: List<ReleaseNote>) {
        val payload = ChangelogAssetFile(
            releases = releases.map { ReleaseNoteDto(it.versionName, it.body) },
        )
        userPreferences.setChangelogCacheJson(json.encodeToString(ChangelogAssetFile.serializer(), payload))
    }

    @Serializable
    private data class ChangelogAssetFile(val releases: List<ReleaseNoteDto> = emptyList())

    @Serializable
    private data class ReleaseNoteDto(val versionName: String, val body: String)

    companion object {
        const val ASSET_PATH = "openbahn/changelog.json"

        internal fun mergeReleases(
            embedded: List<ReleaseNote>,
            remote: List<ReleaseNote>,
            cached: List<ReleaseNote>,
        ): List<ReleaseNote> {
            val byVersion = linkedMapOf<String, ReleaseNote>()
            (cached + embedded + remote).forEach { note ->
                val key = note.versionName
                val existing = byVersion[key]
                if (existing == null || note.body.length > existing.body.length) {
                    byVersion[key] = note
                }
            }
            return byVersion.values.sortedWith(
                compareByDescending<ReleaseNote> { versionSortKey(it.versionName) },
            )
        }

        private fun versionSortKey(version: String): Long =
            parseVersionParts(version).fold(0L) { acc, part ->
                acc * 1_000_000L + part.coerceIn(0, 999_999)
            }

        private fun parseVersionParts(version: String): List<Int> =
            version.split('.', '-', '_')
                .mapNotNull { part -> part.filter(Char::isDigit).toIntOrNull() }
                .ifEmpty { listOf(0) }
    }
}
