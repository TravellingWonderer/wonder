package com.wonder.provider.ui.tripoverview

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wonder.provider.AppContainer
import com.wonder.provider.model.GeoCoordinate
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.MapRouteSegment
import com.wonder.provider.model.TimelineLeg
import com.wonder.provider.model.Trip
import com.wonder.provider.ui.conversation.AmbientBackdrop
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
    val palette = WonderColors.current
    val emptyTrip = state.legs.isEmpty() && state.trip != null

    val mapHeight by animateDpAsState(
        targetValue = when {
            emptyTrip -> 112.dp
            state.mapExpanded -> 280.dp
            else -> 148.dp
        },
        label = "mapHeight"
    )

    fun openChat(withVoice: Boolean) {
        if (withVoice) AppContainer.requestVoiceOnConversationOpen()
        if (state.isActive) {
            onOpenConversation()
        } else {
            viewModel.activateTrip(onDone = onOpenConversation)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AmbientBackdrop(modifier = Modifier.fillMaxSize(), alive = emptyTrip)

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
                TextButton(onClick = onBack) { Text("Go back") }
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
                    emptyTrip = emptyTrip,
                    onBack = onBack,
                    onToggleMeta = { showTripMeta = !showTripMeta },
                    onOpenChat = { openChat(withVoice = false) }
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(mapHeight)
                        .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = palette.aurora[0].copy(0.2f))
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, palette.aurora[0].copy(alpha = 0.28f), RoundedCornerShape(24.dp))
                        .animateContentSize()
                        .clickable(enabled = !emptyTrip) { viewModel.toggleMapExpanded() }
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
                    if (emptyTrip) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            palette.aurora[0].copy(alpha = 0.18f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                                        )
                                    )
                                )
                        )
                    }
                    if (state.routeSegments.isNotEmpty() && (state.mapExpanded || state.selectedLegId != null)) {
                        RouteDirectionsStrip(
                            segments = state.routeSegments,
                            selectedLegId = state.selectedLegId,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }
                    if (!emptyTrip) {
                        MapExpandHint(expanded = state.mapExpanded)
                    }
                }

                if (emptyTrip) {
                    EmptyTripInvite(
                        trip = trip,
                        onVoicePlan = { openChat(withVoice = true) },
                        onTypePlan = { openChat(withVoice = false) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    )
                } else {
                    Text(
                        text = "Timeline · tap a stop for details",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TripPersonasShortcut(
                        tripId = trip.id,
                        onManagePersonas = onManagePersonas,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )

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
    emptyTrip: Boolean,
    onBack: () -> Unit,
    onToggleMeta: () -> Unit,
    onOpenChat: () -> Unit
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(palette.aurora)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = trip.coverEmoji, fontSize = 22.sp)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = trip.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (emptyTrip) {
                        "Blank canvas · ready for plans"
                    } else {
                        trip.destination.split(",").first()
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (emptyTrip) palette.aurora[0] else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleMeta) {
                Icon(Icons.Outlined.Info, contentDescription = "Trip details")
            }
            if (!emptyTrip) {
                IconButton(onClick = onOpenChat) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Open chat")
                }
            }
        }

        if (showMeta) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                palette.aurora[0].copy(alpha = 0.12f),
                                palette.aurora[1].copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${TRIP_RANGE_FORMAT.format(trip.startDate)} – ${TRIP_RANGE_FORMAT.format(trip.endDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${trip.travellers.size} travellers · ${trip.budget.toInt()} ${trip.currency} budget" +
                        if (isActive) " · active" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyTripInvite(
    trip: Trip,
    onVoicePlan: () -> Unit,
    onTypePlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = WonderColors.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = palette.aurora[1].copy(0.28f))
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.aurora[0].copy(alpha = 0.16f),
                            palette.aurora[1].copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .border(1.dp, palette.aurora[0].copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "No plans yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tell Wonder what you want for ${trip.title} — flights, food, days out — and it'll shape the itinerary with you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            VoicePlanButton(onClick = onVoicePlan)

            Text(
                text = "Tap to speak your first plans",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.aurora[0]
            )

            TextButton(onClick = onTypePlan) {
                Text(
                    text = "Or type it in chat",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoicePlanButton(onClick: () -> Unit) {
    val palette = WonderColors.current
    val transition = rememberInfiniteTransition(label = "voice-cta")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7500, easing = LinearEasing)
        ),
        label = "spin"
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .size(156.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val base = size.minDimension / 2f
            palette.aurora.forEachIndexed { index, color ->
                val radius = base * (0.58f + index * 0.15f) * pulse
                drawCircle(
                    color = color.copy(alpha = (0.22f - index * 0.05f) * glow),
                    radius = radius,
                    center = center,
                    style = Stroke(width = (2.5f + index).dp.toPx())
                )
            }
            rotate(degrees = spin, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.aurora[0].copy(alpha = 0.7f),
                            Color.Transparent,
                            palette.aurora[1].copy(alpha = 0.55f),
                            Color.Transparent,
                            palette.aurora[2].copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = base * 0.8f * pulse,
                    center = center,
                    style = Stroke(width = 5.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .size(92.dp)
                .shadow(22.dp, CircleShape, ambientColor = palette.aurora[0].copy(alpha = 0.5f * glow))
                .clip(CircleShape)
                .background(Brush.linearGradient(palette.aurora)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = "Plan by voice",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
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
                .background(
                    if (selected) {
                        Brush.linearGradient(
                            listOf(
                                palette.aurora[0].copy(alpha = 0.14f),
                                palette.aurora[1].copy(alpha = 0.08f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(palette.cardTint, palette.cardTint)
                        )
                    }
                )
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) palette.aurora[0] else palette.hairline,
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
                    if (selected) palette.aurora[0]
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
