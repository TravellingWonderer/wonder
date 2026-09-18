package com.wonder.provider.data.db

import com.wonder.provider.model.Expense
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.Traveller
import com.wonder.provider.model.Trip
import com.wonder.provider.model.TripArchiveStatus
import com.wonder.provider.model.TripMode
import com.wonder.provider.model.TripSummary
import org.json.JSONArray

object TripRecordMapper {

    fun toTrip(details: TripWithDetails): Trip = toTrip(
        entity = details.trip,
        travellers = details.travellers,
        interests = details.interests
    )

    fun toTrip(
        entity: TripEntity,
        travellers: List<TravellerEntity>,
        interests: List<TripInterestEntity>
    ): Trip = Trip(
        id = entity.id,
        title = entity.title,
        destination = entity.destination,
        startDate = entity.startDate,
        endDate = entity.endDate,
        travellers = travellers.sortedBy { it.sortOrder }.map(::toTraveller),
        budget = entity.budget,
        currency = entity.currency,
        homeCurrency = entity.homeCurrency,
        homeRate = entity.homeRate,
        coverEmoji = entity.coverEmoji,
        interests = interests.map { it.interest }.toSet(),
        datesConfirmed = entity.datesConfirmed
    )

    fun toEntity(
        trip: Trip,
        archiveStatus: TripArchiveStatus,
        mode: TripMode,
        modeWasManual: Boolean
    ): TripEntity = TripEntity(
        id = trip.id,
        title = trip.title,
        destination = trip.destination,
        startDate = trip.startDate,
        endDate = trip.endDate,
        budget = trip.budget,
        currency = trip.currency,
        homeCurrency = trip.homeCurrency,
        homeRate = trip.homeRate,
        coverEmoji = trip.coverEmoji,
        archiveStatus = archiveStatus,
        mode = mode,
        modeWasManual = modeWasManual,
        datesConfirmed = trip.datesConfirmed
    )

    fun toTravellerEntities(trip: Trip): List<TravellerEntity> =
        trip.travellers.mapIndexed { index, traveller ->
            TravellerEntity(
                id = traveller.id,
                tripId = trip.id,
                name = traveller.name,
                emoji = traveller.emoji,
                sortOrder = index
            )
        }

    fun toInterestEntities(trip: Trip): List<TripInterestEntity> =
        trip.interests.map { interest ->
            TripInterestEntity(tripId = trip.id, interest = interest)
        }

    fun toTraveller(entity: TravellerEntity): Traveller = Traveller(
        id = entity.id,
        name = entity.name,
        emoji = entity.emoji
    )

    fun toSummary(entity: TripEntity, activeTripId: String): TripSummary = TripSummary(
        id = entity.id,
        title = entity.title,
        destination = entity.destination,
        startDate = entity.startDate,
        endDate = entity.endDate,
        coverEmoji = entity.coverEmoji,
        archiveStatus = entity.archiveStatus,
        isActive = entity.id == activeTripId
    )

    fun toItem(entity: ItineraryItemEntity): ItineraryItem = ItineraryItem(
        id = entity.id,
        date = entity.date,
        title = entity.title,
        kind = entity.kind,
        startTime = entity.startTime,
        durationMinutes = entity.durationMinutes,
        location = entity.location,
        notes = entity.notes,
        estimatedCost = entity.estimatedCost,
        costIsPerPerson = entity.costIsPerPerson,
        status = entity.status,
        paidAmount = entity.paidAmount,
        bookingRef = entity.bookingRef,
        travellerIds = entity.travellerIds
    )

    fun toItemEntity(item: ItineraryItem, tripId: String): ItineraryItemEntity = ItineraryItemEntity(
        id = item.id,
        tripId = tripId,
        date = item.date,
        title = item.title,
        kind = item.kind,
        startTime = item.startTime,
        durationMinutes = item.durationMinutes,
        location = item.location,
        notes = item.notes,
        estimatedCost = item.estimatedCost,
        costIsPerPerson = item.costIsPerPerson,
        status = item.status,
        paidAmount = item.paidAmount,
        bookingRef = item.bookingRef,
        travellerIds = item.travellerIds
    )

    fun toExpense(entity: ExpenseEntity): Expense = Expense(
        id = entity.id,
        label = entity.label,
        amount = entity.amount,
        category = entity.category,
        date = entity.date,
        paidById = entity.paidById,
        itemId = entity.itemId,
        note = entity.note
    )

    fun toExpenseEntity(expense: Expense, tripId: String): ExpenseEntity = ExpenseEntity(
        id = expense.id,
        tripId = tripId,
        label = expense.label,
        amount = expense.amount,
        category = expense.category,
        date = expense.date,
        paidById = expense.paidById,
        itemId = expense.itemId,
        note = expense.note
    )

    fun decodeTravellers(json: String): List<Traveller> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val entry = array.getJSONObject(index)
                add(
                    Traveller(
                        id = entry.getString("id"),
                        name = entry.getString("name"),
                        emoji = entry.getString("emoji")
                    )
                )
            }
        }
    }

    fun decodeInterests(json: String): Set<TourInterest> {
        if (json.isBlank()) return emptySet()
        val array = JSONArray(json)
        return buildSet {
            for (index in 0 until array.length()) {
                runCatching { add(TourInterest.valueOf(array.getString(index))) }
            }
        }
    }
}
