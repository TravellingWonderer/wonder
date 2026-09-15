package com.wonder.provider.ui.tripoverview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wonder.provider.data.TripRepository
import com.wonder.provider.data.maps.CityCoordinates
import com.wonder.provider.data.maps.TripGeocoder
import com.wonder.provider.data.maps.TripRouteFetcher
import com.wonder.provider.model.GeoCoordinate
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.MapLegMarker
import com.wonder.provider.model.MapRouteSegment
import com.wonder.provider.model.TimelineLeg
import com.wonder.provider.model.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripOverviewUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val legs: List<TimelineLeg> = emptyList(),
    val markers: List<MapLegMarker> = emptyList(),
    val routeSegments: List<MapRouteSegment> = emptyList(),
    val mapCenter: GeoCoordinate? = null,
    val isActive: Boolean = false,
    val selectedLegId: String? = null,
    val mapExpanded: Boolean = false
) {
    val selectedLeg: ItineraryItem?
        get() = legs.firstOrNull { it.item.id == selectedLegId }?.item
}

class TripOverviewViewModel(
    private val trips: TripRepository,
    private val geocoder: TripGeocoder,
    private val routeFetcher: TripRouteFetcher,
    private val tripId: String
) : ViewModel() {

    private val _state = MutableStateFlow(TripOverviewUiState())
    val state: StateFlow<TripOverviewUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val snapshot = trips.loadTripSnapshot(tripId) ?: run {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val center = CityCoordinates.forDestination(snapshot.trip.destination)
                ?: GeoCoordinate(48.0, 2.0)
            val legs = snapshot.items.mapIndexed { index, item ->
                val coordinate = geocoder.resolve(item.location, snapshot.trip.destination)
                    ?: CityCoordinates.offsetFromCenter(center, index)
                TimelineLeg(item = item, coordinate = coordinate)
            }
            val markers = legs.mapNotNull { leg ->
                leg.coordinate?.let { coord ->
                    MapLegMarker(
                        legId = leg.item.id,
                        title = leg.item.title,
                        coordinate = coord
                    )
                }
            }
            val routeSegments = routeFetcher.buildSegments(legs)
            _state.update {
                it.copy(
                    loading = false,
                    trip = snapshot.trip,
                    legs = legs,
                    markers = markers,
                    routeSegments = routeSegments,
                    mapCenter = center,
                    isActive = snapshot.isActive
                )
            }
        }
    }

    fun selectLeg(legId: String?) {
        _state.update { it.copy(selectedLegId = legId) }
    }

    fun toggleMapExpanded() {
        _state.update { it.copy(mapExpanded = !it.mapExpanded) }
    }

    fun activateTrip(onDone: () -> Unit) {
        trips.switchActiveTripAsync(tripId)
        _state.update { it.copy(isActive = true) }
        onDone()
    }
}

class TripOverviewViewModelFactory(
    private val trips: TripRepository,
    private val geocoder: TripGeocoder,
    private val routeFetcher: TripRouteFetcher,
    private val tripId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TripOverviewViewModel(trips, geocoder, routeFetcher, tripId) as T
}
