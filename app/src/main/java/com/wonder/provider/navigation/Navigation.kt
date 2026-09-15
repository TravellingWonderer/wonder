package com.wonder.provider.navigation

import java.time.LocalDate

/**
 * Wonder has no tabs. There is the conversation, and there are the three places it can send you:
 * the full plan, the money, and the choice of model. Every one of them is reached by asking or by
 * tapping something Wonder showed you.
 */
sealed class Screen(val route: String) {

    /** Default home — cached discovery feed for the destination. */
    data object Explore : Screen("explore")

    data object Conversation : Screen("conversation")

    data object NewTrip : Screen("new_trip")

    data object Plan : Screen("plan?date={date}&item={item}") {
        const val ARG_DATE = "date"
        const val ARG_ITEM = "item"

        fun build(date: LocalDate? = null, itemId: String? = null): String =
            "plan?date=${date?.toString().orEmpty()}&item=${itemId.orEmpty()}"
    }

    data object Expenses : Screen("expenses")

    data object AiSettings : Screen("ai_settings")

    /** Create and manage custom traveller personas (optional tripId to attach). */
    data object PersonaLibrary : Screen("personas?tripId={tripId}") {
        const val ARG_TRIP_ID = "tripId"
        fun build(tripId: String? = null): String =
            if (tripId.isNullOrBlank()) "personas?tripId=" else "personas?tripId=$tripId"
    }

    data object TripOverview : Screen("trip_overview/{tripId}") {
        const val ARG_TRIP_ID = "tripId"
        fun build(tripId: String): String = "trip_overview/$tripId"
    }
}
