package com.wonder.provider.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "explore_feeds")
data class ExploreFeedEntity(
    @PrimaryKey val cacheKey: String,
    val destination: String,
    val payloadJson: String,
    val storedAtEpochMillis: Long
)

@Dao
interface ExploreCacheDao {

    @Query("SELECT * FROM explore_feeds WHERE cacheKey = :cacheKey")
    suspend fun feed(cacheKey: String): ExploreFeedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExploreFeedEntity)

    @Query("DELETE FROM explore_feeds WHERE storedAtEpochMillis < :cutoffEpochMillis")
    suspend fun deleteStale(cutoffEpochMillis: Long)
}
