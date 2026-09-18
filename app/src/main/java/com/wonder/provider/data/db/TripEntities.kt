package com.wonder.provider.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.wonder.provider.model.ExpenseCategory
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.TripArchiveStatus
import com.wonder.provider.model.TripMode
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val budget: Double,
    val currency: String,
    val homeCurrency: String,
    val homeRate: Double,
    val coverEmoji: String,
    val archiveStatus: TripArchiveStatus,
    val mode: TripMode,
    val modeWasManual: Boolean,
    val datesConfirmed: Boolean = true
)

@Entity(
    tableName = "travellers",
    primaryKeys = ["tripId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class TravellerEntity(
    val id: String,
    val tripId: String,
    val name: String,
    val emoji: String,
    val sortOrder: Int
)

@Entity(
    tableName = "trip_interests",
    primaryKeys = ["tripId", "interest"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class TripInterestEntity(
    val tripId: String,
    val interest: TourInterest
)

@Entity(
    tableName = "itinerary_items",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class ItineraryItemEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val date: LocalDate,
    val title: String,
    val kind: ItemKind,
    val startTime: LocalTime?,
    val durationMinutes: Int,
    val location: String,
    val notes: String,
    val estimatedCost: Double,
    val costIsPerPerson: Boolean,
    val status: ItemStatus,
    val paidAmount: Double?,
    val bookingRef: String,
    val travellerIds: Set<String>
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId"), Index("itemId")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val label: String,
    val amount: Double,
    val category: ExpenseCategory,
    val date: LocalDate,
    val paidById: String,
    val itemId: String?,
    val note: String
)

data class TripWithDetails(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId")
    val travellers: List<TravellerEntity>,
    @Relation(parentColumn = "id", entityColumn = "tripId")
    val interests: List<TripInterestEntity>,
    @Relation(parentColumn = "id", entityColumn = "tripId")
    val items: List<ItineraryItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "tripId")
    val expenses: List<ExpenseEntity>
)
