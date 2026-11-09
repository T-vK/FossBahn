package de.openbahn.navigator.data

import android.content.Context
import de.openbahn.model.Location
import de.openbahn.model.LocationType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline railway station directory bundled from Deutsche Bahn Haltestellendaten (CC BY 4.0).
 * Used for instant autocomplete; live `/orte` remains a fallback for POIs / newer stops.
 */
class StationIndex private constructor(
    private val loader: () -> List<StationRecord>,
) {
    constructor(
        context: Context,
        assetPath: String = DEFAULT_ASSET_PATH,
    ) : this(assetLoader(context, assetPath))

    /** Test / preview constructor with an in-memory station list. */
    internal constructor(stations: List<StationRecord>) : this({ stations })

    @Volatile
    private var cached: List<StationRecord>? = null

    fun search(
        query: String,
        limit: Int = 8,
        nearLatitude: Double? = null,
        nearLongitude: Double? = null,
    ): List<Location> = rankStations(
        stations = ensureLoaded(),
        query = query,
        limit = limit,
        nearLatitude = nearLatitude,
        nearLongitude = nearLongitude,
    )

    /** Exact / best local match for resolving a typed station name without waiting on `/orte`. */
    fun resolveExact(query: String): Location? {
        val q = query.trim()
        if (q.length < 2) return null
        val stations = ensureLoaded()
        stations.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let { return it.toLocation() }
        if (q.length < 4) return null
        val prefix = stations.filter { it.name.startsWith(q, ignoreCase = true) }
        if (prefix.size == 1) return prefix.first().toLocation()
        return null
    }

    private fun ensureLoaded(): List<StationRecord> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = loader()
            cached = loaded
            return loaded
        }
    }

    companion object {
        const val DEFAULT_ASSET_PATH = "openbahn/stations.tsv"

        private fun assetLoader(context: Context, assetPath: String): () -> List<StationRecord> = {
            runCatching {
                context.assets.open(assetPath).bufferedReader().useLines { lines ->
                    lines
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .mapNotNull { line -> parseLine(line) }
                        .toList()
                }
            }.getOrDefault(emptyList())
        }

        internal fun parseLine(line: String): StationRecord? {
            val parts = line.split('\t')
            if (parts.size < 5) return null
            val eva = parts[0].trim()
            val name = parts[1].trim()
            val lat = parts[2].toDoubleOrNull() ?: return null
            val lon = parts[3].toDoubleOrNull() ?: return null
            val importance = parts[4].toIntOrNull() ?: 2
            if (eva.isEmpty() || !eva.all(Char::isDigit) || name.isEmpty()) return null
            return StationRecord(
                eva = eva,
                name = name,
                latitude = lat,
                longitude = lon,
                importance = importance.coerceIn(0, 2),
            )
        }

        internal fun rankStations(
            stations: List<StationRecord>,
            query: String,
            limit: Int = 8,
            nearLatitude: Double? = null,
            nearLongitude: Double? = null,
        ): List<Location> {
            val q = query.trim()
            if (q.length < 2) return emptyList()
            val scored = ArrayList<ScoredStation>(32)
            for (station in stations) {
                val rank = station.name.autocompleteMatchRank(q)
                if (rank >= 3) continue
                val distanceKm = if (nearLatitude != null && nearLongitude != null) {
                    haversineKm(nearLatitude, nearLongitude, station.latitude, station.longitude)
                } else {
                    Double.POSITIVE_INFINITY
                }
                scored += ScoredStation(station, rank, distanceKm)
            }
            scored.sortWith(
                compareBy<ScoredStation> { it.matchRank }
                    .thenBy { hubBoost(it.station.name, q) }
                    .thenBy { it.station.importance }
                    .thenBy { it.distanceKm }
                    .thenBy { it.station.name.lowercase() },
            )
            return scored.asSequence()
                .map { it.station.toLocation() }
                .distinctBy { it.stableKey() }
                .take(limit)
                .toList()
        }

        /**
         * Prefer main stations for city-like queries: "Hamburg" → Hamburg Hbf before Dammtor.
         * Lower is better.
         */
        internal fun hubBoost(name: String, query: String): Int {
            val n = name.trim()
            val q = query.trim()
            if (n.equals(q, ignoreCase = true)) return 0
            val hbf = Regex("""(?i)^${Regex.escape(q)}\s+Hbf(?:\b|$)""")
            if (hbf.containsMatchIn(n)) return 1
            if (n.contains(" Hbf", ignoreCase = true) || n.contains(" Hauptbahnhof", ignoreCase = true)) {
                return 2
            }
            return 3
        }

        internal fun haversineKm(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val earthRadiusKm = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadiusKm * c
        }
    }
}

internal data class StationRecord(
    val eva: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** 0 = long-distance (FV), 1 = regional (RV), 2 = private regional (DPN). */
    val importance: Int,
) {
    fun toLocation(): Location = Location(
        id = eva,
        name = name,
        type = LocationType.STATION,
        latitude = latitude,
        longitude = longitude,
        evaNumber = eva,
    )
}

private data class ScoredStation(
    val station: StationRecord,
    val matchRank: Int,
    val distanceKm: Double,
)
