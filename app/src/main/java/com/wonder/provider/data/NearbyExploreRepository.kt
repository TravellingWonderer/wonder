package com.wonder.provider.data

import android.content.Context
import com.wonder.provider.ai.AiSettingsRepository
import com.wonder.provider.data.db.ExploreCacheDao
import com.wonder.provider.data.db.ExploreFeedEntity
import com.wonder.provider.data.maps.DeviceLocationProvider
import com.wonder.provider.model.GeoCoordinate
import com.wonder.provider.model.ExploreFeed
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class NearbyLocationStatus {
    IDLE,
    LOADING,
    READY,
    PERMISSION_DENIED,
    UNAVAILABLE
}

class NearbyExploreRepository(
    context: Context,
    private val cacheDao: ExploreCacheDao,
    private val locationProvider: DeviceLocationProvider,
    private val aiSettings: AiSettingsRepository
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _feed = MutableStateFlow<ExploreFeed?>(null)
    val feed: StateFlow<ExploreFeed?> = _feed.asStateFlow()

    private val _placeLabel = MutableStateFlow<String?>(null)
    val placeLabel: StateFlow<String?> = _placeLabel.asStateFlow()

    private val _status = MutableStateFlow(NearbyLocationStatus.IDLE)
    val status: StateFlow<NearbyLocationStatus> = _status.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _userCoordinate = MutableStateFlow<GeoCoordinate?>(null)
    val userCoordinate: StateFlow<GeoCoordinate?> = _userCoordinate.asStateFlow()

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    suspend fun currentUserCoordinate(): GeoCoordinate? {
        _userCoordinate.value?.let { return it }
        if (!locationProvider.hasPermission()) return null
        val place = locationProvider.currentPlace() ?: return null
        return GeoCoordinate(place.latitude, place.longitude).also { _userCoordinate.value = it }
    }

    fun refresh() {
        scope.launch { ensureFeed(force = true) }
    }

    suspend fun ensureFeed(force: Boolean = false) {
        mutex.withLock {
            _isRefreshing.value = true
            try {
                if (!locationProvider.hasPermission()) {
                    _status.value = NearbyLocationStatus.PERMISSION_DENIED
                    publishGenericNearby(force)
                    return
                }

                _status.value = NearbyLocationStatus.LOADING
                val place = locationProvider.currentPlace()
                if (place == null) {
                    _status.value = NearbyLocationStatus.UNAVAILABLE
                    publishGenericNearby(force)
                    return
                }

                _placeLabel.value = place.label
                _userCoordinate.value = GeoCoordinate(place.latitude, place.longitude)
                _status.value = NearbyLocationStatus.READY
                val today = LocalDate.now()
                val cacheKey = place.cacheKey()
                val cached = loadCached(cacheKey, today)
                if (!force && cached != null && cached.isFreshFor(today)) {
                    _feed.value = cached
                    return
                }

                val local = ExploreContentGenerator.generateNearby(place.label, today)
                _feed.value = local
                persist(cacheKey, today, local)

                withTimeoutOrNull(AI_TIMEOUT_MS) {
                    runCatching {
                        val upgraded = ExploreContentGenerator.generate(
                            context = ExploreGenerationContext(
                                destination = place.label,
                                interests = emptySet(),
                                feedKind = com.wonder.provider.model.ExploreFeedKind.NEARBY
                            ),
                            chatProvider = aiSettings.chatProvider()
                        )
                        persist(cacheKey, today, upgraded)
                        _feed.value = upgraded
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun publishGenericNearby(force: Boolean) {
        val today = LocalDate.now()
        val cacheKey = "nearby_generic"
        val cached = loadCached(cacheKey, today)
        if (!force && cached != null && cached.isFreshFor(today)) {
            _feed.value = cached
            _placeLabel.value = cached.destination
            return
        }
        val feed = ExploreContentGenerator.generateNearby(_placeLabel.value ?: "Near you", today)
        _feed.value = feed
        _placeLabel.value = feed.destination
        persist(cacheKey, today, feed)
    }

    private suspend fun loadCached(key: String, date: LocalDate): ExploreFeed? {
        val cacheKey = feedKey(key, date)
        cacheDao.feed(cacheKey)?.payloadJson?.let { return ExploreFeedCodec.decode(it) }
        val legacy = prefs.getString(cacheKey, null) ?: return null
        val feed = ExploreFeedCodec.decode(legacy) ?: return null
        persist(key, date, feed)
        prefs.edit().remove(cacheKey).apply()
        return feed
    }

    private suspend fun persist(key: String, date: LocalDate, feed: ExploreFeed) {
        cacheDao.upsert(
            ExploreFeedEntity(
                cacheKey = feedKey(key, date),
                destination = feed.destination,
                payloadJson = ExploreFeedCodec.encode(feed),
                storedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    private fun feedKey(key: String, date: LocalDate) = "nearby_feed_${key}_${date}"

    companion object {
        private const val PREFS_NAME = "wonder_nearby_explore"
        private const val AI_TIMEOUT_MS = 45_000L
    }
}
