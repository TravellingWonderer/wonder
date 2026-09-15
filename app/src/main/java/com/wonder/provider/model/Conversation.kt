package com.wonder.provider.model

import java.time.LocalDate

enum class Speaker { YOU, WONDER }

/**
 * A single turn in the conversation. Assistant turns can carry generative cards — the plan, the
 * money and the day all arrive as part of an answer rather than somewhere you navigate to.
 */
data class ChatTurn(
    val id: String,
    val speaker: Speaker,
    val text: String,
    val cards: List<AgentCard> = emptyList(),
    val personaPanels: List<PersonaPanel> = emptyList(),
    val sourceLabel: String? = null,
    val phase: TurnPhase = TurnPhase.SETTLED,
    val cameFromVoice: Boolean = false
)

enum class TurnPhase {
    /** Wonder is working — the orb pulses and a shimmer stands in for the reply. */
    THINKING,

    /** Reply text is being revealed word by word; cards are still held back. */
    SPEAKING,

    /** Everything is on screen. */
    SETTLED
}

data class DayOutline(
    val date: LocalDate,
    val dayNumber: Int,
    val headline: String,
    val itemCount: Int,
    val cost: Double,
    val isToday: Boolean,
    val isPast: Boolean
)

sealed interface AgentCard {

    /** The whole trip at a glance — one line per day. */
    data class TripOverview(
        val title: String,
        val destination: String,
        val dateRange: String,
        val coverEmoji: String,
        val days: List<DayOutline>,
        val currency: String
    ) : AgentCard

    data class DayPlan(
        val date: LocalDate,
        val heading: String,
        val items: List<ItineraryItem>,
        val cost: Double,
        val currency: String,
        val travellerCount: Int
    ) : AgentCard

    /** Wandering's centrepiece: what you're in the middle of, and what's coming. */
    data class NowNext(
        val current: ItineraryItem?,
        val next: ItineraryItem?,
        val minutesUntilNext: Long,
        val currency: String,
        val travellerCount: Int
    ) : AgentCard

    data class Budget(
        val summary: BudgetSummary,
        val detailed: Boolean
    ) : AgentCard

    data class ExpenseLog(
        val expenses: List<Expense>,
        val summary: BudgetSummary,
        val payerNames: Map<String, String>
    ) : AgentCard

    data class Nearby(
        val heading: String,
        val picks: List<Recommendation>,
        val currency: String
    ) : AgentCard

    /** A day Wonder drafted, offered before it touches the real plan. */
    data class DraftDay(
        val tour: CuratedTour,
        val date: LocalDate?
    ) : AgentCard

    /** Things that still need booking before they sell out or get expensive. */
    data class Loose(
        val heading: String,
        val items: List<ItineraryItem>,
        val currency: String,
        val travellerCount: Int
    ) : AgentCard

    /** A door: into the full plan, into the money, or into model settings. */
    data class Doorway(
        val headline: String,
        val body: String,
        val target: DoorwayTarget
    ) : AgentCard

    /** Live flight fares from a connected travel API (Duffel). */
    data class FlightResults(
        val result: com.wonder.provider.model.FlightSearchResult
    ) : AgentCard

    /**
     * Trip dates were never locked in. Shown before any itinerary leg can be applied;
     * mirrors the New Trip date options.
     */
    data class ConfirmDates(
        val pendingLegs: List<PendingItineraryLeg> = emptyList(),
        val headline: String = "When is this trip?",
        val body: String = "Pick an approximate date range before I add anything to the itinerary."
    ) : AgentCard
}

/** A planned itinerary stop waiting on a confirmed trip date range. */
data class PendingItineraryLeg(
    val title: String,
    val dayOffset: Int = 0,
    val kind: ItemKind = ItemKind.ACTIVITY,
    val startTime: java.time.LocalTime? = null,
    val durationMinutes: Int = 60,
    val location: String = "",
    val notes: String = "",
    val estimatedCost: Double = 0.0,
    val costIsPerPerson: Boolean = false,
    val status: ItemStatus = ItemStatus.PLANNED
)

enum class DoorwayTarget { PLAN, EXPENSES, SETTINGS }
