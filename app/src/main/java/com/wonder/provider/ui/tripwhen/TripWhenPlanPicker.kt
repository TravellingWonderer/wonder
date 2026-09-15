package com.wonder.provider.ui.tripwhen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wonder.provider.model.TripWhenMode
import com.wonder.provider.model.TripWhenPlan
import com.wonder.provider.ui.theme.WonderColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CHIP_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Same date-range choices as New Trip — used inline in conversation when dates aren't locked. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripWhenPlanPicker(
    whenPlan: TripWhenPlan,
    onWhenPlanChange: (TripWhenPlan) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = WonderColors.current
    var showRangeCalendar by remember { mutableStateOf(false) }
    var showAnchorCalendar by remember { mutableStateOf(false) }

    if (showRangeCalendar) {
        TripDateRangePickerDialog(
            initialStart = whenPlan.anchorDate,
            initialEnd = whenPlan.endDate ?: whenPlan.anchorDate,
            onDismiss = { showRangeCalendar = false },
            onConfirm = { start, end ->
                onWhenPlanChange(
                    whenPlan.copy(
                        anchorDate = start,
                        endDate = if (end.isBefore(start)) start else end
                    )
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
                onWhenPlanChange(whenPlan.copy(anchorDate = date))
                showAnchorCalendar = false
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                    enabled = enabled,
                    onClick = { onWhenPlanChange(whenPlan.copy(mode = mode)) }
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = palette.cardTint,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.hairline.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (whenPlan.mode) {
                    TripWhenMode.EXACT_DATES -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .clickable(enabled = enabled) { showRangeCalendar = true }
                                .padding(12.dp),
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
                    }
                    TripWhenMode.CLOSE_TO_DATE -> {
                        AnchorDateRow(whenPlan.anchorDate, enabled) { showAnchorCalendar = true }
                        OptionLabel("Flexibility")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 7, 14).forEach { days ->
                                OptionChip(
                                    label = "±$days days",
                                    selected = whenPlan.flexDays == days,
                                    enabled = enabled,
                                    onClick = { onWhenPlanChange(whenPlan.copy(flexDays = days)) }
                                )
                            }
                        }
                    }
                    TripWhenMode.AFTER_DATE -> {
                        AnchorDateRow(whenPlan.anchorDate, enabled) { showAnchorCalendar = true }
                        OptionLabel("Trip length")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 5, 7, 10).forEach { days ->
                                OptionChip(
                                    label = "$days days",
                                    selected = whenPlan.durationDays == days,
                                    enabled = enabled,
                                    onClick = { onWhenPlanChange(whenPlan.copy(durationDays = days)) }
                                )
                            }
                        }
                    }
                    TripWhenMode.WEEKEND_ONLY -> {
                        AnchorDateRow(whenPlan.anchorDate, enabled, hint = "From") {
                            showAnchorCalendar = true
                        }
                        OptionLabel("Weekends")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3).forEach { count ->
                                OptionChip(
                                    label = if (count == 1) "1 weekend" else "$count weekends",
                                    selected = whenPlan.weekendCount == count,
                                    enabled = enabled,
                                    onClick = { onWhenPlanChange(whenPlan.copy(weekendCount = count)) }
                                )
                            }
                        }
                    }
                    TripWhenMode.FLEXIBLE_CHEAP -> {
                        Text(
                            text = "Still hunting cheap days? Pick any other option to lock an approximate range.",
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
                                    onClick = { onWhenPlanChange(whenPlan.copy(durationDays = days)) }
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
}

@Composable
private fun AnchorDateRow(
    date: LocalDate,
    enabled: Boolean,
    hint: String = "Around",
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            modifier = Modifier.size(20.dp)
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
            .padding(bottom = 4.dp)
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
