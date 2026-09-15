package com.wonder.provider.data.maps

import com.wonder.provider.model.GeoCoordinate
import kotlin.math.cos
import kotlin.math.sin

internal object CityCoordinates {

    private val cities = mapOf(
        "lisbon" to GeoCoordinate(38.7223, -9.1393),
        "porto" to GeoCoordinate(41.1579, -8.6291),
        "sintra" to GeoCoordinate(38.8029, -9.3817),
        "paris" to GeoCoordinate(48.8566, 2.3522),
        "barcelona" to GeoCoordinate(41.3874, 2.1686),
        "rome" to GeoCoordinate(41.9028, 12.4964),
        "athens" to GeoCoordinate(37.9838, 23.7275),
        "london" to GeoCoordinate(51.5074, -0.1278),
        "amsterdam" to GeoCoordinate(52.3676, 4.9041),
        "tokyo" to GeoCoordinate(35.6762, 139.6503),
        "new york" to GeoCoordinate(40.7128, -74.0060)
    )

    fun forDestination(destination: String): GeoCoordinate? {
        val normalized = destination.lowercase()
        return cities.entries.firstOrNull { (key, _) -> normalized.contains(key) }?.value
    }

    /** Spread markers around a city centre when exact geocoding is unavailable. */
    fun offsetFromCenter(center: GeoCoordinate, index: Int): GeoCoordinate {
        val radius = 0.012 + (index % 4) * 0.004
        val angle = index * 1.7
        return GeoCoordinate(
            latitude = center.latitude + radius * cos(angle),
            longitude = center.longitude + radius * sin(angle)
        )
    }
}
