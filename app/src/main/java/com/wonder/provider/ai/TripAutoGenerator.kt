package com.wonder.provider.ai

import com.wonder.provider.data.CityCatalog
import com.wonder.provider.data.TripRepository
import com.wonder.provider.data.maps.CityCoordinates
import com.wonder.provider.data.maps.TripGeocoder
import com.wonder.provider.data.toItemKind
import com.wonder.provider.data.travel.TravelSearchRepository
import com.wonder.provider.model.FlightOfferSummary
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.StayOfferSummary
import com.wonder.provider.model.StaySearchQuery
import com.wonder.provider.model.TourBuildRequest
import com.wonder.provider.model.TourBudget
import com.wonder.provider.model.TourDuration
import com.wonder.provider.model.TourInterest
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Turns vibes + confirmed dates into a usable trip: day plans, live Duffel flights,
 * and stay prices (live when Stays is available, approximate otherwise).
 */
class TripAutoGenerator(
    private val trips: TripRepository,
    private val tourService: AiTourService,
    private val travelSearch: TravelSearchRepository,
    private val geocoder: TripGeocoder
) {

    suspend fun generateFromVibes(
        vibes: String,
        tripTitle: String = "",
        place: String = ""
    ) = withContext(Dispatchers.IO) {
        val notes = vibes.trim().ifBlank { tripTitle.trim() }
        if (notes.isBlank()) return@withContext
        if (!trips.trip.value.datesConfirmed) return@withContext

        val parsed = VibesParser.parse(notes)
        val trip = trips.trip.value
        val interests = parsed.interests.ifEmpty {
            trip.interests.ifEmpty { setOf(TourInterest.LOCAL, TourInterest.FOOD) }
        }

        val resolvedCity = place.trim().takeIf { it.isNotBlank() }
            ?: trip.destination.takeUnless {
                it.equals(TripRepository.DEFAULT_DESTINATION, ignoreCase = true)
            }
            ?: parsed.city
            ?: CityCatalog.knownCities().random()

        val everyone = trips.trip.value.travellers.map { it.id }.toSet()
        val adults = everyone.size.coerceAtLeast(1)

        coroutineScope {
            val travelJob = async {
                addTravelBones(
                    city = resolvedCity,
                    budget = parsed.budget,
                    adults = adults,
                    everyone = everyone
                )
            }
            val daysJob = async {
                fillDayPlans(
                    notes = notes,
                    city = resolvedCity,
                    interests = interests,
                    pace = parsed.pace,
                    budget = parsed.budget,
                    everyone = everyone
                )
            }
            travelJob.await()
            daysJob.await()
        }
    }

    private suspend fun addTravelBones(
        city: String,
        budget: TourBudget,
        adults: Int,
        everyone: Set<String>
    ) {
        val trip = trips.trip.value
        val flightQuery = travelSearch.defaultQuery(
            trip = trip,
            originHint = null,
            destinationHint = city,
            departDate = trip.startDate,
            returnDate = trip.endDate.takeIf { it.isAfter(trip.startDate) },
            adults = adults
        )?.let { query ->
            val safeDepart = ensureSearchableDate(query.departDate)
            val nights = query.returnDate?.let {
                ChronoUnit.DAYS.between(query.departDate, it).coerceAtLeast(1)
            }
            query.copy(
                departDate = safeDepart,
                returnDate = nights?.let { safeDepart.plusDays(it) }
            )
        }

        val coords = runCatching {
            CityCoordinates.forDestination(city)
                ?: geocoder.resolve(city, city)
        }.getOrNull()

        val stayQuery = if (coords != null && trip.endDate.isAfter(trip.startDate)) {
            StaySearchQuery(
                destination = city,
                latitude = coords.latitude,
                longitude = coords.longitude,
                checkIn = trip.startDate,
                checkOut = trip.endDate,
                adults = adults,
                rooms = ceil(adults / 2.0).toInt().coerceAtLeast(1)
            )
        } else {
            null
        }

        coroutineScope {
            val flightDeferred = async {
                flightQuery?.let { travelSearch.searchFlights(it) }
            }
            val stayDeferred = async {
                stayQuery?.let { travelSearch.searchStays(it) }
            }

            val flightResult = flightDeferred.await()
            val bestFlight = flightResult?.offers?.firstOrNull()
            if (bestFlight != null) {
                upsertFlights(bestFlight, trip.startDate, trip.endDate, everyone)
            }

            val stayResult = stayDeferred.await()
            val bestStay = stayResult?.offers?.firstOrNull()
                ?: travelSearch.approximateStay(
                    destination = city,
                    checkIn = trip.startDate,
                    checkOut = trip.endDate.takeIf { it.isAfter(trip.startDate) }
                        ?: trip.startDate.plusDays(2),
                    adults = adults,
                    budget = budget,
                    currency = trip.currency
                )
            upsertStay(bestStay, trip.startDate, everyone)
        }
    }

    private fun upsertFlights(
        offer: FlightOfferSummary,
        start: LocalDate,
        end: LocalDate,
        everyone: Set<String>
    ) {
        val outboundNotes = buildString {
            append(offer.airline)
            append(" · ")
            append(offer.departLabel)
            append("–")
            append(offer.arriveLabel)
            if (offer.durationLabel.isNotBlank()) append(" · ${offer.durationLabel}")
            append(if (offer.stops == 0) " · direct" else " · ${offer.stops} stop${if (offer.stops == 1) "" else "s"}")
            append(if (offer.isLivePrice) " · live Duffel quote" else "")
        }
        trips.upsertItem(
            ItineraryItem(
                id = trips.newItemId(),
                date = start,
                title = "Flight ${offer.origin} → ${offer.destination}",
                kind = ItemKind.FLIGHT,
                startTime = LocalTime.of(8, 0),
                durationMinutes = parseDurationMinutes(offer.durationLabel) ?: 180,
                location = "${offer.origin} → ${offer.destination}",
                notes = outboundNotes,
                estimatedCost = offer.price,
                costIsPerPerson = false,
                status = ItemStatus.PLANNED,
                travellerIds = everyone
            )
        )

        if (offer.isRoundTrip && end.isAfter(start)) {
            val retOrigin = offer.returnOrigin ?: offer.destination
            val retDest = offer.returnDestination ?: offer.origin
            trips.upsertItem(
                ItineraryItem(
                    id = trips.newItemId(),
                    date = end,
                    title = "Flight $retOrigin → $retDest",
                    kind = ItemKind.FLIGHT,
                    startTime = LocalTime.of(16, 0),
                    durationMinutes = 180,
                    location = "$retOrigin → $retDest",
                    notes = buildString {
                        append("Return · fare included in outbound")
                        offer.returnDepartLabel?.let { append(" · departs $it") }
                        offer.returnArriveLabel?.let { append("–$it") }
                    },
                    estimatedCost = 0.0,
                    costIsPerPerson = false,
                    status = ItemStatus.PLANNED,
                    travellerIds = everyone
                )
            )
        }
    }

    private fun upsertStay(stay: StayOfferSummary, checkIn: LocalDate, everyone: Set<String>) {
        val nightly = String.format(Locale.ENGLISH, "%.0f", stay.nightlyApprox)
        val notes = buildString {
            append("~")
            append(stay.currency)
            append(nightly)
            append("/night × ")
            append(stay.nights)
            append(" nights")
            if (stay.neighbourhood.isNotBlank()) append(" · ${stay.neighbourhood}")
            append(if (stay.isLivePrice) " · live Duffel Stays" else " · approximate")
            stay.rating?.let { append(" · rated ${"%.1f".format(Locale.ENGLISH, it)}") }
        }
        trips.upsertItem(
            ItineraryItem(
                id = trips.newItemId(),
                date = checkIn,
                title = stay.name,
                kind = ItemKind.STAY,
                startTime = LocalTime.of(15, 0),
                durationMinutes = 60,
                location = stay.neighbourhood.ifBlank { stay.name },
                notes = notes,
                estimatedCost = stay.totalPrice,
                costIsPerPerson = false,
                status = ItemStatus.PLANNED,
                travellerIds = everyone
            )
        )
    }

    private suspend fun fillDayPlans(
        notes: String,
        city: String,
        interests: Set<TourInterest>,
        pace: com.wonder.provider.model.TourPace,
        budget: TourBudget,
        everyone: Set<String>
    ) {
        val trip = trips.trip.value
        val slotFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        // Keep arrival / departure lighter — fill middle days fully, edges with a shorter ask.
        trip.dates.forEachIndexed { index, date ->
            val isEdge = index == 0 || index == trip.dates.lastIndex
            val request = TourBuildRequest(
                city = city,
                interests = interests,
                pace = pace,
                duration = if (isEdge) TourDuration.HALF_DAY else TourDuration.FULL_DAY,
                budget = budget,
                groupSize = trip.partySize,
                notes = notes
            )
            val tour = runCatching { tourService.curateTour(request).tour }
                .getOrElse { LocalAiProvider().curateTour(request) }

            var fallbackTime = if (isEdge && index == 0) LocalTime.of(14, 0) else LocalTime.of(9, 30)
            val stops = if (isEdge) tour.stops.take(2) else tour.stops
            stops.forEachIndexed { stopIndex, stop ->
                val parsedTime = runCatching {
                    LocalTime.parse(stop.timeSlot.uppercase(Locale.ENGLISH), slotFormat)
                }.getOrNull()
                val start = parsedTime ?: fallbackTime
                fallbackTime = start.plusMinutes((stop.durationMinutes + 30).toLong())

                trips.upsertItem(
                    ItineraryItem(
                        id = "${trips.newItemId()}-$stopIndex",
                        date = date,
                        title = stop.name,
                        kind = stop.category.toItemKind(),
                        startTime = start,
                        durationMinutes = stop.durationMinutes,
                        location = city.substringBefore(","),
                        notes = stop.tip,
                        estimatedCost = stop.estimatedCost.toDouble(),
                        costIsPerPerson = true,
                        status = ItemStatus.PLANNED,
                        travellerIds = everyone
                    )
                )
            }
        }
    }

    private fun ensureSearchableDate(date: LocalDate): LocalDate {
        val min = LocalDate.now().plusDays(2)
        return if (date.isBefore(min)) min else date
    }

    private fun parseDurationMinutes(label: String): Int? {
        if (label.isBlank()) return null
        val hours = Regex("(\\d+)h").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val mins = Regex("(\\d+)m").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + mins
        return total.takeIf { it > 0 }
    }
}
