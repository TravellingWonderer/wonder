package com.wonder.provider.model

import java.time.LocalDate

/**
 * Cached discovery feed for a destination — one batch per calendar day until the next day.
 */
enum class ExploreFeedKind {
    /** Pre-trip inspiration and generic destination picks. */
    DISCOVERY,
    /** Traveller is on the trip today — picks tied to the live itinerary. */
    LIVE_TRIP,
    /** No active trip — picks based on the traveller's current location. */
    NEARBY
}

data class ExploreFeed(
    val destination: String,
    val gatheredAtEpochMillis: Long,
    val sourceLabel: String,
    val contentDate: String,
    val feedKind: ExploreFeedKind = ExploreFeedKind.DISCOVERY,
    val tripIdeas: List<TripIdea>,
    val foodFavorites: List<FoodFavorite>,
    val localGuides: List<LocalGuide>,
    val smallTrips: List<SmallTripIdea>,
    val localTakes: List<LocalTake>
) {
    fun isFreshFor(date: LocalDate = LocalDate.now()): Boolean = contentDate == date.toString()
}

data class TripIdea(
    val id: String,
    val title: String,
    val destination: String,
    val summary: String,
    val durationDays: Int,
    val budgetHint: String,
    val emoji: String,
    val vibe: String
)

data class FoodFavorite(
    val id: String,
    val name: String,
    val place: String,
    val description: String,
    val priceHint: String,
    val emoji: String
)

data class LocalGuide(
    val id: String,
    val name: String,
    val specialty: String,
    val bio: String,
    val tip: String,
    val emoji: String
)

data class SmallTripIdea(
    val id: String,
    val title: String,
    val duration: String,
    val description: String,
    val location: String,
    val emoji: String
)

/** What a resident would actually say about a place — not a review score. */
data class LocalTake(
    val id: String,
    val place: String,
    val category: String,
    val opinion: String,
    val residentName: String,
    val residentDetail: String
)
