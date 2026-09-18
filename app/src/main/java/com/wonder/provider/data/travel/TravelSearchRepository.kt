package com.wonder.provider.data.travel

import com.wonder.provider.model.FlightSearchQuery
import com.wonder.provider.model.FlightSearchResult
import com.wonder.provider.model.StayOfferSummary
import com.wonder.provider.model.StaySearchQuery
import com.wonder.provider.model.StaySearchResult
import com.wonder.provider.model.TourBudget
import com.wonder.provider.model.Trip
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

class TravelSearchRepository(
    private val settings: TravelApiSettingsRepository
) {

    suspend fun searchFlights(query: FlightSearchQuery): FlightSearchResult {
        val token = settings.getDuffelToken()
        if (token.isBlank()) {
            return FlightSearchResult(
                query = query,
                offers = emptyList(),
                providerLabel = "Duffel",
                configured = false,
                errorMessage = "Add a Duffel access token in settings to search live fares."
            )
        }

        return runCatching {
            val offers = DuffelFlightClient(token).search(query)
            FlightSearchResult(
                query = query,
                offers = offers,
                providerLabel = "Duffel · live",
                configured = true,
                errorMessage = if (offers.isEmpty()) "No fares found for those dates." else null
            )
        }.getOrElse { error ->
            FlightSearchResult(
                query = query,
                offers = emptyList(),
                providerLabel = "Duffel",
                configured = true,
                errorMessage = error.message ?: "Flight search failed."
            )
        }
    }

    suspend fun searchStays(query: StaySearchQuery): StaySearchResult {
        val token = settings.getDuffelToken()
        if (token.isBlank()) {
            return StaySearchResult(
                query = query,
                offers = emptyList(),
                providerLabel = "Duffel Stays",
                configured = false,
                errorMessage = "Add a Duffel access token in settings to search stay prices."
            )
        }

        return runCatching {
            val offers = DuffelStaysClient(token).search(query)
            StaySearchResult(
                query = query,
                offers = offers,
                providerLabel = "Duffel Stays · live",
                configured = true,
                errorMessage = if (offers.isEmpty()) "No stays found near that destination." else null
            )
        }.getOrElse { error ->
            StaySearchResult(
                query = query,
                offers = emptyList(),
                providerLabel = "Duffel Stays",
                configured = true,
                errorMessage = error.message ?: "Stay search failed."
            )
        }
    }

    /**
     * Approximate nightly × nights when live Stays isn't available (product not enabled,
     * geocode missing, or API error). Still gives a usable budget line on a new trip.
     */
    fun approximateStay(
        destination: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        adults: Int,
        budget: TourBudget,
        currency: String = "€"
    ): StayOfferSummary {
        val nights = ChronoUnit.DAYS.between(checkIn, checkOut).toInt().coerceAtLeast(1)
        val nightly = when (budget) {
            TourBudget.BUDGET -> 75.0
            TourBudget.MID_RANGE -> 140.0
            TourBudget.PREMIUM -> 280.0
        }
        val rooms = ceil(adults.coerceAtLeast(1) / 2.0).toInt().coerceAtLeast(1)
        val total = nightly * nights * rooms
        return StayOfferSummary(
            id = "approx-$destination-$checkIn",
            name = "Approx. stay in ${destination.substringBefore(",")}",
            totalPrice = total,
            currency = CurrencyCodes.display(currency),
            nights = nights,
            neighbourhood = destination.substringBefore(","),
            isLivePrice = false
        )
    }

    /** Build a search from trip context when the traveller didn't specify every field. */
    fun defaultQuery(
        trip: Trip,
        originHint: String?,
        destinationHint: String?,
        departDate: LocalDate?,
        returnDate: LocalDate?,
        adults: Int
    ): FlightSearchQuery? {
        val destination = AirportCodes.resolve(destinationHint ?: trip.destination) ?: return null
        val origin = AirportCodes.resolve(originHint.orEmpty())
            ?: AirportCodes.resolve(settings.getHomeAirport())
            ?: settings.getHomeAirport()
        val today = LocalDate.now()
        val depart = (departDate ?: trip.startDate).let { date ->
            if (date.isBefore(today.plusDays(2))) today.plusDays(14) else date
        }
        val ret = returnDate?.takeIf { it.isAfter(depart) }
            ?: trip.endDate.takeIf { it.isAfter(depart) }

        return FlightSearchQuery(
            origin = origin,
            destination = destination,
            departDate = depart,
            returnDate = ret,
            adults = adults.coerceIn(1, trip.partySize.coerceAtLeast(1)),
            currency = CurrencyCodes.iso(trip.currency)
        )
    }
}
