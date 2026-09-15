package com.wonder.provider.ai

import com.wonder.provider.data.RecommendationEngine
import com.wonder.provider.data.TripRepository
import com.wonder.provider.data.maps.TravelProfileRepository
import com.wonder.provider.model.AgentCard
import com.wonder.provider.model.ChatTurn
import com.wonder.provider.model.DayOutline
import com.wonder.provider.model.DoorwayTarget
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.CustomPersona
import com.wonder.provider.model.PersonaPanel
import com.wonder.provider.model.Speaker
import com.wonder.provider.model.TravellerPersonas
import com.wonder.provider.model.TourBuildRequest
import com.wonder.provider.model.TripMode
import com.wonder.provider.model.TurnPhase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AgentResponse(
    val say: String,
    val cards: List<AgentCard>,
    val personaPanels: List<PersonaPanel>,
    val sourceLabel: String,
    val didMutate: Boolean = false
)

/**
 * The single entry point the UI talks to. Routes a turn to the traveller's connected model when
 * configured; otherwise uses Wonder's built-in engine. Connected models never silently fall back
 * to rule-based replies — failures are surfaced to the traveller.
 */
class WonderAgent(
    private val settingsRepository: AiSettingsRepository,
    private val trips: TripRepository,
    private val onDevice: LocalConversationEngine,
    private val cards: CardResolver,
    private val travelProfile: TravelProfileRepository
) {

    private val prompt = AgentPrompt(trips) { travelProfile.aiContextSnapshot() }
    private val parser = AgentReplyParser(trips)
    private val actionExecutor = AgentActionExecutor(trips)
    private val suggestionGenerator = TripSuggestionGenerator(prompt) { trips.mode.value }
    private val suggestionPicker = ContextualSuggestionPicker(trips)
    private val customPersonaQuestions = CustomPersonaQuestionGenerator(
        prompt = prompt,
        settingsRepository = settingsRepository,
        suggestionPicker = suggestionPicker
    )
    private val sessionByTrip = mutableMapOf<String, ConversationMemory>()

    private fun memory(): ConversationMemory {
        val tripId = trips.trip.value.id
        return sessionByTrip.getOrPut(tripId) { ConversationMemory() }
    }

    fun resetSession() = clearSession(trips.trip.value.id)

    fun clearSession(tripId: String) {
        sessionByTrip.remove(tripId)
    }

    fun noteSessionEvent(note: String) = memory().noteEvent(note)

    suspend fun generateCustomPersonaQuestions(
        persona: CustomPersona,
        history: List<ChatTurn>
    ): List<String> {
        val lastWonder = history.lastOrNull {
            it.speaker == Speaker.WONDER && it.phase == TurnPhase.SETTLED
        }?.text
        return customPersonaQuestions.generate(persona, history, lastWonder)
    }

    /** Replaces rule-based chip labels with persona-tagged panels when a model is connected. */
    suspend fun enrichPersonaPanels(
        history: List<ChatTurn>,
        lastWonderSay: String,
        fallback: List<String>,
        welcomeOnly: Boolean = false
    ): List<PersonaPanel> = withAiSuggestions(
        response = AgentResponse(
            say = lastWonderSay,
            cards = emptyList(),
            personaPanels = TravellerPersonas.fromPlainQuestions(fallback),
            sourceLabel = sourceLabel()
        ),
        history = history,
        welcomeOnly = welcomeOnly
    ).personaPanels

    suspend fun loadMorePersonaPanels(
        history: List<ChatTurn>,
        lastWonderSay: String,
        existing: List<PersonaPanel>,
        welcomeOnly: Boolean = false
    ): List<PersonaPanel> {
        val usedQuestions = existing.flatMap { it.questions }.toSet()
        val usedPersonas = existing.map { it.persona }.toSet()
        val provider = settingsRepository.chatProvider()
        if (provider != null && settingsRepository.getSettings().isConfigured) {
            val more = suggestionGenerator.generate(
                provider = provider,
                history = history,
                lastWonderSay = lastWonderSay,
                welcomeOnly = welcomeOnly,
                fallbackTriplets = emptyList(),
                exclude = existing
            )
            if (more.isNotEmpty()) {
                return TravellerPersonas.finalizePanels(
                    panels = more,
                    excludePersonas = usedPersonas,
                    excludeQuestions = usedQuestions,
                    targetCount = 2
                )
            }
        }
        return offlineMorePanels(existing)
    }

    private fun offlineMorePanels(existing: List<PersonaPanel>): List<PersonaPanel> {
        val usedPersonas = existing.map { it.persona }.toSet()
        val usedQuestions = existing.flatMap { it.questions }.toSet()
        return TravellerPersonas.finalizePanels(
            panels = TravellerPersonas.buildDefaultPanels(
                personaCount = 2,
                excludePersonas = usedPersonas,
                excludeQuestions = usedQuestions
            ),
            excludePersonas = usedPersonas,
            excludeQuestions = usedQuestions,
            targetCount = 2
        )
    }

    private data class RecentSuggestionExclusions(
        val personas: Set<String>,
        val questions: Set<String>,
        val priorPanels: List<PersonaPanel>
    )

    private fun recentExclusions(history: List<ChatTurn>): RecentSuggestionExclusions {
        val lastUser = history.lastOrNull { it.speaker == Speaker.YOU && it.text.isNotBlank() }?.text?.trim()
        val lastWonder = history.lastOrNull {
            it.speaker == Speaker.WONDER && it.phase == TurnPhase.SETTLED
        }
        val prior = lastWonder?.personaPanels.orEmpty()
        val questions = buildSet {
            lastUser?.let { add(it) }
            prior.forEach { panel -> panel.questions.forEach { add(it) } }
        }
        return RecentSuggestionExclusions(
            personas = prior.map { it.persona }.toSet(),
            questions = questions,
            priorPanels = prior
        )
    }

    private fun preparePanels(
        panels: List<PersonaPanel>,
        exclusions: RecentSuggestionExclusions
    ): List<PersonaPanel> {
        var prepared = TravellerPersonas.finalizePanels(
            panels = panels,
            excludePersonas = exclusions.personas,
            excludeQuestions = exclusions.questions
        )
        if (prepared.size < PersonaPanel.DEFAULT_PERSONA_COUNT) {
            val usedPersonas = exclusions.personas + prepared.map { it.persona }
            prepared = TravellerPersonas.finalizePanels(
                panels = prepared + TravellerPersonas.buildDefaultPanels(
                    personaCount = PersonaPanel.DEFAULT_PERSONA_COUNT - prepared.size,
                    excludePersonas = usedPersonas,
                    excludeQuestions = exclusions.questions + prepared.flatMap { it.questions }.toSet()
                ),
                excludePersonas = exclusions.personas,
                excludeQuestions = exclusions.questions
            )
        }
        return prepared.take(PersonaPanel.DEFAULT_PERSONA_COUNT + 1)
    }

    suspend fun respond(history: List<ChatTurn>, input: String): AgentResponse {
        val provider = settingsRepository.chatProvider()
        if (provider == null || !settingsRepository.getSettings().isConfigured) {
            return builtInResponse(history, input)
        }

        val session = memory().prepare(history, input)
        return invokeProvider(provider, session, history, input)
    }

    /** The opener travellers see when they arrive — from the connected model when one is set up. */
    suspend fun greeting(): AgentResponse {
        val welcomeOnly = trips.consumeWelcomeOnlyGreeting()
        val provider = settingsRepository.chatProvider()
        if (provider == null || !settingsRepository.getSettings().isConfigured) {
            return if (welcomeOnly) builtInWelcomeGreeting() else builtInGreeting()
        }

        val greetingPrompt = if (welcomeOnly) WELCOME_GREETING_PROMPT else GREETING_PROMPT
        val session = memory().prepare(emptyList(), greetingPrompt)
        return try {
            val raw = provider.converse(
                prompt.system(session.checkpoint, welcomeOnly = welcomeOnly),
                session.transcript
            )
            val reply = parser.parse(raw)
            withAiSuggestions(
                AgentResponse(
                    say = reply.say,
                    cards = if (welcomeOnly) emptyList() else cards.resolve(reply.intents),
                    personaPanels = emptyList(),
                    sourceLabel = provider.sourceLabel
                ),
                history = emptyList(),
                welcomeOnly = welcomeOnly
            )
        } catch (error: AiTourError.InvalidApiKey) {
            connectedModelFailure(provider, error.message ?: "Invalid API key.")
        } catch (error: AiTourError.ProviderFailed) {
            connectedModelFailure(provider, error.message ?: "The model failed.")
        } catch (error: Exception) {
            connectedModelFailure(
                provider,
                error.message ?: "Something went wrong while the model was thinking."
            )
        }
    }

    fun sourceLabel(): String {
        val settings = settingsRepository.getSettings()
        return when {
            settings.isGemmaConfigured -> settingsRepository.chatProvider()?.sourceLabel ?: ON_DEVICE
            settings.isCloudConfigured -> settingsRepository.chatProvider()?.sourceLabel
                ?: settings.providerType.displayName
            else -> ON_DEVICE
        }
    }

    private suspend fun invokeProvider(
        provider: AiChatProvider,
        session: PreparedSessionContext,
        history: List<ChatTurn>,
        input: String
    ): AgentResponse = try {
        val raw = provider.converse(prompt.system(session.checkpoint), session.transcript)
        val reply = parser.parse(raw)
        val actionResult = actionExecutor.execute(reply.actions)
        val say = augmentSay(reply.say, actionResult)
        memory().commit(history, input, say)
        withAiSuggestions(
            AgentResponse(
                say = say,
                cards = cards.resolve(reply.intents),
                personaPanels = emptyList(),
                sourceLabel = provider.sourceLabel,
                didMutate = actionResult.didMutate
            ),
            history = history + listOf(
                ChatTurn(id = "pending-user", speaker = Speaker.YOU, text = input),
                ChatTurn(id = "pending-wonder", speaker = Speaker.WONDER, text = say)
            ),
            welcomeOnly = false
        )
    } catch (error: AiTourError.InvalidApiKey) {
        connectedModelFailure(provider, error.message ?: "Invalid API key.")
    } catch (error: AiTourError.ProviderFailed) {
        connectedModelFailure(provider, error.message ?: "The model failed.")
    } catch (error: Exception) {
        connectedModelFailure(
            provider,
            error.message ?: "Something went wrong while the model was thinking."
        )
    }

    private fun connectedModelFailure(provider: AiChatProvider, detail: String): AgentResponse {
        val label = provider.sourceLabel
        return AgentResponse(
            say = "$label couldn't answer that — $detail",
            cards = listOf(
                AgentCard.Doorway(
                    headline = "Check $label",
                    body = "The connected model failed. Open model settings to re-attach Gemma or verify your API key, then try again.",
                    target = DoorwayTarget.SETTINGS
                )
            ),
            personaPanels = TravellerPersonas.buildDefaultPanels(personaCount = 2),
            sourceLabel = label
        )
    }

    private suspend fun builtInResponse(history: List<ChatTurn>, input: String): AgentResponse {
        val reply = onDevice.respond(history, input)
        val actionResult = actionExecutor.execute(reply.actions)
        val say = augmentSay(reply.say, actionResult)
        memory().commit(history, input, say)
        return withAiSuggestions(
            AgentResponse(
                say = say,
                cards = cards.resolve(reply.intents),
                personaPanels = TravellerPersonas.fromPlainQuestions(reply.suggestions),
                sourceLabel = ON_DEVICE,
                didMutate = actionResult.didMutate
            ),
            history = history + listOf(
                ChatTurn(id = "pending-user", speaker = Speaker.YOU, text = input),
                ChatTurn(id = "pending-wonder", speaker = Speaker.WONDER, text = say)
            ),
            welcomeOnly = false
        )
    }

    private fun augmentSay(original: String, result: AgentActionResult): String {
        if (result.failed.isEmpty()) return original
        val note = result.failed.joinToString("; ")
        return if (original.endsWith(".")) "$original I couldn't apply: $note." else "$original. I couldn't apply: $note."
    }

    private suspend fun builtInGreeting(): AgentResponse = withAiSuggestions(
        onDevice.greeting().let { reply ->
            AgentResponse(
                say = reply.say,
                cards = cards.resolve(reply.intents),
                personaPanels = TravellerPersonas.fromPlainQuestions(reply.suggestions),
                sourceLabel = ON_DEVICE
            )
        },
        history = emptyList(),
        welcomeOnly = false
    )

    private suspend fun builtInWelcomeGreeting(): AgentResponse = withAiSuggestions(
        onDevice.welcomeGreeting().let { reply ->
            AgentResponse(
                say = reply.say,
                cards = emptyList(),
                personaPanels = TravellerPersonas.fromPlainQuestions(reply.suggestions),
                sourceLabel = ON_DEVICE
            )
        },
        history = emptyList(),
        welcomeOnly = true
    )

    private suspend fun withAiSuggestions(
        response: AgentResponse,
        history: List<ChatTurn>,
        welcomeOnly: Boolean
    ): AgentResponse {
        val exclusions = recentExclusions(history)
        val fallbackPanels = TravellerPersonas.buildDefaultPanels(
            excludePersonas = exclusions.personas,
            excludeQuestions = exclusions.questions
        )
        val provider = settingsRepository.chatProvider()
            ?: return response.copy(
                personaPanels = preparePanels(
                    panels = fallbackPanels,
                    exclusions = exclusions
                )
            )
        if (!settingsRepository.getSettings().isConfigured) {
            return response.copy(
                personaPanels = preparePanels(
                    panels = fallbackPanels,
                    exclusions = exclusions
                )
            )
        }
        val panels = suggestionGenerator.generate(
            provider = provider,
            history = history,
            lastWonderSay = response.say,
            welcomeOnly = welcomeOnly,
            fallbackTriplets = emptyList(),
            exclude = exclusions.priorPanels
        )
        return response.copy(
            personaPanels = preparePanels(
                panels = panels.ifEmpty { fallbackPanels },
                exclusions = exclusions
            )
        )
    }

    companion object {
        /** The label used when Wonder is thinking with its own engine rather than a connected model. */
        const val ON_DEVICE = "Wonder"

        private const val GREETING_PROMPT =
            "The traveller just opened the conversation. Greet them briefly and naturally for " +
                "this trip and mode — one or two spoken sentences. Return no cards and an empty " +
                "suggestions array; follow-ups are generated separately."

        private const val WELCOME_GREETING_PROMPT =
            "The traveller just created a new trip and opened the conversation for the first time. " +
                "Give a warm, brief, personalized greeting — use their name from the trip or travel " +
                "history if you know it. Do NOT mention the destination, dates, itinerary, budget, " +
                "nearby places, or anything location-specific yet. One or two spoken sentences only. " +
                "Return no cards and an empty suggestions array."
    }
}

/**
 * Turns the abstract views a model asked for into cards backed by the real trip. Figures always
 * come from the repository, never from the model.
 */
class CardResolver(
    private val trips: TripRepository,
    private val recommendations: RecommendationEngine,
    private val tourService: AiTourService,
    private val travelSearch: com.wonder.provider.data.travel.TravelSearchRepository
) {

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
    private val shortFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    suspend fun resolve(intents: List<CardIntent>): List<AgentCard> =
        intents.take(MAX_CARDS).mapNotNull { intent ->
            when (intent) {
                CardIntent.TripOverview -> tripOverview()
                is CardIntent.DayPlan -> dayPlan(intent.date)
                CardIntent.NowNext -> nowNext()
                is CardIntent.Budget -> AgentCard.Budget(trips.budget(), intent.detailed)
                CardIntent.Expenses -> expenses()
                is CardIntent.Nearby -> nearby(intent)
                CardIntent.Loose -> loose()
                is CardIntent.DraftDay -> draft(intent.request, intent.date)
                CardIntent.OpenPlan -> AgentCard.Doorway(
                    headline = "Open the full plan",
                    body = "Every day, every detail — change times, costs, who's coming.",
                    target = DoorwayTarget.PLAN
                )
                CardIntent.OpenExpenses -> AgentCard.Doorway(
                    headline = "Open the expense tracker",
                    body = "Every euro, planned and unplanned, against the budget.",
                    target = DoorwayTarget.EXPENSES
                )
                CardIntent.Connect -> AgentCard.Doorway(
                    headline = "Choose how Wonder thinks",
                    body = "Run Gemma free on this phone via Google AI Edge Gallery, or connect an OpenAI, Gemini, Claude, Mistral, or custom API key.",
                    target = DoorwayTarget.SETTINGS
                )
                is CardIntent.SearchFlights -> searchFlights(intent)
            }
        }

    private fun tripOverview(): AgentCard {
        val trip = trips.trip.value
        val today = LocalDate.now()
        val party = trip.partySize

        val days = trip.dates.map { date ->
            val items = trips.itemsOn(date)
            DayOutline(
                date = date,
                dayNumber = trip.dayNumber(date),
                headline = when {
                    items.isEmpty() -> "Open"
                    else -> items.filter { it.kind != ItemKind.FREE }
                        .take(2)
                        .joinToString(", ") { it.title.substringBefore(",") }
                        .ifBlank { "Open" }
                },
                itemCount = items.count { it.kind != ItemKind.FREE },
                cost = items.sumOf { it.expectedTotal(party) },
                isToday = date == today,
                isPast = date.isBefore(today)
            )
        }

        return AgentCard.TripOverview(
            title = trip.title,
            destination = trip.destination,
            dateRange = "${trip.startDate.format(shortFormat)} – ${trip.endDate.format(shortFormat)}",
            coverEmoji = trip.coverEmoji,
            days = days,
            currency = trip.currency
        )
    }

    private fun dayPlan(date: LocalDate): AgentCard? {
        val trip = trips.trip.value
        val items = trips.itemsOn(date)
        if (items.isEmpty()) return null
        return AgentCard.DayPlan(
            date = date,
            heading = when (date) {
                LocalDate.now() -> "Today · ${date.format(dayFormat)}"
                LocalDate.now().plusDays(1) -> "Tomorrow · ${date.format(dayFormat)}"
                else -> "Day ${trip.dayNumber(date)} · ${date.format(dayFormat)}"
            },
            items = items,
            cost = items.sumOf { it.expectedTotal(trip.partySize) },
            currency = trip.currency,
            travellerCount = trip.partySize
        )
    }

    private fun nowNext(): AgentCard {
        val (current, next) = trips.nowAndNext()
        return AgentCard.NowNext(
            current = current,
            next = next,
            minutesUntilNext = next?.let { trips.minutesUntil(it) } ?: 0,
            currency = trips.trip.value.currency,
            travellerCount = trips.trip.value.partySize
        )
    }

    private fun expenses(): AgentCard {
        val names = trips.trip.value.travellers.associate { it.id to it.name }
        return AgentCard.ExpenseLog(
            expenses = trips.expenses.value.take(6),
            summary = trips.budget(),
            payerNames = names
        )
    }

    private fun nearby(intent: CardIntent.Nearby): AgentCard? {
        val picks = recommendations.suggest(
            limit = 3,
            kind = intent.kind,
            featuredTitles = intent.featuredTitles
        )
        if (picks.isEmpty()) return null
        val heading = when {
            intent.featuredTitles.isNotEmpty() -> "Worth adding"
            intent.kind == null -> "Worth a detour"
            else -> "${intent.kind.label} near you"
        }
        return AgentCard.Nearby(
            heading = heading,
            picks = picks,
            currency = trips.trip.value.currency
        )
    }

    private fun loose(): AgentCard? {
        val items = trips.unbookedEssentials()
        if (items.isEmpty()) return null
        return AgentCard.Loose(
            heading = "Still to lock in",
            items = items,
            currency = trips.trip.value.currency,
            travellerCount = trips.trip.value.partySize
        )
    }

    private suspend fun draft(request: TourBuildRequest, date: LocalDate?): AgentCard? =
        runCatching { tourService.curateTour(request).tour }
            .recoverCatching { LocalAiProvider().curateTour(request) }
            .getOrNull()
            ?.let { AgentCard.DraftDay(it, date) }

    private suspend fun searchFlights(intent: CardIntent.SearchFlights): AgentCard {
        val trip = trips.trip.value
        val query = travelSearch.defaultQuery(
            trip = trip,
            originHint = intent.origin,
            destinationHint = intent.destination,
            departDate = intent.departDate,
            returnDate = intent.returnDate,
            adults = if (intent.adults > 0) intent.adults else trip.partySize
        ) ?: return AgentCard.Doorway(
            headline = "Need clearer airports",
            body = "I couldn't map that route to airport codes. Try city names like London or Lisbon.",
            target = DoorwayTarget.PLAN
        )

        val result = travelSearch.searchFlights(query)
        if (!result.configured) {
            return AgentCard.Doorway(
                headline = "Connect live flight search",
                body = "Wonder uses Duffel for live fares — there is no public Google Flights API. Add a free test token in settings (duffel.com).",
                target = DoorwayTarget.SETTINGS
            )
        }
        return AgentCard.FlightResults(result)
    }

    private companion object {
        const val MAX_CARDS = 3
    }
}
