package com.wonder.provider.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.Traveller
import com.wonder.provider.ui.theme.WonderColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DURATION_PRESETS = listOf(30, 45, 60, 90, 120, 180, 300, 480)

/**
 * Every field of an itinerary item, in one sheet. This is the counterweight to the minimal
 * conversation — when a traveller wants to fuss over the details, everything is here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorSheet(
    item: ItineraryItem,
    travellers: List<Traveller>,
    currency: String,
    isNew: Boolean,
    onSave: (ItineraryItem, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = WonderColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(item.title) }
    var kind by remember { mutableStateOf(item.kind) }
    var startTime by remember { mutableStateOf(item.startTime) }
    var durationMinutes by remember { mutableStateOf(item.durationMinutes) }
    var location by remember { mutableStateOf(item.location) }
    var notes by remember { mutableStateOf(item.notes) }
    var costText by remember { mutableStateOf(if (item.estimatedCost > 0) trim(item.estimatedCost) else "") }
    var perPerson by remember { mutableStateOf(item.costIsPerPerson) }
    var status by remember { mutableStateOf(item.status) }
    var paidText by remember { mutableStateOf(item.paidAmount?.let(::trim) ?: "") }
    var reference by remember { mutableStateOf(item.bookingRef) }
    var attending by remember {
        mutableStateOf(item.travellerIds.ifEmpty { travellers.map { it.id }.toSet() })
    }
    var payer by remember { mutableStateOf(travellers.firstOrNull()?.id.orEmpty()) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isNew) "Something new" else "Edit",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isNew) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove from the plan",
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onDelete(item.id) }
                            .padding(8.dp)
                            .size(20.dp),
                        tint = palette.negative
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What is it") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            EditorSection("Kind") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ItemKind.entries.forEach { option ->
                        Pill(
                            label = "${option.emoji} ${option.label}",
                            selected = kind == option,
                            onClick = { kind = option }
                        )
                    }
                }
            }

            EditorSection("When") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Pill(
                        label = startTime?.format(CLOCK) ?: "Anytime",
                        selected = startTime != null,
                        onClick = { showTimePicker = true }
                    )
                    if (startTime != null) {
                        Pill(
                            label = "Clear",
                            selected = false,
                            onClick = { startTime = null }
                        )
                    }
                }
                // The item's own length earns a pill of its own, so an odd duration set
                // elsewhere survives a trip through this sheet.
                val lengths = remember(item.durationMinutes) {
                    (DURATION_PRESETS + item.durationMinutes).filter { it > 0 }.distinct().sorted()
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lengths.forEach { minutes ->
                        Pill(
                            label = durationLabel(minutes),
                            selected = durationMinutes == minutes,
                            onClick = { durationMinutes = minutes }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Where") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            EditorSection("Cost") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it.filter { char -> char.isDigit() || char == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Estimate ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Each",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = perPerson,
                            onCheckedChange = { perPerson = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.aurora[0])
                        )
                    }
                }
                val estimate = costText.toDoubleOrNull() ?: 0.0
                if (estimate > 0) {
                    Text(
                        text = if (perPerson) {
                            "${TripRepository.format(estimate * attending.size.coerceAtLeast(1), currency)} for ${attending.size} of you"
                        } else {
                            "${TripRepository.format(estimate, currency)} total"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            EditorSection("How settled") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ItemStatus.entries.forEach { option ->
                        Pill(
                            label = option.label,
                            selected = status == option,
                            onClick = { status = option }
                        )
                    }
                }
            }

            // Booking turns an estimate into a real number, and that number goes straight into
            // the expense tracker.
            if (status == ItemStatus.BOOKED || status == ItemStatus.DONE) {
                EditorSection("Booked") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = paidText,
                            onValueChange = { paidText = it.filter { char -> char.isDigit() || char == '.' } },
                            modifier = Modifier.weight(1f),
                            label = { Text("Actually paid") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { reference = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Reference") },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        travellers.forEach { traveller ->
                            Pill(
                                label = "${traveller.name} paid",
                                selected = payer == traveller.id,
                                onClick = { payer = traveller.id }
                            )
                        }
                    }
                }
            }

            EditorSection("Who's coming") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    travellers.forEach { traveller ->
                        Pill(
                            label = "${traveller.emoji} ${traveller.name}",
                            selected = traveller.id in attending,
                            onClick = {
                                attending = if (traveller.id in attending) {
                                    attending - traveller.id
                                } else {
                                    attending + traveller.id
                                }
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().height(110.dp),
                label = { Text("Notes") },
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = {
                    onSave(
                        item.copy(
                            title = title.trim().ifBlank { "Untitled" },
                            kind = kind,
                            startTime = startTime,
                            durationMinutes = durationMinutes,
                            location = location.trim(),
                            notes = notes.trim(),
                            estimatedCost = costText.toDoubleOrNull() ?: 0.0,
                            costIsPerPerson = perPerson,
                            status = status,
                            paidAmount = if (status == ItemStatus.BOOKED || status == ItemStatus.DONE) {
                                paidText.toDoubleOrNull()
                            } else {
                                null
                            },
                            bookingRef = reference.trim(),
                            travellerIds = attending
                        ),
                        payer
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.aurora[0],
                    contentColor = Color.White
                )
            ) {
                Text(text = if (isNew) "Add to the day" else "Save", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showTimePicker) {
        val initial = startTime ?: LocalTime.of(9, 0)
        val pickerState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startTime = LocalTime.of(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}

@Composable
private fun EditorSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label.uppercase(Locale.ENGLISH),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
internal fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = WonderColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) {
                    Modifier.background(Brush.horizontalGradient(palette.aurora.take(2)))
                } else {
                    Modifier.border(1.dp, palette.hairline, RoundedCornerShape(14.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

private fun trim(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
