package com.wonder.provider.data.maps

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "maps_visits",
    indices = [Index("visitedAt"), Index("city"), Index("category")]
)
data class MapsVisitEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "maps_profile")
data class MapsProfileEntity(
    @PrimaryKey val id: Int = 1,
    val googleAccountEmail: String?,
    val googleDisplayName: String?,
    val lastImportedAtEpochMillis: Long,
    val lastSyncedAtEpochMillis: Long?,
    val totalVisits: Int,
    val summaryJson: String
)
