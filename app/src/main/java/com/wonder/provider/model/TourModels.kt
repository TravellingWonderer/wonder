package com.wonder.provider.model

enum class TourInterest(val label: String, val emoji: String) {
    FOOD("Food & Drink", "🍽️"),
    CULTURE("Culture & History", "🏛️"),
    ADVENTURE("Adventure", "🧗"),
    NATURE("Nature & Parks", "🌿"),
    NIGHTLIFE("Nightlife", "🌙"),
    SHOPPING("Shopping", "🛍️"),
    ART("Art & Design", "🎨"),
    LOCAL("Hidden Gems", "💎")
}

enum class TourPace(val label: String, val stopsPerDay: Int) {
    RELAXED("Relaxed", 3),
    BALANCED("Balanced", 5),
    ACTIVE("Active", 7)
}

enum class TourDuration(val label: String, val days: Int) {
    HALF_DAY("Half day", 1),
    FULL_DAY("Full day", 1),
    TWO_DAYS("2 days", 2),
    WEEKEND("Weekend", 3)
}

enum class TourBudget(val label: String, val maxStopCost: Int) {
    BUDGET("Budget", 15),
    MID_RANGE("Mid-range", 40),
    PREMIUM("Premium", 100)
}

data class TourBuildRequest(
    val city: String,
    val interests: Set<TourInterest>,
    val pace: TourPace,
    val duration: TourDuration,
    val budget: TourBudget,
    val groupSize: Int = 2,
    val notes: String = ""
)

data class TourStop(
    val order: Int,
    val name: String,
    val description: String,
    val category: TourInterest,
    val durationMinutes: Int,
    val estimatedCost: Int,
    val currency: String,
    val timeSlot: String,
    val emoji: String,
    val tip: String,
    val location: String = ""
)

data class CuratedTour(
    val id: String,
    val title: String,
    val city: String,
    val summary: String,
    val highlights: List<String>,
    val stops: List<TourStop>,
    val totalDurationHours: Double,
    val estimatedBudget: Int,
    val currency: String,
    val matchScore: Int,
    val days: Int
)
