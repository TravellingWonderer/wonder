package com.wonder.provider.model

import java.time.LocalDate

data class MapsVisit(
    val id: String,
    val placeId: String?,
    val placeName: String,
    val address: String,
    val city: String,
    val country: String,
    val latitude: Double?,
    val longitude: Double?,
    val category: String,
    val visitedAt: LocalDate,
    val startTimeEpochMillis: Long,
    val endTimeEpochMillis: Long?,
    val durationMinutes: Int,
    val source: String
)

data class TravelProfileSummary(
    val googleAccountEmail: String?,
    val googleDisplayName: String?,
    val totalVisits: Int,
    val cityCount: Int,
    val topCities: List<String>,
    val topCategories: List<String>,
    val recentPlaceNames: List<String>,
    val frequentActivities: List<String>,
    val lastImportedAtEpochMillis: Long
) {
    val hasData: Boolean get() = totalVisits > 0
}
