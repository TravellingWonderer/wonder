package com.wonder.provider.model

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class TripOverviewSnapshot(
    val trip: Trip,
    val items: List<ItineraryItem>,
    val isActive: Boolean
)

data class TimelineLeg(
    val item: ItineraryItem,
    val coordinate: GeoCoordinate?
)

data class MapLegMarker(
    val legId: String,
    val title: String,
    val coordinate: GeoCoordinate
)

/** Road or arc path between two consecutive itinerary legs. */
data class MapRouteSegment(
    val fromLegId: String,
    val toLegId: String,
    val fromTitle: String,
    val toTitle: String,
    val points: List<GeoCoordinate>,
    /** Flights and very long hops — drawn dashed instead of as a road route. */
    val isLongHaul: Boolean = false
)
