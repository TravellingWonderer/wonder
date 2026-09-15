package com.wonder.provider.data.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PlaceSnapshot(
    val city: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double
) {
    val label: String
        get() = listOfNotNull(
            city.takeIf { it.isNotBlank() },
            country?.takeIf { it.isNotBlank() }
        ).joinToString(", ").ifBlank { "Near you" }

    fun cacheKey(): String = "nearby_${latitude.format(3)}_${longitude.format(3)}"

    private fun Double.format(decimals: Int): String =
        "%.${decimals}f".format(Locale.US, this)
}

/** Reads GPS coordinates and resolves a human-readable place label. */
class DeviceLocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)
    private val geocoder = if (Geocoder.isPresent()) Geocoder(appContext, Locale.getDefault()) else null

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun currentPlace(): PlaceSnapshot? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val location = withTimeoutOrNull(12_000L) { readLocation() } ?: return@withContext null
        resolvePlace(location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun readLocation(): Location? {
        val current = suspendCancellableCoroutine { cont ->
            val token = CancellationTokenSource()
            cont.invokeOnCancellation { token.cancel() }
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
        if (current != null) return current

        return suspendCancellableCoroutine { cont ->
            fused.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private suspend fun resolvePlace(lat: Double, lng: Double): PlaceSnapshot? {
        nominatimReverse(lat, lng)?.let { return it }
        return androidReverse(lat, lng)
    }

    private fun nominatimReverse(lat: Double, lng: Double): PlaceSnapshot? = runCatching {
        val url = "https://nominatim.openstreetmap.org/reverse" +
            "?lat=$lat&lon=$lng&format=json&zoom=10&addressdetails=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "WonderTravelApp/1.0")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val json = JSONObject(response.body?.string().orEmpty())
            val address = json.optJSONObject("address") ?: return@runCatching null
            val city = address.firstNonBlank("city", "town", "village", "municipality", "county")
                ?: json.optString("name").ifBlank { null }
                ?: return@runCatching null
            PlaceSnapshot(
                city = city,
                region = address.optString("state").ifBlank { address.optString("region").ifBlank { null } },
                country = address.optString("country").ifBlank { null },
                latitude = lat,
                longitude = lng
            )
        }
    }.getOrNull()

    private suspend fun androidReverse(lat: Double, lng: Double): PlaceSnapshot? {
        val coder = geocoder ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    coder.getFromLocation(lat, lng, 1) { addresses ->
                        cont.resume(addresses.firstOrNull()?.toSnapshot(lat, lng))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                coder.getFromLocation(lat, lng, 1)?.firstOrNull()?.toSnapshot(lat, lng)
            }
        }.getOrNull()
    }

    private fun Address.toSnapshot(lat: Double, lng: Double): PlaceSnapshot? {
        val city = locality?.takeIf { it.isNotBlank() }
            ?: subAdminArea?.takeIf { it.isNotBlank() }
            ?: adminArea?.takeIf { it.isNotBlank() }
            ?: return null
        return PlaceSnapshot(
            city = city,
            region = adminArea?.ifBlank { null },
            country = countryName?.ifBlank { null },
            latitude = lat,
            longitude = lng
        )
    }

    private fun JSONObject.firstNonBlank(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            optString(key).takeIf { it.isNotBlank() }
        }
}
