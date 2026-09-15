package com.wonder.provider.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wonder.provider.model.TripArchiveStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY startDate ASC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun tripById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE archiveStatus = :status ORDER BY startDate DESC")
    suspend fun tripsByStatus(status: TripArchiveStatus): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("UPDATE trips SET archiveStatus = :archived WHERE endDate < :today AND archiveStatus != :archived")
    suspend fun archivePastTrips(today: LocalDate, archived: TripArchiveStatus = TripArchiveStatus.ARCHIVED)

    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId")
    suspend fun itemsForTrip(tripId: String): List<ItineraryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ItineraryItemEntity)

    @Query("DELETE FROM itinerary_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("SELECT * FROM expenses WHERE tripId = :tripId")
    suspend fun expensesForTrip(tripId: String): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: String)

    @Query("UPDATE expenses SET itemId = NULL WHERE tripId = :tripId AND itemId = :itemId")
    suspend fun unlinkExpenses(itemId: String, tripId: String)

    @Transaction
    suspend fun replaceTripBundle(
        trip: TripEntity,
        items: List<ItineraryItemEntity>,
        expenses: List<ExpenseEntity>
    ) {
        upsertTrip(trip)
        items.forEach { upsertItem(it) }
        expenses.forEach { upsertExpense(it) }
    }

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: String)

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun tripCount(): Int
}
