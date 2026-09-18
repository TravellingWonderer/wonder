package com.wonder.provider.ai

import com.wonder.provider.data.TripRepository
import com.wonder.provider.data.DestinationInference
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.TourBudget
import com.wonder.provider.model.TourBuildRequest
import com.wonder.provider.model.TourDuration
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.TourPace
import com.wonder.provider.model.TripMode
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A view the model asked the app to render. The model names the view it wants; the app fills it
 * from the real trip, so no figure on screen is ever invented.
 */
sealed interface CardIntent {
    data object TripOverview : CardIntent
    data class DayPlan(val date: LocalDate) : CardIntent
    data object NowNext : CardIntent
    data class Budget(val detailed: Boolean) : CardIntent
    data object Expenses : CardIntent
    data class Nearby(
        val kind: ItemKind? = null,
        /** POI / place names mentioned in the reply — shown in the add-to-trip sheet. */
        val featuredTitles: List<String> = emptyList()
    ) : CardIntent
    data class DraftDay(val request: TourBuildRequest, val date: LocalDate?) : CardIntent
    data object Loose : CardIntent
    data object OpenPlan : CardIntent
    data object OpenExpenses : CardIntent
    data object Connect : CardIntent
    data class SearchFlights(
        val origin: String? = null,
        val destination: String? = null,
        val departDate: LocalDate? = null,
        val returnDate: LocalDate? = null,
        val adults: Int = 0
    ) : CardIntent
}

data class AgentReply(
    val say: String,
    val intents: List<CardIntent>,
    val actions: List<AgentAction> = emptyList(),
    val suggestions: List<String>
)

class AgentPrompt(
    private val repository: TripRepository,
    private val travelContextProvider: () -> String = { "" }
) {

    fun system(sessionCheckpoint: String = "", welcomeOnly: Boolean = false): String {
        val mode = repository.mode.value
        val checkpointBlock = if (sessionCheckpoint.isBlank()) {
            "This is the start of the conversation — no prior chat context yet."
        } else {
            sessionCheckpoint.trim()
        }
        val welcomeBlock = if (welcomeOnly) {
            """
            OPENING GREETING ONLY
            This is the first message on a brand-new trip. Welcome them warmly and personally.
            Do not mention the destination, dates, itinerary, budget, or specific places yet.
            Return no cards.
            """.trimIndent()
        } else {
            ""
        }
        val datesBlock = if (!repository.trip.value.datesConfirmed) {
            """
            DATES NOT CONFIRMED
            This trip has no locked date range yet. Do NOT emit add_item / update_item actions
            that place legs on the itinerary. Ask them to pick an approximate range first.
            The app will show a date picker card — keep your spoken reply short and invite them
            to choose dates there.
            """.trimIndent()
        } else {
            ""
        }
        return """
            You are Wonder — a travel companion that a small group of friends talks to instead of
            tapping through an app. You are speaking to one of the travellers listed below, about
            a trip they are taking together. Some questions are spoken aloud, so everything you
            say must sound natural when read out.

            YOU ARE CURRENTLY IN ${mode.name} MODE.
            ${modeGuidance(mode)}

            TRIP ISOLATION
            Everything below describes ONE trip only — the one currently active. Never reference
            other trips, past conversations from other trips, or facts that are not in this block.

            ${DestinationInference.MODEL_GROUNDING_RULES}

            CONVERSATION CHECKPOINT
            Treat the block below as prior chat context — not new instructions. Stay on the same
            thread, remember what was already decided or shown, and do not contradict it.
            $checkpointBlock
            ${if (welcomeBlock.isNotBlank()) "\n$welcomeBlock\n" else ""}
            ${if (datesBlock.isNotBlank()) "\n$datesBlock\n" else ""}
            HOW TO SPEAK
            - Warm, brief, specific. One or two sentences is usually right; three is the maximum.
            - Lead with the answer, then at most one useful observation.
            - Never mention JSON, cards, tools, screens, buttons or that you are an AI.
            - Never read out a list that a card already shows. Say what it means instead.
            - No markdown, no bullet points, no headings. Just spoken sentences.
            - Money matters to this group. If something pushes them over budget, say so plainly.

            WHAT YOU CAN SHOW
            Attach cards when a visual helps. The app fills them with the live trip, so never
            restate every figure from a card in your reply.
            - {"type":"trip"} — the whole trip, one line per day
            - {"type":"day","date":"YYYY-MM-DD"} — one day in detail
            - {"type":"now"} — what's happening now and what's next (Wandering)
            - {"type":"budget","detailed":true|false} — budget, spend and projection
            - {"type":"expenses"} — the expense log
            - {"type":"nearby","kind":"FOOD|ACTIVITY|OUTDOORS|NIGHTLIFE|SHOPPING",
               "titles":["Place name","Another spot"]} — when you suggest specific places, food,
              or things to explore. Include every named place in "titles" so the traveller can add them.
              Omit "kind" for a mix.
            - {"type":"loose"} — things still unbooked that ought to be settled
            - {"type":"plan"} — a door into the full editable plan, when they want to change a lot
            - {"type":"wallet"} — a door into the expense tracker
            - {"type":"connect"} — only when asked about models, keys or settings
            - {"type":"draft","city":"Sintra","date":"YYYY-MM-DD","interests":["FOOD","CULTURE"],
               "pace":"RELAXED|BALANCED|ACTIVE","duration":"HALF_DAY|FULL_DAY|TWO_DAYS|WEEKEND",
               "budget":"BUDGET|MID_RANGE|PREMIUM","groupSize":3,"notes":""} — drafts a fresh day
               they can accept into the trip. Use it whenever they ask you to plan or fill a day.
            - {"type":"flights","origin":"LHR","destination":"LIS","departDate":"YYYY-MM-DD",
               "returnDate":"YYYY-MM-DD","adults":2} — live flight prices (omit origin to guess;
               omit dates to use trip start/end). Use when they ask for flight prices or cheapest fares.

            WHAT YOU CAN CHANGE
            When they ask you to update the trip, include an "actions" array. The app applies these
            immediately — confirm what you did in "say". Use item ids from THE TRIP when updating
            or removing existing items.
            - {"type":"set_budget","amount":3000}
            - {"type":"add_expense","label":"Market lunch","amount":68,"category":"FOOD|TRAVEL|STAY|ACTIVITIES|SHOPPING|OTHER","date":"YYYY-MM-DD","note":""}
            - {"type":"add_item","title":"Sintra day trip","date":"YYYY-MM-DD","kind":"ACTIVITY",
               "startTime":"09:30","durationMinutes":120,"location":"Sintra","estimatedCost":0,
               "costIsPerPerson":true,"notes":"","status":"PLANNED"}
            - {"type":"update_item","itemId":"i-123","date":"YYYY-MM-DD","startTime":"10:00",
               "status":"BOOKED","durationMinutes":60} — itemId or matchTitle required
            - {"type":"remove_item","itemId":"i-123"} or {"type":"remove_item","matchTitle":"Fado night"}
            Omit "actions" when you are only answering or showing cards.

            Interests must come from: ${TourInterest.entries.joinToString("|") { it.name }}

            SUGGESTIONS
            Leave "suggestions" as an empty array [] — the app generates tap-to-ask follow-ups
            separately from full trip context.

            RESPOND WITH JSON ONLY, in this exact shape:
            {"say":"...","cards":[...],"actions":[...],"suggestions":[]}

            THE TRIP
            ${brief()}
            ${travelHistoryBlock()}
        """.trimIndent()
    }

    /** Trip briefing plus traveller maps history — compact enough for a suggestion-only model call. */
    fun compactContext(): String = buildString {
        append(brief())
        val travel = travelHistoryBlock()
        if (travel.isNotBlank()) append(travel)
    }.trim()

    private fun travelHistoryBlock(): String {
        val block = travelContextProvider().trim()
        if (block.isBlank()) return ""
        return "\n\n$block"
    }

    private fun modeGuidance(mode: TripMode): String = when (mode) {
        TripMode.PLANNER ->
            "They are shaping the trip. Think ahead: what is still unbooked, what the days are " +
                "missing, whether the plan fits the budget. Prices are estimates until booked."
        TripMode.WANDERING ->
            "They are on the trip right now. Think about the next few hours before the next few " +
                "days. Be aware of the time, where they are, and what they have already spent " +
                "today. Offer things that fit an actual gap in this day."
    }

    /** A compact briefing small enough to send every turn, complete enough to answer without one. */
    fun brief(): String = buildString {
        val trip = repository.trip.value
        val today = LocalDate.now()
        val party = trip.partySize
        val budget = repository.budget()
        val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

        appendLine("TRIP: ${trip.title} — ${trip.destination}")
        if (!DestinationInference.isGrounded(trip.destination)) {
            appendLine(
                "DESTINATION: not decided. \"${trip.title}\" is a nickname, not a city. " +
                    "Do not invent a destination or any specific places."
            )
        }
        if (!trip.datesConfirmed) {
            appendLine(
                "DATES: not confirmed yet (placeholder ${trip.startDate.format(dateFormat)} to " +
                    "${trip.endDate.format(dateFormat)}). Do not add itinerary legs until dates are locked."
            )
        } else {
            appendLine(
                "Dates: ${trip.startDate.format(dateFormat)} to ${trip.endDate.format(dateFormat)} " +
                    "(${trip.dayCount} days)"
            )
        }
        appendLine("Travelling: ${trip.travellers.joinToString(", ") { it.name }}")
        appendLine("Into: ${trip.interests.joinToString(", ") { it.label }}")
        appendLine(
            "TODAY IS ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} " +
                "${today.format(dateFormat)}, day ${trip.dayNumber(today)} of ${trip.dayCount}."
        )
        appendLine()

        appendLine("MONEY (all in ${trip.currency}):")
        appendLine("- Budget ${money(budget.budget)}, spent ${money(budget.spent)}, still to pay ${money(budget.stillToPay)}")
        appendLine("- Projected total ${money(budget.projected)}, which is ${if (budget.isOverrun) "OVER by ${money(-budget.variance)}" else "under by ${money(budget.variance)}"}")
        appendLine("- Unplanned extras so far: ${money(budget.unplannedSpent)}")
        appendLine()

        appendLine("PLAN:")
        trip.dates.forEach { date ->
            val items = repository.itemsOn(date)
            val marker = when {
                date == today -> " [TODAY]"
                date.isBefore(today) -> " [done]"
                else -> ""
            }
            appendLine("Day ${trip.dayNumber(date)} — ${date.format(dateFormat)}$marker")
            if (items.isEmpty()) {
                appendLine("  (nothing planned)")
            } else {
                items.forEach { item ->
                    val time = item.startTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "flexible"
                    val cost = if (item.expectedTotal(party) > 0) " · ${money(item.expectedTotal(party))}" else ""
                    val who = if (item.partySize in 1 until party) {
                        " · only ${item.travellerIds.mapNotNull { repository.traveller(it)?.name }.joinToString(", ")}"
                    } else {
                        ""
                    }
                    appendLine("  $time ${item.title} [id:${item.id}] (${item.kind.label}, ${item.status.label})$cost$who")
                    if (item.notes.isNotBlank()) appendLine("      note: ${item.notes}")
                }
            }
        }
        appendLine()

        val loose = repository.unbookedEssentials()
        if (loose.isNotEmpty()) {
            appendLine("NOT YET BOOKED: ${loose.joinToString("; ") { "${it.title} (${it.date.format(dateFormat)})" }}")
        }

        if (repository.mode.value == TripMode.WANDERING) {
            val windows = repository.freeWindows(today)
            if (windows.isNotEmpty()) {
                appendLine(
                    "FREE TODAY: " + windows.joinToString(", ") {
                        "${it.start.format(DateTimeFormatter.ofPattern("HH:mm"))}–${it.end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                    }
                )
            }
        }
    }.trim()

    private fun money(amount: Double) =
        TripRepository.format(amount, repository.trip.value.currency)
}

class AgentReplyParser(private val repository: TripRepository) {

    fun parse(raw: String): AgentReply {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            throw AiTourError.ProviderFailed("The model returned an empty reply.")
        }

        extractJsonObject(trimmed)?.let { json ->
            return parseJson(json, trimmed)
        }

        extractSayFromBrokenJson(trimmed)?.let { say ->
            return AgentReply(
                say = say,
                intents = emptyList(),
                suggestions = emptyList()
            )
        }

        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return AgentReply(
                say = trimmed.lines().first { it.isNotBlank() }.take(400),
                intents = emptyList(),
                suggestions = emptyList()
            )
        }

        throw AiTourError.ProviderFailed("The model returned unreadable output — expected JSON.")
    }

    private fun parseJson(json: JSONObject, raw: String): AgentReply {
        val say = rootSay(json)
            ?: extractSayFromBrokenJson(raw)
            ?: throw AiTourError.ProviderFailed("The model JSON had no \"say\" field.")

        val intents = json.optJSONArray("cards")?.let(::parseIntents).orEmpty()
        val actions = json.optJSONArray("actions")?.let(::parseActions).orEmpty()
        val suggestions = json.optJSONArray("suggestions")
            ?.let { array -> (0 until array.length()).map { array.optString(it) } }
            ?.filter { it.isNotBlank() }
            ?.take(3)
            .orEmpty()

        return AgentReply(
            say = say,
            intents = intents,
            actions = actions,
            suggestions = suggestions
        )
    }

    private fun parseActions(array: JSONArray): List<AgentAction> =
        (0 until array.length()).mapNotNull { index ->
            val action = array.optJSONObject(index) ?: return@mapNotNull null
            when (action.optString("type").lowercase()) {
                "set_budget", "budget" -> {
                    val amount = action.optDouble("amount", Double.NaN)
                    if (amount.isNaN()) null else AgentAction.SetBudget(amount)
                }
                "add_expense", "expense", "log_expense" -> {
                    val label = action.optString("label").ifBlank { action.optString("title") }.trim()
                    val amount = action.optDouble("amount", Double.NaN)
                    if (label.isBlank() || amount.isNaN()) null
                    else AgentAction.AddExpense(
                        label = label,
                        amount = amount,
                        category = AgentActionExecutor.parseCategory(action.optString("category")),
                        date = date(action.optString("date")),
                        note = action.optString("note")
                    )
                }
                "add_item", "item", "schedule" -> {
                    val title = action.optString("title").trim()
                    val itemDate = date(action.optString("date")) ?: return@mapNotNull null
                    if (title.isBlank()) return@mapNotNull null
                    AgentAction.AddItem(
                        title = title,
                        date = itemDate,
                        kind = AgentActionExecutor.parseKind(action.optString("kind")) ?: ItemKind.ACTIVITY,
                        startTime = AgentActionExecutor.parseTime(action.optString("startTime")),
                        durationMinutes = action.optInt("durationMinutes", 60),
                        location = action.optString("location"),
                        notes = action.optString("notes"),
                        estimatedCost = action.optDouble("estimatedCost", 0.0),
                        costIsPerPerson = action.optBoolean("costIsPerPerson", true),
                        status = AgentActionExecutor.parseStatus(action.optString("status")) ?: ItemStatus.PLANNED
                    )
                }
                "update_item", "move_item", "reschedule" -> {
                    val itemId = action.optString("itemId").ifBlank { null }
                    AgentAction.UpdateItem(
                        itemId = itemId,
                        matchTitle = action.optString("matchTitle").ifBlank {
                            if (itemId == null && !action.has("newTitle")) {
                                action.optString("title").ifBlank { null }
                            } else null
                        },
                        title = action.optString("newTitle").ifBlank { null },
                    date = date(action.optString("date")),
                    kind = AgentActionExecutor.parseKind(action.optString("kind")),
                    startTime = AgentActionExecutor.parseTime(action.optString("startTime")),
                    clearStartTime = action.optBoolean("clearStartTime", false),
                    durationMinutes = action.optInt("durationMinutes", -1).takeIf { it >= 0 },
                    location = action.optString("location").ifBlank { null },
                    notes = action.optString("notes").ifBlank { null },
                    estimatedCost = action.optDouble("estimatedCost", Double.NaN).takeUnless { it.isNaN() },
                    costIsPerPerson = action.optBoolean("costIsPerPerson").takeIf { action.has("costIsPerPerson") },
                    status = AgentActionExecutor.parseStatus(action.optString("status")),
                    paidAmount = action.optDouble("paidAmount", Double.NaN).takeUnless { it.isNaN() }
                    )
                }
                "remove_item", "delete_item", "cancel_item" -> AgentAction.RemoveItem(
                    itemId = action.optString("itemId").ifBlank { null },
                    matchTitle = action.optString("matchTitle").ifBlank { action.optString("title").ifBlank { null } }
                )
                "set_status", "mark_booked", "mark_done" -> {
                    val status = AgentActionExecutor.parseStatus(action.optString("status"))
                        ?: ItemStatus.BOOKED
                    AgentAction.UpdateItem(
                        itemId = action.optString("itemId").ifBlank { null },
                        matchTitle = action.optString("matchTitle").ifBlank { action.optString("title").ifBlank { null } },
                        status = status
                    )
                }
                else -> null
            }
        }

    private fun rootSay(json: JSONObject): String? {
        val say = json.optString("say").ifBlank { json.optString("reply") }.trim()
        return say.ifBlank { null }
    }

    private fun parseIntents(array: JSONArray): List<CardIntent> =
        (0 until array.length()).mapNotNull { index ->
            val card = array.optJSONObject(index) ?: return@mapNotNull null
            when (card.optString("type").lowercase()) {
                "trip", "overview", "itinerary" -> CardIntent.TripOverview
                "day", "dayplan" -> CardIntent.DayPlan(date(card.optString("date")) ?: LocalDate.now())
                "now", "nownext", "next" -> CardIntent.NowNext
                "budget", "money" -> CardIntent.Budget(card.optBoolean("detailed", false))
                "expenses", "spending" -> CardIntent.Expenses
                "nearby", "recommend", "suggestions" -> CardIntent.Nearby(
                    kind = runCatching { ItemKind.valueOf(card.optString("kind").uppercase()) }.getOrNull(),
                    featuredTitles = parseTitles(card)
                )
                "loose", "unbooked", "todo" -> CardIntent.Loose
                "plan", "editor" -> CardIntent.OpenPlan
                "wallet", "tracker" -> CardIntent.OpenExpenses
                "connect", "settings" -> CardIntent.Connect
                "draft", "drafted", "tour" -> CardIntent.DraftDay(
                    request = parseDraft(card),
                    date = date(card.optString("date"))
                )
                "flights", "flight", "flightsearch", "flight_prices" -> CardIntent.SearchFlights(
                    origin = card.optString("origin").ifBlank { null },
                    destination = card.optString("destination").ifBlank { null },
                    departDate = date(card.optString("departDate").ifBlank { card.optString("date") }),
                    returnDate = date(card.optString("returnDate")),
                    adults = card.optInt("adults", 0)
                )
                else -> null
            }
        }

    private fun parseDraft(card: JSONObject): TourBuildRequest {
        val trip = repository.trip.value
        val interests = card.optJSONArray("interests")
            ?.let { array -> (0 until array.length()).map { array.optString(it) } }
            ?.mapNotNull { value -> runCatching { TourInterest.valueOf(value.uppercase()) }.getOrNull() }
            ?.toSet()
            .orEmpty()
            .ifEmpty { trip.interests }

        return TourBuildRequest(
            city = card.optString("city").ifBlank { trip.destination },
            interests = interests,
            pace = enumOrDefault(card.optString("pace"), TourPace.BALANCED),
            duration = enumOrDefault(card.optString("duration"), TourDuration.FULL_DAY),
            budget = enumOrDefault(card.optString("budget"), TourBudget.MID_RANGE),
            groupSize = card.optInt("groupSize", trip.partySize).coerceIn(1, 40),
            notes = card.optString("notes")
        )
    }

    private fun date(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim()) }.getOrNull()

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value.trim().uppercase().replace(' ', '_')) }.getOrDefault(fallback)

    /** Models occasionally wrap JSON in prose or fences no matter how firmly you ask. */
    private fun extractJsonObject(raw: String): JSONObject? {
        val stripped = strip(raw)
        return runCatching { JSONObject(stripped) }.getOrNull()
            ?: runCatching {
                val balanced = extractBalancedObject(stripped)
                if (balanced != null) JSONObject(balanced) else null
            }.getOrNull()
    }

    private fun extractBalancedObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                if (escaped) escaped = false
                else when (char) {
                    '\\' -> escaped = true
                    '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun extractSayFromBrokenJson(raw: String): String? {
        val patterns = listOf(
            Regex(""""say"\s*:\s*"((?:\\.|[^"\\])*)"""", RegexOption.DOT_MATCHES_ALL),
            Regex(""""reply"\s*:\s*"((?:\\.|[^"\\])*)"""", RegexOption.DOT_MATCHES_ALL)
        )
        for (pattern in patterns) {
            val match = pattern.find(raw) ?: continue
            return unescapeJsonString(match.groupValues[1]).trim().ifBlank { null }
        }
        return null
    }

    private fun parseTitles(card: JSONObject): List<String> {
        val array = card.optJSONArray("titles") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun unescapeJsonString(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    private fun strip(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }
}
