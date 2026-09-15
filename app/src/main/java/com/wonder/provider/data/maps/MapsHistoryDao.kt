package com.wonder.provider.data.maps

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MapsHistoryDao {

    @Query("SELECT * FROM maps_profile WHERE id = 1")
    fun observeProfile(): Flow<MapsProfileEntity?>

    @Query("SELECT * FROM maps_profile WHERE id = 1")
    suspend fun profile(): MapsProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: MapsProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVisits(visits: List<MapsVisitEntity>)

    @Query("SELECT COUNT(*) FROM maps_visits")
    suspend fun visitCount(): Int

    @Query("SELECT city, COUNT(*) AS c FROM maps_visits GROUP BY city ORDER BY c DESC LIMIT :limit")
    suspend fun topCities(limit: Int): List<CityCount>

    @Query("SELECT category, COUNT(*) AS c FROM maps_visits GROUP BY category ORDER BY c DESC LIMIT :limit")
    suspend fun topCategories(limit: Int): List<CategoryCount>

    @Query(
        """
        SELECT placeName FROM maps_visits
        WHERE placeName != ''
        ORDER BY startTimeEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun recentPlaceNames(limit: Int): List<String>

    @Transaction
    suspend fun replaceVisits(visits: List<MapsVisitEntity>, profile: MapsProfileEntity) {
        clearVisits()
        upsertVisits(visits)
        upsertProfile(profile)
    }

    @Query("DELETE FROM maps_visits")
    suspend fun clearVisits()

    @Query("DELETE FROM maps_profile")
    suspend fun clearProfile()
}

data class CityCount(val city: String, val c: Int)

data class CategoryCount(val category: String, val c: Int)
