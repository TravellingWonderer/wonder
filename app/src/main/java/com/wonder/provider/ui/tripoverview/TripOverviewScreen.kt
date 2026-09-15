package com.wonder.provider.ui.tripoverview

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wonder.provider.AppContainer
import com.wonder.provider.model.GeoCoordinate
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.MapRouteSegment
import com.wonder.provider.model.TimelineLeg
import com.wonder.provider.model.Trip
import com.wonder.provider.ui.personas.TripPersonasShortcut
import com.wonder.provider.ui.theme.WonderColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val TRIP_RANGE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripOverviewScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpenConversation: () -> Unit,
    onManagePersonas: () -> Unit
) {
    val viewModel: TripOverviewViewModel = viewModel(
        factory = TripOverviewViewModelFactory(
            trips = AppContainer.trips,
            geocoder = AppContainer.tripGeocoder,
            routeFetcher = AppContainer.tripRouteFetcher,
            tripId = tripId
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTripMeta by remember { mutableStateOf(false) }

    val mapHeight by animateDpAsState(
        targetValue = if (state.mapExpanded) 280.dp else 128.dp,
        label = "mapHeight"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (state.trip == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Trip not found", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onBack) { Text("Go back") }
            }
        } else {
            val trip = state.trip!!
            val center = state.mapCenter ?: GeoCoordinate(48.0, 2.0)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                OverviewTopBar(
                    trip = trip,
                    isActive = state.isActive,
                    showMeta = showTripMeta,
                    onBack = onBack,
                    onToggleMeta = { showTripMeta = !showTripMeta },
                    onActivate = { viewModel.activateTrip(onDone = onOpenConversation) },
                    onOpenConversation = onOpenConversation
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight)
                        .animateContentSize()
                        .clickable { viewModel.toggleMapExpanded() }
                ) {
                    TripMapView(
                        markers = state.markers,
                        routeSegments = state.routeSegments,
                        center = center,
                        selectedLegId = state.selectedLegId,
                        routeColor = MaterialTheme.colorScheme.primary.toArgb(),
                        routeMutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            .toArgb(),
                        onMarkerClick = viewModel::selectLeg,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (state.routeSegments.isNotEmpty() && (state.mapExpanded || state.selectedLegId != null)) {
                        RouteDirectionsStrip(
                            segments = state.routeSegments,
                            selectedLegId = state.selectedLegId,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }
                    MapExpandHint(expanded = state.mapExpanded)
                }

                Text(
                    text = "Timeline · tap a stop for details",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TripPersonasShortcut(
                    tripId = trip.id,
                    onManagePersonas = onManagePersonas,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )

                if (state.legs.isEmpty()) {
                    Text(
                        text = "No legs planned yet — open chat to start building this trip.",
                        modifier = Modifier.padding(horizontal = 18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(start = 12.dp, end = 18.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(state.legs, key = { _, leg -> leg.item.id }) { index, leg ->
                            val previous = state.legs.getOrNull(index - 1)
                            val showDay = previous == null || previous.item.date != leg.item.date
                            TimelineLegRow(
                                leg = leg,
                                showDayHeader = showDay,
                                selected = state.selectedLegId == leg.item.id,
                                onClick = { viewModel.selectLeg(leg.item.id) }
                            )
                        }
                    }
                }
            }

            state.selectedLeg?.let { item ->
                LegDetailSheet(
                    item = item,
                    trip = trip,
                    onDismiss = { viewModel.selectLeg(null) }
                )
            }
        }
    }
}

@Composable
private fun OverviewTopBar(
    trip: Trip,
    isActive: Boolean,
    showMeta: Boolean,
    onBack: () -> Unit,
    onToggleMeta: () -> Unit,
    onActivate: () -> Unit,
    onOpenConversation: () -> Unit
) {
    val palette = WonderColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = trip.destination.split(",").first(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleMeta) {
                Icon(Icons.Outlined.Info, contentDescription = "Trip details")
            }
        }

        if (showMeta) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.cardTint)
                    .border(1.dp, palette.hairline, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${TRIP_RANGE_FORMAT.format(trip.startDate)} – ${TRIP_RANGE_FORMAT.format(trip.endDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${trip.travellers.size} travellers · ${trip.budget.toInt()} ${trip.currency} budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isActive) {
                Button(
                    onClick = onOpenConversation,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp)
                    )
                    Text("Open chat")
                }
            } else {
                OutlinedButton(
                    onClick = onActivate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Use this trip")
                }
            }
        }
    }
}

@Composable
private fun BoxScope.RouteDirectionsStrip(
    segments: List<MapRouteSegment>,
    selectedLegId: String?,
    modifier: Modifier = Modifier
) {
    val palette = WonderColors.current
    val visible = segments.filter { segment ->
        selectedLegId == null ||
            segment.fromLegId == selectedLegId ||
            segment.toLegId == selectedLegId
    }
    if (visible.isEmpty()) return

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, palette.hairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visible.forEach { segment ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = segment.fromTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (segment.isLongHaul) "✈" else "→",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = segment.toTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.MapExpandHint(expanded: Boolean) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (expanded) "Collapse" else "Expand map",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Composable
private fun TimelineLegRow(
    leg: TimelineLeg,
    showDayHeader: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    val item = leg.item
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TimelineRuler(
            showDayHeader = showDayHeader,
            dateLabel = DAY_FORMAT.format(item.date),
            timeLabel = item.startTime?.let(TIME_FORMAT::format) ?: "—",
            selected = selected
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) palette.cardTint else MaterialTheme.colorScheme.surface)
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else palette.hairline,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.kind.emoji, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (item.location.isNotBlank()) {
                Text(
                    text = item.location,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TimelineRuler(
    showDayHeader: Boolean,
    dateLabel: String,
    timeLabel: String,
    selected: Boolean
) {
    val palette = WonderColors.current
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.End
    ) {
        if (showDayHeader) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        } else {
            Box(modifier = Modifier.height(4.dp))
        }
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(if (selected) 10.dp else 8.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
        )
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegDetailSheet(
    item: ItineraryItem,
    trip: Trip,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val palette = WonderColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.kind.emoji, style = MaterialTheme.typography.headlineSmall)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.kind.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DetailRow("When", buildString {
                append(DAY_FORMAT.format(item.date))
                item.startTime?.let { append(" · ${TIME_FORMAT.format(it)}") }
                if (item.durationMinutes > 0) append(" · ${item.durationMinutes} min")
            })

            if (item.location.isNotBlank()) {
                DetailRow("Where", item.location)
            }

            if (item.notes.isNotBlank()) {
                DetailRow("Notes", item.notes)
            }

            if (item.estimatedCost > 0) {
                val total = item.estimatedTotal(trip.travellers.size.coerceAtLeast(1))
                val costLine = buildString {
                    append("${"%.0f".format(Locale.ENGLISH, total)} ${trip.currency}")
                    if (item.costIsPerPerson) append(" (per person)")
                }
                DetailRow("Estimate", costLine)
            }

            if (item.status != ItemStatus.PLANNED) {
                DetailRow("Status", item.status.name.lowercase().replaceFirstChar { it.titlecase(Locale.ENGLISH) })
            }

            if (item.bookingRef.isNotBlank()) {
                DetailRow("Booking", item.bookingRef)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WonderColors.current.cardTint)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(Locale.ENGLISH),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
