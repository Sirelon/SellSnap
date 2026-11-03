package com.sirelon.sellsnap.features.seller.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    override suspend fun isLocationServicesEnabled(): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    override suspend fun searchPlaces(query: String, countryCode: String): List<GeocodedPlace> {
        if (!Geocoder.isPresent()) throw GeocoderUnavailableException()
        val geocoder = Geocoder(context)
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(
                    query,
                    MAX_RESULTS,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resumeWithException(IOException(errorMessage))
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, MAX_RESULTS).orEmpty()
            }
        }
        return addresses
            .filter { it.hasLatitude() && it.hasLongitude() }
            .filter { it.countryCode.equals(countryCode, ignoreCase = true) }
            .map {
                GeocodedPlace(
                    location = DeviceLocation(latitude = it.latitude, longitude = it.longitude),
                    region = it.adminArea,
                )
            }
    }

    override fun openLocationServicesSettings() {
        context.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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

    private companion object {
        const val MAX_RESULTS = 5
    }
}
