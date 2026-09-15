package com.wonder.provider.data.db

import com.wonder.provider.model.Expense
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.Traveller
import com.wonder.provider.model.Trip
import com.wonder.provider.model.TripArchiveStatus
import com.wonder.provider.model.TripSummary
import org.json.JSONArray
import org.json.JSONObject

object TripRecordMapper {

    fun toTrip(entity: TripEntity): Trip = Trip(
        id = entity.id,
        title = entity.title,
        destination = entity.destination,
        startDate = entity.startDate,
        endDate = entity.endDate,
        travellers = decodeTravellers(entity.travellersJson),
        budget = entity.budget,
        currency = entity.currency,
        homeCurrency = entity.homeCurrency,
        homeRate = entity.homeRate,
        coverEmoji = entity.coverEmoji,
        interests = decodeInterests(entity.interestsJson)
    )

    fun toEntity(
        trip: Trip,
        archiveStatus: TripArchiveStatus,
        mode: com.wonder.provider.model.TripMode,
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
        travellersJson = encodeTravellers(trip.travellers),
        interestsJson = encodeInterests(trip.interests),
        archiveStatus = archiveStatus,
        mode = mode,
        modeWasManual = modeWasManual
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

    fun encodeTravellers(travellers: List<Traveller>): String {
        val array = JSONArray()
        travellers.forEach { traveller ->
            array.put(
                JSONObject()
                    .put("id", traveller.id)
                    .put("name", traveller.name)
                    .put("emoji", traveller.emoji)
            )
        }
        return array.toString()
    }

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

    fun encodeInterests(interests: Set<TourInterest>): String {
        val array = JSONArray()
        interests.forEach { array.put(it.name) }
        return array.toString()
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
