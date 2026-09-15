package com.wonder.provider.model

import java.time.temporal.ChronoUnit

data class NewTripBlueprint(
    val title: String,
    val whenPlan: TripWhenPlan,
    val vibes: String,
    /** True while vibes are being live-composed; false once the traveller edits them. */
    val autoGenerateVibes: Boolean = false,
    /**
     * Name + dates only — no vibe brief, no seeded interests, and a quiet first greeting
     * with no suggestion panels.
     */
    val blankStart: Boolean = false
) {
    val startDate get() = whenPlan.resolvedRange.first
    val endDate get() = whenPlan.resolvedRange.second

    val days: Int
        get() = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(0) + 1

    val effectiveVibes: String
        get() = if (blankStart) "" else vibes.trim()

    val dateRange: Pair<java.time.LocalDate, java.time.LocalDate>
        get() = startDate to endDate

    /** Flexible / cheapest creates leave dates unconfirmed until the traveller locks a range. */
    val datesConfirmed: Boolean
        get() = whenPlan.mode != TripWhenMode.FLEXIBLE_CHEAP
}
