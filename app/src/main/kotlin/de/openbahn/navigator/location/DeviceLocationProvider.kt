package de.openbahn.navigator.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import de.openbahn.api.haltIdForCoordinates
import de.openbahn.model.Location
import de.openbahn.model.LocationType
import de.openbahn.navigator.domain.JourneySearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ResolvedDeviceLocation(
    /** Shown in the search field (street address or coordinates). */
    val displayLabel: String,
    /** Used for journey search (GPS coordinates as bahn halt id). */
    val searchLocation: Location,
)

class DeviceLocationProvider(
    private val context: Context,
    private val searchRepository: JourneySearchRepository,
) {
    suspend fun resolveCurrentLocation(locale: String): ResolvedDeviceLocation? {
        val coords = readCurrentCoordinates() ?: return null
        val (lat, lon) = coords
        val displayLabel = reverseGeocodeDisplayLabel(lat, lon) ?: formatCoordinates(lat, lon)
        val nearby = searchRepository.searchLocationsNearby(lat, lon, locale)
        val nearest = pickNearest(nearby, lat, lon)
        val searchLocation = Location(
            id = haltIdForCoordinates(lat, lon, displayLabel),
            name = displayLabel,
            type = LocationType.ADDRESS,
            latitude = lat,
            longitude = lon,
            evaNumber = nearest?.evaNumber,
        )
        return ResolvedDeviceLocation(displayLabel = displayLabel, searchLocation = searchLocation)
    }

    /** @deprecated Use [resolveCurrentLocation]; kept for callers not yet migrated. */
    suspend fun resolveCurrentStation(locale: String): Location? =
        resolveCurrentLocation(locale)?.searchLocation

    private fun pickNearest(locations: List<Location>, lat: Double, lon: Double): Location? =
        locations
            .mapNotNull { loc ->
                val la = loc.latitude ?: return@mapNotNull null
                val lo = loc.longitude ?: return@mapNotNull null
                loc to haversineMeters(lat, lon, la, lo)
            }
            .minByOrNull { it.second }
            ?.first

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun readCurrentCoordinates(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val location = providers.firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        } ?: return null
        return location.latitude to location.longitude
    }

    private suspend fun reverseGeocodeDisplayLabel(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(context)
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1) { list ->
                            cont.resume(list)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)
                }
                val address = addresses?.firstOrNull() ?: return@withContext null
                address.getAddressLine(0)?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(
                        listOfNotNull(address.thoroughfare, address.subThoroughfare).joinToString(" ").takeIf { it.isNotBlank() },
                        address.locality,
                    ).joinToString(", ").takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }

    private fun formatCoordinates(latitude: Double, longitude: Double): String =
        "%.5f, %.5f".format(latitude, longitude)

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }
}
