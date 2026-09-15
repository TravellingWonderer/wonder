package com.wonder.provider.model

import java.time.LocalDate

/** Planned or in-progress trips the traveller can switch between. */
enum class TripArchiveStatus {
    PLANNED,
    ARCHIVED
}

data class TripSummary(
    val id: String,
    val title: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val coverEmoji: String,
    val archiveStatus: TripArchiveStatus,
    val isActive: Boolean
) {
    val isPast: Boolean get() = endDate.isBefore(LocalDate.now())
    val isUpcoming: Boolean get() = startDate.isAfter(LocalDate.now())
}
