package com.wonder.provider.data

/**
 * Pulls a usable destination out of a trip title, free text, or itinerary locations
 * so Explore / maps don't stay stuck on "Not decided yet".
 *
 * Only grounded places are accepted: gazetteer hits, explicit "City, Country" strings,
 * or preposition phrases that resolve to a known place. Leftover title words
 * ("succint trip" → "Succint") are never treated as cities.
 */
object DestinationInference {

    private val TITLE_NOISE = Regex(
        """(?i)\b(trip|trips|getaway|escape|vacation|holiday|tour|tours|weekend|summer|winter|spring|autumn|fall|family|romantic|food|foodie|adventure|vibes|vibe|friends|honeymoon|anniversary|bucket\s*list|itinerary|plans?|succinct|succint|succient)\b"""
    )
    private val LEAD_IN = Regex("""(?i)^\s*(in|to|for|around|through|visiting|visit)\s+""")
    private val PREPOSITION_CITY = Regex(
        """\b(?:in|to|around|at|near|visiting)\s+([A-Z][\p{L}'-]+(?:\s+[A-Z][\p{L}'-]+){0,2})""",
        RegexOption.IGNORE_CASE
    )
    private val EXPLICIT_PLACE = Regex(
        """^[\p{L}][\p{L}'\s.-]{1,48},\s*[\p{L}][\p{L}'\s.-]{1,48}$"""
    )

    private val NON_PLACE = setOf(
        "the", "and", "for", "with", "from", "into", "our", "my", "new", "a", "an",
        "trip", "trips", "getaway", "escape", "vacation", "holiday", "tour", "tours",
        "weekend", "summer", "winter", "spring", "autumn", "fall", "family", "romantic",
        "food", "foodie", "adventure", "vibes", "vibe", "friends", "honeymoon",
        "anniversary", "plans", "plan", "itinerary", "you", "your", "this", "that",
        "next", "best", "big", "small", "long", "short", "first", "last",
        "succinct", "succint", "succient", "quick", "chill", "cozy", "cosy", "epic",
        "perfect", "amazing", "slow", "fast", "mini", "ultimate", "dream", "magic",
        "magical", "special", "secret", "hidden", "wild", "easy", "simple", "blank"
    )

    /**
     * Hard rule for models: a trip name is a nickname, never a city, unless a real
     * place is clearly present.
     */
    val MODEL_GROUNDING_RULES = """
PLACE GROUNDING (non-negotiable)
- The trip TITLE is a nickname, not a destination. Never invent a city, neighbourhood, restaurant, or landmark from leftover words in the title.
- Only name real places that exist on a map.
- If the destination is "Not decided yet", unknown, or not a real city/country, do not fabricate an itinerary for a made-up place. Speak in destination-agnostic terms, or ask where they want to go.
- Never template "Old Town", viewpoints, or markets onto a word that is not a verified place.
""".trimIndent()

    /** Best destination for a trip still sitting on the undecided placeholder. */
    fun resolve(
        title: String,
        currentDestination: String,
        locations: Iterable<String> = emptyList(),
        vibesHint: String? = null
    ): String {
        if (isGrounded(currentDestination)) {
            return currentDestination.trim()
        }
        return listOfNotNull(
            vibesHint?.trim()?.let(::knownPlaceIn),
            fromTitle(title),
            fromLocations(locations),
            fromText(title)
        ).firstOrNull() ?: TripRepository.DEFAULT_DESTINATION
    }

    /**
     * True when [destination] was copied from a trip nickname and is not a real place
     * (e.g. title "succint trip" → destination "Succint").
     */
    fun shouldClearInferredDestination(destination: String, title: String): Boolean {
        if (isGrounded(destination)) return false
        val dest = destination.substringBefore(",").trim()
        if (dest.length < 3) return true
        val destLower = dest.lowercase()
        if (destLower in NON_PLACE) return true
        val inTitle = Regex("""\b${Regex.escape(destLower)}\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(title)
        return inTitle && knownPlaceIn(title) == null && knownPlaceIn(dest) == null
    }

    /** True when [destination] is a real place we may generate content for. */
    fun isGrounded(destination: String): Boolean {
        if (!TripRepository.hasDecidedDestination(destination)) return false
        if (knownPlaceIn(destination) != null) return true
        return EXPLICIT_PLACE.matches(destination.trim())
    }

    fun fromTitle(title: String): String? {
        val trimmed = title.trim()
        if (trimmed.length < 2) return null

        knownPlaceIn(trimmed)?.let { return it }
        prepositionPlace(trimmed)?.let { return it }

        val cleaned = stripTitleNoise(trimmed)
        return knownPlaceIn(cleaned)
    }

    fun fromText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        knownPlaceIn(trimmed)?.let { return it }
        return prepositionPlace(trimmed)
    }

    /**
     * Leftover title/vibe tokens that *might* be a real place. Callers must verify
     * with a geocoder before committing — never use this string as a city as-is.
     */
    fun geocodeCandidate(title: String, extra: String = ""): String? {
        listOf(title, extra).forEach { source ->
            val cleaned = stripTitleNoise(source)
            if (isViableGeocodeQuery(cleaned) && knownPlaceIn(cleaned) == null) {
                return cleaned
            }
        }
        return null
    }

    fun fromLocations(locations: Iterable<String>): String? {
        val normalized = locations.mapNotNull { location ->
            val part = location.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            knownPlaceIn(part)
                ?: part.split(",")
                    .map { it.trim() }
                    .firstNotNullOfOrNull { knownPlaceIn(it) }
        }
        if (normalized.isEmpty()) return null
        return normalized
            .groupingBy { it.lowercase() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.let { (key, _) -> normalized.first { it.equals(key, ignoreCase = true) } }
    }

    fun knownPlaceIn(text: String): String? = CityCatalog.knownPlaceLabel(text)

    private fun prepositionPlace(text: String): String? {
        val candidate = PREPOSITION_CITY.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        if (candidate.length < 3 || isBlockedToken(candidate)) return null
        return knownPlaceIn(candidate) ?: knownPlaceIn(text)
    }

    private fun stripTitleNoise(title: String): String =
        title.trim()
            .replace(TITLE_NOISE, " ")
            .replace(LEAD_IN, "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun isViableGeocodeQuery(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length !in 4..48) return false
        if (isBlockedToken(trimmed)) return false
        val tokens = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        return tokens.any { it.length >= 3 && !isBlockedToken(it) }
    }

    private fun isBlockedToken(value: String): Boolean {
        val lower = value.trim().lowercase()
        if (lower in NON_PLACE) return true
        return lower.split(Regex("""\s+""")).all { it in NON_PLACE }
    }
}
