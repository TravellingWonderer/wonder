package com.wonder.provider.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class TripWhenMode(val label: String) {
    EXACT_DATES("Exact dates"),
    CLOSE_TO_DATE("Close to date"),
    AFTER_DATE("After date"),
    WEEKEND_ONLY("Weekends only"),
    FLEXIBLE_CHEAP("Cheapest days")
}

data class TripWhenPlan(
    val mode: TripWhenMode = TripWhenMode.EXACT_DATES,
    val anchorDate: LocalDate,
    val endDate: LocalDate? = null,
    val flexDays: Int = 7,
    val durationDays: Int = 5,
    val weekendCount: Int = 1
) {
    val resolvedRange: Pair<LocalDate, LocalDate>
        get() = when (mode) {
            TripWhenMode.EXACT_DATES -> {
                val end = endDate?.takeIf { !it.isBefore(anchorDate) } ?: anchorDate
                anchorDate to end
            }
            TripWhenMode.CLOSE_TO_DATE -> {
                val flex = flexDays.coerceIn(1, 21).toLong()
                anchorDate.minusDays(flex) to anchorDate.plusDays(flex)
            }
            TripWhenMode.AFTER_DATE -> {
                val days = durationDays.coerceAtLeast(1)
                anchorDate to anchorDate.plusDays(days.toLong() - 1)
            }
            TripWhenMode.WEEKEND_ONLY -> weekendSpan(anchorDate, weekendCount.coerceIn(1, 4))
            TripWhenMode.FLEXIBLE_CHEAP -> {
                val days = durationDays.coerceAtLeast(1)
                val start = LocalDate.now().plusWeeks(8)
                start to start.plusDays(days.toLong() - 1)
            }
        }

    val tripDays: Int
        get() {
            val (start, end) = resolvedRange
            return ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(0) + 1
        }

    fun describeForVibes(): String = when (mode) {
        TripWhenMode.EXACT_DATES -> {
            val (start, end) = resolvedRange
            "${start.format(VIBES_DATE)} – ${end.format(VIBES_DATE)}"
        }
        TripWhenMode.CLOSE_TO_DATE ->
            "around ${anchorDate.format(VIBES_MONTH)}, ±$flexDays days flexible"
        TripWhenMode.AFTER_DATE ->
            "starting after ${anchorDate.format(VIBES_DATE)} for $durationDays days"
        TripWhenMode.WEEKEND_ONLY -> {
            val (start, end) = resolvedRange
            if (weekendCount <= 1) {
                "weekends only · ${start.format(VIBES_DATE)} – ${end.format(VIBES_DATE)}"
            } else {
                "$weekendCount weekends only · from ${start.format(VIBES_DATE)}"
            }
        }
        TripWhenMode.FLEXIBLE_CHEAP ->
            "flexible — cheapest $durationDays-day window in the next few months"
    }

    fun summaryLine(): String = when (mode) {
        TripWhenMode.EXACT_DATES -> {
            val (start, end) = resolvedRange
            "${start.format(CHIP_DATE)} – ${end.format(CHIP_DATE)} · $tripDays ${dayLabel(tripDays)}"
        }
        TripWhenMode.CLOSE_TO_DATE ->
            "±$flexDays days around ${anchorDate.format(CHIP_DATE)}"
        TripWhenMode.AFTER_DATE ->
            "$durationDays ${dayLabel(durationDays)} after ${anchorDate.format(CHIP_DATE)}"
        TripWhenMode.WEEKEND_ONLY -> {
            val (start, end) = resolvedRange
            "${if (weekendCount == 1) "1 weekend" else "$weekendCount weekends"} · ${start.format(CHIP_DATE)} – ${end.format(CHIP_DATE)}"
        }
        TripWhenMode.FLEXIBLE_CHEAP ->
            "Any cheap $durationDays-day slot · flexible timing"
    }

    companion object {
        private val VIBES_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private val VIBES_MONTH = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private val CHIP_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

        fun default(reference: LocalDate = LocalDate.now().plusWeeks(2)): TripWhenPlan =
            TripWhenPlan(
                mode = TripWhenMode.EXACT_DATES,
                anchorDate = reference,
                endDate = reference.plusDays(4)
            )

        private fun weekendSpan(anchor: LocalDate, weekends: Int): Pair<LocalDate, LocalDate> {
            var saturday = anchor
            while (saturday.dayOfWeek != DayOfWeek.SATURDAY) {
                saturday = saturday.plusDays(1)
            }
            val lastSunday = saturday.plusDays((weekends * 7L) - 2)
            return saturday to lastSunday
        }

        private fun dayLabel(days: Int): String = if (days == 1) "day" else "days"
    }
}
