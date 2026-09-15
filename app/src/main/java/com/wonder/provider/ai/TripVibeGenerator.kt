package com.wonder.provider.ai

import com.wonder.provider.data.CityCatalog
import com.wonder.provider.data.DestinationInference
import com.wonder.provider.model.TripWhenMode
import com.wonder.provider.model.TripWhenPlan
import java.time.LocalDate
import java.time.Month
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns a trip name into a real travel-advisor vibes brief — unpacking the meaning
 * hidden in the words, never pasting the title into a template sentence.
 */
class TripVibeGenerator(
    private val settingsRepository: AiSettingsRepository
) {

    suspend fun generate(title: String, whenPlan: TripWhenPlan): String = withContext(Dispatchers.IO) {
        val name = title.trim()
        if (name.length < 2) return@withContext ""

        val provider = settingsRepository.chatProvider()
        if (provider != null) {
            val ai = runCatching {
                withTimeoutOrNull(AI_TIMEOUT_MS) {
                    provider.converse(
                        systemPrompt = SYSTEM,
                        turns = listOf(
                            LlmTurn(fromUser = true, content = userPrompt(name, whenPlan))
                        )
                    )
                }
            }.getOrNull()
            sanitize(ai, name)?.let { return@withContext it }
        }

        interpretOffline(name, whenPlan)
    }

    private fun userPrompt(title: String, whenPlan: TripWhenPlan): String = buildString {
        appendLine("Trip name: \"$title\"")
        appendLine("Length: ${whenPlan.tripDays} day(s)")
        appendLine("Timing: ${whenPlan.describeForVibes()}")
        appendLine("Timing mode: ${whenPlan.mode.label}")
        appendLine()
        appendLine(
            "Unpack what this trip name is really asking for. Write the vibes brief now."
        )
    }

    private fun sanitize(raw: String?, title: String): String? {
        if (raw.isNullOrBlank()) return null
        var text = raw.trim()
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        // Drop accidental labels the model sometimes adds.
        text = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                line.startsWith("vibes:", ignoreCase = true) ||
                    line.startsWith("vibe:", ignoreCase = true) ||
                    line.startsWith("trip name:", ignoreCase = true)
            }
            .joinToString(" ")
            .replace(Regex("""^["']+|["']+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (text.length < 40) return null
        // Reject lazy echo of the title.
        val lower = text.lowercase()
        val titleLower = title.trim().lowercase()
        if (lower.startsWith(titleLower) || lower.contains("\"$titleLower\"")) return null
        if (titleLower.length >= 4 && lower.count { it == ' ' } < 6 && lower.contains(titleLower)) {
            return null
        }
        return text.take(520)
    }

    /**
     * Offline path still interprets meaning from tokens (place, season words, food, etc.)
     * and never inserts the raw trip title into the prose.
     */
    private fun interpretOffline(title: String, whenPlan: TripWhenPlan): String {
        val lower = title.lowercase()
        val place = detectPlace(title)
        val season = seasonOf(whenPlan.resolvedRange.first)
        val days = whenPlan.tripDays
        val moods = detectMoods(lower)
        val seed = (place ?: title).length * 31 + whenPlan.mode.ordinal * 17 + days

        val where = place?.let { "around $it" } ?: "wherever this name is pointing"
        val placeLine = place?.let { cityFlavor(it) }
        val pace = when {
            days <= 3 -> "Keep it short and intentional — one standout beat a day, then leave air to wander."
            days <= 7 -> "Build a light spine of must-dos and leave blank afternoons for detours."
            else -> "Settle in: neighborhood mornings, one bigger outing mid-trip, and at least one day that stays deliciously unplanned."
        }

        val interestLine = when {
            Mood.FOOD in moods ->
                "Lead with taste — markets, tiny counters, the dish locals quietly argue about."
            Mood.NATURE in moods ->
                "Chase open air — coasts, parks, viewpoints that make the walk worth it."
            Mood.NIGHT in moods ->
                "Save energy for after dark: a view drink, a late table, a little mischief."
            Mood.ADVENTURE in moods ->
                "Thread in one proper adventure so the trip has a plot twist, not just scenery."
            Mood.ART in moods ->
                "Hunt art and design that feels of-the-moment — galleries, murals, rooms with a point of view."
            Mood.CULTURE in moods ->
                "Take culture personally: neighborhoods and stories first, ticket queues second."
            Mood.ROMANCE in moods ->
                "Keep it intimate — golden-hour walks, one special table, nowhere you have to shout."
            Mood.FAMILY in moods ->
                "Plan for mixed ages: one shared highlight, soft landings, snacks never far away."
            else ->
                "Blend local food, a cultural hit, and one hidden corner so it feels cracked-open, not guidebooked."
        }

        val seasonLine = when (season) {
            Season.SPRING -> "Spring light is forgiving — outdoor cafés and long walks earn their keep."
            Season.SUMMER -> "Summer wants early starts, shade strategies, and nights that stay out late."
            Season.AUTUMN -> "Autumn is peak mood: softer crowds, warmer plates, amber evenings."
            Season.WINTER -> "Winter travel loves cozy rituals — hot bowls, indoor treasures, crisp outdoor resets."
        }

        val whenLine = when (whenPlan.mode) {
            TripWhenMode.WEEKEND_ONLY ->
                "It's a weekend dash: punchy highlights, great food, zero guilt about skipping the 'shoulds'."
            TripWhenMode.FLEXIBLE_CHEAP ->
                "Stay thrifty on getting there, then spend the savings on one gloriously unnecessary experience."
            TripWhenMode.CLOSE_TO_DATE, TripWhenMode.AFTER_DATE ->
                "Timing is flexible — chase the kinder weather window, not the first calendar slot."
            TripWhenMode.EXACT_DATES ->
                "Dates are locked, so protect one completely free afternoon on purpose."
        }

        val spark = listOf(
            "Bonus quest $where: find the place you'd accidentally brag about to a stranger on the way home.",
            "Leave one gloriously unproductive hour — that's usually where the best story hides.",
            "If it starts feeling too postcard-perfect, duck a side street and ask where *they* eat.",
            "Photograph the ridiculous snack you weren't supposed to order."
        )[Math.floorMod(seed, 4)]

        return listOfNotNull(
            placeLine ?: "Read this name as a mood brief, not a destination checklist.",
            pace,
            interestLine,
            seasonLine,
            whenLine,
            spark
        ).joinToString(" ")
    }

    private fun cityFlavor(place: String): String? {
        val key = CityCatalog.resolveCity(place).substringBefore(",").trim().lowercase()
        return when {
            key.contains("lisbon") ->
                "Tram-rattle mornings, miradouro sunsets, and the kind of seafood that makes you late for everything else."
            key.contains("paris") ->
                "Arrondissement wandering, bakery discipline, and evenings that stretch because the light refuses to quit."
            key.contains("barcelona") ->
                "Sea-breeze mornings, tapas that turn into dinner, and architecture you feel in your neck from looking up."
            key.contains("tokyo") ->
                "Neon alleys, quiet shrines, and the joy of getting gloriously lost between a bowl of ramen and a side-street bar."
            key.contains("rome") ->
                "Ancient stones underfoot, espresso standing up, and piazzas that reward whoever arrives with no agenda."
            key.contains("new york") ->
                "Block-by-block discovery, late tables, and the energy of a city that never asks you to pick just one mood."
            key.contains("london") ->
                "Market mornings, museum afternoons, and pubs that feel like the real reason you crossed the river."
            key.contains("amsterdam") ->
                "Canal-side meandering, bike-simple logistics, and brown-café evenings that go longer than planned."
            else -> null
        }
    }

    private fun detectPlace(title: String): String? = DestinationInference.fromTitle(title)

    private fun detectMoods(lower: String): Set<Mood> = buildSet {
        if (FOOD.any { lower.contains(it) }) add(Mood.FOOD)
        if (NATURE.any { lower.contains(it) }) add(Mood.NATURE)
        if (NIGHT.any { lower.contains(it) }) add(Mood.NIGHT)
        if (ADVENTURE.any { lower.contains(it) }) add(Mood.ADVENTURE)
        if (ART.any { lower.contains(it) }) add(Mood.ART)
        if (CULTURE.any { lower.contains(it) }) add(Mood.CULTURE)
        if (ROMANCE.any { lower.contains(it) }) add(Mood.ROMANCE)
        if (FAMILY.any { lower.contains(it) }) add(Mood.FAMILY)
    }

    private fun seasonOf(date: LocalDate): Season = when (date.month) {
        Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.WINTER
        Month.MARCH, Month.APRIL, Month.MAY -> Season.SPRING
        Month.JUNE, Month.JULY, Month.AUGUST -> Season.SUMMER
        else -> Season.AUTUMN
    }

    private enum class Mood { FOOD, NATURE, NIGHT, ADVENTURE, ART, CULTURE, ROMANCE, FAMILY }
    private enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

    private companion object {
        const val AI_TIMEOUT_MS = 25_000L

        val SYSTEM = """
            You are an expert travel advisor writing the "Vibes" field for a new trip.
            Infer the REAL intent hidden inside the trip name — destination, mood, pace,
            who it's for, what kind of days they want — then write 2–4 vivid sentences.

            Rules:
            - Do NOT quote, repeat, or paste the trip name into the text.
            - Do NOT start with "Think of…", "Here's the brief…", or "A X-day trip named…".
            - Sound original and fun, like a sharp advisor with a wink — never brochure filler.
            - Mention concrete cues (food, neighborhoods, pace, season, nightlife, nature) when the name implies them.
            - Plain prose only. No markdown, bullets, titles, or JSON.
            - Keep it under 80 words.
        """.trimIndent()

        val FOOD = listOf("food", "foodie", "eat", "cuisine", "culinary", "wine", "taste", "gastro", "cafe", "café")
        val NATURE = listOf("beach", "coast", "nature", "hike", "mountain", "island", "lake", "park", "outdoor", "sea")
        val NIGHT = listOf("night", "nightlife", "party", "club", "bar", "cocktail")
        val ADVENTURE = listOf("adventure", "surf", "ski", "trek", "climb", "safari", "dive", "kayak")
        val ART = listOf("art", "design", "gallery", "architecture", "fashion")
        val CULTURE = listOf("culture", "historic", "history", "heritage", "museum", "ancient", "temple", "palace")
        val ROMANCE = listOf("romantic", "honeymoon", "anniversary", "couple")
        val FAMILY = listOf("family", "kids", "children", "with kids")
    }
}
