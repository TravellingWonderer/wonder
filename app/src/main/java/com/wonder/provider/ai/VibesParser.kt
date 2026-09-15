package com.wonder.provider.ai

import com.wonder.provider.data.CityCatalog
import com.wonder.provider.model.TourBudget
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.TourPace

data class ParsedVibes(
    val city: String?,
    val interests: Set<TourInterest>,
    val pace: TourPace,
    val budget: TourBudget
)

object VibesParser {

    fun parse(input: String): ParsedVibes {
        val text = input.trim()
        return ParsedVibes(
            city = detectCity(text),
            interests = detectInterests(text),
            pace = detectPace(text),
            budget = detectBudget(text)
        )
    }

    private fun detectCity(input: String): String? {
        val lower = input.lowercase()
        CityCatalog.knownCities().firstOrNull { lower.contains(it.substringBefore(",").lowercase()) }
            ?.let { return it }
        val match = Regex("\\b(?:in|to|around|at)\\s+([A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)?)").find(input)
        return match?.groupValues?.get(1)?.takeIf { it.length > 2 }
    }

    private fun detectInterests(input: String): Set<TourInterest> {
        val lower = input.lowercase()
        return TourInterest.entries.filter { interest ->
            INTEREST_WORDS[interest]?.any { lower.contains(it) } == true
        }.toSet()
    }

    private fun detectPace(input: String): TourPace {
        val lower = input.lowercase()
        return when {
            listOf("relaxed", "slow", "easy", "gentle", "chill", "lazy").any { lower.contains(it) } -> TourPace.RELAXED
            listOf("packed", "active", "fast", "busy", "as much as").any { lower.contains(it) } -> TourPace.ACTIVE
            else -> TourPace.BALANCED
        }
    }

    private fun detectBudget(input: String): TourBudget {
        val lower = input.lowercase()
        return when {
            listOf("cheap", "budget", "affordable", "free", "save", "tight").any { lower.contains(it) } -> TourBudget.BUDGET
            listOf("premium", "luxury", "splurge", "special", "treat").any { lower.contains(it) } -> TourBudget.PREMIUM
            else -> TourBudget.MID_RANGE
        }
    }

    private val INTEREST_WORDS = mapOf(
        TourInterest.FOOD to listOf("food", "eat", "culinary", "restaurant", "tasting", "wine", "foodie"),
        TourInterest.CULTURE to listOf("culture", "history", "historic", "heritage", "museum", "monument"),
        TourInterest.ADVENTURE to listOf("adventure", "hike", "kayak", "outdoor", "adrenaline", "surf"),
        TourInterest.NATURE to listOf("nature", "park", "garden", "coast", "beach", "scenic", "view"),
        TourInterest.NIGHTLIFE to listOf("nightlife", "bar", "club", "cocktail", "party"),
        TourInterest.SHOPPING to listOf("shopping", "shop", "market", "boutique", "vintage"),
        TourInterest.ART to listOf("art", "gallery", "design", "street art", "architecture"),
        TourInterest.LOCAL to listOf("local", "hidden", "off the beaten", "authentic", "secret", "like a local")
    )
}
