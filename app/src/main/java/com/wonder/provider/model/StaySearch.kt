package com.wonder.provider.model

import java.time.LocalDate

data class StaySearchQuery(
    val destination: String,
    val latitude: Double,
    val longitude: Double,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val adults: Int = 1,
    val rooms: Int = 1,
    val radiusKm: Int = 8
)

data class StayOfferSummary(
    val id: String,
    val name: String,
    /** Cheapest total for the full stay (not nightly). */
    val totalPrice: Double,
    val currency: String,
    val nights: Int,
    val rating: Double? = null,
    val neighbourhood: String = "",
    val isLivePrice: Boolean = true
) {
    val nightlyApprox: Double
        get() = if (nights > 0) totalPrice / nights else totalPrice
}

data class StaySearchResult(
    val query: StaySearchQuery,
    val offers: List<StayOfferSummary>,
    val providerLabel: String,
    val configured: Boolean,
    val errorMessage: String? = null
)
