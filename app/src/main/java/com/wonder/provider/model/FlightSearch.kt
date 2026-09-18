package com.wonder.provider.model

import java.time.LocalDate

data class FlightSearchQuery(
    val origin: String,
    val destination: String,
    val departDate: LocalDate,
    val returnDate: LocalDate? = null,
    val adults: Int = 1,
    val currency: String = "EUR"
)

data class FlightOfferSummary(
    val id: String,
    val airline: String,
    val price: Double,
    val currency: String,
    val origin: String,
    val destination: String,
    val departLabel: String,
    val arriveLabel: String,
    val durationLabel: String,
    val stops: Int,
    val isLivePrice: Boolean = true,
    val returnOrigin: String? = null,
    val returnDestination: String? = null,
    val returnDepartLabel: String? = null,
    val returnArriveLabel: String? = null
) {
    val isRoundTrip: Boolean get() = !returnOrigin.isNullOrBlank()
}

data class FlightSearchResult(
    val query: FlightSearchQuery,
    val offers: List<FlightOfferSummary>,
    val providerLabel: String,
    val configured: Boolean,
    val errorMessage: String? = null
)
