package com.wonder.provider.ai

import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.SuggestionSimilarity
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.TripMode
import java.time.LocalDate
import kotlin.random.Random

/**
 * Picks tap-to-ask follow-ups with variety: budget, schedule, bookings, and practical needs
 * first — discovery-style prompts are occasional, never the default.
 */
class ContextualSuggestionPicker(private val trips: TripRepository) {

    fun pick(
        count: Int = 3,
        exclude: Set<String> = emptySet(),
        hints: List<String> = emptyList(),
        welcomeOnly: Boolean = false
    ): List<String> {
        val random = Random.Default
        val blocked = SuggestionSimilarity.expand(exclude)
        if (welcomeOnly) return welcomeQuestions(count, blocked, hints, random)

        val trip = trips.trip.value
        val today = LocalDate.now()
        val mode = trips.mode.value
        val budget = trips.budget()
        val unbooked = trips.unbookedEssentials()
        val emptyDays = trip.dates.filter { !it.isBefore(today) && trips.itemsOn(it).isEmpty() }
        val city = trip.destination.substringBefore(",").trim()
        val exploreDay = when {
            emptyDays.isNotEmpty() -> trip.dayNumber(emptyDays.first())
            trip.startDate.isAfter(today) -> 1
            trip.covers(today) -> trip.dayNumber(today)
            else -> 1
        }
        val (_, next) = trips.nowAndNext()

        val weighted = buildList {
            addAll(budgetQuestions(budget.isOverrun, budget.variance < 200))
            if (mode == TripMode.WANDERING) {
                addAll(wanderingQuestions(next?.title))
            } else {
                addAll(plannerQuestions(unbooked.size, emptyDays.size, exploreDay, city))
            }
            if (TourInterest.FOOD in trip.interests) addAll(foodQuestions())
            if (TourInterest.NATURE in trip.interests || TourInterest.ADVENTURE in trip.interests) {
                add(Weighted("What outdoor plans fit our schedule?", Topic.ACTIVITIES, 2))
            }
            if (TourInterest.CULTURE in trip.interests || TourInterest.ART in trip.interests) {
                add(Weighted("What cultural stops should we prioritise?", Topic.ACTIVITIES, 2))
            }
            add(Weighted("Any ideas for a free afternoon on day $exploreDay?", Topic.DISCOVERY, 1))
            add(Weighted("What should we add to day $exploreDay?", Topic.DISCOVERY, 1))
        }

        val chosen = linkedSetOf<String>()
        hints
            .map { it.trim() }
            .filter { it.isNotBlank() && !SuggestionSimilarity.matchesAny(it, blocked) }
            .shuffled(random)
            .take(1)
            .forEach { chosen.add(it) }

        val byTopic = weighted
            .filter { candidate ->
                !SuggestionSimilarity.matchesAny(candidate.text, blocked) &&
                    candidate.text !in chosen
            }
            .groupBy { it.topic }
            .mapValues { (_, entries) -> entries.shuffled(random) }

        val topicOrder = weighted
            .groupBy { it.topic }
            .mapValues { (_, entries) -> entries.maxOf { it.weight } }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .shuffled(random)

        var discoveryUsed = chosen.any { isDiscoveryQuestion(it) }
        for (topic in topicOrder) {
            if (chosen.size >= count) break
            if (topic == Topic.DISCOVERY && discoveryUsed) continue
            val candidate = byTopic[topic]?.firstOrNull { it.text !in chosen } ?: continue
            chosen.add(candidate.text)
            if (topic == Topic.DISCOVERY) discoveryUsed = true
        }

        if (chosen.size < count) {
            weighted
                .shuffled(random)
                .map { it.text }
                .filter { text ->
                    !SuggestionSimilarity.matchesAny(text, blocked) &&
                        text !in chosen &&
                        !(isDiscoveryQuestion(text) && discoveryUsed)
                }
                .forEach { text ->
                    if (chosen.size >= count) return@forEach
                    chosen.add(text)
                    if (isDiscoveryQuestion(text)) discoveryUsed = true
                }
        }

        return chosen.take(count).shuffled(random).toList()
    }

    /** Three varied questions per persona slot for expandable avatar panels. */
    fun pickPanelSet(
        personaCount: Int = com.wonder.provider.model.PersonaPanel.DEFAULT_PERSONA_COUNT,
        exclude: Set<String> = emptySet(),
        hints: List<String> = emptyList(),
        welcomeOnly: Boolean = false
    ): List<List<String>> {
        val used = exclude.toMutableSet()
        return List(personaCount) { index ->
            pick(
                count = com.wonder.provider.model.PersonaPanel.QUESTIONS_PER_PERSONA,
                exclude = used,
                hints = if (index == 0) hints else emptyList(),
                welcomeOnly = welcomeOnly && index == 0
            ).also { used.addAll(it) }
        }
    }

    private fun welcomeQuestions(
        count: Int,
        exclude: Set<String>,
        hints: List<String>,
        random: Random
    ): List<String> {
        val pool = listOf(
            "Help me shape this trip",
            "What can you help with?",
            "Where should we start planning?",
            "Show me the whole trip",
            "How's our budget looking?",
            "What's still unbooked?",
            "Help me draft day 1"
        )
        return mergeHints(hints, pool, count, exclude, random)
    }

    private fun budgetQuestions(tight: Boolean, veryTight: Boolean): List<Weighted> = listOf(
        Weighted("How's our budget?", Topic.BUDGET, if (tight) 5 else 3),
        Weighted("How much have we spent?", Topic.BUDGET, if (veryTight) 4 else 2),
        Weighted("Show me the expense tracker", Topic.BUDGET, if (tight) 3 else 1),
        Weighted("Are we still on track budget-wise?", Topic.BUDGET, if (tight) 4 else 2)
    )

    private fun wanderingQuestions(nextTitle: String?): List<Weighted> = buildList {
        add(Weighted("What's next?", Topic.SCHEDULE, 5))
        add(Weighted("What's on tomorrow?", Topic.SCHEDULE, 4))
        add(Weighted("What's free nearby?", Topic.NEARBY, 4))
        add(Weighted("How much have we spent?", Topic.BUDGET, 3))
        if (nextTitle != null) {
            add(Weighted("How much time before $nextTitle?", Topic.SCHEDULE, 3))
        }
    }

    private fun plannerQuestions(
        unbookedCount: Int,
        emptyDayCount: Int,
        exploreDay: Int,
        city: String
    ): List<Weighted> = buildList {
        add(Weighted("Show me the whole trip", Topic.PLANNING, 4))
        add(Weighted("Show me the full plan", Topic.PLANNING, 3))
        add(Weighted("What's still unbooked?", Topic.BOOKINGS, if (unbookedCount > 0) 5 else 2))
        add(Weighted("Which days are empty?", Topic.PLANNING, if (emptyDayCount > 0) 5 else 2))
        add(Weighted("Help me draft a relaxed day", Topic.PLANNING, 3))
        add(Weighted("Help me plan day $exploreDay", Topic.PLANNING, if (emptyDayCount > 0) 4 else 2))
        add(Weighted("How's our budget looking?", Topic.BUDGET, 3))
        add(Weighted("What should we lock in first?", Topic.BOOKINGS, if (unbookedCount > 0) 4 else 1))
        if (city.isNotBlank()) {
            add(Weighted("What's worth doing in $city we haven't planned?", Topic.DISCOVERY, 1))
        }
    }

    private fun foodQuestions(): List<Weighted> = listOf(
        Weighted("Where should we eat tonight?", Topic.FOOD, 3),
        Weighted("What's good for lunch near our plans?", Topic.FOOD, 2)
    )

    private fun mergeHints(
        hints: List<String>,
        pool: List<String>,
        count: Int,
        exclude: Set<String>,
        random: Random
    ): List<String> {
        val chosen = linkedSetOf<String>()
        hints
            .map { it.trim() }
            .filter { it.isNotBlank() && !SuggestionSimilarity.matchesAny(it, exclude) }
            .shuffled(random)
            .take(1)
            .forEach { chosen.add(it) }
        pool
            .shuffled(random)
            .filter { !SuggestionSimilarity.matchesAny(it, exclude) && it !in chosen }
            .forEach { text ->
                if (chosen.size >= count) return@forEach
                chosen.add(text)
            }
        return chosen.take(count).shuffled(random).toList()
    }

    private fun isDiscoveryQuestion(text: String): Boolean =
        discoveryPhrases.any { phrase -> phrase in text.lowercase() }

    private enum class Topic {
        BUDGET, SCHEDULE, BOOKINGS, PLANNING, FOOD, NEARBY, ACTIVITIES, DISCOVERY
    }

    private data class Weighted(val text: String, val topic: Topic, val weight: Int)

    private companion object {
        val discoveryPhrases = listOf(
            "hidden",
            "off the beaten",
            "secret",
            "locals only",
            "undiscovered",
            "haven't planned"
        )
    }
}
