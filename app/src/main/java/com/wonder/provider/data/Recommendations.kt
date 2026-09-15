package com.wonder.provider.data

import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.Recommendation
import com.wonder.provider.model.TourInterest
import java.time.LocalDate
import java.time.LocalTime

/**
 * Suggestions for the trip as it actually stands: what's near, what fits the gaps in the day,
 * what the group already said they like, and what the budget can still absorb.
 */
class RecommendationEngine(private val repository: TripRepository) {

    fun suggest(
        limit: Int = 3,
        kind: ItemKind? = null,
        featuredTitles: List<String> = emptyList(),
        date: LocalDate = LocalDate.now(),
        at: LocalTime = LocalTime.now()
    ): List<Recommendation> {
        val trip = repository.trip.value
        val pois = CityCatalog.getPois(trip.destination)
        val targetDate = if (trip.covers(date)) date else trip.startDate
        val windows = repository.freeWindows(targetDate)
        val longestWindow = windows.maxByOrNull { it.minutes }

        if (featuredTitles.isNotEmpty()) {
            val featured = featuredTitles.mapNotNull { title -> matchPoi(pois, title) }.distinctBy { it.name }
            if (featured.isNotEmpty()) {
                return featured.take(limit).map { poi ->
                    toRecommendation(poi, targetDate, windows, longestWindow)
                }
            }
            return featuredTitles.take(limit).map { title ->
                fallbackRecommendation(title, kind, targetDate, windows)
            }
        }

        val alreadyPlanned = repository.items.value.map { it.title.lowercase() }
        val headroom = repository.budget().variance
        val party = trip.partySize

        val slot = when {
            at.hour < 12 -> 0
            at.hour < 18 -> 1
            else -> 2
        }

        return pois
            .asSequence()
            .filter { poi -> alreadyPlanned.none { it.contains(poi.name.lowercase().take(12)) } }
            .filter { poi -> kind == null || poi.category.toItemKind() == kind }
            .filter { poi -> longestWindow == null || poi.durationMinutes <= longestWindow.minutes + 30 }
            .map { poi -> poi to score(poi, trip.interests, headroom, party, slot) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (poi, _) -> toRecommendation(poi, targetDate, windows, longestWindow) }
            .toList()
    }

    private fun matchPoi(pois: List<PointOfInterest>, title: String): PointOfInterest? {
        val normalized = title.lowercase()
        return pois.firstOrNull { it.name.equals(title, ignoreCase = true) }
            ?: pois.firstOrNull { normalized.contains(it.name.lowercase()) || it.name.lowercase().contains(normalized) }
    }

    private fun toRecommendation(
        poi: PointOfInterest,
        targetDate: LocalDate,
        windows: List<com.wonder.provider.model.FreeWindow>,
        longestWindow: com.wonder.provider.model.FreeWindow?
    ): Recommendation {
        val trip = repository.trip.value
        val headroom = repository.budget().variance
        val party = trip.partySize
        val window = windows.firstOrNull { poi.durationMinutes <= it.minutes }
        return Recommendation(
            id = "r-${poi.name.hashCode()}",
            title = poi.name,
            why = reason(poi, trip.interests, headroom, party),
            emoji = poi.emoji,
            kind = poi.category.toItemKind(),
            estimatedCost = poi.cost.toDouble(),
            durationMinutes = poi.durationMinutes,
            tip = poi.tip,
            suggestedDate = window?.date ?: targetDate,
            suggestedTime = window?.start
        )
    }

    private fun fallbackRecommendation(
        title: String,
        kind: ItemKind?,
        targetDate: LocalDate,
        windows: List<com.wonder.provider.model.FreeWindow>
    ): Recommendation {
        val window = windows.firstOrNull()
        return Recommendation(
            id = "r-${title.hashCode()}",
            title = title,
            why = "Suggested in our chat",
            emoji = kind?.emoji ?: "📍",
            kind = kind ?: ItemKind.ACTIVITY,
            estimatedCost = 0.0,
            durationMinutes = 90,
            tip = "",
            suggestedDate = window?.date ?: targetDate,
            suggestedTime = window?.start
        )
    }

    private fun score(
        poi: PointOfInterest,
        interests: Set<TourInterest>,
        headroom: Double,
        party: Int,
        slot: Int
    ): Int {
        var score = 0
        if (poi.category in interests) score += 4
        if (poi.timePreference == slot) score += 3

        val groupCost = poi.cost * party
        score += when {
            groupCost == 0 -> 3
            headroom <= 0 && groupCost > 30 -> -4
            headroom > groupCost * 3 -> 2
            headroom > groupCost -> 1
            else -> -2
        }

        if (poi.durationMinutes in 45..150) score += 1
        return score
    }

    private fun reason(
        poi: PointOfInterest,
        interests: Set<TourInterest>,
        headroom: Double,
        party: Int
    ): String {
        val groupCost = poi.cost * party
        return when {
            poi.cost == 0 && headroom < 200 -> "Free, and the budget could use that right now"
            poi.category in interests && poi.cost == 0 -> "Your kind of thing, and it costs nothing"
            poi.category in interests -> "Matches what you came here for"
            headroom > groupCost * 4 -> "Well within what's left of the budget"
            groupCost == 0 -> "Costs nothing but the walk"
            else -> "Fits the gap in your day"
        }
    }
}

internal fun TourInterest.toItemKind(): ItemKind = when (this) {
    TourInterest.FOOD -> ItemKind.FOOD
    TourInterest.CULTURE, TourInterest.ART -> ItemKind.ACTIVITY
    TourInterest.ADVENTURE, TourInterest.NATURE -> ItemKind.OUTDOORS
    TourInterest.NIGHTLIFE -> ItemKind.NIGHTLIFE
    TourInterest.SHOPPING -> ItemKind.SHOPPING
    TourInterest.LOCAL -> ItemKind.ACTIVITY
}
