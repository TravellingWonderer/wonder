package com.wonder.provider.data

import android.content.Context
import com.wonder.provider.ai.AiSettingsRepository
import com.wonder.provider.model.ExploreFeed
import com.wonder.provider.model.ExploreFeedKind
import com.wonder.provider.model.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class ExploreRepository(
    context: Context,
    private val trips: TripRepository,
    private val aiSettings: AiSettingsRepository
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _feed = MutableStateFlow<ExploreFeed?>(null)
    val feed: StateFlow<ExploreFeed?> = _feed.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        scope.launch {
            val tripId = trips.activeTripId()
            val trip = trips.trip.value
            _feed.value = if (tripId != null && TripRepository.hasDecidedDestination(trip.destination)) {
                loadCachedForToday(tripId)
            } else {
                null
            }
            ensureFeed(force = false)
        }
        scope.launch {
            // Switching trips must stay instant — only swap in today's cache.
            // AI generation runs when Explore is visible via ensureFeed().
            trips.activeTripSwitched.collect { showCachedFeedForActiveTrip() }
        }
    }

    /** Instant: show today's cached feed for the active trip, or clear. No AI. */
    private fun showCachedFeedForActiveTrip() {
        val tripId = trips.activeTripId() ?: run {
            _feed.value = null
            return
        }
        val trip = trips.trip.value
        if (!TripRepository.hasDecidedDestination(trip.destination)) {
            _feed.value = null
            return
        }
        _feed.value = loadCachedForToday(tripId)
    }

    /** Loads today's cached feed; generates once per day when missing or stale. */
    suspend fun ensureFeed(force: Boolean = false) {
        mutex.withLock {
            val tripId = trips.activeTripId() ?: run {
                _feed.value = null
                return
            }
            trips.resolveDestinationIfNeeded()
            val trip = trips.trip.value
            val destination = trip.destination

            // Blank / undecided trips must not inherit the previous destination's ideas.
            if (!TripRepository.hasDecidedDestination(destination)) {
                _feed.value = null
                _isRefreshing.value = false
                return
            }

            val today = LocalDate.now()
            val feedKind = feedKindFor(trip, today)
            val cached = loadCached(tripId, today, feedKind)

            if (cached != null && cached.destination == destination && cached.isFreshFor(today)) {
                _feed.value = cached
                return
            }

            if (force && cached != null && cached.isFreshFor(today)) {
                _feed.value = cached
                return
            }

            val context = buildGenerationContext(trip, today, feedKind)
            if (_feed.value == null || _feed.value?.destination != destination) {
                _feed.value = ExploreContentGenerator.generate(context, chatProvider = null)
            }

            _isRefreshing.value = true
            try {
                val generated = ExploreContentGenerator.generate(
                    context = context,
                    chatProvider = aiSettings.chatProvider()
                )
                persist(tripId, today, feedKind, generated)
                _feed.value = generated
            } catch (_: Exception) {
                if (_feed.value == null) {
                    _feed.value = ExploreContentGenerator.generate(context, chatProvider = null)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refresh() {
        scope.launch { ensureFeed(force = true) }
    }

    private fun loadCachedForToday(tripId: String): ExploreFeed? {
        val trip = trips.trip.value
        val today = LocalDate.now()
        return loadCached(tripId, today, feedKindFor(trip, today))
    }

    private fun feedKindFor(trip: Trip, today: LocalDate): ExploreFeedKind =
        if (trip.covers(today)) ExploreFeedKind.LIVE_TRIP else ExploreFeedKind.DISCOVERY

    private fun buildGenerationContext(
        trip: Trip,
        today: LocalDate,
        feedKind: ExploreFeedKind
    ): ExploreGenerationContext {
        val todayItems = trips.items.value.filter { it.date == today }
        return ExploreGenerationContext(
            destination = trip.destination,
            interests = trip.interests,
            feedKind = feedKind,
            tripTitle = trip.title,
            tripStartDate = trip.startDate,
            tripEndDate = trip.endDate,
            today = today,
            dayNumber = if (trip.covers(today)) trip.dayNumber(today) else null,
            todayPlan = todayItems.map { item ->
                buildString {
                    append(item.title)
                    if (item.location.isNotBlank()) append(" @ ${item.location}")
                }
            }
        )
    }

    private fun loadCached(tripId: String, date: LocalDate, kind: ExploreFeedKind): ExploreFeed? =
        prefs.getString(feedKey(tripId, date, kind), null)?.let { ExploreFeedCodec.decode(it) }

    private fun persist(tripId: String, date: LocalDate, kind: ExploreFeedKind, feed: ExploreFeed) {
        prefs.edit().putString(feedKey(tripId, date, kind), ExploreFeedCodec.encode(feed)).apply()
    }

    private fun feedKey(tripId: String, date: LocalDate, kind: ExploreFeedKind) =
        "feed_v2_${tripId}_${date}_${kind.name}"

    companion object {
        private const val PREFS_NAME = "wonder_explore"
    }
}

internal data class ExploreGenerationContext(
    val destination: String,
    val interests: Set<com.wonder.provider.model.TourInterest>,
    val feedKind: ExploreFeedKind,
    val tripTitle: String = "",
    val tripStartDate: LocalDate? = null,
    val tripEndDate: LocalDate? = null,
    val today: LocalDate = LocalDate.now(),
    val dayNumber: Int? = null,
    val todayPlan: List<String> = emptyList()
)
