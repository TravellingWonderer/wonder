package com.wonder.provider

import android.content.Context
import com.wonder.provider.ai.AiSettingsRepository
import com.wonder.provider.ai.AiTourService
import com.wonder.provider.ai.TripAutoGenerator
import com.wonder.provider.ai.CardResolver
import com.wonder.provider.ai.LocalConversationEngine
import com.wonder.provider.ai.WonderAgent
import com.wonder.provider.auth.GoogleDriveTimelineFetcher
import com.wonder.provider.auth.GoogleMapsAuth
import com.wonder.provider.auth.GoogleSignInBridge
import com.wonder.provider.data.ExploreRepository
import com.wonder.provider.data.CustomPersonaRepository
import com.wonder.provider.data.NearbyExploreRepository
import com.wonder.provider.data.RecommendationEngine
import com.wonder.provider.data.TripRepository
import com.wonder.provider.data.maps.DeviceLocationProvider
import com.wonder.provider.data.maps.TripGeocoder
import com.wonder.provider.data.maps.TripRouteFetcher
import com.wonder.provider.data.maps.TravelProfileRepository
import com.wonder.provider.data.travel.TravelApiSettingsRepository
import com.wonder.provider.data.travel.TravelSearchRepository
import com.wonder.provider.notify.TripAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppContainer {

    private var initialized = false
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var aiSettingsRepository: AiSettingsRepository
        private set
    lateinit var aiTourService: AiTourService
        private set
    lateinit var tripAutoGenerator: TripAutoGenerator
        private set

    /** Trips live on-device in Room; one is active at a time. */
    lateinit var trips: TripRepository
        private set
    lateinit var travelProfile: TravelProfileRepository
        private set
    lateinit var googleMapsAuth: GoogleMapsAuth
        private set
    lateinit var driveTimelineFetcher: GoogleDriveTimelineFetcher
        private set
    lateinit var googleSignInBridge: GoogleSignInBridge
        private set
    lateinit var tripGeocoder: TripGeocoder
        private set
    lateinit var tripRouteFetcher: TripRouteFetcher
        private set
    lateinit var explore: ExploreRepository
        private set
    lateinit var nearbyExplore: NearbyExploreRepository
        private set
    lateinit var recommendations: RecommendationEngine
        private set
    lateinit var alarms: TripAlarms
        private set
    lateinit var travelApiSettings: TravelApiSettingsRepository
        private set
    lateinit var travelSearch: TravelSearchRepository
        private set
    lateinit var customPersonas: CustomPersonaRepository
        private set
    lateinit var wonderAgent: WonderAgent
        private set

    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext

        aiSettingsRepository = AiSettingsRepository(app)
        aiTourService = AiTourService(aiSettingsRepository)
        trips = TripRepository(app, appScope)
        tripAutoGenerator = TripAutoGenerator(trips, aiTourService)
        customPersonas = CustomPersonaRepository(app)
        travelProfile = TravelProfileRepository(app)
        googleMapsAuth = GoogleMapsAuth(app)
        driveTimelineFetcher = GoogleDriveTimelineFetcher()
        googleSignInBridge = GoogleSignInBridge()
        appScope.launch { travelProfile.refreshAiContextCache() }
        tripGeocoder = TripGeocoder(app)
        tripRouteFetcher = TripRouteFetcher()
        val deviceLocation = DeviceLocationProvider(app)
        explore = ExploreRepository(app, trips, aiSettingsRepository)
        nearbyExplore = NearbyExploreRepository(app, deviceLocation, aiSettingsRepository)
        recommendations = RecommendationEngine(trips)
        alarms = TripAlarms(app, trips)
        travelApiSettings = TravelApiSettingsRepository(app)
        travelSearch = TravelSearchRepository(travelApiSettings)

        wonderAgent = WonderAgent(
            settingsRepository = aiSettingsRepository,
            trips = trips,
            onDevice = LocalConversationEngine(trips),
            cards = CardResolver(trips, recommendations, aiTourService, travelSearch),
            travelProfile = travelProfile
        )

        TripAlarms.createChannel(app)
        initialized = true
    }
}
