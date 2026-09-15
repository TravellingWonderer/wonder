package com.wonder.provider.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonder.provider.AppContainer
import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.CategorySpend
import com.wonder.provider.model.Expense
import com.wonder.provider.model.ExpenseCategory
import com.wonder.provider.ui.conversation.BudgetBar
import com.wonder.provider.ui.conversation.ExpenseRow
import com.wonder.provider.ui.plan.Pill
import com.wonder.provider.ui.theme.WonderColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_HEADER = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

/**
 * The extra dashboard Wandering mode earns: every euro against the budget, planned spend versus
 * what the trip actually cost, and the extras that crept in along the way.
 */
@Composable
fun ExpensesScreen(onBack: () -> Unit) {
    val trips = AppContainer.trips
    val trip by trips.trip.collectAsStateWithLifecycle()
    val expenses by trips.expenses.collectAsStateWithLifecycle()
    val items by trips.items.collectAsStateWithLifecycle()
    val summary = remember(expenses, items, trip) { trips.budget() }
    val palette = WonderColors.current

    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Expense?>(null) }
    val payerNames = trip.travellers.associate { it.id to it.name }
    val grouped = expenses.groupBy { it.date }.toSortedMap(compareByDescending { it })

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.statusBarsPadding().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            text = "Money",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${trip.travellers.size} travelling · ${trip.currency} on this trip",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "headline") { HeadlineBlock(summary) }

            item(key = "variance") { VarianceBlock(summary) }

            if (summary.byCategory.isNotEmpty()) {
                item(key = "categories") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel("Where it's going")
                        summary.byCategory.forEach { entry ->
                            CategoryRow(entry, summary.currency, summary.byCategory.maxOf {
                                maxOf(it.spent, it.planned)
                            })
                        }
                    }
                }
            }

            item(key = "log-label") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Every expense", modifier = Modifier.weight(1f))
                    Text(
                        text = "${expenses.size} logged",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            grouped.forEach { (date, dayExpenses) ->
                item(key = "day-$date") {
                    Text(
                        text = "${date.format(DAY_HEADER)} · ${
                            TripRepository.format(dayExpenses.sumOf { it.amount }, trip.currency)
                        }",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.aurora[1]
                    )
                }
                items(dayExpenses, key = { it.id }) { expense ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { removing = expense }
                    ) {
                        ExpenseRow(expense, trip.currency, payerNames[expense.paidById].orEmpty())
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(palette.aurora.take(2)))
                .clickable { adding = true }
                .padding(horizontal = 22.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
            Text(
                text = "Log an expense",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }

    removing?.let { expense ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(expense.label) },
            text = {
                Text(
                    text = if (expense.isUnplanned) {
                        "Take this ${TripRepository.format(expense.amount, trip.currency)} back out of the trip?"
                    } else {
                        "This is the real cost of something in your plan. Removing it puts that back to an estimate."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    trips.removeExpense(expense.id)
                    removing = null
                }) { Text("Remove", color = palette.negative) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Keep") }
            }
        )
    }

    if (adding) {
        AddExpenseSheet(
            currency = trip.currency,
            travellers = trip.travellers.map { it.id to it.name },
            onAdd = { expense ->
                trips.addExpense(expense)
                adding = false
            },
            onDismiss = { adding = false }
        )
    }
}

@Composable
private fun HeadlineBlock(summary: com.wonder.provider.model.BudgetSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = TripRepository.format(summary.spent, summary.currency),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "of ${TripRepository.format(summary.budget, summary.currency)}",
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "≈ ${TripRepository.format(summary.inHomeCurrency, summary.homeCurrency)} back home",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BudgetBar(summary)
    }
}

/** Overrun, saved, or underrun — the one number that tells the group whether to relax. */
@Composable
private fun VarianceBlock(summary: com.wonder.provider.model.BudgetSummary) {
    val palette = WonderColors.current
    val tone = if (summary.isOverrun) palette.negative else palette.positive

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tone.copy(alpha = 0.08f))
            .border(1.dp, tone.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (summary.isOverrun) "HEADING OVER BUDGET" else "ON TRACK",
            style = MaterialTheme.typography.labelSmall,
            color = tone
        )
        Text(
            text = if (summary.isOverrun) {
                "${TripRepository.format(-summary.variance, summary.currency)} over"
            } else {
                "${TripRepository.format(summary.variance, summary.currency)} to spare"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MoneyLine("Paid so far", summary.spent, summary.currency)
            MoneyLine("Still to pay on the plan", summary.stillToPay, summary.currency)
            MoneyLine(
                "Projected total",
                summary.projected,
                summary.currency,
                emphasise = true
            )
            MoneyLine(
                "Unplanned extras",
                summary.unplannedSpent,
                summary.currency,
                tint = palette.warning
            )
        }
    }
}

@Composable
private fun MoneyLine(
    label: String,
    amount: Double,
    currency: String,
    emphasise: Boolean = false,
    tint: Color? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = TripRepository.format(amount, currency),
            style = if (emphasise) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = tint ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CategoryRow(entry: CategorySpend, currency: String, largest: Double) {
    val palette = WonderColors.current
    val spentFraction = if (largest <= 0) 0f else (entry.spent / largest).toFloat()
    val plannedFraction = if (largest <= 0) 0f else (entry.planned / largest).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = entry.category.emoji, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = entry.category.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = TripRepository.format(entry.spent, currency),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (entry.planned > entry.spent) {
                    "of ${TripRepository.format(entry.planned, currency)}"
                } else if (entry.spent > entry.planned) {
                    "+${TripRepository.format(entry.spent - entry.planned, currency)}"
                } else {
                    "as planned"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.spent > entry.planned) palette.warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(plannedFraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(palette.aurora[1].copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(spentFraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(palette.aurora.take(2)))
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.ENGLISH),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseSheet(
    currency: String,
    travellers: List<Pair<String, String>>,
    onAdd: (Expense) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = WonderColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val trips = AppContainer.trips

    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var payer by remember { mutableStateOf(travellers.firstOrNull()?.first.orEmpty()) }
    var note by remember { mutableStateOf("") }

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
                .padding(top = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Something extra",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What was it") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount ($currency)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpenseCategory.entries.forEach { option ->
                    Pill(
                        label = "${option.emoji} ${option.label}",
                        selected = category == option,
                        onClick = { category = option }
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                travellers.forEach { (id, name) ->
                    Pill(
                        label = name,
                        selected = payer == id,
                        onClick = { payer = id }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Button(
                onClick = {
                    val value = amount.toDoubleOrNull() ?: return@Button
                    onAdd(
                        Expense(
                            id = trips.newExpenseId(),
                            label = label.trim().ifBlank { category.label },
                            amount = value,
                            category = category,
                            date = LocalDate.now(),
                            paidById = payer,
                            itemId = null,
                            note = note.trim()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.aurora[0],
                    contentColor = Color.White
                )
            ) {
                Text(text = "Log it", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
