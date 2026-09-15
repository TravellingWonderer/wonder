package com.wonder.provider.model

import java.time.temporal.ChronoUnit

data class NewTripBlueprint(
    val title: String,
    val whenPlan: TripWhenPlan,
    val vibes: String,
    /** True while vibes are being live-composed; false once the traveller edits them. */
    val autoGenerateVibes: Boolean
) {
    val startDate get() = whenPlan.resolvedRange.first
    val endDate get() = whenPlan.resolvedRange.second

    val days: Int
        get() = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(0) + 1

    val effectiveVibes: String
        get() = vibes.trim()

    val dateRange: Pair<java.time.LocalDate, java.time.LocalDate>
        get() = startDate to endDate
}

object TripBlueprintComposer {

    fun composeVibes(blueprint: NewTripBlueprint): String = composeVibes(
        title = blueprint.title,
        whenPlan = blueprint.whenPlan
    )

    fun composeVibes(title: String, whenPlan: TripWhenPlan): String {
        val name = title.trim()
        if (name.length < 2) return ""

        val tripDays = whenPlan.tripDays
        val whenLabel = whenPlan.describeForVibes()

        return buildString {
            append("A ${paceLabel(tripDays)} trip — $name")
            append(" over $tripDays ${if (tripDays == 1) "day" else "days"}")
            append(", $whenLabel.")
            append(" Mix local food, culture, and hidden gems at a balanced pace.")
        }
    }

    private fun paceLabel(days: Int): String = when {
        days <= 3 -> "short"
        days <= 7 -> "week-long"
        else -> "extended"
    }
}
