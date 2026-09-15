package com.wonder.provider.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Wonder is one app with two temperaments. Planner is for shaping a trip that hasn't happened
 * yet; Wandering is for living the one you're on.
 */
enum class TripMode(val label: String) {
    PLANNER("Planning"),
    WANDERING("Wandering")
}

data class Traveller(
    val id: String,
    val name: String,
    val emoji: String
)

enum class ItemKind(val label: String, val emoji: String) {
    FLIGHT("Flight", "✈️"),
    TRANSPORT("Transport", "🚆"),
    STAY("Stay", "🛏️"),
    FOOD("Food", "🍽️"),
    ACTIVITY("Activity", "🎟️"),
    OUTDOORS("Outdoors", "🌿"),
    NIGHTLIFE("Night out", "🌙"),
    SHOPPING("Shopping", "🛍️"),
    FREE("Free time", "🫧")
}

/** How settled a plan is. Only [BOOKED] items carry a real, paid amount. */
enum class ItemStatus(val label: String) {
    IDEA("Idea"),
    PLANNED("Planned"),
    BOOKED("Booked"),
    DONE("Done")
}

data class ItineraryItem(
    val id: String,
    val date: LocalDate,
    val title: String,
    val kind: ItemKind,
    /** Null means the day holds this loosely — no fixed time. */
    val startTime: LocalTime? = null,
    val durationMinutes: Int = 60,
    val location: String = "",
    val notes: String = "",
    val estimatedCost: Double = 0.0,
    val costIsPerPerson: Boolean = false,
    val status: ItemStatus = ItemStatus.PLANNED,
    val paidAmount: Double? = null,
    val bookingRef: String = "",
    val travellerIds: Set<String> = emptySet()
) {
    val endTime: LocalTime?
        get() = startTime?.plusMinutes(durationMinutes.toLong())

    fun estimatedTotal(travellerCount: Int): Double =
        if (costIsPerPerson) estimatedCost * travellerCount.coerceAtLeast(1) else estimatedCost

    /** What this item should ultimately cost: the real figure once booked, the guess until then. */
    fun expectedTotal(travellerCount: Int): Double =
        paidAmount ?: estimatedTotal(travellerCount)

    val partySize: Int get() = travellerIds.size
}

data class Trip(
    val id: String,
    val title: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val travellers: List<Traveller>,
    val budget: Double,
    val currency: String,
    val homeCurrency: String,
    /** One unit of [currency] in [homeCurrency]. */
    val homeRate: Double,
    val coverEmoji: String,
    val interests: Set<TourInterest> = emptySet(),
    /**
     * False when the trip was created without a real date plan (e.g. "cheapest days").
     * Itinerary legs must wait until the traveller confirms a range.
     */
    val datesConfirmed: Boolean = true
) {
    val dates: List<LocalDate>
        get() = generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .toList()

    val dayCount: Int get() = dates.size

    fun dayNumber(date: LocalDate): Int =
        ChronoUnit.DAYS.between(startDate, date).toInt() + 1

    fun covers(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)

    fun daysUntilStart(from: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(from, startDate)

    fun daysRemaining(from: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(from, endDate)

    val partySize: Int get() = travellers.size.coerceAtLeast(1)
}

/** An unclaimed stretch of a day — where recommendations are worth offering. */
data class FreeWindow(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime
) {
    val minutes: Int get() = ChronoUnit.MINUTES.between(start, end).toInt()
}

/** Something Wonder thinks is worth adding, with the reason it thinks so. */
data class Recommendation(
    val id: String,
    val title: String,
    val why: String,
    val emoji: String,
    val kind: ItemKind,
    val estimatedCost: Double,
    val durationMinutes: Int,
    val tip: String,
    val suggestedDate: LocalDate? = null,
    val suggestedTime: LocalTime? = null
)
