package com.wonder.provider.ai

import com.wonder.provider.data.CityCatalog
import com.wonder.provider.data.PointOfInterest
import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.ChatTurn
import com.wonder.provider.model.ExpenseCategory
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.Speaker
import com.wonder.provider.model.TourBudget
import com.wonder.provider.model.TourBuildRequest
import com.wonder.provider.model.TourDuration
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.TourPace
import com.wonder.provider.model.TripMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Wonder's built-in mind. It reads intent from what the traveller said and answers from the trip
 * itself, so the app is fully conversational before any key is connected. What it says depends on
 * which mode the trip is in.
 */
class LocalConversationEngine(private val trips: TripRepository) {

    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val suggestionPicker = ContextualSuggestionPicker(trips)

    private fun chips(vararg hints: String): List<String> =
        suggestionPicker.pick(hints = hints.toList())

    fun greeting(): AgentReply =
        if (trips.mode.value == TripMode.WANDERING) wanderingGreeting() else plannerGreeting()

    /** Warm opener for a freshly created trip — no destination or itinerary status yet. */
    fun welcomeGreeting(quiet: Boolean = false): AgentReply {
        val trip = trips.trip.value
        val name = trip.travellers.firstOrNull()?.name
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("you", ignoreCase = true) }

        val say = when {
            quiet && name != null ->
                "Hey $name — ${trip.title} is ready whenever you are. Ask me anything when you want to start shaping it."
            quiet ->
                "Hey — ${trip.title} is ready whenever you are. Ask me anything when you want to start shaping it."
            name != null ->
                "Hey $name — good to see you. Let's shape ${trip.title} together whenever you're ready."
            else ->
                "Hey — good to see you. Let's shape ${trip.title} together whenever you're ready."
        }

        return AgentReply(
            say = say,
            intents = emptyList(),
            suggestions = if (quiet) emptyList() else suggestionPicker.pick(welcomeOnly = true)
        )
    }

    private fun wanderingGreeting(): AgentReply {
        val (current, next) = trips.nowAndNext()
        val budget = trips.budget()

        val say = when {
            current != null && next != null ->
                "You're in the middle of ${current.title}. ${next.title} is next, at ${next.startTime?.format(clock)}."
            current != null ->
                "You're at ${current.title} — nothing after it today, so take your time."
            next != null -> {
                val minutes = trips.minutesUntil(next)
                when {
                    minutes in 0..90 -> "${next.title} in ${minutes} minutes, ${next.location.ifBlank { "just up the road" }}."
                    next.date == LocalDate.now() -> "Next up today is ${next.title} at ${next.startTime?.format(clock)}."
                    else -> "Nothing left today. Tomorrow starts with ${next.title} at ${next.startTime?.format(clock)}."
                }
            }
            else -> "Day's yours — nothing on the schedule at all."
        }

        val tail = if (budget.isOverrun) {
            " Heads up: you're tracking ${money(-budget.variance)} over budget."
        } else {
            ""
        }

        return AgentReply(
            say = say + tail,
            intents = listOf(CardIntent.NowNext),
            suggestions = chips("What's free nearby?", "How much have we spent?", "What's on tomorrow?")
        )
    }

    private fun plannerGreeting(): AgentReply {
        val trip = trips.trip.value
        val today = LocalDate.now()
        val loose = trips.unbookedEssentials()
        val emptyDays = trip.dates.filter { !it.isBefore(today) && trips.itemsOn(it).isEmpty() }
        val budget = trips.budget()

        val opener = when {
            trip.startDate.isAfter(today) -> {
                val days = trip.daysUntilStart()
                "${days} days until ${trip.destination.substringBefore(",")}."
            }
            else -> "Day ${trip.dayNumber(today)} of ${trip.dayCount}, ${trip.daysRemaining()} still to shape."
        }

        val focus = when {
            loose.isNotEmpty() -> " ${loose.size} ${if (loose.size == 1) "thing" else "things"} still need booking — ${loose.first().title} is the urgent one."
            emptyDays.isNotEmpty() -> " ${emptyDays.size} ${if (emptyDays.size == 1) "day has" else "days have"} nothing in them yet."
            budget.isOverrun -> " The plan currently runs ${money(-budget.variance)} over budget."
            else -> " Everything's booked and the budget holds."
        }

        val exploreDay = when {
            emptyDays.isNotEmpty() -> trip.dayNumber(emptyDays.first())
            trip.startDate.isAfter(today) -> 1
            else -> trip.dayNumber(today)
        }

        return AgentReply(
            say = opener + focus,
            intents = listOf(if (loose.isNotEmpty()) CardIntent.Loose else CardIntent.TripOverview),
            suggestions = chips(
                "Show me the whole trip",
                "How's our budget looking?",
                "Help me plan day $exploreDay"
            )
        )
    }

    fun respond(history: List<ChatTurn>, input: String): AgentReply {
        val text = input.lowercase().trim()
        val previous = history.lastOrNull { it.speaker == Speaker.WONDER }?.text.orEmpty().lowercase()

        if (previous.contains("which day") && text.split(" ").size <= 4) {
            return dayPlan(text)
        }

        tryMutations(input)?.let { return it }

        if (matches(text, FLIGHT_SEARCH)) return searchFlights(text)

        return when {
            text.isBlank() -> greeting()
            matches(text, CONNECT) -> connect()
            matches(text, MODE_SWITCH) -> modeExplainer()
            wantsDraft(text) -> draftDay(input)
            matches(text, NEARBY) -> nearby(text)
            matches(text, EXPENSE_LOG) -> expenseLog()
            matches(text, MONEY) -> budgetAnswer(text)
            matches(text, NOW) -> now()
            matches(text, LOOSE) -> loose()
            matches(text, WHOLE_TRIP) -> overview()
            matches(text, DAY) -> dayPlan(text)
            matches(text, EDIT) -> openPlan()
            matches(text, CAPABILITY) -> capability()
            matches(text, GREETING) -> greeting()
            matches(text, THANKS) -> AgentReply(
                say = "Any time. I'm right here.",
                intents = emptyList(),
                suggestions = chips("What's next?", "How's our budget?")
            )
            else -> travelAnswer(text, input)
        }
    }

    // region intents

    private fun now(): AgentReply {
        val (current, next) = trips.nowAndNext()
        val say = when {
            current != null -> "Right now: ${current.title}${location(current.location)}. ${
                next?.let { "Then ${it.title} at ${it.startTime?.format(clock)}." } ?: "Nothing after it."
            }"
            next != null && next.date == LocalDate.now() ->
                "${next.title} at ${next.startTime?.format(clock)}, ${trips.minutesUntil(next)} minutes away${location(next.location)}."
            next != null -> "Nothing more today. Tomorrow opens with ${next.title} at ${next.startTime?.format(clock)}."
            else -> "Nothing scheduled — the day is entirely yours."
        }
        return AgentReply(
            say = say,
            intents = listOf(CardIntent.NowNext),
            suggestions = chips("What's free nearby?", "What's on tomorrow?", "How much have we spent?")
        )
    }

    private fun dayPlan(text: String): AgentReply {
        val date = detectDate(text)
        val items = trips.itemsOn(date)
        val trip = trips.trip.value
        val label = when (date) {
            LocalDate.now() -> "today"
            LocalDate.now().plusDays(1) -> "tomorrow"
            else -> "day ${trip.dayNumber(date)}"
        }

        if (items.isEmpty()) {
            return AgentReply(
                say = "Nothing on $label yet. Want me to draft something?",
                intents = emptyList(),
                suggestions = chips("Help me draft a relaxed day", "Show me the whole trip", "What's free nearby?")
            )
        }

        val cost = items.sumOf { it.expectedTotal(trip.partySize) }
        val first = items.firstOrNull { it.startTime != null }
        return AgentReply(
            say = "${items.size} ${plural(items.size, "thing")} on $label${
                first?.let { ", starting with ${it.title} at ${it.startTime?.format(clock)}" } ?: ""
            }. ${if (cost > 0) "Costs about ${money(cost)} for the three of you." else "Nothing to pay."}",
            intents = listOf(CardIntent.DayPlan(date)),
            suggestions = chips("What's still unbooked?", "Help me add something to this day", "How's our budget?")
        )
    }

    private fun overview(): AgentReply {
        val trip = trips.trip.value
        val planned = trip.dates.count { trips.itemsOn(it).isNotEmpty() }
        return AgentReply(
            say = "${trip.dayCount} days in ${trip.destination.substringBefore(",")}, $planned of them with something in. " +
                "Projected spend is ${money(trips.budget().projected)}.",
            intents = listOf(CardIntent.TripOverview),
            suggestions = chips("What's still unbooked?", "Which days are empty?", "Show me the full plan")
        )
    }

    private fun budgetAnswer(text: String): AgentReply {
        val budget = trips.budget()
        val detailed = matches(text, listOf("breakdown", "detail", "category", "categories", "where"))

        val say = when {
            budget.isOverrun ->
                "Spent ${money(budget.spent)} of ${money(budget.budget)}. With what's still to pay you're heading ${money(-budget.variance)} over."
            budget.stillToPay > 0 ->
                "Spent ${money(budget.spent)} so far, ${money(budget.stillToPay)} still to pay. That lands you ${money(budget.variance)} under budget."
            else ->
                "Spent ${money(budget.spent)} of ${money(budget.budget)}, and nothing left outstanding."
        }

        val extras = if (budget.unplannedSpent > 0) {
            " ${money(budget.unplannedSpent)} of that wasn't planned."
        } else {
            ""
        }

        return AgentReply(
            say = say + extras,
            intents = listOf(CardIntent.Budget(detailed)),
            suggestions = chips("What did we spend it on?", "Show me the expense tracker", "How's our budget?")
        )
    }

    private fun expenseLog(): AgentReply {
        val budget = trips.budget()
        val expenses = trips.expenses.value
        val biggest = expenses.maxByOrNull { it.amount }
        return AgentReply(
            say = "${expenses.size} expenses logged, ${money(budget.spent)} in total${
                biggest?.let { ", the largest being ${it.label.lowercase()} at ${money(it.amount)}" } ?: ""
            }.",
            intents = listOf(CardIntent.Expenses),
            suggestions = chips("How's our budget?", "Show me the expense tracker", "Show me what was unplanned")
        )
    }

    private fun nearby(text: String): AgentReply {
        val kind = detectKind(text)
        val windows = trips.freeWindows(LocalDate.now())
        val gap = windows.maxByOrNull { it.minutes }

        return AgentReply(
            say = when {
                gap != null && gap.minutes >= 90 ->
                    "You've got ${gap.minutes / 60} free ${plural(gap.minutes / 60, "hour")} from ${gap.start.format(clock)}. Here's what I'd do with it."
                gap != null -> "There's a short gap around ${gap.start.format(clock)} — these would fit."
                else -> "The day's pretty full, but these are worth knowing about."
            },
            intents = listOf(CardIntent.Nearby(kind)),
            suggestions = chips("Show me something cheaper", "How's our budget?", "What's next?")
        )
    }

    private fun loose(): AgentReply {
        val items = trips.unbookedEssentials()
        if (items.isEmpty()) {
            return AgentReply(
                say = "Nothing outstanding — everything that needs booking is booked.",
                intents = listOf(CardIntent.Budget(false)),
                suggestions = chips("How's our budget?", "Show me the whole trip")
            )
        }
        val soonest = items.first()
        return AgentReply(
            say = "${items.size} ${plural(items.size, "thing")} still unbooked. ${soonest.title} is the one I'd sort first — it's on ${
                soonest.date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))
            }.",
            intents = listOf(CardIntent.Loose),
            suggestions = chips("Show me the full plan", "How's our budget?", "What's on tomorrow?")
        )
    }

    private fun draftDay(input: String): AgentReply {
        val trip = trips.trip.value
        val city = detectCity(input) ?: trip.destination
        val date = detectDate(input.lowercase(), fallbackToEmptyDay = true)

        val request = TourBuildRequest(
            city = city,
            interests = detectInterests(input).ifEmpty { trip.interests },
            pace = detectPace(input),
            duration = detectDuration(input),
            budget = detectBudget(input),
            groupSize = trip.partySize,
            notes = ""
        )

        return AgentReply(
            say = "Here's a ${request.duration.label.lowercase()} in ${city.substringBefore(",")}, " +
                "paced ${request.pace.label.lowercase()} for the ${numberWord(trip.partySize)} of you. Say the word and I'll drop it into the trip.",
            intents = listOf(CardIntent.DraftDay(request, date)),
            suggestions = chips("Make it cheaper for us", "Make it more relaxed", "Show me the whole trip")
        )
    }

    private fun openPlan(): AgentReply = AgentReply(
        say = "Everything's editable in the full plan — times, costs, who's coming, all of it.",
        intents = listOf(CardIntent.OpenPlan),
        suggestions = chips("What's still unbooked?", "How's our budget?")
    )

    private fun modeExplainer(): AgentReply {
        val mode = trips.mode.value
        return AgentReply(
            say = if (mode == TripMode.WANDERING) {
                "You're in Wandering — I'm watching the clock and what's around you. Switch to Planning from the chip up top when you want to reshape the days."
            } else {
                "You're in Planning — I'm thinking about the whole trip and what it'll cost. Switch to Wandering from the chip up top once you're out there."
            },
            intents = emptyList(),
            suggestions = chips("What's next?", "How's our budget?")
        )
    }

    private fun connect(): AgentReply = AgentReply(
        say = "You can run Gemma free on this phone through Google AI Edge Gallery — open model settings for the steps. Or connect a cloud key for OpenAI, Gemini, Claude, Mistral, or any OpenAI-compatible API.",
        intents = listOf(CardIntent.Connect),
        suggestions = chips("What's next?", "How's our budget?")
    )

    private fun capability(): AgentReply {
        val mode = trips.mode.value
        val trip = trips.trip.value
        val city = trip.destination.substringBefore(",")
        return AgentReply(
            say = if (mode == TripMode.WANDERING) {
                "Ask me what's next, what's nearby, or what you've spent. I can log expenses, move things on the calendar, and nudge you before things start."
            } else {
                "Ask me about any day, what's still unbooked, or what the trip will cost. I can change the budget, add stops to the plan, log spending, and draft whole days."
            },
            intents = emptyList(),
            suggestions = chips("What's on today?", "How's our budget?", "Help me draft a day in $city")
        )
    }

    private fun travelAnswer(text: String, input: String): AgentReply {
        val trip = trips.trip.value
        val city = trip.destination.substringBefore(",").trim()
        val pois = CityCatalog.getPois(CityCatalog.resolveCity(trip.destination))
        val picks = pickPoisForQuestion(pois, text)
        val featuredPois = when {
            picks.isNotEmpty() -> picks
            matches(text, PEOPLE_WATCH) -> emptyList()
            else -> spotlightPois(pois, text)
        }

        val say = when {
            picks.isNotEmpty() -> formatPoiAnswer(city, text, picks)
            matches(text, PEOPLE_WATCH) -> peopleWatchAnswer(city)
            matches(text, FOOD_ASK) -> foodAnswer(city, featuredPois.firstOrNull())
            matches(text, VIEW_ASK) -> viewAnswer(city, featuredPois.firstOrNull())
            matches(text, WHERE_ASK) || matches(text, WHAT_ASK) || text.contains("?") ->
                generalDiscoveryAnswer(city, featuredPois.firstOrNull())
            else -> generalDiscoveryAnswer(city, featuredPois.firstOrNull())
        }

        return AgentReply(
            say = say,
            intents = if (featuredPois.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    CardIntent.Nearby(
                        kind = detectKind(text),
                        featuredTitles = featuredPois.map { it.name }
                    )
                )
            },
            suggestions = chips(
                "What's free nearby?",
                "Help me plan a relaxed day",
                "Show me the whole trip"
            )
        )
    }

    private fun spotlightPois(pois: List<PointOfInterest>, text: String): List<PointOfInterest> = when {
        matches(text, FOOD_ASK) -> pois.filter { it.category == TourInterest.FOOD }.shuffled().take(2)
        matches(text, VIEW_ASK) -> pois.filter { it.category == TourInterest.NATURE }.shuffled().take(2)
        matches(text, NIGHT_ASK) -> pois.filter { it.category == TourInterest.NIGHTLIFE }.shuffled().take(2)
        matches(text, SHOP_ASK) -> pois.filter { it.category == TourInterest.SHOPPING }.shuffled().take(2)
        matches(text, CULTURE_ASK) -> pois.filter {
            it.category == TourInterest.CULTURE || it.category == TourInterest.ART
        }.shuffled().take(2)
        matches(text, OUTDOOR_ASK) -> pois.filter {
            it.category == TourInterest.NATURE || it.category == TourInterest.ADVENTURE
        }.shuffled().take(2)
        else -> pois.shuffled().take(2)
    }

    private fun pickPoisForQuestion(pois: List<PointOfInterest>, text: String): List<PointOfInterest> {
        val themes = when {
            matches(text, PEOPLE_WATCH) -> setOf(TourInterest.LOCAL, TourInterest.FOOD, TourInterest.CULTURE)
            matches(text, FOOD_ASK) -> setOf(TourInterest.FOOD)
            matches(text, VIEW_ASK) -> setOf(TourInterest.NATURE)
            matches(text, NIGHT_ASK) -> setOf(TourInterest.NIGHTLIFE)
            matches(text, SHOP_ASK) -> setOf(TourInterest.SHOPPING)
            matches(text, CULTURE_ASK) -> setOf(TourInterest.CULTURE, TourInterest.ART)
            matches(text, OUTDOOR_ASK) -> setOf(TourInterest.NATURE, TourInterest.ADVENTURE)
            else -> TourInterest.entries.toSet()
        }

        val themed = pois.filter { it.category in themes }
        val pool = if (themed.isNotEmpty()) themed else pois

        if (matches(text, PEOPLE_WATCH)) {
            val sitSpots = pool.filter { poi ->
                poi.category == TourInterest.LOCAL ||
                    poi.name.contains("caf", ignoreCase = true) ||
                    poi.name.contains("tasca", ignoreCase = true) ||
                    poi.name.contains("market", ignoreCase = true) ||
                    poi.name.contains("miradouro", ignoreCase = true) ||
                    poi.name.contains("plaza", ignoreCase = true) ||
                    poi.name.contains("quarter", ignoreCase = true) ||
                    poi.description.contains("wander", ignoreCase = true) ||
                    poi.description.contains("caf", ignoreCase = true)
            }
            if (sitSpots.isNotEmpty()) return sitSpots.shuffled().take(2)
        }

        return pool.shuffled().take(2)
    }

    private fun formatPoiAnswer(city: String, text: String, picks: List<PointOfInterest>): String {
        val lead = when {
            matches(text, PEOPLE_WATCH) -> "For people-watching in $city"
            matches(text, FOOD_ASK) -> "For food in $city"
            matches(text, VIEW_ASK) -> "For views in $city"
            else -> "In $city"
        }
        val primary = picks.first()
        val secondary = picks.getOrNull(1)
        return buildString {
            append("$lead, I'd start with ${primary.name} — ${primary.description.trimEnd('.')}.")
            secondary?.let { append(" ${it.name} is another good bet — ${it.tip}") }
                ?: append(" ${primary.tip}")
        }
    }

    private fun peopleWatchAnswer(city: String): String =
        "In $city, find a café or square one street off the main drag — order something small and stay awhile. " +
            "Markets, old-town benches, and waterfront promenades are usually better than the famous viewpoint everyone queues for."

    private fun foodAnswer(city: String, food: PointOfInterest?): String {
        return if (food != null) {
            "For food in $city, try ${food.name} — ${food.description.trimEnd('.')}. ${food.tip}"
        } else {
            "In $city, I'd eat where locals queue at lunch — a market counter or neighbourhood spot away from the postcard strip."
        }
    }

    private fun viewAnswer(city: String, view: PointOfInterest?): String {
        return if (view != null) {
            "${view.name} is worth it — ${view.description.trimEnd('.')}. ${view.tip}"
        } else {
            "In $city, head uphill or to the waterfront around golden hour — that's when the city looks its best."
        }
    }

    private fun generalDiscoveryAnswer(city: String, pick: PointOfInterest?): String {
        return if (pick != null) {
            "Good question. In $city I'd look at ${pick.name} — ${pick.description.trimEnd('.')}. ${pick.tip}"
        } else {
            "Happy to help you explore $city — tell me if you're thinking food, views, a slow afternoon, or something livelier."
        }
    }

    private fun unsure(): AgentReply = AgentReply(
        say = "I didn't quite follow. I can change the budget, log expenses, add or move plan items, and show what's next.",
        intents = emptyList(),
        suggestions = chips("What's next?", "How's our budget?", "What's free nearby?")
    )

    // region mutations

    private fun tryMutations(input: String): AgentReply? {
        val text = input.lowercase().trim()
        return setBudgetMutation(text)
            ?: logExpenseMutation(input, text)
            ?: addItemMutation(input, text)
            ?: removeItemMutation(text)
            ?: rescheduleMutation(input, text)
            ?: markStatusMutation(text)
    }

    private fun setBudgetMutation(text: String): AgentReply? {
        if (!matches(text, listOf("set budget", "change budget", "raise budget", "update budget", "budget to", "increase budget"))) {
            return null
        }
        val amount = extractAmount(text) ?: return null
        return AgentReply(
            say = "Done — budget is now ${money(amount)}.",
            intents = listOf(CardIntent.Budget(detailed = false)),
            actions = listOf(AgentAction.SetBudget(amount)),
            suggestions = chips("How's our budget now?", "Show me the whole trip", "What's still unbooked?")
        )
    }

    private fun logExpenseMutation(input: String, text: String): AgentReply? {
        val amount = extractAmount(text) ?: return null
        val wantsLog = matches(text, listOf("log expense", "add expense", "log a", "spent", "paid", "we spent", "i spent"))
        if (!wantsLog) return null

        val label = extractExpenseLabel(input) ?: "Expense"
        val category = detectExpenseCategory(text)
        return AgentReply(
            say = "Logged ${money(amount)} for ${label.lowercase()}.",
            intents = listOf(CardIntent.Expenses),
            actions = listOf(
                AgentAction.AddExpense(
                    label = label,
                    amount = amount,
                    category = category,
                    date = LocalDate.now(),
                    note = ""
                )
            ),
            suggestions = chips("How's our budget?", "What else did we spend?", "What's next?")
        )
    }

    private fun addItemMutation(input: String, text: String): AgentReply? {
        if (!matches(text, listOf("add ", "schedule ", "put ", "book ", "plan "))) return null
        if (matches(text, listOf("expense", "budget"))) return null

        val title = extractQuotedOrAfter(input, listOf("add ", "schedule ", "put ", "book ", "plan "))
            ?.substringBefore(" on ")
            ?.substringBefore(" to ")
            ?.substringBefore(" for ")
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.length >= 3 }
            ?: return null

        val date = detectDate(text)
        val kind = detectKind(text) ?: ItemKind.ACTIVITY
        val time = extractTime(text)

        return AgentReply(
            say = "Added $title on ${dayLabel(date)}${time?.let { " at ${it.format(clock)}" } ?: ""}.",
            intents = listOf(CardIntent.DayPlan(date)),
            actions = listOf(
                AgentAction.AddItem(
                    title = title.replaceFirstChar { it.titlecase() },
                    date = date,
                    kind = kind,
                    startTime = time,
                    durationMinutes = 90,
                    location = trips.trip.value.destination.substringBefore(","),
                    notes = "",
                    estimatedCost = 0.0,
                    costIsPerPerson = true,
                    status = ItemStatus.PLANNED
                )
            ),
            suggestions = chips("What's on that day?", "How's our budget?", "What else should we add?")
        )
    }

    private fun removeItemMutation(text: String): AgentReply? {
        if (!matches(text, listOf("remove ", "delete ", "cancel ", "drop "))) return null
        val matchTitle = text
            .substringAfter("remove ")
            .substringAfter("delete ")
            .substringAfter("cancel ")
            .substringAfter("drop ")
            .substringBefore(" from")
            .substringBefore(" on")
            .trim()
            .trim('"')
            .takeIf { it.length >= 3 }
            ?: return null

        return AgentReply(
            say = "Removed ${matchTitle.replaceFirstChar { it.titlecase() }} from the plan.",
            intents = listOf(CardIntent.TripOverview),
            actions = listOf(AgentAction.RemoveItem(itemId = null, matchTitle = matchTitle)),
            suggestions = chips("Show me the whole trip", "How's our budget?", "What's still unbooked?")
        )
    }

    private fun rescheduleMutation(input: String, text: String): AgentReply? {
        if (!matches(text, listOf("move ", "reschedule ", "shift ", "push "))) return null
        val matchTitle = text
            .substringAfter("move ")
            .substringAfter("reschedule ")
            .substringAfter("shift ")
            .substringAfter("push ")
            .substringBefore(" to ")
            .substringBefore(" on ")
            .trim()
            .trim('"')
            .takeIf { it.length >= 3 }
            ?: return null
        val date = detectDate(text)

        return AgentReply(
            say = "Moved ${matchTitle.replaceFirstChar { it.titlecase() }} to ${dayLabel(date)}.",
            intents = listOf(CardIntent.DayPlan(date)),
            actions = listOf(
                AgentAction.UpdateItem(
                    itemId = null,
                    matchTitle = matchTitle,
                    date = date,
                    startTime = extractTime(text)
                )
            ),
            suggestions = chips("What's on that day?", "Show me the whole trip", "How's our budget?")
        )
    }

    private fun markStatusMutation(text: String): AgentReply? {
        val status = when {
            matches(text, listOf("mark", "booked", "confirm")) && text.contains("book") -> ItemStatus.BOOKED
            matches(text, listOf("mark", "done", "finished")) && text.contains("done") -> ItemStatus.DONE
            else -> return null
        }
        val matchTitle = text
            .substringAfter("mark ")
            .substringBefore(" as ")
            .substringBefore(" booked")
            .substringBefore(" done")
            .trim()
            .trim('"')
            .takeIf { it.length >= 3 }
            ?: return null

        return AgentReply(
            say = "Marked ${matchTitle.replaceFirstChar { it.titlecase() }} as ${status.label.lowercase()}.",
            intents = listOf(CardIntent.Loose),
            actions = listOf(
                AgentAction.UpdateItem(
                    itemId = null,
                    matchTitle = matchTitle,
                    status = status
                )
            ),
            suggestions = chips("What's still unbooked?", "How's our budget?", "Show me the whole trip")
        )
    }

    private fun extractAmount(text: String): Double? {
        val match = Regex("(?:€|\\$|£)?\\s*(\\d+(?:[.,]\\d+)?)").find(text) ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()
    }

    private fun extractExpenseLabel(input: String): String? {
        val onFor = Regex("(?:on|for|at)\\s+(.+)", RegexOption.IGNORE_CASE).find(input)?.groupValues?.get(1)
        return onFor
            ?.trim()
            ?.trimEnd('.', '!')
            ?.takeIf { it.length in 3..60 }
    }

    private fun extractQuotedOrAfter(input: String, prefixes: List<String>): String? {
        val quoted = Regex("\"([^\"]+)\"").find(input)?.groupValues?.get(1)
        if (!quoted.isNullOrBlank()) return quoted
        val lower = input.lowercase()
        val prefix = prefixes.firstOrNull { lower.contains(it) } ?: return null
        return input.substringAfter(prefix, "").trim().takeIf { it.isNotBlank() }
    }

    private fun extractTime(text: String): LocalTime? {
        val atMatch = Regex("at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(text) ?: return null
        val hour = atMatch.groupValues[1].toIntOrNull() ?: return null
        val minute = atMatch.groupValues[2].toIntOrNull() ?: 0
        val meridiem = atMatch.groupValues[3]
        var h = hour
        if (meridiem == "pm" && h < 12) h += 12
        if (meridiem == "am" && h == 12) h = 0
        return runCatching { LocalTime.of(h.coerceIn(0, 23), minute.coerceIn(0, 59)) }.getOrNull()
    }

    private fun detectExpenseCategory(text: String): ExpenseCategory? = when {
        matches(text, listOf("lunch", "dinner", "breakfast", "food", "restaurant", "coffee", "drink")) -> ExpenseCategory.FOOD
        matches(text, listOf("taxi", "metro", "train", "flight", "uber", "transport")) -> ExpenseCategory.TRAVEL
        matches(text, listOf("hotel", "stay", "airbnb")) -> ExpenseCategory.STAY
        matches(text, listOf("ticket", "museum", "tour", "activity")) -> ExpenseCategory.ACTIVITIES
        matches(text, listOf("shop", "souvenir", "market")) -> ExpenseCategory.SHOPPING
        else -> ExpenseCategory.OTHER
    }

    private fun dayLabel(date: LocalDate): String = when (date) {
        LocalDate.now() -> "today"
        LocalDate.now().plusDays(1) -> "tomorrow"
        else -> "day ${trips.trip.value.dayNumber(date)}"
    }

    private fun searchFlights(text: String): AgentReply {
        val trip = trips.trip.value
        return AgentReply(
            say = "Searching live fares for ${trip.destination.substringBefore(",")} — results in a moment.",
            intents = listOf(
                CardIntent.SearchFlights(
                    origin = null,
                    destination = trip.destination,
                    departDate = trip.startDate,
                    returnDate = trip.endDate,
                    adults = trip.partySize
                )
            ),
            suggestions = chips("How's our budget?", "What's still unbooked?", "Show me the whole trip")
        )
    }

    // endregion

    // region reading the request

    /**
     * "Plan a relaxed food day in Sintra" is a request to build something; "what's the plan for
     * today" is a question about what already exists. The difference is whether the sentence opens
     * as a question, so an asking phrase vetoes the drafting verbs.
     */
    private fun wantsDraft(text: String): Boolean {
        if (matches(text, DRAFT)) return true
        if (ASKING.any { text.startsWith(it) }) return false
        return DRAFT_VERBS.any { text.contains(it) } && DRAFT_SUBJECTS.any { text.contains(it) }
    }

    private fun detectDate(text: String, fallbackToEmptyDay: Boolean = false): LocalDate {
        val trip = trips.trip.value
        val today = LocalDate.now()

        Regex("day\\s+(\\d+)").find(text)?.let { match ->
            val number = match.groupValues[1].toIntOrNull()
            if (number != null && number in 1..trip.dayCount) {
                return trip.startDate.plusDays((number - 1).toLong())
            }
        }

        trip.dates.forEach { date ->
            val name = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).lowercase()
            if (text.contains(name)) return date
        }

        return when {
            text.contains("tomorrow") -> today.plusDays(1)
            text.contains("today") || text.contains("tonight") -> today
            text.contains("yesterday") -> today.minusDays(1)
            text.contains("last day") -> trip.endDate
            text.contains("first day") -> trip.startDate
            fallbackToEmptyDay ->
                trip.dates.firstOrNull { !it.isBefore(today) && trips.itemsOn(it).isEmpty() } ?: today
            else -> today
        }
    }

    private fun detectCity(input: String): String? {
        val lower = input.lowercase()
        CityCatalog.knownCities().firstOrNull { lower.contains(it.substringBefore(",").lowercase()) }
            ?.let { return it }
        val match = Regex("\\b(?:in|to|around|at)\\s+([A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)?)").find(input)
        return match?.groupValues?.get(1)?.takeIf { it.length > 2 }
    }

    private fun detectKind(text: String): ItemKind? = when {
        matches(text, listOf("eat", "food", "lunch", "dinner", "breakfast", "restaurant", "hungry")) -> ItemKind.FOOD
        matches(text, listOf("drink", "bar", "night", "cocktail", "club")) -> ItemKind.NIGHTLIFE
        matches(text, listOf("walk", "outdoor", "park", "beach", "hike", "view")) -> ItemKind.OUTDOORS
        matches(text, listOf("shop", "market", "buy", "souvenir")) -> ItemKind.SHOPPING
        matches(text, listOf("museum", "gallery", "see", "visit", "sight")) -> ItemKind.ACTIVITY
        else -> null
    }

    private fun detectInterests(input: String): Set<TourInterest> {
        val lower = input.lowercase()
        return TourInterest.entries.filter { interest ->
            INTEREST_WORDS[interest]?.any { lower.contains(it) } == true
        }.toSet()
    }

    private fun detectPace(input: String): TourPace {
        val lower = input.lowercase()
        return when {
            listOf("relaxed", "slow", "easy", "gentle", "chill", "lazy").any { lower.contains(it) } -> TourPace.RELAXED
            listOf("packed", "active", "fast", "busy", "as much as").any { lower.contains(it) } -> TourPace.ACTIVE
            else -> TourPace.BALANCED
        }
    }

    private fun detectDuration(input: String): TourDuration {
        val lower = input.lowercase()
        return when {
            listOf("half day", "half-day", "morning", "afternoon", "few hours").any { lower.contains(it) } -> TourDuration.HALF_DAY
            listOf("weekend", "three day", "3 day").any { lower.contains(it) } -> TourDuration.WEEKEND
            listOf("two day", "2 day", "couple of days").any { lower.contains(it) } -> TourDuration.TWO_DAYS
            else -> TourDuration.FULL_DAY
        }
    }

    private fun detectBudget(input: String): TourBudget {
        val lower = input.lowercase()
        return when {
            listOf("cheap", "budget", "affordable", "free", "save", "tight").any { lower.contains(it) } -> TourBudget.BUDGET
            listOf("premium", "luxury", "splurge", "special", "treat").any { lower.contains(it) } -> TourBudget.PREMIUM
            else -> TourBudget.MID_RANGE
        }
    }

    // endregion

    private fun location(value: String) = if (value.isBlank()) "" else ", $value"

    private fun money(amount: Double) =
        TripRepository.format(amount, trips.trip.value.currency)

    private fun numberWord(count: Int) = when (count) {
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        else -> count.toString()
    }

    private fun plural(count: Int, singular: String, plural: String = "${singular}s") =
        if (count == 1) singular else plural

    private fun matches(text: String, words: List<String>) = words.any { text.contains(it) }

    private companion object {
        val GREETING = listOf("hello", "hi ", "hey", "good morning", "good evening", "morning", "yo ")
        val THANKS = listOf("thank", "thanks", "cheers", "appreciate", "nice one")
        val CAPABILITY = listOf("what can you", "help me", "what do you do", "who are you", "how does this")
        val CONNECT = listOf("api key", "connect a key", "which model", "what model", "openai", "gemini", "claude", "anthropic", "mistral", "subscription")
        val MODE_SWITCH = listOf("wandering mode", "planner mode", "planning mode", "switch mode", "what mode", "which mode")
        val NOW = listOf("what's next", "whats next", "right now", "what now", "happening now", "next up", "where do i need", "am i late")
        val DAY = listOf("today", "tomorrow", "day ", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "schedule", "itinerary for", "tonight")
        val WHOLE_TRIP = listOf("whole trip", "the trip", "all the days", "overview", "everything", "full plan", "how many days")
        val MONEY = listOf("budget", "spend", "spent", "cost", "money", "afford", "expensive", "over budget", "left to spend", "how much")
        val EXPENSE_LOG = listOf("expenses", "expense", "receipts", "what did we spend", "spent it on", "transactions", "log")
        val NEARBY = listOf("nearby", "near me", "around here", "what's around", "recommend", "suggestion", "worth doing", "free time", "bored", "hungry", "close by")
        val LOOSE = listOf("unbooked", "not booked", "still book", "need to book", "outstanding", "to do", "todo", "loose ends", "sort out")
        val DRAFT = listOf("draft", "plan a day", "plan me", "fill the day", "fill this day", "come up with", "design a day", "make a day", "build a day", "what should we do on")
        val DRAFT_VERBS = listOf("plan ", "draft", "design", "build", "sketch", "put together", "organise", "organize", "fill ", "sort us out")
        val DRAFT_SUBJECTS = listOf("day", "morning", "afternoon", "evening", "itinerary", "trip to", "visit to")
        val ASKING = listOf("what", "when", "where", "how", "which", "who", "show ", "tell ", "is ", "are ", "did ", "do we", "have we", "can i see")
        val EDIT = listOf("edit", "change the plan", "open the plan", "full plan", "rearrange", "move something", "reorder")
        val FLIGHT_SEARCH = listOf(
            "flight price", "flight prices", "find flights", "search flights",
            "cheapest flight", "how much are flights", "airfare", "plane ticket", "fly to"
        )
        val PEOPLE_WATCH = listOf(
            "people-watch", "people watch", "peoplewatch", "people watching",
            "sit and watch", "watch the world", "watch life", "watch the crowd",
            "hang out", "chill out", "lazy afternoon", "slow afternoon"
        )
        val FOOD_ASK = listOf(
            "where to eat", "where should we eat", "good place to eat", "best food",
            "restaurant", "lunch", "dinner", "breakfast", "street food", "what to eat"
        )
        val VIEW_ASK = listOf(
            "viewpoint", "view point", "sunset", "sunrise", "panorama", "vista", "lookout", "scenic"
        )
        val NIGHT_ASK = listOf("nightlife", "bar", "bars", "club", "drinks", "cocktail", "after dark")
        val SHOP_ASK = listOf("shop", "shopping", "market", "souvenir", "boutique")
        val CULTURE_ASK = listOf("museum", "gallery", "historic", "history", "culture", "architecture")
        val OUTDOOR_ASK = listOf("hike", "walk", "park", "beach", "nature", "outdoor", "garden")
        val WHERE_ASK = listOf("where", "whaere", "wheres", "where's", "where is", "where are", "where can")
        val WHAT_ASK = listOf(
            "what's a good", "whats a good", "what is a good", "any good",
            "recommend", "suggestion", "worth", "should i", "should we", "can i", "can we"
        )

        val INTEREST_WORDS = mapOf(
            TourInterest.FOOD to listOf("food", "eat", "culinary", "restaurant", "tasting", "wine", "foodie"),
            TourInterest.CULTURE to listOf("culture", "history", "historic", "heritage", "museum", "monument"),
            TourInterest.ADVENTURE to listOf("adventure", "hike", "kayak", "outdoor", "adrenaline", "surf"),
            TourInterest.NATURE to listOf("nature", "park", "garden", "coast", "beach", "scenic", "view"),
            TourInterest.NIGHTLIFE to listOf("nightlife", "bar", "club", "cocktail", "party"),
            TourInterest.SHOPPING to listOf("shopping", "shop", "market", "boutique", "vintage"),
            TourInterest.ART to listOf("art", "gallery", "design", "street art", "architecture"),
            TourInterest.LOCAL to listOf("local", "hidden", "off the beaten", "authentic", "secret", "like a local")
        )
    }
}
