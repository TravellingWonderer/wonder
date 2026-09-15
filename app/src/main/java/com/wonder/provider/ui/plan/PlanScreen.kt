package com.wonder.provider.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonder.provider.AppContainer
import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.ui.conversation.BudgetBar
import com.wonder.provider.ui.conversation.duration
import com.wonder.provider.ui.conversation.statusColour
import com.wonder.provider.ui.theme.WonderColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

/**
 * The full plan: one day at a time, everything editable. Reached by asking Wonder or by tapping
 * anything it shows you, so the conversation stays the front door.
 */
@Composable
fun PlanScreen(
    initialDate: LocalDate?,
    initialItemId: String?,
    onBack: () -> Unit,
    onPlanChanged: () -> Unit
) {
    val trips = AppContainer.trips
    val trip by trips.trip.collectAsStateWithLifecycle()
    val items by trips.items.collectAsStateWithLifecycle()
    val expenses by trips.expenses.collectAsStateWithLifecycle()

    var selectedDate by remember {
        mutableStateOf(
            initialDate
                ?: initialItemId?.let { id -> items.firstOrNull { it.id == id }?.date }
                ?: LocalDate.now().takeIf { trip.covers(it) }
                ?: trip.startDate
        )
    }
    var editing by remember { mutableStateOf<ItineraryItem?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }

    LaunchedEffect(initialItemId) {
        initialItemId?.let { id ->
            trips.item(id)?.let {
                selectedDate = it.date
                editing = it
                editingIsNew = false
            }
        }
    }

    val dayItems = items.filter { it.date == selectedDate }
    val budget = remember(items, expenses, trip) { trips.budget() }
    val palette = WonderColors.current
    val dayListState = rememberLazyListState()

    LaunchedEffect(selectedDate) {
        val index = trip.dates.indexOf(selectedDate)
        if (index >= 0) dayListState.animateScrollToItem(index)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Wonder",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(8.dp)
                        .size(21.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = trip.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${trip.destination.substringBefore(",")} · ${trip.dayCount} days · ${
                            trip.travellers.size
                        } travelling",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (budget.isOverrun) {
                        "Plan projects ${TripRepository.format(budget.projected, trip.currency)} — ${
                            TripRepository.format(-budget.variance, trip.currency)
                        } over budget"
                    } else {
                        "Plan projects ${TripRepository.format(budget.projected, trip.currency)} of ${
                            TripRepository.format(budget.budget, trip.currency)
                        }"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (budget.isOverrun) palette.negative else MaterialTheme.colorScheme.onSurfaceVariant
                )
                BudgetBar(budget)
            }

            LazyRow(
                state = dayListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(trip.dates, key = { it.toString() }) { date ->
                    DayPill(
                        date = date,
                        dayNumber = trip.dayNumber(date),
                        count = items.count { it.date == date && it.kind != ItemKind.FREE },
                        selected = date == selectedDate,
                        isToday = date == LocalDate.now(),
                        onClick = { selectedDate = date }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "heading") {
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(
                        text = selectedDate.format(LONG_DAY),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val dayCost = dayItems.sumOf { it.expectedTotal(trip.partySize) }
                    Text(
                        text = if (dayCost > 0) {
                            "${dayItems.size} planned · ${TripRepository.format(dayCost, trip.currency)}"
                        } else {
                            "${dayItems.size} planned"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(dayItems, key = { it.id }) { item ->
                PlanRow(
                    item = item,
                    currency = trip.currency,
                    travellerCount = trip.partySize,
                    onClick = {
                        editing = item
                        editingIsNew = false
                    }
                )
            }

            item(key = "add") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                        .clickable {
                            editing = ItineraryItem(
                                id = trips.newItemId(),
                                date = selectedDate,
                                title = "",
                                kind = ItemKind.ACTIVITY,
                                travellerIds = trip.travellers.map { it.id }.toSet()
                            )
                            editingIsNew = true
                        }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = palette.aurora[0]
                    )
                    Text(
                        text = "Add something to this day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    editing?.let { item ->
        ItemEditorSheet(
            item = item,
            travellers = trip.travellers,
            currency = trip.currency,
            isNew = editingIsNew,
            onSave = { updated, paidById ->
                trips.saveEdited(updated, paidById)
                editing = null
                onPlanChanged()
            },
            onDelete = { id ->
                trips.removeItem(id)
                editing = null
                onPlanChanged()
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun DayPill(
    date: LocalDate,
    dayNumber: Int,
    count: Int,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                when {
                    selected -> Modifier.background(Brush.verticalGradient(palette.aurora.take(2)))
                    isToday -> Modifier.background(palette.aurora[0].copy(alpha = 0.14f))
                    else -> Modifier.border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = date.format(WEEKDAY),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) {
                androidx.compose.ui.graphics.Color.White
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        count == 0 -> androidx.compose.ui.graphics.Color.Transparent
                        selected -> androidx.compose.ui.graphics.Color.White
                        else -> palette.aurora[1]
                    }
                )
        )
    }
}

@Composable
private fun PlanRow(
    item: ItineraryItem,
    currency: String,
    travellerCount: Int,
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.width(60.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = item.startTime?.format(CLOCK) ?: "Anytime",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (item.durationMinutes > 0) {
                Text(
                    text = duration(item.durationMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColour(item.status))
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(1.dp)
                    .height(28.dp)
                    .background(palette.hairline)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "${item.kind.emoji}  ${item.title}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.status == ItemStatus.DONE) TextDecoration.LineThrough else null
            )
            val meta = listOfNotNull(
                item.location.ifBlank { null },
                item.status.label,
                item.bookingRef.ifBlank { null },
                "${item.partySize} of ${travellerCount}".takeIf { item.partySize < travellerCount }
            ).joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 2
                )
            }
        }

        val total = item.expectedTotal(travellerCount)
        if (total > 0) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = TripRepository.format(total, currency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (item.paidAmount != null) "paid" else "est.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.paidAmount != null) palette.positive else palette.warning
                )
            }
        }
    }
}
