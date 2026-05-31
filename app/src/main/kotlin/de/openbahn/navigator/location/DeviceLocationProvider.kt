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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class ResolvedDeviceLocation(
    /** Shown in the search field (street address or coordinates). */
    val displayLabel: String,
    /** Used for journey search (GPS coordinates as bahn halt id). */
    val searchLocation: Location,
)

class DeviceLocationProvider(
    private val context: Context,
) {
    suspend fun resolveCurrentLocation(@Suppress("UNUSED_PARAMETER") locale: String): ResolvedDeviceLocation? {
        val coords = readCurrentCoordinates() ?: return null
        val (lat, lon) = coords
        val displayLabel = reverseGeocodeDisplayLabel(lat, lon) ?: formatCoordinates(lat, lon)
        val searchLocation = Location(
            id = haltIdForCoordinates(lat, lon, displayLabel),
            name = displayLabel,
            type = LocationType.ADDRESS,
            latitude = lat,
            longitude = lon,
        )
        return ResolvedDeviceLocation(displayLabel = displayLabel, searchLocation = searchLocation)
    }

    /** @deprecated Use [resolveCurrentLocation]; kept for callers not yet migrated. */
    suspend fun resolveCurrentStation(locale: String): Location? =
        resolveCurrentLocation(locale)?.searchLocation

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
