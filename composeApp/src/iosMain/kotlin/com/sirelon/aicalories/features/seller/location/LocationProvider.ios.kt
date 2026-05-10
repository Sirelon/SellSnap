package com.sirelon.sellsnap.features.seller.location

import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

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
}
