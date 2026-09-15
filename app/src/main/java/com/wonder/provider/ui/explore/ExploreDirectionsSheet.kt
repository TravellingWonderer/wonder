package com.wonder.provider.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wonder.provider.AppContainer
import com.wonder.provider.model.ExplorePlacePick
import com.wonder.provider.model.GeoCoordinate
import com.wonder.provider.model.MapLegMarker
import com.wonder.provider.model.MapRouteSegment
import com.wonder.provider.ui.theme.WonderColors
import com.wonder.provider.ui.tripoverview.TripMapView

private data class DirectionsMapState(
    val markers: List<MapLegMarker>,
    val segments: List<MapRouteSegment>,
    val center: GeoCoordinate,
    val approximate: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreDirectionsSheet(
    pick: ExplorePlacePick,
    onDismiss: () -> Unit
) {
    val palette = WonderColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember(pick) { mutableStateOf(true) }
    var error by remember(pick) { mutableStateOf<String?>(null) }
    var mapState by remember(pick) { mutableStateOf<DirectionsMapState?>(null) }

    LaunchedEffect(pick) {
        loading = true
        error = null
        mapState = null
        try {
            val routeFetcher = AppContainer.tripRouteFetcher
            val userCoord = AppContainer.nearbyExplore.currentUserCoordinate()
            val resolution = AppContainer.tripGeocoder.resolveExplore(pick, userCoord)
            val destination = resolution.coordinate
            val origin = userCoord

            if (origin == null) {
                mapState = DirectionsMapState(
                    markers = listOf(MapLegMarker("dest", pick.title, destination)),
                    segments = emptyList(),
                    center = destination,
                    approximate = resolution.approximate
                )
                error = "Turn on location to see the route from where you are."
                loading = false
                return@LaunchedEffect
            }

            val routePoints = routeFetcher.routeBetween(origin, destination)
                ?: listOf(origin, destination)
            mapState = DirectionsMapState(
                markers = listOf(
                    MapLegMarker("you", "You", origin),
                    MapLegMarker("dest", pick.title, destination)
                ),
                segments = listOf(
                    MapRouteSegment(
                        fromLegId = "you",
                        toLegId = "dest",
                        fromTitle = "You",
                        toTitle = pick.title,
                        points = routePoints,
                        isLongHaul = false
                    )
                ),
                center = GeoCoordinate(
                    latitude = (origin.latitude + destination.latitude) / 2,
                    longitude = (origin.longitude + destination.longitude) / 2
                ),
                approximate = resolution.approximate
            )
        } catch (_: Exception) {
            error = "Couldn't load the map right now."
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = pick.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "From where you are now",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                            .height(24.dp),
                        strokeWidth = 2.dp,
                        color = palette.aurora[0]
                    )
                }
                mapState != null -> {
                    TripMapView(
                        markers = mapState!!.markers,
                        routeSegments = mapState!!.segments,
                        center = mapState!!.center,
                        selectedLegId = null,
                        routeColor = palette.aurora[0].toArgb(),
                        routeMutedColor = palette.aurora[1].copy(alpha = 0.45f).toArgb(),
                        onMarkerClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    if (mapState!!.approximate) {
                        Text(
                            text = "Approximate pin — exact spot may vary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Text(
                        text = error ?: "Couldn't load the map.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
