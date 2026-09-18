package com.wonder.provider.ui.conversation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wonder.provider.AppContainer
import com.wonder.provider.ai.AgentResponse
import com.wonder.provider.ai.WonderAgent
import com.wonder.provider.data.CustomPersonaRepository
import com.wonder.provider.data.TripRepository
import com.wonder.provider.data.toItemKind
import com.wonder.provider.model.AgentCard
import com.wonder.provider.model.BudgetSummary
import com.wonder.provider.model.ChatTurn
import com.wonder.provider.model.FlightOfferSummary
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.PendingItineraryLeg
import com.wonder.provider.model.PersonaPanel
import com.wonder.provider.model.PersonaQuestionCatalog
import com.wonder.provider.model.Recommendation
import com.wonder.provider.model.Speaker
import com.wonder.provider.model.TripMode
import com.wonder.provider.model.TripWhenMode
import com.wonder.provider.model.TripWhenPlan
import com.wonder.provider.model.TurnPhase
import com.wonder.provider.notify.TripAlarms
import com.wonder.provider.voice.SpeechController
import com.wonder.provider.voice.VoiceSpeaker
import com.wonder.provider.voice.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Modal state when the user taps + on a suggested place or activity. */
data class AddToTripSheetState(
    val pick: Recommendation,
    val currency: String,
    val tripDates: List<LocalDate>
)

data class ConversationUiState(
    val turns: List<ChatTurn> = emptyList(),
    val draft: String = "",
    val voice: VoiceState = VoiceState.Idle,
    val isReadingAloud: Boolean = false,
    val isThinking: Boolean = false,
    val micAvailable: Boolean = true,
    val sourceLabel: String = "Wonder",
    val mode: TripMode = TripMode.PLANNER,
    val tripTitle: String = "",
    val dayLabel: String = "",
    val budget: BudgetSummary? = null,
    val isLoadingMoreSuggestions: Boolean = false,
    val stickyPersonaPanels: List<PersonaPanel> = emptyList(),
    val loadingStickyPersonaId: String? = null,
    val pinningPersonaKey: String? = null,
    val suggestionSheet: AddToTripSheetState? = null
) {
    /** Only worth naming in the header when it isn't Wonder's own engine. */
    val connectedModel: String?
        get() = sourceLabel.takeIf { !it.equals(WonderAgent.ON_DEVICE, ignoreCase = true) }

    val isListening: Boolean get() = voice is VoiceState.Listening
    val heardSoFar: String get() = (voice as? VoiceState.Listening)?.partial.orEmpty()
    val voiceLevel: Float get() = (voice as? VoiceState.Listening)?.level ?: 0f

    /** Dynamic follow-ups from the latest Wonder reply. */
    val dynamicPersonaPanels: List<PersonaPanel>
        get() = turns.lastOrNull()
            ?.takeIf { it.speaker == Speaker.WONDER && it.phase == TurnPhase.SETTLED }
            ?.personaPanels
            .orEmpty()

    /** Sticky custom personas first, then dynamic suggestions (excluding pinned names). */
    val allPersonaPanels: List<PersonaPanel>
        get() {
            val stickyIds = stickyPersonaPanels.mapNotNull { it.customPersonaId }.toSet()
            val stickyNames = stickyPersonaPanels.map { it.persona.lowercase() }.toSet()
            val dynamic = dynamicPersonaPanels.filter { panel ->
                panel.customPersonaId !in stickyIds && panel.persona.lowercase() !in stickyNames
            }
            return stickyPersonaPanels + dynamic
        }
}

class ConversationViewModel(
    private val agent: WonderAgent,
    private val trips: TripRepository,
    private val customPersonas: CustomPersonaRepository,
    private val alarms: TripAlarms,
    private val speech: SpeechController,
    private val speaker: VoiceSpeaker
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationUiState(micAvailable = speech.isAvailable))
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var turnCount = 0
    private var loadedTripId: String? = null
    private var stickyObservationTripId: String? = null
    private val turnsByTripId = mutableMapOf<String, List<ChatTurn>>()
    private val turnCountByTripId = mutableMapOf<String, Int>()

    init {
        observeVoice()
        observeTrip()
        observeTripSwitches()
        observeTripDeletions()
        openConversation()
        alarms.refresh()
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
    }

    fun sendDraft() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(draft = "") }
        send(text, spoken = false)
    }

    fun loadMoreSuggestions() {
        if (_state.value.isLoadingMoreSuggestions || _state.value.isThinking) return
        val lastWonder = _state.value.turns.lastOrNull {
            it.speaker == Speaker.WONDER && it.phase == TurnPhase.SETTLED
        } ?: return
        val history = _state.value.turns.filter { it.id != lastWonder.id }
        _state.update { it.copy(isLoadingMoreSuggestions = true) }
        viewModelScope.launch {
            val existing = lastWonder.personaPanels + _state.value.stickyPersonaPanels
            val more = agent.loadMorePersonaPanels(
                history = history,
                lastWonderSay = lastWonder.text,
                existing = existing
            )
            if (more.isNotEmpty()) {
                updateTurn(lastWonder.id) {
                    it.copy(personaPanels = it.personaPanels + more)
                }
            }
            _state.update { it.copy(isLoadingMoreSuggestions = false) }
        }
    }

    fun expandStickyPersona(personaId: String) {
        if (_state.value.loadingStickyPersonaId == personaId) return
        viewModelScope.launch {
            _state.update { it.copy(loadingStickyPersonaId = personaId) }
            val persona = customPersonas.getById(personaId) ?: run {
                _state.update { it.copy(loadingStickyPersonaId = null) }
                return@launch
            }
            val questions = agent.generateCustomPersonaQuestions(persona, _state.value.turns)
            val clean = PersonaQuestionCatalog.sanitize(persona.name, questions)
            _state.update { state ->
                state.copy(
                    loadingStickyPersonaId = null,
                    stickyPersonaPanels = state.stickyPersonaPanels.map { panel ->
                        if (panel.customPersonaId == personaId) {
                            panel.copy(questions = clean)
                        } else {
                            panel
                        }
                    }
                )
            }
        }
    }

    fun pinPersona(panel: PersonaPanel) {
        if (panel.isSticky || _state.value.pinningPersonaKey == panel.persona) return
        val tripId = trips.activeTripId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(pinningPersonaKey = panel.persona) }
            runCatching {
                val persona = customPersonas.findOrCreateFromPanel(panel)
                if (!customPersonas.isAttached(tripId, persona.id)) {
                    customPersonas.attachToTrip(tripId, persona.id)
                }
                val stickyPanel = persona.toStickyPanel(panel.questions)
                _state.update { state ->
                    val withoutDuplicate = state.stickyPersonaPanels.filterNot {
                        it.customPersonaId == persona.id || it.persona.equals(panel.persona, ignoreCase = true)
                    }
                    state.copy(
                        pinningPersonaKey = null,
                        stickyPersonaPanels = withoutDuplicate + stickyPanel
                    )
                }
            }.onFailure {
                _state.update { it.copy(pinningPersonaKey = null) }
            }
        }
    }

    fun unpinPersona(personaId: String) {
        val tripId = trips.activeTripId() ?: return
        viewModelScope.launch {
            customPersonas.detachFromTrip(tripId, personaId)
            _state.update { state ->
                state.copy(stickyPersonaPanels = state.stickyPersonaPanels.filterNot { it.customPersonaId == personaId })
            }
        }
    }

    fun send(text: String, spoken: Boolean) {
        if (_state.value.isThinking) return
        speaker.stop()

        val history = _state.value.turns
        appendTurn(
            ChatTurn(
                id = nextId(),
                speaker = Speaker.YOU,
                text = text,
                cameFromVoice = spoken
            )
        )

        val replyId = nextId()
        appendTurn(ChatTurn(id = replyId, speaker = Speaker.WONDER, text = "", phase = TurnPhase.THINKING))
        _state.update { it.copy(isThinking = true) }

        viewModelScope.launch {
            val response = agent.respond(history, text)
            if (response.didMutate) {
                alarms.refresh()
                refreshTripState()
            }
            _state.update { it.copy(isThinking = false, sourceLabel = response.sourceLabel) }
            if (spoken) speaker.speak(response.say)
            unfold(replyId, response)
        }
    }

    // region modes

    fun toggleMode() {
        trips.toggleMode()
        alarms.refresh()
        refreshTripState()

        val mode = trips.mode.value
        agent.noteSessionEvent("Switched to ${mode.name} mode.")
        viewModelScope.launch {
            val response = agent.greeting()
            val id = nextId()
            appendTurn(ChatTurn(id = id, speaker = Speaker.WONDER, text = "", phase = TurnPhase.THINKING))
            unfold(
                id,
                response.copy(
                    say = when (mode) {
                        TripMode.WANDERING -> "Wandering now. ${response.say}"
                        TripMode.PLANNER -> "Back to planning. ${response.say}"
                    }
                )
            )
        }
    }

    // endregion

    // region acting on the plan

    /** Turns a drafted day into real itinerary items, then says what it did. */
    fun acceptDraft(card: AgentCard.DraftDay) {
        val trip = trips.trip.value
        if (!trip.datesConfirmed) {
            respondLocally(
                say = "Before I add that draft, I need an approximate date range for this trip.",
                cards = listOf(
                    AgentCard.ConfirmDates(
                        pendingLegs = card.tour.stops.mapIndexed { index, stop ->
                            PendingItineraryLeg(
                                title = stop.name,
                                dayOffset = 0,
                                kind = stop.category.toItemKind(),
                                durationMinutes = stop.durationMinutes,
                                location = card.tour.city.substringBefore(","),
                                notes = stop.tip,
                                estimatedCost = stop.estimatedCost.toDouble(),
                                costIsPerPerson = true
                            )
                        }
                    )
                ),
                suggestions = emptyList()
            )
            return
        }
        val date = card.date
            ?: trip.dates.firstOrNull { !it.isBefore(LocalDate.now()) && trips.itemsOn(it).isEmpty() }
            ?: LocalDate.now()

        val everyone = trip.travellers.map { it.id }.toSet()
        var fallbackTime = LocalTime.of(9, 0)

        card.tour.stops.forEachIndexed { index, stop ->
            val parsed = runCatching {
                LocalTime.parse(stop.timeSlot.uppercase(Locale.ENGLISH), SLOT_FORMAT)
            }.getOrNull()
            val start = parsed ?: fallbackTime
            fallbackTime = start.plusMinutes((stop.durationMinutes + 30).toLong())

            trips.upsertItem(
                ItineraryItem(
                    id = "${trips.newItemId()}-$index",
                    date = date,
                    title = stop.name,
                    kind = stop.category.toItemKind(),
                    startTime = start,
                    durationMinutes = stop.durationMinutes,
                    location = card.tour.city.substringBefore(","),
                    notes = stop.tip,
                    estimatedCost = stop.estimatedCost.toDouble(),
                    costIsPerPerson = true,
                    status = ItemStatus.PLANNED,
                    travellerIds = everyone
                )
            )
        }

        alarms.refresh()
        refreshTripState()
        respondLocally(
            say = "Added — ${card.tour.stops.size} stops on ${dayName(date)}. Nothing's booked yet, so shuffle it however you like.",
            cards = listOf(dayCard(date)),
            suggestions = listOf("What's still unbooked?", "How's our budget now?", "Show me the full plan")
        )
    }

    fun addRecommendation(pick: Recommendation, date: LocalDate = pick.suggestedDate ?: LocalDate.now()) {
        val trip = trips.trip.value
        if (!trip.datesConfirmed) {
            respondLocally(
                say = "Before I add ${pick.title}, pick an approximate date range for this trip.",
                cards = listOf(
                    AgentCard.ConfirmDates(
                        pendingLegs = listOf(
                            PendingItineraryLeg(
                                title = pick.title,
                                dayOffset = 0,
                                kind = pick.kind,
                                startTime = pick.suggestedTime,
                                durationMinutes = pick.durationMinutes,
                                location = trip.destination.substringBefore(","),
                                notes = pick.tip,
                                estimatedCost = pick.estimatedCost,
                                costIsPerPerson = true
                            )
                        )
                    )
                ),
                suggestions = emptyList()
            )
            return
        }
        trips.upsertItem(
            ItineraryItem(
                id = trips.newItemId(),
                date = date,
                title = pick.title,
                kind = pick.kind,
                startTime = pick.suggestedTime,
                durationMinutes = pick.durationMinutes,
                location = trip.destination.substringBefore(","),
                notes = pick.tip,
                estimatedCost = pick.estimatedCost,
                costIsPerPerson = true,
                status = ItemStatus.PLANNED,
                travellerIds = trip.travellers.map { it.id }.toSet()
            )
        )
        alarms.refresh()
        refreshTripState()

        val budget = trips.budget()
        respondLocally(
            say = buildString {
                append("${pick.title} is in, ${dayName(date)}")
                pick.suggestedTime?.let { append(" at ${it.format(CLOCK)}") }
                append(". ")
                append(
                    if (budget.isOverrun) {
                        "That does put you ${TripRepository.format(-budget.variance, budget.currency)} over though."
                    } else {
                        "Still ${TripRepository.format(budget.variance, budget.currency)} of room left."
                    }
                )
            },
            cards = listOf(dayCard(date)),
            suggestions = listOf("What else is nearby?", "How's our budget?", "What's next?")
        )
    }

    fun confirmTripDates(card: AgentCard.ConfirmDates, whenPlan: TripWhenPlan) {
        if (whenPlan.mode == TripWhenMode.FLEXIBLE_CHEAP) return
        trips.confirmDates(whenPlan)
        val trip = trips.trip.value
        val everyone = trip.travellers.map { it.id }.toSet()
        val lastDayIndex = (trip.dayCount - 1).coerceAtLeast(0)
        card.pendingLegs.forEach { leg ->
            val date = trip.startDate.plusDays(leg.dayOffset.coerceIn(0, lastDayIndex).toLong())
            trips.upsertItem(
                ItineraryItem(
                    id = trips.newItemId(),
                    date = date,
                    title = leg.title,
                    kind = leg.kind,
                    startTime = leg.startTime,
                    durationMinutes = leg.durationMinutes,
                    location = leg.location.ifBlank { trip.destination.substringBefore(",") },
                    notes = leg.notes,
                    estimatedCost = leg.estimatedCost,
                    costIsPerPerson = leg.costIsPerPerson,
                    status = leg.status,
                    travellerIds = everyone
                )
            )
        }
        alarms.refresh()
        refreshTripState()
        val rangeLabel = whenPlan.summaryLine()
        respondLocally(
            say = if (card.pendingLegs.isEmpty()) {
                "Dates locked — $rangeLabel. Ask me to add anything whenever you're ready."
            } else {
                "Dates locked — $rangeLabel. Added ${card.pendingLegs.size} " +
                    if (card.pendingLegs.size == 1) "stop to the plan." else "stops to the plan."
            },
            cards = if (card.pendingLegs.isNotEmpty()) {
                listOf(dayCard(trip.startDate.plusDays(card.pendingLegs.first().dayOffset.coerceIn(0, lastDayIndex).toLong())))
            } else {
                emptyList()
            },
            suggestions = listOf("Show me the full plan", "Help me draft day 1", "How's our budget?")
        )
    }

    fun openAddToTripSheet(pick: Recommendation) {
        val trip = trips.trip.value
        _state.update {
            it.copy(
                suggestionSheet = AddToTripSheetState(
                    pick = pick,
                    currency = trip.currency,
                    tripDates = trip.dates.filter { date -> !date.isBefore(LocalDate.now()) }
                        .ifEmpty { trip.dates }
                )
            )
        }
    }

    fun dismissAddToTripSheet() {
        _state.update { it.copy(suggestionSheet = null) }
    }

    fun addFlightOffer(offer: FlightOfferSummary, date: LocalDate) {
        val trip = trips.trip.value
        if (!trip.datesConfirmed) {
            respondLocally(
                say = "Before I add that flight, pick an approximate date range for this trip.",
                cards = listOf(
                    AgentCard.ConfirmDates(
                        pendingLegs = listOf(
                            PendingItineraryLeg(
                                title = "Flight ${offer.origin} → ${offer.destination}",
                                dayOffset = 0,
                                kind = ItemKind.FLIGHT,
                                durationMinutes = 180,
                                location = offer.origin,
                                notes = "${offer.airline} · live quote via Duffel",
                                estimatedCost = offer.price,
                                costIsPerPerson = false
                            )
                        )
                    )
                ),
                suggestions = emptyList()
            )
            return
        }
        val title = "Flight ${offer.origin} → ${offer.destination}"
        trips.upsertItem(
            ItineraryItem(
                id = trips.newItemId(),
                date = date,
                title = title,
                kind = ItemKind.FLIGHT,
                startTime = null,
                durationMinutes = 180,
                location = offer.origin,
                notes = "${offer.airline} · live quote via Duffel",
                estimatedCost = offer.price,
                costIsPerPerson = false,
                status = ItemStatus.PLANNED,
                travellerIds = trip.travellers.map { it.id }.toSet()
            )
        )
        if (offer.isRoundTrip) {
            val retOrigin = offer.returnOrigin ?: offer.destination
            val retDest = offer.returnDestination ?: offer.origin
            trips.upsertItem(
                ItineraryItem(
                    id = trips.newItemId(),
                    date = trip.endDate.takeIf { it.isAfter(date) } ?: date,
                    title = "Flight $retOrigin → $retDest",
                    kind = ItemKind.FLIGHT,
                    startTime = null,
                    durationMinutes = 180,
                    location = "$retOrigin → $retDest",
                    notes = "Return · fare included in outbound · live Duffel quote",
                    estimatedCost = 0.0,
                    costIsPerPerson = false,
                    status = ItemStatus.PLANNED,
                    travellerIds = trip.travellers.map { it.id }.toSet()
                )
            )
        }
        alarms.refresh()
        refreshTripState()
        val budget = trips.budget()
        respondLocally(
            say = buildString {
                append("${offer.airline} at ${TripRepository.format(offer.price, offer.currency)} is on the plan for ${dayName(date)}.")
                append(
                    if (budget.isOverrun) {
                        " That pushes the projection ${TripRepository.format(-budget.variance, budget.currency)} over budget."
                    } else {
                        " Still ${TripRepository.format(budget.variance, budget.currency)} of headroom."
                    }
                )
            },
            cards = listOf(dayCard(date)),
            suggestions = listOf("Search return flights", "How's our budget?", "What's still unbooked?")
        )
    }

    /** Called after the plan is edited elsewhere, so alarms and the header stay truthful. */
    fun onPlanChanged() {
        alarms.refresh()
        refreshTripState()
    }

    // endregion

    // region voice

    fun startListening() {
        speaker.stop()
        speech.start()
    }

    fun stopListening() = speech.finish()

    fun cancelListening() = speech.cancel()

    fun stopReadingAloud() = speaker.stop()

    private fun observeVoice() {
        viewModelScope.launch {
            speech.state.collect { voice ->
                _state.update { it.copy(voice = voice) }
                if (voice is VoiceState.Heard) {
                    speech.reset()
                    send(voice.text, spoken = true)
                }
            }
        }
        viewModelScope.launch {
            speaker.isSpeaking.collect { speaking ->
                _state.update { it.copy(isReadingAloud = speaking) }
            }
        }
    }

    // endregion

    private fun observeTrip() {
        viewModelScope.launch {
            trips.items.collect { refreshTripState() }
        }
        viewModelScope.launch {
            trips.expenses.collect { refreshTripState() }
        }
        viewModelScope.launch {
            trips.trip.collect { refreshTripState() }
        }
    }

    private var stickyJob: kotlinx.coroutines.Job? = null

    private fun observeStickyPersonas(tripId: String) {
        stickyJob?.cancel()
        stickyObservationTripId = tripId
        stickyJob = viewModelScope.launch {
            customPersonas.observeAttachedToTrip(tripId).collect { personas ->
                _state.update { state ->
                    state.copy(
                        stickyPersonaPanels = personas.map { custom ->
                            val existing = state.stickyPersonaPanels.find { it.customPersonaId == custom.id }
                            custom.toStickyPanel(existing?.questions.orEmpty())
                        }
                    )
                }
            }
        }
    }

    private fun observeTripSwitches() {
        viewModelScope.launch {
            trips.activeTripSwitched.collect {
                speaker.stop()
                speech.cancel()
                alarms.cancelAll()
                alarms.refresh()
                openConversation()
            }
        }
    }

    private fun observeTripDeletions() {
        viewModelScope.launch {
            trips.tripDeleted.collect { tripId ->
                turnsByTripId.remove(tripId)
                turnCountByTripId.remove(tripId)
                agent.clearSession(tripId)
            }
        }
    }

    private fun refreshTripState() {
        val trip = trips.trip.value
        val today = LocalDate.now()
        _state.update {
            it.copy(
                mode = trips.mode.value,
                tripTitle = trip.title,
                dayLabel = when {
                    trip.covers(today) -> "day ${trip.dayNumber(today)} of ${trip.dayCount}"
                    trip.startDate.isAfter(today) -> "${trip.daysUntilStart()} days to go"
                    else -> "trip complete"
                },
                budget = trips.budget()
            )
        }
    }

    private fun openConversation() {
        if (!trips.hasActiveTrip()) {
            loadedTripId = null
            _state.update {
                it.copy(
                    turns = emptyList(),
                    draft = "",
                    isThinking = false,
                    tripTitle = "",
                    dayLabel = "",
                    budget = null
                )
            }
            return
        }

        val tripId = trips.trip.value.id
        val welcomeOnly = trips.peekWelcomeOnlyGreeting()

        loadedTripId?.takeIf { it != tripId }?.let { outgoing ->
            turnsByTripId[outgoing] = _state.value.turns
            turnCountByTripId[outgoing] = turnCount
        }
        loadedTripId = tripId
        stickyObservationTripId = null
        refreshTripState()
        observeStickyPersonas(tripId)

        val savedTurns = turnsByTripId[tripId]?.takeIf { it.isNotEmpty() }

        when {
            welcomeOnly -> {
                agent.clearSession(tripId)
                turnsByTripId.remove(tripId)
                turnCountByTripId.remove(tripId)
                turnCount = 0
                _state.update { it.copy(turns = emptyList(), draft = "", isThinking = false) }
                showGreeting()
            }
            savedTurns != null -> {
                turnCount = turnCountByTripId[tripId] ?: savedTurns.size
                _state.update { it.copy(turns = savedTurns, draft = "", isThinking = false) }
            }
            else -> {
                agent.clearSession(tripId)
                turnCount = 0
                _state.update { it.copy(turns = emptyList(), draft = "", isThinking = false) }
                showGreeting()
            }
        }
    }

    private fun showGreeting() {
        val id = nextId()
        appendTurn(ChatTurn(id = id, speaker = Speaker.WONDER, text = "", phase = TurnPhase.THINKING))
        viewModelScope.launch {
            val response = agent.greeting()
            _state.update { it.copy(sourceLabel = response.sourceLabel) }
            unfold(id, response)
        }
    }

    /** A reply the app itself composed, revealed exactly like one that came from a model. */
    private fun respondLocally(say: String, cards: List<AgentCard>, suggestions: List<String>) {
        val id = nextId()
        appendTurn(ChatTurn(id = id, speaker = Speaker.WONDER, text = "", phase = TurnPhase.THINKING))
        viewModelScope.launch {
            val history = _state.value.turns.filter { it.id != id }
            val enriched = agent.enrichPersonaPanels(
                history = history,
                lastWonderSay = say,
                fallback = suggestions
            )
            unfold(
                id,
                AgentResponse(
                    say = say,
                    cards = cards,
                    personaPanels = enriched,
                    sourceLabel = _state.value.sourceLabel
                )
            )
        }
    }

    private fun dayCard(date: LocalDate): AgentCard {
        val trip = trips.trip.value
        val items = trips.itemsOn(date)
        val label = when (date) {
            LocalDate.now() -> "Today"
            LocalDate.now().plusDays(1) -> "Tomorrow"
            else -> "Day ${trip.dayNumber(date)}"
        }
        return AgentCard.DayPlan(
            date = date,
            heading = "$label · ${date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH))}",
            items = items,
            cost = items.sumOf { it.expectedTotal(trip.partySize) },
            currency = trip.currency,
            travellerCount = trip.partySize
        )
    }

    private fun dayName(date: LocalDate): String = when (date) {
        LocalDate.now() -> "today"
        LocalDate.now().plusDays(1) -> "tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))
    }

    /**
     * Reveals the reply a word at a time, then lets the cards land. Nothing about a finished
     * reply appears all at once — the pacing is what makes it feel like Wonder is talking.
     */
    private suspend fun unfold(turnId: String, response: AgentResponse) {
        val words = response.say.split(" ").filter { it.isNotEmpty() }
        var shown = ""

        updateTurn(turnId) { it.copy(phase = TurnPhase.SPEAKING, sourceLabel = response.sourceLabel) }

        words.forEach { word ->
            shown = if (shown.isEmpty()) word else "$shown $word"
            updateTurn(turnId) { it.copy(text = shown) }
            delay(wordDelay(word))
        }

        updateTurn(turnId) {
            it.copy(
                text = response.say,
                cards = response.cards,
                personaPanels = response.personaPanels,
                phase = TurnPhase.SETTLED
            )
        }
    }

    private fun wordDelay(word: String): Long {
        val base = 26L + word.length * 3L
        return if (word.endsWith(".") || word.endsWith("?") || word.endsWith("!")) base + 140L else base
    }

    private fun appendTurn(turn: ChatTurn) {
        _state.update { it.copy(turns = it.turns + turn) }
    }

    private fun updateTurn(id: String, transform: (ChatTurn) -> ChatTurn) {
        _state.update { current ->
            current.copy(turns = current.turns.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun nextId(): String = "turn-${turnCount++}"

    override fun onCleared() {
        speech.release()
        speaker.release()
    }

    private companion object {
        val SLOT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }
}

fun conversationViewModelFactory(context: Context): ViewModelProvider.Factory {
    val appContext = context.applicationContext
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ConversationViewModel(
            agent = AppContainer.wonderAgent,
            trips = AppContainer.trips,
            customPersonas = AppContainer.customPersonas,
            alarms = AppContainer.alarms,
            speech = SpeechController(appContext),
            speaker = VoiceSpeaker(appContext)
        ) as T
    }
}
