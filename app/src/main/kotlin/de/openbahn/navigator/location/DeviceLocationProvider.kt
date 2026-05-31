package de.openbahn.navigator.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import de.openbahn.model.Location
import de.openbahn.model.LocationType
import de.openbahn.navigator.domain.JourneySearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class DeviceLocationProvider(
    private val context: Context,
    private val searchRepository: JourneySearchRepository,
) {
    suspend fun resolveCurrentStation(locale: String): Location? {
        val coords = readCurrentCoordinates() ?: return null
        val query = reverseGeocodeQuery(coords.first, coords.second) ?: return null
        val results = searchRepository.searchLocations(query, locale)
        return results.firstOrNull { it.type == LocationType.STATION } ?: results.firstOrNull()
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

    private suspend fun reverseGeocodeQuery(latitude: Double, longitude: Double): String? =
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
                listOfNotNull(
                    address.locality,
                    address.subAdminArea,
                    address.adminArea,
                ).firstOrNull { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }
}
