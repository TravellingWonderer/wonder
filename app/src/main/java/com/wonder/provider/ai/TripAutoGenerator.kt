package com.wonder.provider.ai

import com.wonder.provider.data.CityCatalog
import com.wonder.provider.data.TripRepository
import com.wonder.provider.data.toItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.TourBuildRequest
import com.wonder.provider.model.TourDuration
import com.wonder.provider.model.TourInterest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TripAutoGenerator(
    private val trips: TripRepository,
    private val tourService: AiTourService
) {

    suspend fun generateFromVibes(
        vibes: String,
        tripTitle: String = "",
        place: String = ""
    ) = withContext(Dispatchers.IO) {
        val notes = vibes.trim().ifBlank { tripTitle.trim() }
        if (notes.isBlank()) return@withContext

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

        val everyone = trip.travellers.map { it.id }.toSet()
        val slotFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

        trips.trip.value.dates.forEach { date ->
            val request = TourBuildRequest(
                city = resolvedCity,
                interests = interests,
                pace = parsed.pace,
                duration = TourDuration.FULL_DAY,
                budget = parsed.budget,
                groupSize = trip.partySize,
                notes = notes
            )
            val tour = runCatching { tourService.curateTour(request).tour }
                .getOrElse { LocalAiProvider().curateTour(request) }

            var fallbackTime = LocalTime.of(9, 0)
            tour.stops.forEachIndexed { index, stop ->
                val parsedTime = runCatching {
                    LocalTime.parse(stop.timeSlot.uppercase(Locale.ENGLISH), slotFormat)
                }.getOrNull()
                val start = parsedTime ?: fallbackTime
                fallbackTime = start.plusMinutes((stop.durationMinutes + 30).toLong())

                trips.upsertItem(
                    ItineraryItem(
                        id = "${trips.newItemId()}-$index",
                        date = date,
                        title = stop.name,
                        kind = stop.category.toItemKind(),
                        startTime = start,
                        durationMinutes = stop.durationMinutes,
                        location = resolvedCity.substringBefore(","),
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
}
