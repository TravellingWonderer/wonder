package com.wonder.provider.ui.conversation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.AgentCard
import com.wonder.provider.model.BudgetSummary
import com.wonder.provider.model.DayOutline
import com.wonder.provider.model.DoorwayTarget
import com.wonder.provider.model.Expense
import com.wonder.provider.model.FlightOfferSummary
import com.wonder.provider.model.FlightSearchResult
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.Recommendation
import com.wonder.provider.model.TripWhenMode
import com.wonder.provider.model.TripWhenPlan
import com.wonder.provider.ui.theme.WonderColors
import com.wonder.provider.ui.tripwhen.TripWhenPlanPicker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything a card might need to do. Cards never navigate on their own. */
data class CardActions(
    val onOpenPlan: () -> Unit = {},
    val onOpenExpenses: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onEditItem: (String) -> Unit = {},
    val onAddRecommendation: (Recommendation, LocalDate) -> Unit = { _, _ -> },
    val onOpenAddToTrip: (Recommendation) -> Unit = {},
    val onAcceptDraft: (AgentCard.DraftDay) -> Unit = {},
    val onOpenDay: (java.time.LocalDate) -> Unit = {},
    val onAddFlightOffer: (com.wonder.provider.model.FlightOfferSummary, java.time.LocalDate) -> Unit = { _, _ -> },
    val onConfirmDates: (AgentCard.ConfirmDates, com.wonder.provider.model.TripWhenPlan) -> Unit = { _, _ -> }
)

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@Composable
fun AgentCardView(card: AgentCard, actions: CardActions, modifier: Modifier = Modifier) {
    when (card) {
        is AgentCard.TripOverview -> TripOverviewCard(card, actions, modifier)
        is AgentCard.DayPlan -> DayPlanCard(card, actions, modifier)
        is AgentCard.NowNext -> NowNextCard(card, actions, modifier)
        is AgentCard.Budget -> BudgetCard(card, actions, modifier)
        is AgentCard.ExpenseLog -> ExpenseLogCard(card, actions, modifier)
        is AgentCard.Nearby -> NearbyCard(card, actions, modifier)
        is AgentCard.DraftDay -> DraftDayCard(card, actions, modifier)
        is AgentCard.Loose -> LooseCard(card, actions, modifier)
        is AgentCard.Doorway -> DoorwayCard(card, actions, modifier)
        is AgentCard.FlightResults -> FlightResultsCard(card, actions, modifier)
        is AgentCard.ConfirmDates -> ConfirmDatesCard(card, actions, modifier)
    }
}

@Composable
private fun CardShell(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = WonderColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.cardTint)
            .border(1.dp, palette.hairline, RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp)
            .animateContentSize(spring(stiffness = 320f)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun CardHeading(label: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(Locale.ENGLISH),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// region trip

@Composable
private fun TripOverviewCard(
    card: AgentCard.TripOverview,
    actions: CardActions,
    modifier: Modifier
) {
    CardShell(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = card.coverEmoji, style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${card.destination.substringBefore(",")} · ${card.dateRange}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            card.days.forEach { day ->
                DayOutlineRow(day, card.currency) { actions.onOpenDay(day.date) }
            }
        }
    }
}

@Composable
private fun DayOutlineRow(day: DayOutline, currency: String, onClick: () -> Unit) {
    val palette = WonderColors.current
    val dim = day.isPast
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (day.isToday) {
                    Modifier.background(palette.aurora[0].copy(alpha = 0.10f))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.width(42.dp)) {
            Text(
                text = day.date.format(WEEKDAY),
                style = MaterialTheme.typography.labelMedium,
                color = if (day.isToday) palette.aurora[0] else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = if (dim) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Text(
            text = day.headline,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (day.itemCount == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else if (dim) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (dim) TextDecoration.LineThrough else null,
            maxLines = 1
        )
        if (day.cost > 0) {
            Text(
                text = TripRepository.format(day.cost, currency),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayPlanCard(card: AgentCard.DayPlan, actions: CardActions, modifier: Modifier) {
    CardShell(modifier) {
        CardHeading(
            label = card.heading.substringBefore(" · "),
            detail = card.heading.substringAfter(" · ", "")
                .ifBlank { null }
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            card.items.forEach { item ->
                ItemRow(
                    item = item,
                    currency = card.currency,
                    travellerCount = card.travellerCount,
                    onClick = { actions.onEditItem(item.id) }
                )
            }
        }
        if (card.cost > 0) {
            Text(
                text = "About ${TripRepository.format(card.cost, card.currency)} across the group",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ItemRow(
    item: ItineraryItem,
    currency: String,
    travellerCount: Int,
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.width(58.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColour(item.status))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${item.kind.emoji}  ${item.title}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.status == ItemStatus.DONE) TextDecoration.LineThrough else null
            )
            val meta = listOfNotNull(
                item.location.ifBlank { null },
                item.status.label.takeIf { item.status != ItemStatus.PLANNED },
                "only ${item.partySize} of you".takeIf { item.partySize in 1 until travellerCount }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                if (item.paidAmount == null) {
                    Text(
                        text = "est.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.warning
                    )
                }
            }
        }
    }
}

@Composable
private fun NowNextCard(card: AgentCard.NowNext, actions: CardActions, modifier: Modifier) {
    val palette = WonderColors.current
    CardShell(modifier) {
        if (card.current != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "HAPPENING NOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.positive
                )
                Text(
                    text = "${card.current.kind.emoji}  ${card.current.title}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val until = card.current.endTime
                Text(
                    text = listOfNotNull(
                        card.current.location.ifBlank { null },
                        until?.let { "until ${it.format(CLOCK)}" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (card.next != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (card.minutesUntilNext in 0..240) {
                        "NEXT · IN ${humanMinutes(card.minutesUntilNext).uppercase(Locale.ENGLISH)}"
                    } else {
                        "NEXT"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.aurora[1]
                )
                Text(
                    text = "${card.next.kind.emoji}  ${card.next.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(
                        card.next.startTime?.format(CLOCK),
                        card.next.location.ifBlank { null },
                        card.next.notes.ifBlank { null }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (card.current == null && card.next == null) {
            Text(
                text = "Nothing scheduled. Genuinely nothing — enjoy it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// endregion

// region money

@Composable
private fun BudgetCard(card: AgentCard.Budget, actions: CardActions, modifier: Modifier) {
    val palette = WonderColors.current
    val summary = card.summary

    CardShell(modifier, onClick = actions.onOpenExpenses) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardHeading("Budget")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = TripRepository.format(summary.spent, summary.currency),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "of ${TripRepository.format(summary.budget, summary.currency)}",
                    modifier = Modifier.padding(bottom = 5.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        BudgetBar(summary)

        Text(
            text = if (summary.isOverrun) {
                "Projected ${TripRepository.format(summary.projected, summary.currency)} — ${
                    TripRepository.format(-summary.variance, summary.currency)
                } over"
            } else {
                "Projected ${TripRepository.format(summary.projected, summary.currency)} — ${
                    TripRepository.format(summary.variance, summary.currency)
                } to spare"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (summary.isOverrun) palette.negative else palette.positive
        )

        if (card.detailed) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                summary.byCategory.forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = entry.category.emoji, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = entry.category.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = TripRepository.format(entry.spent, summary.currency),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (entry.planned > entry.spent) {
                            Text(
                                text = "+${TripRepository.format(entry.planned - entry.spent, summary.currency)} due",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Spend and projection on one track, so an overrun is visible before it happens. */
@Composable
fun BudgetBar(summary: BudgetSummary, modifier: Modifier = Modifier) {
    val palette = WonderColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(summary.projectedFraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(
                    if (summary.isOverrun) {
                        palette.negative.copy(alpha = 0.30f)
                    } else {
                        palette.aurora[1].copy(alpha = 0.30f)
                    }
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(summary.spentFraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        if (summary.isOverrun) {
                            listOf(palette.warning, palette.negative)
                        } else {
                            listOf(palette.aurora[0], palette.aurora[1])
                        }
                    )
                )
        )
    }
}

@Composable
private fun ExpenseLogCard(card: AgentCard.ExpenseLog, actions: CardActions, modifier: Modifier) {
    CardShell(modifier, onClick = actions.onOpenExpenses) {
        CardHeading(
            "Spending",
            "${TripRepository.format(card.summary.spent, card.summary.currency)} so far"
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            card.expenses.forEach { expense ->
                ExpenseRow(expense, card.summary.currency, card.payerNames[expense.paidById].orEmpty())
            }
        }
        Text(
            text = "Tap for the full tracker",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ExpenseRow(expense: Expense, currency: String, payerName: String) {
    val palette = WonderColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = expense.category.emoji, style = MaterialTheme.typography.bodyLarge)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = expense.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = listOfNotNull(
                    expense.date.format(DAY_MONTH),
                    payerName.ifBlank { null },
                    if (expense.isUnplanned) "unplanned" else null
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (expense.isUnplanned) palette.warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = TripRepository.format(expense.amount, currency),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// endregion

// region suggestions

@Composable
private fun NearbyCard(card: AgentCard.Nearby, actions: CardActions, modifier: Modifier) {
    val palette = WonderColors.current
    CardShell(modifier) {
        CardHeading(
            label = "Optional",
            detail = card.heading
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            card.picks.forEachIndexed { index, pick ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.hairline)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = pick.emoji, style = MaterialTheme.typography.titleMedium)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = pick.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = pick.why,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = listOfNotNull(
                                duration(pick.durationMinutes),
                                if (pick.estimatedCost > 0) {
                                    "${TripRepository.format(pick.estimatedCost, card.currency)} each"
                                } else {
                                    "free"
                                },
                                pick.suggestedTime?.let { "fits ${it.format(CLOCK)}" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.aurora[1]
                        )
                    }
                    AddToTripButton(onClick = { actions.onOpenAddToTrip(pick) })
                }
            }
        }
    }
}

@Composable
private fun AddToTripButton(onClick: () -> Unit) {
    val palette = WonderColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(palette.aurora))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Add to trip",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DraftDayCard(card: AgentCard.DraftDay, actions: CardActions, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val palette = WonderColors.current
    val tour = card.tour
    val visible = if (expanded) tour.stops else tour.stops.take(3)

    CardShell(modifier, onClick = { expanded = !expanded }) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "DRAFT · NOT IN YOUR TRIP YET",
                style = MaterialTheme.typography.labelSmall,
                color = palette.warning
            )
            Text(
                text = tour.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${tour.stops.size} stops · ${
                    String.format(Locale.ENGLISH, "%.1f", tour.totalDurationHours)
                }h · ${tour.currency}${tour.estimatedBudget} each" +
                    (card.date?.let { " · ${it.format(DAY_MONTH)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            visible.forEach { stop ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stop.timeSlot,
                        modifier = Modifier.width(58.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.aurora[1]
                    )
                    Text(text = stop.emoji, style = MaterialTheme.typography.titleMedium)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stop.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stop.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (expanded && stop.tip.isNotBlank()) {
                            Text(
                                text = stop.tip,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.aurora[0]
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Add to trip",
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(palette.aurora.take(2)))
                    .clickable { actions.onAcceptDraft(card) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            if (tour.stops.size > 3) {
                Text(
                    text = if (expanded) "Show less" else "All ${tour.stops.size} stops",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LooseCard(card: AgentCard.Loose, actions: CardActions, modifier: Modifier) {
    val palette = WonderColors.current
    CardShell(modifier) {
        CardHeading(card.heading, "${card.items.size} unbooked")
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            card.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { actions.onEditItem(item.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = item.kind.emoji, style = MaterialTheme.typography.bodyLarge)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${item.date.format(WEEKDAY)} ${item.date.format(DAY_MONTH)} · ${item.status.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.warning
                        )
                    }
                    val total = item.expectedTotal(card.travellerCount)
                    if (total > 0) {
                        Text(
                            text = TripRepository.format(total, card.currency),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightResultsCard(card: AgentCard.FlightResults, actions: CardActions, modifier: Modifier) {
    val palette = WonderColors.current
    val result: FlightSearchResult = card.result
    val query = result.query

    CardShell(modifier) {
        CardHeading(
            label = "Live flights",
            detail = "${query.origin} → ${query.destination} · ${query.departDate.format(DAY_MONTH)}${
                query.returnDate?.let { " – ${it.format(DAY_MONTH)}" }.orEmpty()
            }"
        )

        result.errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = palette.negative,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (result.offers.isEmpty() && result.configured && result.errorMessage == null) {
            Text(
                text = "No fares returned for those dates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            result.offers.take(5).forEach { offer ->
                FlightOfferRow(offer = offer, onAdd = {
                    actions.onAddFlightOffer(offer, query.departDate)
                })
            }
        }

        Text(
            text = result.providerLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun FlightOfferRow(offer: FlightOfferSummary, onAdd: () -> Unit) {
    val palette = WonderColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.cardTint)
            .border(1.dp, palette.hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onAdd)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "✈️", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = offer.airline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${offer.departLabel}–${offer.arriveLabel} · ${offer.durationLabel}${
                    if (offer.stops == 0) " · direct" else " · ${offer.stops} stop${if (offer.stops == 1) "" else "s"}"
                }",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = TripRepository.format(offer.price, offer.currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Add to trip",
                style = MaterialTheme.typography.labelSmall,
                color = palette.aurora[1],
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

@Composable
private fun DoorwayCard(card: AgentCard.Doorway, actions: CardActions, modifier: Modifier) {
    val palette = WonderColors.current
    val open = when (card.target) {
        DoorwayTarget.PLAN -> actions.onOpenPlan
        DoorwayTarget.EXPENSES -> actions.onOpenExpenses
        DoorwayTarget.SETTINGS -> actions.onOpenSettings
    }

    CardShell(modifier, onClick = open) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AmbientOrb(modifier = Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = card.headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = card.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowOutward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.aurora[1]
            )
        }
    }
}

@Composable
private fun ConfirmDatesCard(
    card: AgentCard.ConfirmDates,
    actions: CardActions,
    modifier: Modifier
) {
    val palette = WonderColors.current
    var whenPlan by remember { mutableStateOf(TripWhenPlan.default()) }
    val canConfirm = whenPlan.mode != TripWhenMode.FLEXIBLE_CHEAP

    CardShell(modifier) {
        CardHeading(card.headline)
        Text(
            text = card.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (card.pendingLegs.isNotEmpty()) {
            Text(
                text = "Waiting to add: ${card.pendingLegs.joinToString { it.title }}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.aurora[0]
            )
        }
        TripWhenPlanPicker(
            whenPlan = whenPlan,
            onWhenPlanChange = { whenPlan = it }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (canConfirm) {
                        Brush.linearGradient(palette.aurora)
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                )
                .clickable(enabled = canConfirm) {
                    actions.onConfirmDates(card, whenPlan)
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (canConfirm) "Lock dates & continue" else "Pick a date option first",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (canConfirm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// endregion

@Composable
internal fun statusColour(status: ItemStatus): Color {
    val palette = WonderColors.current
    return when (status) {
        ItemStatus.IDEA -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ItemStatus.PLANNED -> palette.aurora[1]
        ItemStatus.BOOKED -> palette.positive
        ItemStatus.DONE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
}

internal fun duration(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

internal fun humanMinutes(minutes: Long): String = when {
    minutes < 1 -> "moments"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} hr"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
