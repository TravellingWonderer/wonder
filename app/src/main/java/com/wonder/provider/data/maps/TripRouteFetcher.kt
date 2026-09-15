package com.wonder.provider.data.maps

import com.wonder.provider.model.GeoCoordinate
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.MapRouteSegment
import com.wonder.provider.model.TimelineLeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Fetches turn-by-turn geometry between consecutive legs via OSRM (OpenStreetMap). */
class TripRouteFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = mutableMapOf<String, List<GeoCoordinate>>()
    private val mutex = Mutex()

    suspend fun buildSegments(legs: List<TimelineLeg>): List<MapRouteSegment> {
        if (legs.size < 2) return emptyList()

        return legs.zip(legs.drop(1)).mapNotNull { (fromLeg, toLeg) ->
            val from = fromLeg.coordinate ?: return@mapNotNull null
            val to = toLeg.coordinate ?: return@mapNotNull null
            val longHaul = isLongHaul(fromLeg, toLeg, from, to)
            val points = if (longHaul) {
                greatCircleArc(from, to)
            } else {
                routeBetween(from, to, profileFor(fromLeg, toLeg, from, to)) ?: listOf(from, to)
            }
            MapRouteSegment(
                fromLegId = fromLeg.item.id,
                toLegId = toLeg.item.id,
                fromTitle = fromLeg.item.title,
                toTitle = toLeg.item.title,
                points = points,
                isLongHaul = longHaul
            )
        }
    }

    /** Road or walking path between two coordinates (OSRM, cached). */
    suspend fun routeBetween(
        from: GeoCoordinate,
        to: GeoCoordinate,
        profile: String = walkingOrDrivingProfile(from, to)
    ): List<GeoCoordinate>? = route(from, to, profile)

    private fun walkingOrDrivingProfile(from: GeoCoordinate, to: GeoCoordinate): String =
        if (distanceKm(from, to) <= 5.0) "walking" else "driving"

    private suspend fun route(
        from: GeoCoordinate,
        to: GeoCoordinate,
        profile: String
    ): List<GeoCoordinate>? {
        val key = "$profile:${from.latitude},${from.longitude}:${to.latitude},${to.longitude}"
        cache[key]?.let { return it }

        return mutex.withLock {
            cache[key]?.let { return it }
            val resolved = fetchOsrmRoute(from, to, profile)
            if (resolved != null) cache[key] = resolved
            resolved
        }
    }

    private suspend fun fetchOsrmRoute(
        from: GeoCoordinate,
        to: GeoCoordinate,
        profile: String
    ): List<GeoCoordinate>? = withContext(Dispatchers.IO) {
        val coords = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
        val url = "https://router.project-osrm.org/route/v1/$profile/$coords" +
            "?overview=full&geometries=geojson&steps=false"

        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WonderTravelApp/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return@runCatching null
                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) return@runCatching null
                val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                val points = ArrayList<GeoCoordinate>(coordinates.length())
                for (i in 0 until coordinates.length()) {
                    val pair = coordinates.getJSONArray(i)
                    points.add(
                        GeoCoordinate(
                            latitude = pair.getDouble(1),
                            longitude = pair.getDouble(0)
                        )
                    )
                }
                points
            }
        }.getOrNull()
    }

    private fun profileFor(
        fromLeg: TimelineLeg,
        toLeg: TimelineLeg,
        from: GeoCoordinate,
        to: GeoCoordinate
    ): String {
        val km = distanceKm(from, to)
        val groundKinds = setOf(ItemKind.FOOD, ItemKind.ACTIVITY, ItemKind.OUTDOORS, ItemKind.SHOPPING, ItemKind.FREE)
        return when {
            fromLeg.item.kind == ItemKind.TRANSPORT || toLeg.item.kind == ItemKind.TRANSPORT -> "driving"
            fromLeg.item.kind in groundKinds && toLeg.item.kind in groundKinds && km <= 3.0 -> "walking"
            else -> "driving"
        }
    }

    private fun isLongHaul(
        fromLeg: TimelineLeg,
        toLeg: TimelineLeg,
        from: GeoCoordinate,
        to: GeoCoordinate
    ): Boolean {
        if (fromLeg.item.kind == ItemKind.FLIGHT || toLeg.item.kind == ItemKind.FLIGHT) return true
        return distanceKm(from, to) > 120.0
    }

    private fun distanceKm(a: GeoCoordinate, b: GeoCoordinate): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun greatCircleArc(from: GeoCoordinate, to: GeoCoordinate, steps: Int = 24): List<GeoCoordinate> {
        if (from == to) return listOf(from)
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)

        val delta = 2 * asin(
            sqrt(
                sin((lat2 - lat1) / 2).pow(2) +
                    cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2).pow(2)
            )
        )
        if (delta == 0.0) return listOf(from, to)

        return (0..steps).map { step ->
            val f = step.toDouble() / steps
            val a = sin((1 - f) * delta) / sin(delta)
            val b = sin(f * delta) / sin(delta)
            val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
            val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
            val z = a * sin(lat1) + b * sin(lat2)
            GeoCoordinate(
                latitude = Math.toDegrees(atan2(z, sqrt(x * x + y * y))),
                longitude = Math.toDegrees(atan2(y, x))
            )
        }
    }

    private fun asin(value: Double): Double = kotlin.math.asin(value.coerceIn(-1.0, 1.0))
}
