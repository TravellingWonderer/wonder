package com.wonder.provider.ai

import com.wonder.provider.data.DestinationInference
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
import com.wonder.provider.model.TourStop
import com.wonder.provider.model.Trip
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
        targetTripId: String? = null,
        vibes: String = "",
        tripTitle: String = "",
        place: String = "",
        onProgress: suspend (step: Int, message: String) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val tripId = targetTripId ?: trips.trip.value.id
        val tripSnapshot = trips.loadTripSnapshot(tripId)?.trip ?: trips.trip.value
        val notes = vibes.trim().ifBlank { tripTitle.trim().ifBlank { tripSnapshot.title.trim() } }

        onProgress(0, "Analyzing travel vibes & destination profile...")
        val parsed = if (notes.isNotBlank()) VibesParser.parse(notes) else VibesParser.parse("")
        val interests = parsed.interests.ifEmpty {
            tripSnapshot.interests.ifEmpty { setOf(TourInterest.LOCAL, TourInterest.FOOD) }
        }

        val titleForPlace = tripTitle.ifBlank { tripSnapshot.title }
        val inferred = listOfNotNull(
            place.trim().takeIf { DestinationInference.isGrounded(it) },
            tripSnapshot.destination.takeIf { DestinationInference.isGrounded(it) },
            DestinationInference.fromTitle(titleForPlace),
            DestinationInference.fromText(notes),
            parsed.city?.takeIf { DestinationInference.isGrounded(it) }
        ).firstOrNull()
        val resolvedCity = inferred
            ?: DestinationInference.geocodeCandidate(titleForPlace, notes)?.let { geocoder.verifySettlement(it) }

        if (resolvedCity == null) {
            if (TripRepository.hasDecidedDestination(tripSnapshot.destination) &&
                !DestinationInference.isGrounded(tripSnapshot.destination)
            ) {
                trips.updateTripDestinationDirect(tripId, TripRepository.DEFAULT_DESTINATION)
            }
            onProgress(5, "Trip saved. Add a real destination when you know where you're going.")
            return@withContext
        }

        trips.updateTripDestinationDirect(tripId, resolvedCity)

        val everyone = tripSnapshot.travellers.map { it.id }.toSet().ifEmpty { setOf("t1") }
        val adults = everyone.size.coerceAtLeast(1)

        onProgress(1, "Searching live flights & scouting curated base stay...")
        val stayInfo = addTravelBones(
            targetTripId = tripId,
            trip = tripSnapshot,
            city = resolvedCity,
            budget = parsed.budget,
            adults = adults,
            everyone = everyone
        )

        onProgress(2, "Curating daily activities & hidden local gems...")
        onProgress(3, "Connecting public & private transit to/from ${stayInfo.first}...")
        fillDayPlans(
            targetTripId = tripId,
            trip = tripSnapshot,
            notes = notes,
            city = resolvedCity,
            interests = interests,
            pace = parsed.pace,
            budget = parsed.budget,
            everyone = everyone,
            stayName = stayInfo.first,
            stayLocation = stayInfo.second
        )

        onProgress(4, "Persisting curated trip & transit routes to offline database...")
        kotlinx.coroutines.delay(400)
        onProgress(5, "Curated trip ready! Opening your itinerary...")
    }

    private suspend fun addTravelBones(
        targetTripId: String,
        trip: Trip,
        city: String,
        budget: TourBudget,
        adults: Int,
        everyone: Set<String>
    ): Pair<String, String> {
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

        return coroutineScope {
            val flightDeferred = async {
                flightQuery?.let { travelSearch.searchFlights(it) }
            }
            val stayDeferred = async {
                stayQuery?.let { travelSearch.searchStays(it) }
            }

            val flightResult = flightDeferred.await()
            val bestFlight = flightResult?.offers?.firstOrNull()
            if (bestFlight != null) {
                upsertFlights(targetTripId, bestFlight, trip.startDate, trip.endDate, everyone)
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
            upsertStay(targetTripId, bestStay, trip.startDate, everyone)
            val stayLoc = bestStay.neighbourhood.ifBlank { "${bestStay.name}, ${city.substringBefore(',')}" }
            Pair(bestStay.name, stayLoc)
        }
    }

    private suspend fun upsertFlights(
        targetTripId: String,
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
        trips.upsertItemDirect(
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
            ),
            targetTripId
        )

        if (offer.isRoundTrip && end.isAfter(start)) {
            val retOrigin = offer.returnOrigin ?: offer.destination
            val retDest = offer.returnDestination ?: offer.origin
            trips.upsertItemDirect(
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
                ),
                targetTripId
            )
        }
    }

    private suspend fun upsertStay(
        targetTripId: String,
        stay: StayOfferSummary,
        checkIn: LocalDate,
        everyone: Set<String>
    ) {
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
        trips.upsertItemDirect(
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
            ),
            targetTripId
        )
    }

    private suspend fun fillDayPlans(
        targetTripId: String,
        trip: Trip,
        notes: String,
        city: String,
        interests: Set<TourInterest>,
        pace: com.wonder.provider.model.TourPace,
        budget: TourBudget,
        everyone: Set<String>,
        stayName: String,
        stayLocation: String
    ) {
        val slotFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        // Keep arrival / departure lighter — fill middle days fully, edges with a shorter ask.
        for ((index, date) in trip.dates.withIndex()) {
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
            val rawStops = if (isEdge) tour.stops.take(2) else tour.stops
            if (rawStops.isEmpty()) continue

            data class ScheduledStop(
                val stop: TourStop,
                val start: LocalTime,
                val location: String
            )

            val scheduled = rawStops.mapIndexed { _, stop ->
                val parsedTime = runCatching {
                    LocalTime.parse(stop.timeSlot.uppercase(Locale.ENGLISH), slotFormat)
                }.getOrNull()
                val start = parsedTime ?: fallbackTime
                fallbackTime = start.plusMinutes((stop.durationMinutes + 35).toLong())
                val stopLoc = stop.location.ifBlank { "${stop.name}, ${city.substringBefore(',')}" }
                ScheduledStop(stop, start, stopLoc)
            }

            // 1. Morning transit: from Main Stay to First Activity
            val first = scheduled.first()
            val morningTransitStart = first.start.minusMinutes(25)
            trips.upsertItemDirect(
                ItineraryItem(
                    id = "${trips.newItemId()}-transit-depart-$index",
                    date = date,
                    title = "Transit to ${first.stop.name}",
                    kind = ItemKind.TRANSPORT,
                    startTime = morningTransitStart,
                    durationMinutes = 20,
                    location = "$stayLocation → ${first.location}",
                    notes = "Public transit: Metro / Bus ~20m (~2.50 ${trip.currency}) · Private: Taxi / Rideshare ~10m (~8.00 ${trip.currency}) from $stayName",
                    estimatedCost = 2.50,
                    costIsPerPerson = true,
                    status = ItemStatus.PLANNED,
                    travellerIds = everyone
                ),
                targetTripId
            )

            // 2. Add each activity, and intermediate transit between consecutive activities
            scheduled.forEachIndexed { i, current ->
                trips.upsertItemDirect(
                    ItineraryItem(
                        id = "${trips.newItemId()}-act-$index-$i",
                        date = date,
                        title = current.stop.name,
                        kind = current.stop.category.toItemKind(),
                        startTime = current.start,
                        durationMinutes = current.stop.durationMinutes,
                        location = current.location,
                        notes = current.stop.tip,
                        estimatedCost = current.stop.estimatedCost.toDouble(),
                        costIsPerPerson = true,
                        status = ItemStatus.PLANNED,
                        travellerIds = everyone
                    ),
                    targetTripId
                )

                val next = scheduled.getOrNull(i + 1)
                if (next != null) {
                    val transitStartTime = current.start.plusMinutes(current.stop.durationMinutes.toLong())
                    trips.upsertItemDirect(
                        ItineraryItem(
                            id = "${trips.newItemId()}-transit-hop-$index-$i",
                            date = date,
                            title = "Transit: ${current.stop.name} → ${next.stop.name}",
                            kind = ItemKind.TRANSPORT,
                            startTime = transitStartTime,
                            durationMinutes = 15,
                            location = "${current.location} → ${next.location}",
                            notes = "Public transit: Walk or Tram / Metro ~15m (~2.00 ${trip.currency}) · Private: Taxi / Rideshare ~8m (~6.00 ${trip.currency})",
                            estimatedCost = 2.00,
                            costIsPerPerson = true,
                            status = ItemStatus.PLANNED,
                            travellerIds = everyone
                        ),
                        targetTripId
                    )
                }
            }

            // 3. Evening transit: from Last Activity back to Main Stay
            val last = scheduled.last()
            val eveningTransitStart = last.start.plusMinutes(last.stop.durationMinutes.toLong())
            trips.upsertItemDirect(
                ItineraryItem(
                    id = "${trips.newItemId()}-transit-return-$index",
                    date = date,
                    title = "Transit back to $stayName",
                    kind = ItemKind.TRANSPORT,
                    startTime = eveningTransitStart,
                    durationMinutes = 20,
                    location = "${last.location} → $stayLocation",
                    notes = "Return to base stay · Public transit: Metro / Bus ~20m (~2.50 ${trip.currency}) · Private: Taxi / Uber ~12m (~10.00 ${trip.currency})",
                    estimatedCost = 2.50,
                    costIsPerPerson = true,
                    status = ItemStatus.PLANNED,
                    travellerIds = everyone
                ),
                targetTripId
            )
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
