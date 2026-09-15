package com.wonder.provider.data.travel

import com.wonder.provider.model.FlightSearchQuery
import com.wonder.provider.model.FlightSearchResult
import com.wonder.provider.model.Trip
import java.time.LocalDate

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
        val origin = AirportCodes.resolve(originHint.orEmpty()) ?: "LHR"
        val depart = departDate?.takeIf { !it.isBefore(trip.startDate.minusDays(21)) }
            ?: trip.startDate
        val ret = returnDate?.takeIf { it.isAfter(depart) } ?: trip.endDate.takeIf { it.isAfter(depart) }

        return FlightSearchQuery(
            origin = origin,
            destination = destination,
            departDate = depart,
            returnDate = ret,
            adults = adults.coerceIn(1, trip.partySize.coerceAtLeast(1)),
            currency = trip.currency
        )
    }
}
