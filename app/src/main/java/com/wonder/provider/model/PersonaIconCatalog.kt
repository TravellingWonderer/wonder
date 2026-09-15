package com.wonder.provider.model

/**
 * Icon keys and emoji for persona avatars. The AI picks a key (+ optional emoji) for invented
 * personas; offline paths infer from the persona name.
 */
object PersonaIconCatalog {
    val allKeys: List<String> = listOf(
        "spa", "shield", "restaurant", "bar", "runner", "wallet", "sunset", "bolt",
        "museum", "moon", "sunrise", "camera", "rain", "home", "meditation", "flight",
        "map", "coffee", "music", "pets", "hike", "shop", "family", "compass",
        "sparkle", "hotel", "train", "wine", "theater", "sport", "eco", "history",
        "beach", "bike", "book", "ghost", "robot", "crown", "fire", "snow"
    )

    private val catalogIcons = mapOf(
        "Lazy Panda" to "spa",
        "Sceptic Nanny" to "shield",
        "Food Junkie" to "restaurant",
        "Beer-a-holic" to "bar",
        "Weekend Warrior" to "runner",
        "Budget Hawk" to "wallet",
        "Golden Hour Chaser" to "sunset",
        "Chaos Gremlin" to "bolt",
        "Culture Vulture" to "museum",
        "Night Owl" to "moon",
        "Early Riser" to "sunrise",
        "Instagram Realist" to "camera",
        "Rain Plan B" to "rain",
        "Local's Cousin" to "home",
        "Slow Travel Monk" to "meditation"
    )

    private val catalogEmoji = mapOf(
        "Lazy Panda" to "🦥",
        "Sceptic Nanny" to "🧐",
        "Food Junkie" to "🍜",
        "Beer-a-holic" to "🍺",
        "Weekend Warrior" to "🏃",
        "Budget Hawk" to "💰",
        "Golden Hour Chaser" to "🌅",
        "Chaos Gremlin" to "⚡",
        "Culture Vulture" to "🏛️",
        "Night Owl" to "🦉",
        "Early Riser" to "🌄",
        "Instagram Realist" to "📸",
        "Rain Plan B" to "☔",
        "Local's Cousin" to "🏠",
        "Slow Travel Monk" to "🧘"
    )

    private val keyEmoji = mapOf(
        "spa" to "🦥", "shield" to "🛡️", "restaurant" to "🍽️", "bar" to "🍺",
        "runner" to "🏃", "wallet" to "💰", "sunset" to "🌅", "bolt" to "⚡",
        "museum" to "🏛️", "moon" to "🌙", "sunrise" to "🌄", "camera" to "📸",
        "rain" to "☔", "home" to "🏠", "meditation" to "🧘", "flight" to "✈️",
        "map" to "🗺️", "coffee" to "☕", "music" to "🎵", "pets" to "🐾",
        "hike" to "🥾", "shop" to "🛍️", "family" to "👨‍👩‍👧", "compass" to "🧭",
        "sparkle" to "✨", "hotel" to "🏨", "train" to "🚆", "wine" to "🍷",
        "theater" to "🎭", "sport" to "⚽", "eco" to "🌿", "history" to "📜",
        "beach" to "🏖️", "bike" to "🚲", "book" to "📚", "ghost" to "👻",
        "robot" to "🤖", "crown" to "👑", "fire" to "🔥", "snow" to "❄️"
    )

    private val keywordToKey = listOf(
        "spa" to listOf("panda", "lazy", "relax", "chill", "nap", "slow", "monk", "calm"),
        "shield" to listOf("sceptic", "skeptic", "nanny", "safe", "careful", "warn", "cautious"),
        "restaurant" to listOf("food", "eat", "hungry", "snack", "junkie", "chef", "taste", "brunch"),
        "bar" to listOf("beer", "pub", "drink", "wine", "cocktail", "ale", "bar"),
        "runner" to listOf("warrior", "active", "sport", "run", "hike", "adventure", "energy"),
        "wallet" to listOf("budget", "hawk", "cheap", "money", "cost", "frugal", "save"),
        "sunset" to listOf("golden", "hour", "photo", "sunset", "glow", "light"),
        "bolt" to listOf("chaos", "gremlin", "wild", "spontaneous", "rush", "impulse"),
        "museum" to listOf("culture", "vulture", "art", "history", "gallery", "heritage"),
        "moon" to listOf("night", "owl", "late", "midnight", "dark", "club"),
        "sunrise" to listOf("early", "riser", "morning", "dawn", "alarm"),
        "camera" to listOf("instagram", "realist", "photo", "selfie", "story"),
        "rain" to listOf("rain", "plan b", "backup", "indoor", "umbrella"),
        "home" to listOf("local", "cousin", "native", "neighbour", "neighbor", "homely"),
        "meditation" to listOf("zen", "mindful", "yoga", "peace", "quiet"),
        "flight" to listOf("fly", "flight", "airport", "plane", "pilot"),
        "map" to listOf("map", "route", "lost", "navigate", "direction"),
        "coffee" to listOf("coffee", "cafe", "espresso", "latte"),
        "music" to listOf("music", "jazz", "concert", "festival", "dj"),
        "pets" to listOf("pet", "dog", "cat", "animal"),
        "hike" to listOf("trail", "mountain", "nature", "outdoor", "trek"),
        "shop" to listOf("shop", "market", "souvenir", "mall"),
        "family" to listOf("family", "kid", "child", "parent", "baby"),
        "compass" to listOf("explorer", "wander", "roam", "nomad", "backpack"),
        "sparkle" to listOf("magic", "wonder", "dream", "fancy", "luxury"),
        "hotel" to listOf("hotel", "stay", "suite", "hostel"),
        "train" to listOf("train", "rail", "metro", "transit"),
        "beach" to listOf("beach", "sea", "ocean", "surf", "sand"),
        "bike" to listOf("bike", "cycle", "cycling"),
        "book" to listOf("book", "read", "nerd", "study"),
        "ghost" to listOf("ghost", "haunt", "spooky", "paranormal"),
        "robot" to listOf("robot", "tech", "geek", "digital", "ai"),
        "crown" to listOf("royal", "vip", "queen", "king", "lux"),
        "fire" to listOf("fire", "hot", "spicy", "bold"),
        "snow" to listOf("snow", "ski", "winter", "cold", "alpine")
    )

    fun promptForAi(): String = buildString {
        appendLine("ICON KEYS (pick the single best key per persona — required for invented names):")
        appendLine(allKeys.joinToString(", "))
        appendLine("Also add \"emoji\": one fitting emoji character that matches the persona vibe.")
    }

    fun catalogIconKey(persona: String): String? = catalogIcons[persona]

    fun catalogEmoji(persona: String): String? = catalogEmoji[persona]

    fun normalizeIconKey(raw: String?): String? {
        val trimmed = raw?.trim()?.lowercase()?.replace(' ', '_') ?: return null
        if (trimmed in allKeys) return trimmed
        return keywordToKey.firstOrNull { (_, words) -> words.any { trimmed.contains(it) } }?.first
    }

    fun normalizeEmoji(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed.codePointCount(0, trimmed.length).let { count ->
            if (count in 1..2) trimmed else trimmed.take(2)
        }
    }

    fun inferIconKey(persona: String): String {
        catalogIcons[persona]?.let { return it }
        val key = persona.lowercase()
        var best: Pair<String, Int>? = null
        for ((iconKey, words) in keywordToKey) {
            val score = words.count { word -> word in key }
            if (score > 0 && (best == null || score > best.second)) {
                best = iconKey to score
            }
        }
        if (best != null) return best.first
        return allKeys[kotlin.math.abs(persona.hashCode()) % allKeys.size]
    }

    fun inferEmoji(persona: String, iconKey: String): String =
        catalogEmoji[persona] ?: keyEmoji[iconKey] ?: "✨"

    fun resolveIconKey(persona: String, iconKey: String?): String =
        normalizeIconKey(iconKey) ?: inferIconKey(persona)

    fun resolveEmoji(persona: String, iconKey: String?, emoji: String?): String =
        normalizeEmoji(emoji) ?: catalogEmoji(persona) ?: keyEmoji[resolveIconKey(persona, iconKey)] ?: "✨"
}
