package com.wonder.provider.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wonder.provider.data.maps.MapsHistoryDao
import com.wonder.provider.data.maps.MapsProfileEntity
import com.wonder.provider.data.maps.MapsVisitEntity

@Database(
    entities = [
        TripEntity::class,
        ItineraryItemEntity::class,
        ExpenseEntity::class,
        MapsVisitEntity::class,
        MapsProfileEntity::class,
        CustomPersonaEntity::class,
        TripPersonaAttachmentEntity::class
    ],
        version = 4,
        exportSchema = false
)
@TypeConverters(WonderTypeConverters::class)
abstract class WonderDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun mapsHistoryDao(): MapsHistoryDao
    abstract fun customPersonaDao(): CustomPersonaDao

    companion object {
        @Volatile private var instance: WonderDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS maps_visits (
                        id TEXT NOT NULL PRIMARY KEY,
                        placeId TEXT,
                        placeName TEXT NOT NULL,
                        address TEXT NOT NULL,
                        city TEXT NOT NULL,
                        country TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        category TEXT NOT NULL,
                        visitedAt TEXT NOT NULL,
                        startTimeEpochMillis INTEGER NOT NULL,
                        endTimeEpochMillis INTEGER,
                        durationMinutes INTEGER NOT NULL,
                        source TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_maps_visits_visitedAt ON maps_visits(visitedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_maps_visits_city ON maps_visits(city)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_maps_visits_category ON maps_visits(category)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS maps_profile (
                        id INTEGER NOT NULL PRIMARY KEY,
                        googleAccountEmail TEXT,
                        googleDisplayName TEXT,
                        lastImportedAtEpochMillis INTEGER NOT NULL,
                        lastSyncedAtEpochMillis INTEGER,
                        totalVisits INTEGER NOT NULL,
                        summaryJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS custom_personas (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        iconKey TEXT,
                        emoji TEXT,
                        createdAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trip_persona_attachments (
                        tripId TEXT NOT NULL,
                        personaId TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        attachedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(tripId, personaId),
                        FOREIGN KEY(tripId) REFERENCES trips(id) ON DELETE CASCADE,
                        FOREIGN KEY(personaId) REFERENCES custom_personas(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_trip_persona_attachments_tripId " +
                        "ON trip_persona_attachments(tripId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_trip_persona_attachments_personaId " +
                        "ON trip_persona_attachments(personaId)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE trips ADD COLUMN datesConfirmed INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        fun get(context: Context): WonderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WonderDatabase::class.java,
                    "wonder.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
