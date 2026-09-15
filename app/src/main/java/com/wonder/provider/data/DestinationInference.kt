package com.wonder.provider.data

/**
 * Pulls a usable destination out of a trip title, free text, or itinerary locations
 * so Explore / maps don't stay stuck on "Not decided yet".
 */
object DestinationInference {

    private val TITLE_NOISE = Regex(
        """(?i)\b(trip|trips|getaway|escape|vacation|holiday|tour|tours|weekend|summer|winter|spring|autumn|fall|family|romantic|food|foodie|adventure|vibes|vibe|friends|honeymoon|anniversary|bucket\s*list|itinerary|plans?)\b"""
    )
    private val LEAD_IN = Regex("""(?i)^\s*(in|to|for|around|through|visiting|visit)\s+""")
    private val PREPOSITION_CITY = Regex(
        """\b(?:in|to|around|at|near|visiting)\s+([A-Z][\p{L}'-]+(?:\s+[A-Z][\p{L}'-]+){0,2})"""
    )
    private val PROPER_NAME = Regex("""\b([A-Z][\p{L}'-]+(?:\s+[A-Z][\p{L}'-]+){0,2})\b""")

    private val NON_PLACE = setOf(
        "the", "and", "for", "with", "from", "into", "our", "my", "new", "a", "an",
        "trip", "trips", "getaway", "escape", "vacation", "holiday", "tour", "tours",
        "weekend", "summer", "winter", "spring", "autumn", "fall", "family", "romantic",
        "food", "foodie", "adventure", "vibes", "vibe", "friends", "honeymoon",
        "anniversary", "plans", "plan", "itinerary", "you", "your", "this", "that",
        "next", "best", "big", "small", "long", "short", "first", "last"
    )

    /** Best destination for a trip still sitting on the undecided placeholder. */
    fun resolve(
        title: String,
        currentDestination: String,
        locations: Iterable<String> = emptyList(),
        vibesHint: String? = null
    ): String {
        if (TripRepository.hasDecidedDestination(currentDestination)) {
            return currentDestination.trim()
        }
        return listOfNotNull(
            vibesHint?.trim()?.takeIf { TripRepository.hasDecidedDestination(it) },
            fromTitle(title),
            fromLocations(locations),
            fromText(title)
        ).firstOrNull() ?: TripRepository.DEFAULT_DESTINATION
    }

    fun fromTitle(title: String): String? {
        val trimmed = title.trim()
        if (trimmed.length < 2) return null

        knownCityIn(trimmed)?.let { return it }

        val cleaned = trimmed
            .replace(TITLE_NOISE, " ")
            .replace(LEAD_IN, "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val caps = PROPER_NAME.findAll(trimmed)
            .map { it.groupValues[1] }
            .filterNot { isNonPlace(it) }
            .toList()

        val raw = caps.lastOrNull()
            ?: cleaned.takeIf { it.length in 3..40 && !isNonPlace(it) }
            ?: return null

        return normalizePlace(raw)
    }

    fun fromText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        knownCityIn(trimmed)?.let { return it }
        PREPOSITION_CITY.find(trimmed)?.groupValues?.get(1)
            ?.takeIf { it.length > 2 && !isNonPlace(it) }
            ?.let { return normalizePlace(it) }
        return fromTitle(trimmed)
    }

    fun fromLocations(locations: Iterable<String>): String? {
        val normalized = locations.mapNotNull { location ->
            val part = location.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            knownCityIn(part)
                ?: part.split(",")
                    .map { it.trim() }
                    .filter { it.length >= 3 && !isNonPlace(it) }
                    .let { segments ->
                        segments.lastOrNull()?.let(::normalizePlace)
                    }
        }
        if (normalized.isEmpty()) return null
        return normalized
            .groupingBy { it.lowercase() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.let { (key, _) -> normalized.first { it.equals(key, ignoreCase = true) } }
    }

    private fun knownCityIn(text: String): String? {
        val lower = text.lowercase()
        return CityCatalog.knownCities().firstOrNull {
            lower.contains(it.substringBefore(",").trim().lowercase())
        }
    }

    private fun normalizePlace(raw: String): String? {
        val place = raw.split(' ')
            .filter { it.isNotBlank() && !isNonPlace(it) }
            .joinToString(" ") { token ->
                token.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                }
            }
            .trim()
        return place.takeIf { it.length >= 3 && !isNonPlace(it) }
    }

    private fun isNonPlace(value: String): Boolean {
        val lower = value.trim().lowercase()
        if (lower in NON_PLACE) return true
        return lower.split(Regex("""\s+""")).all { it in NON_PLACE }
    }
}
