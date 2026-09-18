package com.wonder.provider.model

import java.time.LocalTime

/**
 * High-level daytime windows to categorize activities and facilitate rapid timeline navigation.
 */
enum class TimeWindow(
    val label: String,
    val shortLabel: String,
    val emoji: String,
    val startHour: Int,
    val endHour: Int
) {
    LATE_NIGHT(
        label = "Late Night",
        shortLabel = "Late Night",
        emoji = "✨",
        startHour = 0,
        endHour = 4
    ),
    EARLY_MORNING(
        label = "Early Morning",
        shortLabel = "Early",
        emoji = "🌅",
        startHour = 5,
        endHour = 8
    ),
    MORNING(
        label = "Morning",
        shortLabel = "Morning",
        emoji = "☀️",
        startHour = 9,
        endHour = 11
    ),
    NOON(
        label = "Noon",
        shortLabel = "Noon",
        emoji = "🥪",
        startHour = 12,
        endHour = 13
    ),
    AFTERNOON(
        label = "Afternoon",
        shortLabel = "Afternoon",
        emoji = "☕",
        startHour = 14,
        endHour = 17
    ),
    EVENING(
        label = "Evening",
        shortLabel = "Evening",
        emoji = "🌇",
        startHour = 18,
        endHour = 20
    ),
    NIGHT(
        label = "Night",
        shortLabel = "Night",
        emoji = "🌙",
        startHour = 21,
        endHour = 23
    );

    fun matches(time: LocalTime?): Boolean {
        if (time == null) return false
        val h = time.hour
        return if (startHour <= endHour) {
            h in startHour..endHour
        } else {
            h >= startHour || h <= endHour
        }
    }

    companion object {
        fun forTime(time: LocalTime?): TimeWindow {
            if (time == null) return MORNING
            return entries.firstOrNull { it.matches(time) } ?: MORNING
        }
    }
}
