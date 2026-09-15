package com.wonder.provider.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonder.provider.AppContainer
import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.NewTripBlueprint
import com.wonder.provider.model.TripWhenMode
import com.wonder.provider.model.TripWhenPlan
import com.wonder.provider.ui.components.MovingGlowBorderBox
import com.wonder.provider.ui.conversation.AmbientBackdrop
import com.wonder.provider.ui.theme.WonderColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val CHIP_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTripScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val palette = WonderColors.current
    val trips = AppContainer.trips
    val scope = rememberCoroutineScope()
    val defaultWhen = remember { TripWhenPlan.default() }

    var title by remember { mutableStateOf("") }
    var whenPlan by remember { mutableStateOf(defaultWhen) }
    var vibes by remember { mutableStateOf("") }
    var generatingVibes by remember { mutableStateOf(false) }
    var vibeDraftToken by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var showRangeCalendar by remember { mutableStateOf(false) }
    var showAnchorCalendar by remember { mutableStateOf(false) }

    val titleReady = TripRepository.isValidTripTitle(title)

    LaunchedEffect(vibeDraftToken) {
        if (vibeDraftToken == 0) return@LaunchedEffect
        if (!TripRepository.isValidTripTitle(title)) {
            generatingVibes = false
            return@LaunchedEffect
        }
        generatingVibes = true
        val drafted = runCatching {
            AppContainer.tripVibeGenerator.generate(title.trim(), whenPlan)
        }.getOrDefault("")
        vibes = drafted
        generatingVibes = false
    }

    val blueprint = remember(title, whenPlan, vibes) {
        NewTripBlueprint(
            title = title.trim(),
            whenPlan = whenPlan,
            vibes = vibes.trim(),
            autoGenerateVibes = false,
            blankStart = false
        )
    }

    val valid = titleReady

    fun createTrip(blankStart: Boolean) {
        if (creating || !valid) return
        val plan = if (blankStart) {
            blueprint.copy(vibes = "", blankStart = true)
        } else {
            blueprint.copy(blankStart = false)
        }
        creating = true
        scope.launch {
            try {
                val (start, end) = plan.dateRange
                trips.createBlankTrip(
                    title = plan.title,
                    destination = TripRepository.DEFAULT_DESTINATION,
                    startDate = start,
                    endDate = end,
                    vibes = plan.effectiveVibes,
                    blankStart = plan.blankStart || plan.effectiveVibes.isBlank(),
                    datesConfirmed = plan.datesConfirmed
                )
                onCreated()
            } finally {
                creating = false
            }
        }
    }

    if (showRangeCalendar) {
        TripDateRangePickerDialog(
            initialStart = whenPlan.anchorDate,
            initialEnd = whenPlan.endDate ?: whenPlan.anchorDate,
            onDismiss = { showRangeCalendar = false },
            onConfirm = { start, end ->
                whenPlan = whenPlan.copy(
                    anchorDate = start,
                    endDate = if (end.isBefore(start)) start else end
                )
                showRangeCalendar = false
            }
        )
    }

    if (showAnchorCalendar) {
        TripAnchorDatePickerDialog(
            initialDate = whenPlan.anchorDate,
            onDismiss = { showAnchorCalendar = false },
            onConfirm = { date ->
                whenPlan = whenPlan.copy(anchorDate = date)
                showAnchorCalendar = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AmbientBackdrop(modifier = Modifier.fillMaxSize(), alive = generatingVibes || creating)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !creating, onClick = onBack)
                        .padding(12.dp)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "New trip",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Name it, pick when — vibes are optional",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(palette.aurora)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FlightTakeoff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                PopupTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Trip name",
                    placeholder = "Summer in Sicily",
                    singleLine = true,
                    enabled = !creating
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("When")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripWhenMode.entries.forEach { mode ->
                            ModeChip(
                                label = mode.label,
                                selected = whenPlan.mode == mode,
                                enabled = !creating,
                                onClick = { whenPlan = whenPlan.copy(mode = mode) }
                            )
                        }
                    }

                    WhenPlanCard(
                        whenPlan = whenPlan,
                        enabled = !creating,
                        onOpenRangeCalendar = { showRangeCalendar = true },
                        onOpenAnchorCalendar = { showAnchorCalendar = true },
                        onFlexDaysChange = { whenPlan = whenPlan.copy(flexDays = it) },
                        onDurationChange = { whenPlan = whenPlan.copy(durationDays = it) },
                        onWeekendCountChange = { whenPlan = whenPlan.copy(weekendCount = it) }
                    )
                }

                if (generatingVibes || vibes.isNotBlank()) {
                    MovingGlowBorderBox(
                        active = generatingVibes || vibes.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        colors = palette.aurora
                    ) {
                        OptionalVibesField(
                            value = vibes,
                            onValueChange = { vibes = it },
                            generating = generatingVibes,
                            titleReady = titleReady,
                            enabled = !creating && !generatingVibes,
                            onDraft = { vibeDraftToken++ },
                            elevated = false
                        )
                    }
                } else {
                    OptionalVibesField(
                        value = vibes,
                        onValueChange = { vibes = it },
                        generating = generatingVibes,
                        titleReady = titleReady,
                        enabled = !creating && !generatingVibes,
                        onDraft = { vibeDraftToken++ },
                        elevated = true
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = palette.aurora[0].copy(0.3f))
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(palette.aurora))
                            .clickable(enabled = valid && !creating && !generatingVibes) {
                                createTrip(blankStart = false)
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (creating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = if (vibes.isBlank()) "Create trip" else "Create with vibes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    TextButton(
                        onClick = { createTrip(blankStart = true) },
                        enabled = valid && !creating && !generatingVibes
                    ) {
                        Text(
                            text = "Create blank trip",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Just the name and dates — no plans or suggestions yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenPlanCard(
    whenPlan: TripWhenPlan,
    enabled: Boolean,
    onOpenRangeCalendar: () -> Unit,
    onOpenAnchorCalendar: () -> Unit,
    onFlexDaysChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    onWeekendCountChange: (Int) -> Unit
) {
    val palette = WonderColors.current

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = palette.cardTint,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairline.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (whenPlan.mode) {
                TripWhenMode.EXACT_DATES -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .clickable(enabled = enabled, onClick = onOpenRangeCalendar)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DateBadge("From", whenPlan.anchorDate, palette.aurora[0])
                        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DateBadge(
                            "To",
                            whenPlan.endDate ?: whenPlan.anchorDate,
                            palette.aurora[1],
                            alignEnd = true
                        )
                    }
                    Text(
                        text = "Tap dates to open calendar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                TripWhenMode.CLOSE_TO_DATE -> {
                    AnchorDateRow(whenPlan.anchorDate, enabled, onOpenAnchorCalendar)
                    OptionLabel("Flexibility")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 7, 14).forEach { days ->
                            OptionChip(
                                label = "±$days days",
                                selected = whenPlan.flexDays == days,
                                enabled = enabled,
                                onClick = { onFlexDaysChange(days) }
                            )
                        }
                    }
                }
                TripWhenMode.AFTER_DATE -> {
                    AnchorDateRow(whenPlan.anchorDate, enabled, onOpenAnchorCalendar)
                    OptionLabel("Trip length")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 7, 10).forEach { days ->
                            OptionChip(
                                label = "$days days",
                                selected = whenPlan.durationDays == days,
                                enabled = enabled,
                                onClick = { onDurationChange(days) }
                            )
                        }
                    }
                }
                TripWhenMode.WEEKEND_ONLY -> {
                    AnchorDateRow(whenPlan.anchorDate, enabled, onOpenAnchorCalendar, hint = "From")
                    OptionLabel("Weekends")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { count ->
                            OptionChip(
                                label = if (count == 1) "1 weekend" else "$count weekends",
                                selected = whenPlan.weekendCount == count,
                                enabled = enabled,
                                onClick = { onWeekendCountChange(count) }
                            )
                        }
                    }
                    val (start, end) = whenPlan.resolvedRange
                    Text(
                        text = "${start.format(CHIP_DATE)} – ${end.format(CHIP_DATE)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.aurora[0]
                    )
                }
                TripWhenMode.FLEXIBLE_CHEAP -> {
                    Text(
                        text = "Wonder will hunt the cheapest travel days — no fixed calendar yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OptionLabel("Trip length")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 7, 10).forEach { days ->
                            OptionChip(
                                label = "$days days",
                                selected = whenPlan.durationDays == days,
                                enabled = enabled,
                                onClick = { onDurationChange(days) }
                            )
                        }
                    }
                }
            }

            Text(
                text = whenPlan.summaryLine(),
                style = MaterialTheme.typography.labelMedium,
                color = palette.aurora[0],
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnchorDateRow(
    date: LocalDate,
    enabled: Boolean,
    onClick: () -> Unit,
    hint: String = "Around"
) {
    val palette = WonderColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = hint.uppercase(Locale.ENGLISH),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = date.format(CHIP_DATE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.aurora[0]
            )
        }
        Icon(
            imageVector = Icons.Rounded.CalendarMonth,
            contentDescription = "Pick date",
            tint = palette.aurora[0],
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Brush.linearGradient(palette.aurora))
                } else {
                    Modifier
                        .background(palette.cardTint)
                        .border(1.dp, palette.hairline, shape)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(palette.aurora[0].copy(alpha = 0.14f))
                        .border(1.dp, palette.aurora[0], shape)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                        .border(1.dp, palette.hairline, shape)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.aurora[0] else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.ENGLISH),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun DateBadge(
    label: String,
    date: LocalDate,
    accent: Color,
    alignEnd: Boolean = false
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = label.uppercase(Locale.ENGLISH),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = date.format(CHIP_DATE),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }
}

@Composable
private fun OptionalVibesField(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    titleReady: Boolean,
    enabled: Boolean,
    onDraft: () -> Unit,
    elevated: Boolean
) {
    val palette = WonderColors.current
    val shape = RoundedCornerShape(22.dp)

    Surface(
        shape = shape,
        color = palette.cardTint,
        shadowElevation = if (elevated) 10.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (elevated) {
                    Modifier.border(1.dp, palette.hairline.copy(alpha = 0.5f), shape)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (generating) "Vibes · drafting…" else "Vibes · optional",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.aurora[0],
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = onDraft,
                    enabled = enabled && titleReady && !generating,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (value.isBlank()) "Draft for me" else "Redraft",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled && titleReady) palette.aurora[0]
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(palette.aurora[0]),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = when {
                                    generating -> "Reading the meaning in your trip name…"
                                    titleReady -> "Optional — add a brief, or tap Draft for me"
                                    else -> "Name your trip first if you want a drafted vibe"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

@Composable
private fun PopupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int = 1,
    enabled: Boolean,
    elevated: Boolean = true
) {
    val palette = WonderColors.current
    val shape = RoundedCornerShape(22.dp)

    Surface(
        shape = shape,
        color = palette.cardTint,
        shadowElevation = if (elevated) 10.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (elevated) {
                    Modifier.border(1.dp, palette.hairline.copy(alpha = 0.5f), shape)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.aurora[0],
                fontWeight = FontWeight.SemiBold
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = if (singleLine) 1 else 6,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(palette.aurora[0]),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDateRangePickerDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val palette = WonderColors.current
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart.toUtcMillis(),
        initialSelectedEndDateMillis = initialEnd.toUtcMillis(),
        yearRange = LocalDate.now().year..LocalDate.now().year + 3
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMillis = state.selectedStartDateMillis ?: return@TextButton
                    val endMillis = state.selectedEndDateMillis ?: return@TextButton
                    onConfirm(startMillis.toLocalDate(), endMillis.toLocalDate())
                }
            ) {
                Text("Done", color = palette.aurora[0])
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            showModeToggle = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripAnchorDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val palette = WonderColors.current
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcMillis(),
        yearRange = LocalDate.now().year..LocalDate.now().year + 3
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onConfirm(millis.toLocalDate())
                }
            ) {
                Text("Done", color = palette.aurora[0])
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
