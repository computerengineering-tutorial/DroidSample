package com.example.droidsample.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Structured presentation of geocoded address components.
 */
data class AddressInfo(
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val featureName: String? = null,
    val thoroughfare: String? = null,
    val subThoroughfare: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val adminArea: String? = null,
    val subAdminArea: String? = null,
    val postalCode: String? = null,
    val countryName: String? = null,
    val countryCode: String? = null
)

/**
 * Helper for forward and reverse geocoding using Android's native [Geocoder].
 * Supports Android 13+ (API 33) asynchronous [Geocoder.GeocodeListener] callbacks
 * with clean backwards-compatible coroutine fallbacks for earlier API levels.
 */
class GeocodingHelper(private val context: Context, private val locale: Locale = Locale.getDefault()) {

    private val geocoder: Geocoder by lazy { Geocoder(context, locale) }

    /**
     * Checks if geocoding backend services are available on the current device.
     */
    fun isGeocoderAvailable(): Boolean {
        return Geocoder.isPresent()
    }

    /**
     * Reverse geocoding: Converts geographic coordinates (latitude, longitude) into structured addresses.
     *
     * @param latitude Target latitude in degrees (-90 to 90)
     * @param longitude Target longitude in degrees (-180 to 180)
     * @param maxResults Maximum number of address candidate results
     * @return Result containing list of mapped [AddressInfo]
     */
    suspend fun getFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int = 5
    ): Result<List<AddressInfo>> {
        if (!isGeocoderAvailable()) {
            return Result.failure(IllegalStateException("Geocoder service is not available on this device."))
        }
        if (!DistanceCalculator.isValidCoordinate(latitude, longitude)) {
            return Result.failure(IllegalArgumentException("Invalid latitude ($latitude) or longitude ($longitude)."))
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getFromLocationApi33(latitude, longitude, maxResults)
            } else {
                getFromLocationLegacy(latitude, longitude, maxResults)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Forward geocoding: Resolves a textual address or location query into geographic coordinates and address objects.
     *
     * @param locationName Search query (e.g. "Eiffel Tower, Paris" or "1600 Amphitheatre Pkwy, Mountain View, CA")
     * @param maxResults Maximum number of address candidate results
     * @return Result containing list of mapped [AddressInfo]
     */
    suspend fun getFromLocationName(
        locationName: String,
        maxResults: Int = 5
    ): Result<List<AddressInfo>> {
        if (!isGeocoderAvailable()) {
            return Result.failure(IllegalStateException("Geocoder service is not available on this device."))
        }
        if (locationName.isBlank()) {
            return Result.failure(IllegalArgumentException("Location search query must not be blank."))
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getFromLocationNameApi33(locationName, maxResults)
            } else {
                getFromLocationNameLegacy(locationName, maxResults)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun getFromLocationApi33(
        latitude: Double,
        longitude: Double,
        maxResults: Int
    ): Result<List<AddressInfo>> = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(latitude, longitude, maxResults, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                val list = addresses.map { mapAddress(it) }
                continuation.resume(Result.success(list))
            }

            override fun onError(errorMessage: String?) {
                continuation.resume(Result.failure(Exception(errorMessage ?: "Geocoding failed.")))
            }
        })
    }

    @Suppress("DEPRECATION")
    private suspend fun getFromLocationLegacy(
        latitude: Double,
        longitude: Double,
        maxResults: Int
    ): Result<List<AddressInfo>> = withContext(Dispatchers.IO) {
        val addresses = geocoder.getFromLocation(latitude, longitude, maxResults)
        val list = addresses?.map { mapAddress(it) } ?: emptyList()
        Result.success(list)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun getFromLocationNameApi33(
        locationName: String,
        maxResults: Int
    ): Result<List<AddressInfo>> = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocationName(locationName, maxResults, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                val list = addresses.map { mapAddress(it) }
                continuation.resume(Result.success(list))
            }

            override fun onError(errorMessage: String?) {
                continuation.resume(Result.failure(Exception(errorMessage ?: "Forward geocoding failed.")))
            }
        })
    }

    @Suppress("DEPRECATION")
    private suspend fun getFromLocationNameLegacy(
        locationName: String,
        maxResults: Int
    ): Result<List<AddressInfo>> = withContext(Dispatchers.IO) {
        val addresses = geocoder.getFromLocationName(locationName, maxResults)
        val list = addresses?.map { mapAddress(it) } ?: emptyList()
        Result.success(list)
    }

    /**
     * Maps an Android [Address] object to our domain [AddressInfo].
     */
    fun mapAddress(address: Address): AddressInfo {
        val lines = (0..address.maxAddressLineIndex).mapNotNull { address.getAddressLine(it) }
        val fullAddress = if (lines.isNotEmpty()) {
            lines.joinToString(", ")
        } else {
            listOfNotNull(
                address.subThoroughfare,
                address.thoroughfare,
                address.locality,
                address.adminArea,
                address.postalCode,
                address.countryName
            ).joinToString(", ")
        }

        return AddressInfo(
            formattedAddress = fullAddress.ifEmpty { "${address.latitude}, ${address.longitude}" },
            latitude = address.latitude,
            longitude = address.longitude,
            featureName = address.featureName,
            thoroughfare = address.thoroughfare,
            subThoroughfare = address.subThoroughfare,
            locality = address.locality,
            subLocality = address.subLocality,
            adminArea = address.adminArea,
            subAdminArea = address.subAdminArea,
            postalCode = address.postalCode,
            countryName = address.countryName,
            countryCode = address.countryCode
        )
    }
}
