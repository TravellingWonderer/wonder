package com.wonder.provider.ui.tripoverview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.TimelineLeg
import com.wonder.provider.model.TimeWindow
import com.wonder.provider.ui.theme.WonderColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * Layout position computed for an activity beacon along the ruler track,
 * with anti-collision spacing and staggered horizontal offset.
 */
private data class PlacedRulerNode(
    val leg: TimelineLeg,
    val yPx: Float,
    val xOffsetDp: Float,
    val fraction: Float
)

/**
 * Modern interactive scrollable side ruler.
 * - Seamlessly fades into the dark background without harsh borders.
 * - In idle state, displays subtle ambient time references (early morning to late night).
 * - On touch or scrub, illuminates calibrated graduated ticks and interactive track.
 * - Auto-stretches when a specific TimeWindow is selected (e.g. morning, noon, etc.).
 * - Applies anti-collision layout to prevent activity beacons from crowding or overlapping.
 * - Strictly guides and scrolls the main pane; beacons do NOT open modal sheets.
 */
@Composable
fun TimelineRulerScrubber(
    legs: List<TimelineLeg>,
    selectedLegId: String?,
    hoveredLegId: String?,
    onHoverLeg: (String?) -> Unit,
    onGuideToLeg: (String) -> Unit,
    activeTimeWindow: TimeWindow?,
    onSelectTimeWindow: (TimeWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = WonderColors.current
    val density = LocalDensity.current

    // Auto-stretching time span calculation
    val (startHour, endHour) = remember(activeTimeWindow, legs) {
        if (activeTimeWindow != null) {
            val start = activeTimeWindow.startHour
            val end = (activeTimeWindow.endHour + 1).coerceAtMost(24)
            Pair(start, end)
        } else {
            // Whole day span: 05:00 (Early morning) to 24:00 (Midnight)
            val minLegHour = legs.mapNotNull { it.item.startTime?.hour }.minOrNull() ?: 6
            val maxLegHour = legs.mapNotNull { it.item.startTime?.hour }.maxOrNull() ?: 22
            val start = minOf(5, minLegHour)
            val end = maxOf(23, maxLegHour + 1).coerceAtMost(24)
            Pair(start, end)
        }
    }

    val totalHours = endHour - startHour
    val totalSpanMinutes = (totalHours * 60).toFloat().coerceAtLeast(60f)

    // Filter legs to display on the ruler:
    // If a time window is active, show only activities in that window.
    val visibleLegs = remember(legs, activeTimeWindow, startHour, endHour) {
        if (activeTimeWindow != null) {
            legs.filter { activeTimeWindow.matches(it.item.startTime) }
        } else {
            legs
        }
    }

    var rulerHeightPx by remember { mutableFloatStateOf(1f) }
    var scrubY by remember { mutableFloatStateOf(-1f) }
    var isInteracting by remember { mutableStateOf(false) }

    // Smooth reveal animation when touched / scrubbed
    val rulerDetailAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "rulerDetailAlpha"
    )

    // Anti-collision algorithm: compute (yPx, xOffsetDp) for each activity beacon
    val placedNodes = remember(visibleLegs, rulerHeightPx, startHour, totalSpanMinutes, density) {
        if (rulerHeightPx <= 10f || visibleLegs.isEmpty()) return@remember emptyList<PlacedRulerNode>()

        val topPaddingPx = with(density) { 26.dp.toPx() }
        val bottomPaddingPx = with(density) { 26.dp.toPx() }
        val availableHeight = (rulerHeightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(10f)
        val minGapPx = with(density) { 28.dp.toPx() }

        val sorted = visibleLegs.sortedBy {
            val t = it.item.startTime ?: LocalTime.of(startHour, 0)
            t.hour * 60 + t.minute
        }

        val count = sorted.size
        val idealYList = FloatArray(count)
        val fractions = FloatArray(count)

        for (i in 0 until count) {
            val item = sorted[i].item
            val time = item.startTime ?: LocalTime.of(startHour, 0)
            val itemMinutes = (time.hour * 60 + time.minute) - (startHour * 60)
            val fraction = (itemMinutes / totalSpanMinutes).coerceIn(0f, 1f)
            fractions[i] = fraction
            idealYList[i] = topPaddingPx + (fraction * availableHeight)
        }

        // Forward relaxation pass: ensure pos[i] >= pos[i-1] + minGapPx
        val pos = idealYList.clone()
        for (i in 1 until count) {
            if (pos[i] < pos[i - 1] + minGapPx) {
                pos[i] = pos[i - 1] + minGapPx
            }
        }

        // Backward relaxation pass if overflowing the bottom
        val maxAllowedY = rulerHeightPx - bottomPaddingPx
        if (count > 0 && pos[count - 1] > maxAllowedY) {
            pos[count - 1] = maxAllowedY
            for (i in count - 2 downTo 0) {
                if (pos[i] > pos[i + 1] - minGapPx) {
                    pos[i] = pos[i + 1] - minGapPx
                }
            }
            // Clamp top boundary
            if (pos[0] < topPaddingPx) {
                val step = ((maxAllowedY - topPaddingPx) / (count - 1).coerceAtLeast(1)).coerceAtMost(minGapPx)
                for (i in 0 until count) {
                    pos[i] = topPaddingPx + (i * step)
                }
            }
        }

        // Horizontal stagger for items that are close or shifted, avoiding any visual crowding
        sorted.mapIndexed { i, leg ->
            val wasShifted = abs(pos[i] - idealYList[i]) > with(density) { 6.dp.toPx() }
            val closeToPrev = i > 0 && (idealYList[i] - idealYList[i - 1]) < with(density) { 24.dp.toPx() }
            val xOffset = if (wasShifted || closeToPrev) {
                if (i % 2 == 1) -14f else 0f
            } else {
                0f
            }
            PlacedRulerNode(
                leg = leg,
                yPx = pos[i],
                xOffsetDp = xOffset,
                fraction = fractions[i]
            )
        }
    }

    // Helper to find closest activity to touch position
    fun findClosestLeg(y: Float): TimelineLeg? {
        if (visibleLegs.isEmpty()) return legs.firstOrNull()
        val topPaddingPx = with(density) { 26.dp.toPx() }
        val bottomPaddingPx = with(density) { 26.dp.toPx() }
        val availableHeight = (rulerHeightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(10f)
        val fraction = ((y - topPaddingPx) / availableHeight).coerceIn(0f, 1f)
        val targetMinute = (startHour * 60 + (fraction * totalSpanMinutes)).toInt()

        return visibleLegs.minByOrNull { leg ->
            val time = leg.item.startTime ?: LocalTime.of(startHour, 0)
            val itemMinute = time.hour * 60 + time.minute
            abs(itemMinute - targetMinute)
        }
    }

    Box(
        modifier = modifier
            .width(66.dp)
            .fillMaxHeight()
            // Seamless fade into dark background without harsh borders
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)
                    )
                )
            )
            .onGloballyPositioned { coordinates ->
                rulerHeightPx = coordinates.size.height.toFloat()
            }
            // Pointer enter / exit / hover detection
            .pointerInput(visibleLegs, startHour, totalSpanMinutes) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position
                        when (event.type) {
                            PointerEventType.Move, PointerEventType.Enter -> {
                                if (position != null) {
                                    isInteracting = true
                                    scrubY = position.y
                                    val closest = findClosestLeg(position.y)
                                    closest?.let {
                                        onHoverLeg(it.item.id)
                                    }
                                }
                            }
                            PointerEventType.Exit -> {
                                isInteracting = false
                                onHoverLeg(null)
                            }
                        }
                    }
                }
            }
            // Drag gesture for smooth scrubbing guidance
            .pointerInput(visibleLegs, startHour, totalSpanMinutes) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isInteracting = true
                        scrubY = offset.y
                        val closest = findClosestLeg(offset.y)
                        closest?.let {
                            onGuideToLeg(it.item.id)
                            onHoverLeg(it.item.id)
                        }
                    },
                    onDragEnd = {
                        isInteracting = false
                        onHoverLeg(null)
                    },
                    onDragCancel = {
                        isInteracting = false
                        onHoverLeg(null)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        isInteracting = true
                        scrubY = change.position.y
                        val closest = findClosestLeg(change.position.y)
                        closest?.let {
                            onGuideToLeg(it.item.id)
                            onHoverLeg(it.item.id)
                        }
                    }
                )
            }
            // Tap gesture: guides and scrolls to corresponding item
            .pointerInput(visibleLegs, startHour, totalSpanMinutes) {
                detectTapGestures { offset ->
                    isInteracting = true
                    scrubY = offset.y
                    val closest = findClosestLeg(offset.y)
                    closest?.let {
                        onGuideToLeg(it.item.id)
                        onHoverLeg(it.item.id)
                    }
                }
            }
    ) {
        val topPaddingPx = with(density) { 26.dp.toPx() }
        val bottomPaddingPx = with(density) { 26.dp.toPx() }
        val availableHeight = (rulerHeightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(10f)

        // -------------------------------------------------------------
        // LAYER 1: Idle Ambient Time References (Early Morning to Late Night)
        // -------------------------------------------------------------
        if (rulerDetailAlpha < 0.95f) {
            val idleAlpha = (1f - rulerDetailAlpha) * 0.55f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(idleAlpha)
            ) {
                if (activeTimeWindow == null) {
                    // Full day view: show major daytime phase indicators
                    val phases = listOf(
                        Triple(TimeWindow.EARLY_MORNING, 5, "05:00"),
                        Triple(TimeWindow.MORNING, 9, "09:00"),
                        Triple(TimeWindow.NOON, 12, "12:00"),
                        Triple(TimeWindow.AFTERNOON, 15, "15:00"),
                        Triple(TimeWindow.EVENING, 18, "18:00"),
                        Triple(TimeWindow.NIGHT, 21, "21:00"),
                        Triple(TimeWindow.LATE_NIGHT, 23, "23:00")
                    )

                    phases.forEach { (window, hour, _) ->
                        val itemMinutes = (hour * 60) - (startHour * 60)
                        val fraction = (itemMinutes / totalSpanMinutes).coerceIn(0f, 1f)
                        val yOffset = topPaddingPx + (fraction * availableHeight)

                        Row(
                            modifier = Modifier
                                .offset { IntOffset(x = 0, y = (yOffset - 8.dp.toPx()).roundToInt()) }
                                .fillMaxWidth()
                                .padding(end = 12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = window.shortLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(palette.aurora[0].copy(alpha = 0.4f))
                            )
                        }
                    }
                } else {
                    // Stretched Time Window active: show the active window title and bounds
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 10.dp, end = 10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${activeTimeWindow.emoji} ${activeTimeWindow.shortLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = palette.aurora[0].copy(alpha = 0.7f)
                        )
                        Text(
                            text = String.format(Locale.ENGLISH, "%02d:00", startHour),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = String.format(Locale.ENGLISH, "%02d:00", endHour),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // LAYER 2: Touched / Scrubbed Calibrated Ruler (Revealed on touch)
        // -------------------------------------------------------------
        if (rulerDetailAlpha > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(rulerDetailAlpha)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val trackX = size.width - 12.dp.toPx()

                    // Illuminated aurora guide track
                    drawLine(
                        brush = Brush.verticalGradient(
                            listOf(
                                palette.aurora[0].copy(alpha = 0.35f * rulerDetailAlpha),
                                palette.aurora[1].copy(alpha = 0.7f * rulerDetailAlpha),
                                palette.aurora[2].copy(alpha = 0.45f * rulerDetailAlpha)
                            )
                        ),
                        start = Offset(trackX, topPaddingPx),
                        end = Offset(trackX, size.height - bottomPaddingPx),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Draw calibrated ticks
                    val isStretched = totalHours <= 5
                    val hourStep = if (isStretched) 1 else if (totalHours <= 10) 2 else 3

                    for (h in startHour..endHour) {
                        val minuteOffset = (h - startHour) * 60
                        val fraction = (minuteOffset / totalSpanMinutes).coerceIn(0f, 1f)
                        val y = topPaddingPx + (fraction * availableHeight)
                        val isLabeled = (h - startHour) % hourStep == 0 || h == endHour

                        // Major hourly tick
                        val tickLen = if (isLabeled) 12.dp.toPx() else 6.dp.toPx()
                        val tickColor = if (isLabeled)
                            palette.aurora[0].copy(alpha = 0.85f * rulerDetailAlpha)
                        else
                            Color.White.copy(alpha = 0.25f * rulerDetailAlpha)

                        drawLine(
                            color = tickColor,
                            start = Offset(trackX - tickLen, y),
                            end = Offset(trackX, y),
                            strokeWidth = if (isLabeled) 1.5.dp.toPx() else 1.dp.toPx()
                        )

                        // Minor sub-ticks for stretched mode
                        if (isStretched && h < endHour) {
                            for (subMin in listOf(15, 30, 45)) {
                                val subFraction = ((minuteOffset + subMin) / totalSpanMinutes).coerceIn(0f, 1f)
                                val subY = topPaddingPx + (subFraction * availableHeight)
                                val subTickLen = if (subMin == 30) 7.dp.toPx() else 4.dp.toPx()
                                drawLine(
                                    color = Color.White.copy(alpha = (if (subMin == 30) 0.2f else 0.12f) * rulerDetailAlpha),
                                    start = Offset(trackX - subTickLen, subY),
                                    end = Offset(trackX, subY),
                                    strokeWidth = 0.8.dp.toPx()
                                )
                            }
                        }
                    }
                }

                // Numerical hour labels along the left side
                val isStretched = totalHours <= 5
                val hourStep = if (isStretched) 1 else if (totalHours <= 10) 2 else 3

                for (h in startHour..endHour step hourStep) {
                    val minuteOffset = (h - startHour) * 60
                    val fraction = (minuteOffset / totalSpanMinutes).coerceIn(0f, 1f)
                    val y = topPaddingPx + (fraction * availableHeight)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(x = 0, y = (y - 7.dp.toPx()).roundToInt()) }
                            .fillMaxWidth()
                            .padding(end = 26.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = String.format(Locale.ENGLISH, "%02d:00", h),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = palette.aurora[0].copy(alpha = 0.8f * rulerDetailAlpha)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // LAYER 3: Activity Beacons (Anti-collision, visual guide only)
        // -------------------------------------------------------------
        placedNodes.forEach { node ->
            val item = node.leg.item
            val isSelected = selectedLegId == item.id
            val isHovered = hoveredLegId == item.id

            ActivityRulerBeacon(
                item = item,
                yPx = node.yPx,
                xOffsetDp = node.xOffsetDp,
                isSelected = isSelected,
                isHovered = isHovered,
                onClick = {
                    // Visual appeal and guidance ONLY: scroll to item & highlight, never open modal sheet
                    onGuideToLeg(item.id)
                    onHoverLeg(item.id)
                }
            )
        }

        // -------------------------------------------------------------
        // LAYER 4: Interactive Laser Scrub Indicator Line
        // -------------------------------------------------------------
        if (isInteracting && scrubY >= 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, scrubY.roundToInt()) }
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                palette.aurora[0].copy(alpha = 0.6f),
                                Color.White
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Pure visual beacon along the ruler track.
 * Provides rich feedback (scale, halo pulse) and guides scrolling, but does NOT trigger dialogs/sheets.
 */
@Composable
private fun ActivityRulerBeacon(
    item: ItineraryItem,
    yPx: Float,
    xOffsetDp: Float,
    isSelected: Boolean,
    isHovered: Boolean,
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    val highlight = isSelected || isHovered

    val scale by animateFloatAsState(
        targetValue = if (highlight) 1.25f else 1.0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "beaconScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (xOffsetDp * density).roundToInt(),
                        y = (yPx - 11.dp.toPx()).roundToInt()
                    )
                }
                .padding(end = 4.dp)
                .size(if (highlight) 26.dp else 22.dp)
                .shadow(
                    elevation = if (highlight) 10.dp else 2.dp,
                    shape = CircleShape,
                    ambientColor = if (highlight) palette.aurora[0].copy(alpha = pulseGlow) else Color.Transparent
                )
                .clip(CircleShape)
                .background(
                    if (highlight)
                        Brush.linearGradient(palette.aurora)
                    else if (item.kind == ItemKind.TRANSPORT)
                        Brush.linearGradient(listOf(Color(0xFF38EF7D), Color(0xFF11998E)))
                    else
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                )
                .border(
                    width = if (highlight) 2.dp else 1.dp,
                    color = if (highlight) Color.White else palette.aurora[0].copy(alpha = 0.45f),
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.kind.emoji,
                fontSize = if (highlight) 12.sp else 10.5.sp
            )
        }
    }
}

/**
 * Floating preview capsule displayed when hovering or scrubbing over a ruler node.
 */
@Composable
fun FloatingRulerPreview(
    item: ItineraryItem,
    tripCurrency: String,
    modifier: Modifier = Modifier
) {
    val palette = WonderColors.current
    val timeLabel = item.startTime?.let(TIME_FORMAT::format) ?: "Flexible"
    val window = TimeWindow.forTime(item.startTime)

    Row(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = palette.aurora[0].copy(alpha = 0.35f))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.5.dp, Brush.horizontalGradient(palette.aurora), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(palette.aurora)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = item.kind.emoji, fontSize = 16.sp)
        }
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = " ·  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.aurora[0],
                    fontWeight = FontWeight.Bold
                )
                if (item.estimatedCost > 0) {
                    Text(
                        text = "·  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Horizontal strip of navigable Time Window chips.
 */
@Composable
fun TimeWindowNavigationStrip(
    activeWindow: TimeWindow?,
    onSelectWindow: (TimeWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = WonderColors.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeWindow.entries.forEach { window ->
            val isSelected = activeWindow == window
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected)
                            Brush.horizontalGradient(palette.aurora)
                        else
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                )
                            )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else palette.hairline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectWindow(window) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = window.emoji, fontSize = 12.sp)
                    Text(
                        text = window.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
