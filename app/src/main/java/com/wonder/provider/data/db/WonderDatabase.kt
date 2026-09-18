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
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        TripEntity::class,
        TravellerEntity::class,
        TripInterestEntity::class,
        ItineraryItemEntity::class,
        ExpenseEntity::class,
        MapsVisitEntity::class,
        MapsProfileEntity::class,
        CustomPersonaEntity::class,
        TripPersonaAttachmentEntity::class,
        AppSettingsEntity::class,
        ExploreFeedEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(WonderTypeConverters::class)
abstract class WonderDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun mapsHistoryDao(): MapsHistoryDao
    abstract fun customPersonaDao(): CustomPersonaDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun exploreCacheDao(): ExploreCacheDao

    companion object {
        const val NAME = "wonder.db"

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `travellers` (
                        `id` TEXT NOT NULL,
                        `tripId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`tripId`, `id`),
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_travellers_tripId` ON `travellers` (`tripId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trip_interests` (
                        `tripId` TEXT NOT NULL,
                        `interest` TEXT NOT NULL,
                        PRIMARY KEY(`tripId`, `interest`),
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_trip_interests_tripId` ON `trip_interests` (`tripId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_settings` (
                        `id` INTEGER NOT NULL,
                        `activeTripId` TEXT,
                        `userClearedTrips` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `explore_feeds` (
                        `cacheKey` TEXT NOT NULL,
                        `destination` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `storedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expenses_itemId` ON `expenses` (`itemId`)"
                )

                db.query("SELECT `id`, `travellersJson`, `interestsJson` FROM `trips`").use { cursor ->
                    val idIndex = cursor.getColumnIndex("id")
                    val travellersIndex = cursor.getColumnIndex("travellersJson")
                    val interestsIndex = cursor.getColumnIndex("interestsJson")
                    while (cursor.moveToNext()) {
                        val tripId = cursor.getString(idIndex)
                        val travellersJson = cursor.getString(travellersIndex).orEmpty()
                        val interestsJson = cursor.getString(interestsIndex).orEmpty()
                        TripRecordMapper.decodeTravellers(travellersJson).forEachIndexed { index, traveller ->
                            db.execSQL(
                                "INSERT OR REPLACE INTO `travellers` (`id`, `tripId`, `name`, `emoji`, `sortOrder`) VALUES (?, ?, ?, ?, ?)",
                                arrayOf(traveller.id, tripId, traveller.name, traveller.emoji, index)
                            )
                        }
                        TripRecordMapper.decodeInterests(interestsJson).forEach { interest ->
                            db.execSQL(
                                "INSERT OR REPLACE INTO `trip_interests` (`tripId`, `interest`) VALUES (?, ?)",
                                arrayOf(tripId, interest.name)
                            )
                        }
                    }
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trips_new` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `destination` TEXT NOT NULL,
                        `startDate` TEXT NOT NULL,
                        `endDate` TEXT NOT NULL,
                        `budget` REAL NOT NULL,
                        `currency` TEXT NOT NULL,
                        `homeCurrency` TEXT NOT NULL,
                        `homeRate` REAL NOT NULL,
                        `coverEmoji` TEXT NOT NULL,
                        `archiveStatus` TEXT NOT NULL,
                        `mode` TEXT NOT NULL,
                        `modeWasManual` INTEGER NOT NULL,
                        `datesConfirmed` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `trips_new` (
                        `id`, `title`, `destination`, `startDate`, `endDate`, `budget`, `currency`,
                        `homeCurrency`, `homeRate`, `coverEmoji`, `archiveStatus`, `mode`,
                        `modeWasManual`, `datesConfirmed`
                    )
                    SELECT
                        `id`, `title`, `destination`, `startDate`, `endDate`, `budget`, `currency`,
                        `homeCurrency`, `homeRate`, `coverEmoji`, `archiveStatus`, `mode`,
                        `modeWasManual`, `datesConfirmed`
                    FROM `trips`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `trips`")
                db.execSQL("ALTER TABLE `trips_new` RENAME TO `trips`")
                db.execSQL(
                    "INSERT OR REPLACE INTO `app_settings` (`id`, `activeTripId`, `userClearedTrips`) VALUES (1, NULL, 0)"
                )
            }
        }

        fun build(context: Context): WonderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WonderDatabase::class.java,
                    NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
                    .also { instance = it }
            }
    }
}
