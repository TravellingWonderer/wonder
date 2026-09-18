package com.wonder.provider.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val activeTripId: String? = null,
    val userClearedTrips: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Dao
interface AppSettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun settings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettingsEntity)
}
