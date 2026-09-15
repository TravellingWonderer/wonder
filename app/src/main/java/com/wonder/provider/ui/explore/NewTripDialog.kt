package com.wonder.provider.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.NewTripBlueprint
import com.wonder.provider.model.TripBlueprintComposer
import com.wonder.provider.model.TripWhenMode
import com.wonder.provider.model.TripWhenPlan
import com.wonder.provider.ui.components.MovingGlowBorderBox
import com.wonder.provider.ui.theme.WonderColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CHIP_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTripDialog(
    creating: Boolean,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (NewTripBlueprint) -> Unit
) {
    val palette = WonderColors.current
    val defaultWhen = remember { TripWhenPlan.default() }

    var title by remember { mutableStateOf("") }
    var whenPlan by remember { mutableStateOf(defaultWhen) }
    var vibes by remember { mutableStateOf("") }
    var autoGenerateVibes by remember { mutableStateOf(true) }
    var showRangeCalendar by remember { mutableStateOf(false) }
    var showAnchorCalendar by remember { mutableStateOf(false) }

    val titleReadyForVibes = TripRepository.isValidTripTitle(title)
    val shouldComposeVibes = autoGenerateVibes && titleReadyForVibes

    LaunchedEffect(title, whenPlan, autoGenerateVibes) {
        if (shouldComposeVibes) {
            vibes = TripBlueprintComposer.composeVibes(title, whenPlan)
        } else if (autoGenerateVibes && !titleReadyForVibes) {
            vibes = ""
        }
    }

    val blueprint = remember(title, whenPlan, vibes, autoGenerateVibes) {
        NewTripBlueprint(
            title = title.trim(),
            whenPlan = whenPlan,
            vibes = vibes.trim(),
            autoGenerateVibes = autoGenerateVibes
        )
    }

    val valid = TripRepository.isValidTripTitle(title)

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

    Dialog(
        onDismissRequest = { if (dismissible && !creating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    enabled = dismissible && !creating,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .shadow(28.dp, RoundedCornerShape(30.dp), ambientColor = palette.aurora[0].copy(0.25f))
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(palette.aurora))
                    .padding(1.5.dp)
                    .clickable(enabled = false, onClick = {})
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            palette.aurora[0].copy(alpha = 0.16f),
                                            palette.aurora[1].copy(alpha = 0.08f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .padding(horizontal = 22.dp, vertical = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(palette.aurora)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FlightTakeoff,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "New trip",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Name it, pick when, describe the vibe",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (dismissible) {
                                    IconButton(onClick = onDismiss, enabled = !creating) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Close",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 22.dp),
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
                                            onClick = {
                                                whenPlan = whenPlan.copy(mode = mode)
                                            }
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

                            if (shouldComposeVibes) {
                                MovingGlowBorderBox(
                                    active = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 22.dp,
                                    colors = palette.aurora
                                ) {
                                    PopupTextField(
                                        value = vibes,
                                        onValueChange = { updated ->
                                            if (autoGenerateVibes) autoGenerateVibes = false
                                            vibes = updated
                                        },
                                        label = "Vibes",
                                        placeholder = "Relaxed food crawl, hidden gems, golden-hour views…",
                                        singleLine = false,
                                        minLines = 3,
                                        enabled = !creating,
                                        elevated = false
                                    )
                                }
                            } else {
                                PopupTextField(
                                    value = vibes,
                                    onValueChange = { updated ->
                                        if (autoGenerateVibes) autoGenerateVibes = false
                                        vibes = updated
                                    },
                                    label = "Vibes",
                                    placeholder = if (titleReadyForVibes) {
                                        "Relaxed food crawl, hidden gems, golden-hour views…"
                                    } else {
                                        "Name your trip first — we'll draft the vibe for you"
                                    },
                                    singleLine = false,
                                    minLines = 3,
                                    enabled = !creating
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = palette.aurora[0].copy(0.3f))
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Brush.linearGradient(palette.aurora))
                                    .clickable(enabled = valid && !creating) { onCreate(blueprint) }
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
                                        text = "Create trip",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
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
