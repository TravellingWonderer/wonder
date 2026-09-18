package com.wonder.provider.data

import android.content.Context
import com.wonder.provider.ai.VibesParser
import com.wonder.provider.data.db.AppSettingsDao
import com.wonder.provider.data.db.AppSettingsEntity
import com.wonder.provider.data.db.TripDao
import com.wonder.provider.data.db.TripRecordMapper
import com.wonder.provider.data.db.WonderDatabase
import com.wonder.provider.model.BudgetSummary
import com.wonder.provider.model.CategorySpend
import com.wonder.provider.model.Expense
import com.wonder.provider.model.ExpenseCategory
import com.wonder.provider.model.FreeWindow
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.Traveller
import com.wonder.provider.model.Trip
import com.wonder.provider.model.TripArchiveStatus
import com.wonder.provider.model.TripMode
import com.wonder.provider.model.TripOverviewSnapshot
import com.wonder.provider.model.TripSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Trip data backed by Room on the device. One trip is active at a time; past trips archive
 * automatically once their end date has passed.
 */
class TripRepository(
    context: Context,
    database: WonderDatabase,
    private val scope: CoroutineScope
) {

    private val appContext = context.applicationContext
    private val dao: TripDao = database.tripDao()
    private val settingsDao: AppSettingsDao = database.appSettingsDao()
    private val legacyPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val prepared = CompletableDeferred<Unit>()

    private val _trip = MutableStateFlow(SampleTrip.trip)
    val trip: StateFlow<Trip> = _trip.asStateFlow()

    private val _items = MutableStateFlow(SampleTrip.items.sortedWith(itemOrder))
    val items: StateFlow<List<ItineraryItem>> = _items.asStateFlow()

    private val _expenses = MutableStateFlow(SampleTrip.expenses.sortedByDescending { it.date })
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _mode = MutableStateFlow(TripMode.PLANNER)
    val mode: StateFlow<TripMode> = _mode.asStateFlow()

    private val _catalog = MutableStateFlow<List<TripSummary>>(emptyList())
    val catalog: StateFlow<List<TripSummary>> = _catalog.asStateFlow()

    private val _activeTripSwitched = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val activeTripSwitched: SharedFlow<Unit> = _activeTripSwitched.asSharedFlow()

    private val _tripDeleted = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val tripDeleted: SharedFlow<String> = _tripDeleted.asSharedFlow()

    private var modeWasChosen = false
    private var activeEntityArchiveStatus = TripArchiveStatus.PLANNED
    @Volatile private var cachedActiveTripId: String? = null

    /** Set when a trip is freshly created; consumed once for a welcome-only opener. */
    @Volatile
    private var welcomeOnlyGreetingPending = false

    /** Blank create: greet quietly with no suggestion / persona panels. */
    @Volatile
    private var quietWelcomePending = false

    init {
        scope.launch {
            prepared.await()
            combine(dao.observeTrips(), settingsDao.observe()) { entities, settings ->
                val activeId = settings?.activeTripId.orEmpty()
                cachedActiveTripId = settings?.activeTripId
                entities.map { TripRecordMapper.toSummary(it, activeId) }
            }.collect { summaries ->
                _catalog.value = summaries
            }
        }
    }

    suspend fun prepare() {
        try {
            withContext(Dispatchers.IO) { bootstrap() }
            prepared.complete(Unit)
        } catch (error: Throwable) {
            prepared.completeExceptionally(error)
            throw error
        }
    }

    private suspend fun bootstrap() {
        migrateLegacyPreferences()
        val settings = settingsDao.settings() ?: AppSettingsEntity().also { settingsDao.upsert(it) }
        if (dao.tripCount() == 0 && !settings.userClearedTrips) {
            seedSampleTrip()
        }
        dao.archivePastTrips(LocalDate.now())
        val activeId = cachedActiveTripId
            ?: dao.tripsByStatus(TripArchiveStatus.PLANNED).firstOrNull()?.id
            ?: return
        if (cachedActiveTripId == null) {
            persistActiveTripId(activeId)
        }
        loadTrip(activeId, notify = false)
    }

    private suspend fun migrateLegacyPreferences() {
        val existing = settingsDao.settings() ?: AppSettingsEntity().also { settingsDao.upsert(it) }
        val legacyActive = legacyPrefs.getString(KEY_ACTIVE_TRIP, null)
        val legacyCleared = legacyPrefs.getBoolean(KEY_USER_CLEARED_TRIPS, false)
        val migrated = existing.copy(
            activeTripId = existing.activeTripId ?: legacyActive,
            userClearedTrips = existing.userClearedTrips || legacyCleared
        )
        if (migrated != existing) {
            settingsDao.upsert(migrated)
        }
        cachedActiveTripId = settingsDao.settings()?.activeTripId
        if (legacyPrefs.all.isNotEmpty()) {
            legacyPrefs.edit().clear().apply()
        }
    }

    private suspend fun persistActiveTripId(tripId: String?) {
        cachedActiveTripId = tripId
        val current = settingsDao.settings() ?: AppSettingsEntity()
        settingsDao.upsert(current.copy(activeTripId = tripId))
    }

    private suspend fun seedSampleTrip() {
        val trip = SampleTrip.trip
        val entity = TripRecordMapper.toEntity(
            trip = trip,
            archiveStatus = TripArchiveStatus.PLANNED,
            mode = naturalModeFor(trip),
            modeWasManual = false
        )
        dao.insertFullTrip(
            trip = entity,
            travellers = TripRecordMapper.toTravellerEntities(trip),
            interests = TripRecordMapper.toInterestEntities(trip),
            items = SampleTrip.items.map { TripRecordMapper.toItemEntity(it, trip.id) },
            expenses = SampleTrip.expenses.map { TripRecordMapper.toExpenseEntity(it, trip.id) }
        )
        persistActiveTripId(trip.id)
    }

    fun activeTripId(): String? = cachedActiveTripId

    fun hasActiveTrip(): Boolean = activeTripId() != null

    /** True once after creating a trip; cleared on read. */
    fun consumeWelcomeOnlyGreeting(): Boolean {
        if (!welcomeOnlyGreetingPending) return false
        welcomeOnlyGreetingPending = false
        return true
    }

    fun peekWelcomeOnlyGreeting(): Boolean = welcomeOnlyGreetingPending

    /** True once after a blank create; cleared with the welcome flag. */
    fun consumeQuietWelcome(): Boolean {
        if (!quietWelcomePending) return false
        quietWelcomePending = false
        return true
    }

    fun peekQuietWelcome(): Boolean = quietWelcomePending

    val plannedTrips: List<TripSummary>
        get() = _catalog.value.filter { it.archiveStatus == TripArchiveStatus.PLANNED }

    val archivedTrips: List<TripSummary>
        get() = _catalog.value.filter { it.archiveStatus == TripArchiveStatus.ARCHIVED }

    suspend fun switchActiveTrip(tripId: String) {
        withContext(Dispatchers.IO) {
            persistActiveTripState()
            dao.archivePastTrips(LocalDate.now())
            val entity = dao.tripById(tripId) ?: return@withContext
            if (entity.archiveStatus == TripArchiveStatus.ARCHIVED) return@withContext
            persistActiveTripId(tripId)
            loadTrip(tripId, notify = true)
        }
    }

    fun switchActiveTripAsync(tripId: String) {
        scope.launch { switchActiveTrip(tripId) }
    }

    suspend fun loadTripSnapshot(tripId: String): TripOverviewSnapshot? = withContext(Dispatchers.IO) {
        val details = dao.tripWithDetails(tripId) ?: return@withContext null
        TripOverviewSnapshot(
            trip = TripRecordMapper.toTrip(details),
            items = details.items.map(TripRecordMapper::toItem).sortedWith(itemOrder),
            isActive = activeTripId() == tripId
        )
    }

    suspend fun deleteTrip(tripId: String) = withContext(Dispatchers.IO) {
        val wasActive = activeTripId() == tripId
        if (wasActive) {
            persistActiveTripState()
        }
        dao.deleteTrip(tripId)
        _tripDeleted.tryEmit(tripId)
        if (wasActive) {
            activateNextTripAfterDelete()
        }
    }

    fun deleteTripAsync(tripId: String) {
        scope.launch { deleteTrip(tripId) }
    }

    private suspend fun activateNextTripAfterDelete() {
        val next = dao.tripsByStatus(TripArchiveStatus.PLANNED).firstOrNull()
            ?: dao.tripsByStatus(TripArchiveStatus.ARCHIVED).firstOrNull()
        if (next != null) {
            persistActiveTripId(next.id)
            loadTrip(next.id, notify = true)
            return
        }
        val current = settingsDao.settings() ?: AppSettingsEntity()
        settingsDao.upsert(current.copy(activeTripId = null, userClearedTrips = true))
        cachedActiveTripId = null
        _items.value = emptyList()
        _expenses.value = emptyList()
        _mode.value = TripMode.PLANNER
        _activeTripSwitched.tryEmit(Unit)
    }

    suspend fun createBlankTrip(
        title: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        vibes: String = "",
        interests: Set<TourInterest> = setOf(TourInterest.LOCAL),
        blankStart: Boolean = false,
        datesConfirmed: Boolean = true
    ): String =
        withContext(Dispatchers.IO) {
            require(isValidTripTitle(title)) { "Trip name must be at least 2 characters." }
            persistActiveTripState()
            val notes = vibes.trim().takeUnless { blankStart }.orEmpty()
            val parsed = if (notes.isNotBlank()) VibesParser.parse(notes) else null
            val resolvedDestination = DestinationInference.resolve(
                title = title.trim(),
                currentDestination = destination,
                vibesHint = parsed?.city
            )
            val resolvedInterests = when {
                blankStart || notes.isBlank() -> emptySet()
                else -> parsed?.interests?.takeIf { it.isNotEmpty() } ?: interests
            }
            createAndActivateTrip(
                title = title.trim(),
                destination = resolvedDestination,
                startDate = startDate,
                endDate = endDate,
                coverEmoji = "✈️",
                interests = resolvedInterests,
                quietWelcome = blankStart || notes.isBlank(),
                datesConfirmed = datesConfirmed
            )
        }

    private suspend fun createAndActivateTrip(
        title: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        coverEmoji: String,
        interests: Set<TourInterest>,
        quietWelcome: Boolean = false,
        datesConfirmed: Boolean = true
    ): String {
        val id = "trip-${System.currentTimeMillis()}"
        val traveller = Traveller("t1", "You", "🙂")
        val trip = Trip(
            id = id,
            title = title,
            destination = destination,
            startDate = startDate,
            endDate = endDate,
            travellers = listOf(traveller),
            budget = 2500.0,
            currency = "€",
            homeCurrency = "$",
            homeRate = 1.09,
            coverEmoji = coverEmoji,
            interests = interests,
            datesConfirmed = datesConfirmed
        )
        val entity = TripRecordMapper.toEntity(
            trip = trip,
            archiveStatus = TripArchiveStatus.PLANNED,
            mode = naturalModeFor(trip),
            modeWasManual = false
        )
        dao.persistTripGraph(
            trip = entity,
            travellers = TripRecordMapper.toTravellerEntities(trip),
            interests = TripRecordMapper.toInterestEntities(trip)
        )
        persistActiveTripId(id)
        welcomeOnlyGreetingPending = true
        quietWelcomePending = quietWelcome
        loadTrip(id, notify = true)
        return id
    }

    private suspend fun loadTrip(tripId: String, notify: Boolean) {
        val details = dao.tripWithDetails(tripId) ?: return
        activeEntityArchiveStatus = details.trip.archiveStatus
        modeWasChosen = details.trip.modeWasManual
        val loaded = TripRecordMapper.toTrip(details)
        val destination = if (DestinationInference.shouldClearInferredDestination(loaded.destination, loaded.title)) {
            TripRepository.DEFAULT_DESTINATION
        } else {
            loaded.destination
        }
        if (destination != loaded.destination) {
            dao.upsertTrip(details.trip.copy(destination = destination))
        }
        _trip.value = loaded.copy(destination = destination)
        _items.value = details.items.map(TripRecordMapper::toItem).sortedWith(itemOrder)
        _expenses.value = details.expenses.map(TripRecordMapper::toExpense)
            .sortedByDescending { it.date }
        _mode.value = if (details.trip.modeWasManual) details.trip.mode else naturalMode()
        val destinationFilled = backfillDestinationIfNeeded()
        if (notify || destinationFilled) _activeTripSwitched.tryEmit(Unit)
    }

    /**
     * When destination is still the blank placeholder, infer it from the trip title
     * and/or itinerary locations (e.g. title "Athens" → destination Athens).
     */
    private suspend fun backfillDestinationIfNeeded(): Boolean {
        val trip = _trip.value
        if (hasDecidedDestination(trip.destination)) return false
        val inferred = DestinationInference.resolve(
            title = trip.title,
            currentDestination = trip.destination,
            locations = _items.value.map { it.location }
        )
        if (!hasDecidedDestination(inferred) || inferred.equals(trip.destination, ignoreCase = true)) {
            return false
        }
        _trip.value = trip.copy(destination = inferred)
        persistTripMeta()
        return true
    }

    private suspend fun persistActiveTripState() {
        val trip = _trip.value
        dao.persistTripGraph(
            trip = TripRecordMapper.toEntity(
                trip = trip,
                archiveStatus = activeEntityArchiveStatus,
                mode = _mode.value,
                modeWasManual = modeWasChosen
            ),
            travellers = TripRecordMapper.toTravellerEntities(trip),
            interests = TripRecordMapper.toInterestEntities(trip)
        )
    }

    private suspend fun persistTripMeta() {
        persistActiveTripState()
    }

    // region mode

    private fun naturalModeFor(trip: Trip): TripMode =
        if (trip.covers(LocalDate.now())) TripMode.WANDERING else TripMode.PLANNER

    private fun naturalMode(): TripMode = naturalModeFor(_trip.value)

    fun setMode(mode: TripMode) {
        modeWasChosen = true
        _mode.value = mode
        scope.launch(Dispatchers.IO) { persistTripMeta() }
    }

    fun toggleMode() {
        setMode(if (_mode.value == TripMode.PLANNER) TripMode.WANDERING else TripMode.PLANNER)
    }

    val isModeManual: Boolean get() = modeWasChosen && _mode.value != naturalMode()

    // endregion

    // region itinerary

    fun itemsOn(date: LocalDate): List<ItineraryItem> =
        _items.value.filter { it.date == date }.sortedWith(itemOrder)

    fun item(id: String): ItineraryItem? = _items.value.firstOrNull { it.id == id }

    fun upsertItem(item: ItineraryItem) {
        _items.update { current ->
            val existing = current.indexOfFirst { it.id == item.id }
            val next = if (existing >= 0) {
                current.toMutableList().apply { set(existing, item) }
            } else {
                current + item
            }
            next.sortedWith(itemOrder)
        }
        scope.launch(Dispatchers.IO) {
            dao.upsertItem(TripRecordMapper.toItemEntity(item, _trip.value.id))
            if (backfillDestinationIfNeeded()) {
                _activeTripSwitched.tryEmit(Unit)
            }
        }
    }

    suspend fun upsertItemDirect(item: ItineraryItem, tripId: String) = withContext(Dispatchers.IO) {
        dao.upsertItem(TripRecordMapper.toItemEntity(item, tripId))
        if (_trip.value.id == tripId) {
            _items.update { current ->
                val existing = current.indexOfFirst { it.id == item.id }
                val next = if (existing >= 0) {
                    current.toMutableList().apply { set(existing, item) }
                } else {
                    current + item
                }
                next.sortedWith(itemOrder)
            }
        }
    }

    suspend fun updateTripDestinationDirect(tripId: String, destination: String) = withContext(Dispatchers.IO) {
        val entity = dao.tripById(tripId) ?: return@withContext
        val updated = entity.copy(destination = destination)
        dao.upsertTrip(updated)
        if (_trip.value.id == tripId) {
            _trip.value = _trip.value.copy(destination = destination)
            _activeTripSwitched.tryEmit(Unit)
        }
    }

    /** Infers and persists a real destination when the trip is still on the blank placeholder. */
    suspend fun resolveDestinationIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        backfillDestinationIfNeeded()
    }

    fun removeItem(id: String) {
        _items.update { current -> current.filterNot { it.id == id } }
        _expenses.update { current -> current.map { if (it.itemId == id) it.copy(itemId = null) else it } }
        scope.launch(Dispatchers.IO) {
            dao.deleteItem(id)
            dao.unlinkExpenses(id, _trip.value.id)
        }
    }

    fun newItemId(): String = "i-${System.currentTimeMillis()}"

    fun saveEdited(item: ItineraryItem, paidById: String) {
        upsertItem(item)

        val paid = item.paidAmount
        val linked = _expenses.value.firstOrNull { it.itemId == item.id }

        when {
            paid != null && paid > 0 && linked == null -> addExpense(
                Expense(
                    id = newExpenseId(),
                    label = item.title,
                    amount = paid,
                    category = item.kind.toCategory(),
                    date = minOf(item.date, LocalDate.now()),
                    paidById = paidById,
                    itemId = item.id
                )
            )

            paid != null && paid > 0 && linked != null -> {
                _expenses.update { current ->
                    current.map {
                        if (it.id == linked.id) {
                            it.copy(amount = paid, label = item.title, category = item.kind.toCategory())
                        } else {
                            it
                        }
                    }
                }
                scope.launch(Dispatchers.IO) {
                    dao.upsertExpense(
                        TripRecordMapper.toExpenseEntity(
                            linked.copy(amount = paid, label = item.title, category = item.kind.toCategory()),
                            _trip.value.id
                        )
                    )
                }
            }

            paid == null && linked != null -> removeExpense(linked.id)
        }
    }

    fun nowAndNext(at: LocalDateTime = LocalDateTime.now()): Pair<ItineraryItem?, ItineraryItem?> {
        val timed = _items.value.filter { it.startTime != null && it.status != ItemStatus.IDEA }
        val current = timed.firstOrNull { item ->
            val start = LocalDateTime.of(item.date, item.startTime)
            val finish = start.plusMinutes(item.durationMinutes.toLong())
            !at.isBefore(start) && at.isBefore(finish)
        }
        val next = timed
            .filter { LocalDateTime.of(it.date, it.startTime).isAfter(at) }
            .minByOrNull { LocalDateTime.of(it.date, it.startTime) }
        return current to next
    }

    fun minutesUntil(item: ItineraryItem, from: LocalDateTime = LocalDateTime.now()): Long {
        val start = item.startTime ?: return 0
        return Duration.between(from, LocalDateTime.of(item.date, start)).toMinutes()
    }

    fun freeWindows(date: LocalDate, minimumMinutes: Int = 60): List<FreeWindow> {
        val timed = itemsOn(date).filter { it.startTime != null && it.status != ItemStatus.IDEA }
        if (timed.isEmpty()) {
            return listOf(FreeWindow(date, LocalTime.of(9, 0), LocalTime.of(21, 0)))
        }

        val windows = mutableListOf<FreeWindow>()
        var cursor = maxOf(timed.first().startTime!!, LocalTime.of(8, 0))

        val dayOpens = LocalTime.of(8, 30)
        if (timed.first().startTime!!.isAfter(dayOpens.plusMinutes(minimumMinutes.toLong()))) {
            windows += FreeWindow(date, dayOpens, timed.first().startTime!!)
        }

        timed.forEach { item ->
            val finish = item.endTime ?: return@forEach
            if (finish.isAfter(cursor)) cursor = finish
        }

        timed.zipWithNext { earlier, later ->
            val gapStart = earlier.endTime ?: return@zipWithNext
            val gapEnd = later.startTime ?: return@zipWithNext
            if (Duration.between(gapStart, gapEnd).toMinutes() >= minimumMinutes) {
                windows += FreeWindow(date, gapStart, gapEnd)
            }
        }

        val dayCloses = LocalTime.of(22, 0)
        if (cursor.isBefore(dayCloses.minusMinutes(minimumMinutes.toLong()))) {
            windows += FreeWindow(date, cursor, dayCloses)
        }

        return windows.sortedBy { it.start }
    }

    fun unbookedEssentials(): List<ItineraryItem> =
        _items.value.filter {
            it.status != ItemStatus.BOOKED &&
                it.status != ItemStatus.DONE &&
                !it.date.isBefore(LocalDate.now()) &&
                it.kind in setOf(ItemKind.FLIGHT, ItemKind.STAY, ItemKind.ACTIVITY, ItemKind.TRANSPORT)
        }.sortedWith(itemOrder)

    // endregion

    // region money

    fun addExpense(expense: Expense) {
        _expenses.update { (it + expense).sortedByDescending { entry -> entry.date } }
        scope.launch(Dispatchers.IO) {
            dao.upsertExpense(TripRecordMapper.toExpenseEntity(expense, _trip.value.id))
        }
    }

    fun removeExpense(id: String) {
        _expenses.update { current -> current.filterNot { it.id == id } }
        scope.launch(Dispatchers.IO) { dao.deleteExpense(id) }
    }

    fun newExpenseId(): String = "e-${System.currentTimeMillis()}"

    fun setBudget(amount: Double) {
        _trip.update { it.copy(budget = amount) }
        scope.launch(Dispatchers.IO) { persistTripMeta() }
    }

    /** Locks in a real date plan for trips created without confirmed dates. */
    fun confirmDates(whenPlan: com.wonder.provider.model.TripWhenPlan) {
        val (start, end) = whenPlan.resolvedRange
        val previous = _trip.value
        val confirmed = previous.copy(
            startDate = start,
            endDate = end,
            datesConfirmed = true
        )
        _trip.value = confirmed
        if (!modeWasChosen) {
            _mode.value = naturalModeFor(confirmed)
        }
        scope.launch(Dispatchers.IO) { persistTripMeta() }
    }

    fun expensesOn(date: LocalDate): List<Expense> = _expenses.value.filter { it.date == date }

    fun budget(): BudgetSummary {
        val trip = _trip.value
        val expenses = _expenses.value
        val party = trip.partySize
        val spent = expenses.sumOf { it.amount }
        val settled = expenses.mapNotNull { it.itemId }.toSet()

        val stillToPay = _items.value
            .filter { it.id !in settled && it.status != ItemStatus.DONE }
            .sumOf { it.expectedTotal(party) }

        val plannedByCategory = _items.value
            .groupBy { it.kind.toCategory() }
            .mapValues { (_, items) -> items.sumOf { it.expectedTotal(party) } }

        val spentByCategory = expenses
            .groupBy { it.category }
            .mapValues { (_, entries) -> entries.sumOf { it.amount } }

        val categories = ExpenseCategory.entries
            .map { category ->
                CategorySpend(
                    category = category,
                    spent = spentByCategory[category] ?: 0.0,
                    planned = plannedByCategory[category] ?: 0.0
                )
            }
            .filter { it.spent > 0 || it.planned > 0 }
            .sortedByDescending { maxOf(it.spent, it.planned) }

        return BudgetSummary(
            budget = trip.budget,
            spent = spent,
            unplannedSpent = expenses.filter { it.isUnplanned }.sumOf { it.amount },
            stillToPay = stillToPay,
            currency = trip.currency,
            homeCurrency = trip.homeCurrency,
            homeRate = trip.homeRate,
            byCategory = categories
        )
    }

    fun traveller(id: String): Traveller? = _trip.value.travellers.firstOrNull { it.id == id }

    fun money(amount: Double): String = format(amount, _trip.value.currency)

    // endregion

    companion object {
        private const val PREFS_NAME = "wonder_trips"
        private const val KEY_ACTIVE_TRIP = "active_trip_id"
        /** Keeps an intentionally empty trip library empty after a process restart. */
        private const val KEY_USER_CLEARED_TRIPS = "user_cleared_trips"
        const val DEFAULT_DESTINATION = "Not decided yet"

        fun isValidTripTitle(title: String): Boolean {
            val trimmed = title.trim()
            return trimmed.length >= 2 && !trimmed.equals("new trip", ignoreCase = true)
        }

        /** True once the traveller has named a real place — not the blank-trip placeholder. */
        fun hasDecidedDestination(destination: String): Boolean {
            val trimmed = destination.trim()
            return trimmed.isNotBlank() && !trimmed.equals(DEFAULT_DESTINATION, ignoreCase = true)
        }

        private val itemOrder = compareBy<ItineraryItem>(
            { it.date },
            { it.startTime ?: LocalTime.MAX },
            { it.title }
        )

        fun format(amount: Double, currency: String): String {
            val rounded = if (amount % 1.0 == 0.0) "%,.0f" else "%,.2f"
            return "$currency${String.format(Locale.ENGLISH, rounded, amount)}"
        }
    }
}

fun ItemKind.toCategory(): ExpenseCategory = when (this) {
    ItemKind.FLIGHT, ItemKind.TRANSPORT -> ExpenseCategory.TRAVEL
    ItemKind.STAY -> ExpenseCategory.STAY
    ItemKind.FOOD -> ExpenseCategory.FOOD
    ItemKind.ACTIVITY, ItemKind.OUTDOORS, ItemKind.NIGHTLIFE -> ExpenseCategory.ACTIVITIES
    ItemKind.SHOPPING -> ExpenseCategory.SHOPPING
    ItemKind.FREE -> ExpenseCategory.OTHER
}
