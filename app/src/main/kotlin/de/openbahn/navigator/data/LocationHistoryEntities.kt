package de.openbahn.navigator.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "recent_locations")
data class RecentLocationEntity(
    @PrimaryKey val locationKey: String,
    val locationJson: String,
    val lastUsedAt: Long,
    val useCount: Int,
)

@Entity(tableName = "favorite_locations")
data class FavoriteLocationEntity(
    @PrimaryKey val locationKey: String,
    val locationJson: String,
    val addedAt: Long,
)

@Entity(tableName = "favorite_routes")
data class FavoriteRouteEntity(
    @PrimaryKey val id: String,
    val fromJson: String,
    val toJson: String,
    val optionsJson: String,
    val label: String?,
    val createdAt: Long,
)

fun Location.stableKey(): String = evaNumber ?: id

fun RecentLocationEntity.toLocation(): Location = Json.decodeFromString(locationJson)

fun FavoriteLocationEntity.toLocation(): Location = Json.decodeFromString(locationJson)

fun FavoriteRouteEntity.toRoute(): FavoriteRoute = FavoriteRoute(
    id = id,
    from = Json.decodeFromString(fromJson),
    to = Json.decodeFromString(toJson),
    options = Json.decodeFromString(optionsJson),
    label = label,
    createdAt = createdAt,
)

data class FavoriteRoute(
    val id: String,
    val from: Location,
    val to: Location,
    val options: JourneySearchOptions,
    val label: String?,
    val createdAt: Long,
)

@Dao
interface RecentLocationDao {
    @Query("SELECT * FROM recent_locations ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<RecentLocationEntity>>

    @Query("SELECT * FROM recent_locations ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<RecentLocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentLocationEntity)

    @Query("DELETE FROM recent_locations")
    suspend fun clearAll()

    @Query("DELETE FROM recent_locations WHERE locationKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface FavoriteLocationDao {
    @Query("SELECT * FROM favorite_locations ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteLocationEntity>>

    @Query("SELECT * FROM favorite_locations ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteLocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteLocationEntity)

    @Query("DELETE FROM favorite_locations WHERE locationKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT COUNT(*) FROM favorite_locations WHERE locationKey = :key")
    suspend fun isFavorite(key: String): Int
}

@Dao
interface FavoriteRouteDao {
    @Query("SELECT * FROM favorite_routes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteRouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(route: FavoriteRouteEntity)

    @Query("DELETE FROM favorite_routes WHERE id = :id")
    suspend fun delete(id: String)
}

class LocationHistoryRepository(
    private val recentDao: RecentLocationDao,
    private val favoriteLocationDao: FavoriteLocationDao,
) {
    fun observeRecent(): Flow<List<Location>> =
        recentDao.observeRecent().map { list -> list.map { it.toLocation() } }

    fun observeFavoriteLocations(): Flow<List<Location>> =
        favoriteLocationDao.observeAll().map { list -> list.map { it.toLocation() } }

    suspend fun recordUsed(location: Location) {
        val key = location.stableKey()
        val existing = recentDao.getRecent(50).firstOrNull { it.locationKey == key }
        val now = System.currentTimeMillis()
        recentDao.upsert(
            RecentLocationEntity(
                locationKey = key,
                locationJson = Json.encodeToString(location),
                lastUsedAt = now,
                useCount = (existing?.useCount ?: 0) + 1,
            ),
        )
    }

    suspend fun recentMatching(query: String, limit: Int = 8): List<Location> =
        rankedForAutocomplete(query, limit)

    /**
     * Favorites first (by recent use), then other recent stations.
     * When [query] is non-empty, prefix matches rank above substring matches.
     */
    suspend fun rankedForAutocomplete(query: String, limit: Int = 8): List<Location> {
        val q = query.trim()
        val recentEntities = recentDao.getRecent(50)
        val recentByKey = recentEntities.associateBy { it.locationKey }
        val favoriteEntities = favoriteLocationDao.getAll()
        val favoriteKeys = favoriteEntities.map { it.locationKey }.toSet()

        data class Candidate(val location: Location, val recent: RecentLocationEntity?, val favoriteAddedAt: Long?)

        val candidates = mutableListOf<Candidate>()
        for (fav in favoriteEntities) {
            candidates += Candidate(
                location = fav.toLocation(),
                recent = recentByKey[fav.locationKey],
                favoriteAddedAt = fav.addedAt,
            )
        }
        for (entity in recentEntities) {
            if (entity.locationKey !in favoriteKeys) {
                candidates += Candidate(entity.toLocation(), entity, null)
            }
        }

        return candidates
            .filter { (loc, _, _) -> q.isEmpty() || loc.name.matchesAutocompleteQuery(q) }
            .sortedWith(
                compareBy<Candidate> { (loc, _, _) -> loc.name.autocompleteMatchRank(q) }
                    .thenByDescending { (_, recent, favAdded) ->
                        recent?.useCount ?: 0
                    }
                    .thenByDescending { (_, recent, favAdded) ->
                        recent?.lastUsedAt ?: favAdded ?: 0L
                    }
                    .thenBy { (loc, _, _) -> loc.name.lowercase() },
            )
            .map { it.location }
            .distinctBy { it.stableKey() }
            .take(limit)
    }

    suspend fun recentAll(limit: Int = 12): List<Location> =
        recentDao.getRecent(limit).map { it.toLocation() }

    suspend fun clearRecent() = recentDao.clearAll()

    suspend fun removeRecent(location: Location) = recentDao.delete(location.stableKey())

    suspend fun addFavoriteLocation(location: Location) {
        favoriteLocationDao.upsert(
            FavoriteLocationEntity(
                locationKey = location.stableKey(),
                locationJson = Json.encodeToString(location),
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeFavoriteLocation(location: Location) =
        favoriteLocationDao.delete(location.stableKey())

    suspend fun isFavoriteLocation(location: Location): Boolean =
        favoriteLocationDao.isFavorite(location.stableKey()) > 0
}

/** 0 = name starts with query; 1 = word-prefix; 2 = contains; 3 = no match (filtered out). */
internal fun String.autocompleteMatchRank(query: String): Int {
    if (query.isEmpty()) return 0
    val name = this
    val q = query
    return when {
        name.startsWith(q, ignoreCase = true) -> 0
        name.split(Regex("\\s+")).any { it.startsWith(q, ignoreCase = true) } -> 1
        name.contains(q, ignoreCase = true) -> 2
        else -> 3
    }
}

internal fun String.matchesAutocompleteQuery(query: String): Boolean =
    query.isEmpty() || autocompleteMatchRank(query) < 3

class FavoriteRouteRepository(private val dao: FavoriteRouteDao) {
    fun observeAll(): Flow<List<FavoriteRoute>> =
        dao.observeAll().map { list -> list.map { it.toRoute() } }

    suspend fun save(from: Location, to: Location, options: JourneySearchOptions, label: String? = null) {
        val id = "${from.stableKey()}_${to.stableKey()}_${System.currentTimeMillis()}"
        dao.upsert(
            FavoriteRouteEntity(
                id = id,
                fromJson = Json.encodeToString(from),
                toJson = Json.encodeToString(to),
                optionsJson = Json.encodeToString(options),
                label = label,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: String) = dao.delete(id)
}
