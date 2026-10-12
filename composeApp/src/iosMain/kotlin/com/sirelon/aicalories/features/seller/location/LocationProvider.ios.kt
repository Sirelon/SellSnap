package com.sirelon.sellsnap.features.seller.location

import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLPlacemark
import platform.CoreLocation.kCLErrorDomain
import platform.CoreLocation.kCLErrorGeocodeFoundNoResult
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createLocationProvider(): LocationProvider = IosLocationProvider()

class IosLocationProvider : LocationProvider {

    private var locationManager: CLLocationManager? = null
    private var locationDelegate: CLLocationManagerDelegateProtocol? = null

    private fun clearManager() {
        locationManager?.stopUpdatingLocation()
        locationManager = null
        locationDelegate = null
    }

    override suspend fun getCurrentLocation(): DeviceLocation? =
        suspendCancellableCoroutine { continuation ->
            val manager = CLLocationManager()
            locationManager = manager
            manager.desiredAccuracy = kCLLocationAccuracyKilometer

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>,
                ) {
                    val location = didUpdateLocations.lastOrNull() as? CLLocation
                    if (location != null) {
                        val result = location.coordinate.useContents {
                            DeviceLocation(latitude = latitude, longitude = longitude)
                        }
                        if (continuation.isActive) continuation.resume(result)
                        clearManager()
                    } else {
                        if (continuation.isActive) continuation.resume(null)
                        clearManager()
                    }
                }

                override fun locationManager(
                    manager: CLLocationManager,
                    didFailWithError: NSError,
                ) {
                    if (continuation.isActive) continuation.resume(null)
                    clearManager()
                }
            }

            locationDelegate = delegate
            manager.delegate = delegate
            manager.requestWhenInUseAuthorization()
            manager.requestLocation()

            continuation.invokeOnCancellation {
                clearManager()
            }
        }

    // Apple warns this call can block, so it stays off the main thread.
    override suspend fun isLocationServicesEnabled(): Boolean =
        withContext(Dispatchers.IO) { CLLocationManager.locationServicesEnabled() }

    override suspend fun searchPlaces(query: String, countryCode: String): List<GeocodedPlace> =
        suspendCancellableCoroutine { continuation ->
            val geocoder = CLGeocoder()
            geocoder.geocodeAddressString(query) { placemarks, error ->
                if (!continuation.isActive) return@geocodeAddressString
                when {
                    error == null -> continuation.resume(
                        placemarks.orEmpty()
                            .filterIsInstance<CLPlacemark>()
                            .filter { it.ISOcountryCode.equals(countryCode, ignoreCase = true) }
                            .mapNotNull { placemark ->
                                val location = placemark.location ?: return@mapNotNull null
                                GeocodedPlace(
                                    location = location.coordinate.useContents {
                                        DeviceLocation(latitude = latitude, longitude = longitude)
                                    },
                                    region = placemark.administrativeArea,
                                )
                            },
                    )
                    error.domain == kCLErrorDomain && error.code == kCLErrorGeocodeFoundNoResult ->
                        continuation.resume(emptyList())
                    else -> continuation.resumeWithException(
                        IllegalStateException(error.localizedDescription),
                    )
                }
            }
            continuation.invokeOnCancellation { geocoder.cancelGeocode() }
        }

    // iOS has no public link to the Location Services switch; the dialog tells the user the path.
    override fun openLocationServicesSettings() = Unit
}
