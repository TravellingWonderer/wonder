package com.wonder.provider.ai

import com.wonder.provider.data.CityCatalog
import com.wonder.provider.model.*
import kotlin.random.Random

class LocalAiProvider : AiTourProvider {

    override val sourceLabel = "Wonder AI"

    private val timeSlots = listOf(
        "9:00 AM", "10:30 AM", "12:00 PM", "1:30 PM", "3:00 PM", "4:30 PM", "6:00 PM", "8:00 PM"
    )

    override suspend fun curateTour(request: TourBuildRequest): CuratedTour {
        val cityName = CityCatalog.resolveCity(request.city)
        val allPois = CityCatalog.getPois(request.city)

        val scored = allPois.map { poi ->
            val interestScore = if (request.interests.isEmpty() || poi.category in request.interests) 3 else 0
            val budgetScore = when {
                poi.cost <= request.budget.maxStopCost -> 2
                poi.cost <= request.budget.maxStopCost * 1.5 -> 1
                else -> -2
            }
            poi to (interestScore + budgetScore + Random.nextInt(0, 2))
        }.sortedByDescending { it.second }

        val totalStops = request.pace.stopsPerDay * request.duration.days
        val selected = scored.take(totalStops.coerceAtMost(scored.size)).map { it.first }

        val ordered = selected
            .sortedBy { it.timePreference }
            .mapIndexed { index, poi ->
                TourStop(
                    order = index + 1,
                    name = poi.name,
                    description = poi.description,
                    category = poi.category,
                    durationMinutes = adjustDuration(poi.durationMinutes, request.pace),
                    estimatedCost = poi.cost,
                    currency = "€",
                    timeSlot = timeSlots.getOrElse(index) { "Flexible" },
                    emoji = poi.emoji,
                    tip = poi.tip,
                    location = poi.location.ifBlank { "${poi.name}, ${cityName.split(",").first().trim()}" }
                )
            }

        val totalMinutes = ordered.sumOf { it.durationMinutes }
        val totalBudget = ordered.sumOf { it.estimatedCost }

        return CuratedTour(
            id = "tour_${System.currentTimeMillis()}",
            title = generateTitle(cityName, request),
            city = request.city.trim(),
            summary = generateSummary(cityName, request, ordered),
            highlights = generateHighlights(ordered),
            stops = ordered,
            totalDurationHours = totalMinutes / 60.0,
            estimatedBudget = totalBudget,
            currency = "€",
            matchScore = calculateMatchScore(request, ordered),
            days = request.duration.days
        )
    }

    private fun adjustDuration(base: Int, pace: TourPace): Int = when (pace) {
        TourPace.RELAXED -> (base * 1.2).toInt()
        TourPace.BALANCED -> base
        TourPace.ACTIVE -> (base * 0.85).toInt()
    }

    private fun calculateMatchScore(request: TourBuildRequest, stops: List<TourStop>): Int {
        if (stops.isEmpty()) return 70
        val interestMatches = stops.count { it.category in request.interests || request.interests.isEmpty() }
        val budgetMatches = stops.count { it.estimatedCost <= request.budget.maxStopCost }
        val base = 75
        val interestBonus = (interestMatches.toFloat() / stops.size * 20).toInt()
        val budgetBonus = (budgetMatches.toFloat() / stops.size * 5).toInt()
        return (base + interestBonus + budgetBonus).coerceIn(78, 99)
    }

    private fun generateTitle(city: String, request: TourBuildRequest): String {
        val vibe = when {
            request.interests.contains(TourInterest.FOOD) -> "Culinary Journey"
            request.interests.contains(TourInterest.ADVENTURE) -> "Adventure Trail"
            request.interests.contains(TourInterest.CULTURE) -> "Heritage Discovery"
            request.interests.contains(TourInterest.LOCAL) -> "Local Secrets"
            request.interests.size == 1 -> "${request.interests.first().label} Tour"
            else -> "Personalised Experience"
        }
        val durationLabel = if (request.duration.days > 1) "${request.duration.days}-Day" else request.duration.label
        return "$durationLabel $vibe in $city"
    }

    private fun generateSummary(city: String, request: TourBuildRequest, stops: List<TourStop>): String {
        val interestText = if (request.interests.isEmpty()) {
            "a well-rounded mix of experiences"
        } else {
            request.interests.joinToString(", ") { it.label.lowercase() }
        }
        val cityShort = city.split(",").first().trim()
        return "Crafted for ${request.groupSize} traveller${if (request.groupSize > 1) "s" else ""} " +
            "with a ${request.pace.label.lowercase()} pace, this ${request.duration.label.lowercase()} " +
            "itinerary in $cityShort focuses on $interestText. " +
            "I've sequenced ${stops.size} stops to flow naturally through the day" +
            if (request.duration.days > 1) "s" else "" +
            ", balancing must-sees with off-the-beaten-path moments" +
            if (request.notes.isNotBlank()) ". Special note: ${request.notes}" else "."
    }

    private fun generateHighlights(stops: List<TourStop>): List<String> =
        stops.take(3).map { "${it.emoji} ${it.name}" } +
            listOf("🗺️ Optimised route to minimise backtracking")
}
