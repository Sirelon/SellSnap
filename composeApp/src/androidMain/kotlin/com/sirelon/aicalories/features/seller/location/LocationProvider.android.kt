package com.sirelon.sellsnap.features.seller.location

import android.Manifest
import android.content.Context
import android.location.LocationManager
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.resume

actual fun createLocationProvider(): LocationProvider = AndroidLocationProvider()

class AndroidLocationProvider : LocationProvider, KoinComponent {

    private val context: Context by inject()

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override suspend fun getCurrentLocation(): DeviceLocation? {
        val client = LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(
                            value = DeviceLocation(
                                latitude = location.latitude,
                                longitude = location.longitude
                            ),
                        )
                    } else {
                        val lastKnownLocation = getLastKnownLocation()
                        cont.resume(lastKnownLocation)
                    }
                }
                .addOnFailureListener {
                    val lastKnownLocation = getLastKnownLocation()
                    cont.resume(lastKnownLocation)
                }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getLastKnownLocation(): DeviceLocation? {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )

        for (provider in providers) {
            if (!locationManager.isProviderEnabled(provider)) continue
            val location = locationManager.getLastKnownLocation(provider)
            if (location != null) {
                return DeviceLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                )
            }
        }

        return null
    }
}
