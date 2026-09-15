package com.wonder.provider.data.maps

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.wonder.provider.data.CityCatalog
import com.wonder.provider.model.ExplorePlacePick
import com.wonder.provider.model.GeoCoordinate
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Resolves place names to coordinates via Nominatim, Android Geocoder, and local fallbacks. */
class TripGeocoder(context: Context) {

    private val appContext = context.applicationContext
    private val androidGeocoder = if (Geocoder.isPresent()) Geocoder(appContext, Locale.getDefault()) else null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = mutableMapOf<String, GeoCoordinate>()
    private val mutex = Mutex()

    suspend fun resolve(location: String, destination: String, near: GeoCoordinate? = null): GeoCoordinate =
        resolveExplore(
            ExplorePlacePick(
                title = location,
                query = location,
                context = destination
            ),
            near
        ).coordinate

    suspend fun resolveExplore(
        pick: ExplorePlacePick,
        near: GeoCoordinate?
    ): ExplorePlaceResolution {
        val cacheKey = "${pick.query}|${pick.context}|${near?.latitude}|${near?.longitude}"
        cache[cacheKey]?.let {
            return ExplorePlaceResolution(it, approximate = false)
        }

        return mutex.withLock {
            cache[cacheKey]?.let { return ExplorePlaceResolution(it, approximate = false) }

            val cityHint = usableCityHint(pick.context)
            val queries = buildQueryCandidates(pick, cityHint)

            for (query in queries) {
                lookupOnline(query, cityHint, near)?.let { coord ->
                    cache[cacheKey] = coord
                    return ExplorePlaceResolution(coord, approximate = false)
                }
            }

            val anchor = near
                ?: CityCoordinates.forDestination(pick.context)
                ?: CityCoordinates.forDestination(CityCatalog.resolveCity(pick.context))
                ?: cityHint?.let { CityCoordinates.forDestination(it) }

            val index = stableIndex(pick)
            val approx = when {
                anchor != null -> CityCoordinates.offsetFromCenter(anchor, index)
                near != null -> CityCoordinates.offsetFromCenter(near, index)
                else -> CityCoordinates.offsetFromCenter(DEFAULT_CENTER, index)
            }
            cache[cacheKey] = approx
            ExplorePlaceResolution(approx, approximate = true)
        }
    }

    private fun buildQueryCandidates(pick: ExplorePlacePick, cityHint: String?): List<String> =
        listOfNotNull(
            cleanQuery(pick.query),
            cityHint?.let { cleanQuery("${pick.title}, $it") },
            cleanQuery(pick.title),
            cityHint?.let { cleanQuery(it) }
        ).distinct().filter { it.isNotBlank() }

    private fun usableCityHint(context: String): String? {
        val trimmed = context.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.equals("near you", ignoreCase = true)) return null
        if (trimmed.equals("not decided yet", ignoreCase = true)) return null
        return trimmed.substringBefore(",").trim().ifBlank { null }
    }

    private suspend fun lookupOnline(
        query: String,
        cityHint: String?,
        near: GeoCoordinate?
    ): GeoCoordinate? =
        nominatim(query, cityHint, near) ?: androidForward(query, cityHint)

    private suspend fun nominatim(
        query: String,
        cityHint: String?,
        near: GeoCoordinate?
    ): GeoCoordinate? = withContext(Dispatchers.IO) {
        val search = buildString {
            append(query)
            if (!cityHint.isNullOrBlank() && !query.contains(cityHint, ignoreCase = true)) {
                append(", $cityHint")
            }
        }
        val encoded = URLEncoder.encode(search, Charsets.UTF_8.name())
        var url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
        near?.let {
            val delta = 0.2
            url += "&viewbox=${it.longitude - delta},${it.latitude + delta}," +
                "${it.longitude + delta},${it.latitude - delta}&bounded=0"
        }

        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WonderTravelApp/1.0 (com.wonder.provider; android)")
                .header("Accept-Language", "en")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val array = JSONArray(response.body?.string().orEmpty())
                if (array.length() == 0) return@runCatching null
                val hit = array.getJSONObject(0)
                GeoCoordinate(
                    latitude = hit.getDouble("lat"),
                    longitude = hit.getDouble("lon")
                )
            }
        }.getOrNull()
    }

    private suspend fun androidForward(query: String, cityHint: String?): GeoCoordinate? =
        withContext(Dispatchers.IO) {
            val geocoder = androidGeocoder ?: return@withContext null
            val fullQuery = if (!cityHint.isNullOrBlank() && !query.contains(cityHint, ignoreCase = true)) {
                "$query, $cityHint"
            } else {
                query
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocationName(fullQuery, 1) { addresses ->
                            cont.resume(addresses.firstOrNull()?.toCoordinate())
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(fullQuery, 1)?.firstOrNull()?.toCoordinate()
                }
            }.getOrNull()
        }

    private fun android.location.Address.toCoordinate(): GeoCoordinate? {
        if (!hasLatitude() || !hasLongitude()) return null
        return GeoCoordinate(latitude, longitude)
    }

    private fun cleanQuery(raw: String): String =
        raw.trim()
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace(Regex("\\s+"), " ")

    private fun stableIndex(pick: ExplorePlacePick): Int =
        (pick.title.hashCode() xor pick.query.hashCode()).and(0x7FFFFFFF) % 12

    companion object {
        private val DEFAULT_CENTER = GeoCoordinate(51.5074, -0.1278)
    }
}

data class ExplorePlaceResolution(
    val coordinate: GeoCoordinate,
    val approximate: Boolean
)
