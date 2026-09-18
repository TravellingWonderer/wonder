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

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun tripWithDetails(tripId: String): TripWithDetails?

    @Query("SELECT * FROM trips WHERE archiveStatus = :status ORDER BY startDate DESC")
    suspend fun tripsByStatus(status: TripArchiveStatus): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("UPDATE trips SET archiveStatus = :archived WHERE endDate < :today AND archiveStatus != :archived")
    suspend fun archivePastTrips(today: LocalDate, archived: TripArchiveStatus = TripArchiveStatus.ARCHIVED)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTravellers(travellers: List<TravellerEntity>)

    @Query("DELETE FROM travellers WHERE tripId = :tripId")
    suspend fun deleteTravellersForTrip(tripId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInterests(interests: List<TripInterestEntity>)

    @Query("DELETE FROM trip_interests WHERE tripId = :tripId")
    suspend fun deleteInterestsForTrip(tripId: String)

    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId")
    suspend fun itemsForTrip(tripId: String): List<ItineraryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ItineraryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<ItineraryItemEntity>)

    @Query("DELETE FROM itinerary_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("DELETE FROM itinerary_items WHERE tripId = :tripId")
    suspend fun deleteItemsForTrip(tripId: String)

    @Query("SELECT * FROM expenses WHERE tripId = :tripId")
    suspend fun expensesForTrip(tripId: String): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpenses(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: String)

    @Query("DELETE FROM expenses WHERE tripId = :tripId")
    suspend fun deleteExpensesForTrip(tripId: String)

    @Query("UPDATE expenses SET itemId = NULL WHERE tripId = :tripId AND itemId = :itemId")
    suspend fun unlinkExpenses(itemId: String, tripId: String)

    @Transaction
    suspend fun persistTripGraph(
        trip: TripEntity,
        travellers: List<TravellerEntity>,
        interests: List<TripInterestEntity>
    ) {
        upsertTrip(trip)
        deleteTravellersForTrip(trip.id)
        if (travellers.isNotEmpty()) upsertTravellers(travellers)
        deleteInterestsForTrip(trip.id)
        if (interests.isNotEmpty()) upsertInterests(interests)
    }

    @Transaction
    suspend fun insertFullTrip(
        trip: TripEntity,
        travellers: List<TravellerEntity>,
        interests: List<TripInterestEntity>,
        items: List<ItineraryItemEntity>,
        expenses: List<ExpenseEntity>
    ) {
        persistTripGraph(trip, travellers, interests)
        if (items.isNotEmpty()) upsertItems(items)
        if (expenses.isNotEmpty()) upsertExpenses(expenses)
    }

    @Transaction
    suspend fun replaceTripBundle(
        trip: TripEntity,
        travellers: List<TravellerEntity>,
        interests: List<TripInterestEntity>,
        items: List<ItineraryItemEntity>,
        expenses: List<ExpenseEntity>
    ) {
        persistTripGraph(trip, travellers, interests)
        deleteItemsForTrip(trip.id)
        deleteExpensesForTrip(trip.id)
        if (items.isNotEmpty()) upsertItems(items)
        if (expenses.isNotEmpty()) upsertExpenses(expenses)
    }

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: String)

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun tripCount(): Int
}
