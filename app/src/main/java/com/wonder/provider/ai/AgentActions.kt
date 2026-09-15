package com.wonder.provider.ai

import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.Expense
import com.wonder.provider.model.ExpenseCategory
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A trip mutation the model asked the app to apply immediately. */
sealed interface AgentAction {
    data class SetBudget(val amount: Double) : AgentAction

    data class AddExpense(
        val label: String,
        val amount: Double,
        val category: ExpenseCategory?,
        val date: LocalDate?,
        val note: String
    ) : AgentAction

    data class AddItem(
        val title: String,
        val date: LocalDate,
        val kind: ItemKind,
        val startTime: LocalTime?,
        val durationMinutes: Int,
        val location: String,
        val notes: String,
        val estimatedCost: Double,
        val costIsPerPerson: Boolean,
        val status: ItemStatus
    ) : AgentAction

    data class UpdateItem(
        val itemId: String?,
        val matchTitle: String?,
        val title: String? = null,
        val date: LocalDate? = null,
        val kind: ItemKind? = null,
        val startTime: LocalTime? = null,
        val clearStartTime: Boolean = false,
        val durationMinutes: Int? = null,
        val location: String? = null,
        val notes: String? = null,
        val estimatedCost: Double? = null,
        val costIsPerPerson: Boolean? = null,
        val status: ItemStatus? = null,
        val paidAmount: Double? = null
    ) : AgentAction

    data class RemoveItem(
        val itemId: String?,
        val matchTitle: String?
    ) : AgentAction
}

data class AgentActionResult(
    val applied: List<String>,
    val failed: List<String>
) {
    val didMutate: Boolean get() = applied.isNotEmpty()
}

class AgentActionExecutor(private val trips: TripRepository) {

    fun execute(actions: List<AgentAction>): AgentActionResult {
        if (actions.isEmpty()) return AgentActionResult(emptyList(), emptyList())

        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()

        actions.forEach { action ->
            runCatching { apply(action) }
                .onSuccess { applied += it }
                .onFailure { failed += it.message.orEmpty().ifBlank { "Could not apply change." } }
        }

        return AgentActionResult(applied, failed)
    }

    private fun apply(action: AgentAction): String = when (action) {
        is AgentAction.SetBudget -> {
            trips.setBudget(action.amount.coerceAtLeast(0.0))
            "Budget set to ${money(action.amount)}"
        }

        is AgentAction.AddExpense -> {
            val trip = trips.trip.value
            val payer = trip.travellers.firstOrNull()?.id.orEmpty()
            require(payer.isNotBlank()) { "No travellers on this trip." }
            trips.addExpense(
                Expense(
                    id = trips.newExpenseId(),
                    label = action.label.trim(),
                    amount = action.amount.coerceAtLeast(0.0),
                    category = action.category ?: ExpenseCategory.OTHER,
                    date = action.date ?: LocalDate.now(),
                    paidById = payer,
                    note = action.note
                )
            )
            "Logged ${money(action.amount)} for ${action.label.trim()}"
        }

        is AgentAction.AddItem -> {
            val trip = trips.trip.value
            require(trip.covers(action.date)) { "That date is outside the trip." }
            val everyone = trip.travellers.map { it.id }.toSet()
            trips.upsertItem(
                ItineraryItem(
                    id = trips.newItemId(),
                    date = action.date,
                    title = action.title.trim(),
                    kind = action.kind,
                    startTime = action.startTime,
                    durationMinutes = action.durationMinutes.coerceAtLeast(15),
                    location = action.location,
                    notes = action.notes,
                    estimatedCost = action.estimatedCost.coerceAtLeast(0.0),
                    costIsPerPerson = action.costIsPerPerson,
                    status = action.status,
                    travellerIds = everyone
                )
            )
            "Added ${action.title.trim()} on ${dayLabel(action.date)}"
        }

        is AgentAction.UpdateItem -> {
            val existing = resolveItem(action.itemId, action.matchTitle)
                ?: error("Couldn't find that plan item.")
            val updated = existing.copy(
                title = action.title?.trim()?.ifBlank { existing.title } ?: existing.title,
                date = action.date ?: existing.date,
                kind = action.kind ?: existing.kind,
                startTime = when {
                    action.clearStartTime -> null
                    action.startTime != null -> action.startTime
                    else -> existing.startTime
                },
                durationMinutes = action.durationMinutes ?: existing.durationMinutes,
                location = action.location ?: existing.location,
                notes = action.notes ?: existing.notes,
                estimatedCost = action.estimatedCost ?: existing.estimatedCost,
                costIsPerPerson = action.costIsPerPerson ?: existing.costIsPerPerson,
                status = action.status ?: existing.status,
                paidAmount = action.paidAmount ?: existing.paidAmount
            )
            trips.upsertItem(updated)
            "Updated ${updated.title}"
        }

        is AgentAction.RemoveItem -> {
            val existing = resolveItem(action.itemId, action.matchTitle)
                ?: error("Couldn't find that plan item.")
            trips.removeItem(existing.id)
            "Removed ${existing.title}"
        }
    }

    private fun resolveItem(itemId: String?, matchTitle: String?): ItineraryItem? {
        itemId?.trim()?.takeIf { it.isNotBlank() }?.let { trips.item(it) }?.let { return it }
        val needle = matchTitle?.trim()?.lowercase().orEmpty()
        if (needle.isBlank()) return null
        val items = trips.items.value
        return items.firstOrNull { it.title.equals(needle, ignoreCase = true) }
            ?: items.firstOrNull { it.title.lowercase().contains(needle) }
            ?: items.firstOrNull { needle.contains(it.title.lowercase()) }
    }

    private fun dayLabel(date: LocalDate): String = when (date) {
        LocalDate.now() -> "today"
        LocalDate.now().plusDays(1) -> "tomorrow"
        else -> "day ${trips.trip.value.dayNumber(date)}"
    }

    private fun money(amount: Double) =
        TripRepository.format(amount, trips.trip.value.currency)

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")

        fun parseTime(value: String?): LocalTime? {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) return null
            return runCatching { LocalTime.parse(trimmed, TIME) }
                .recoverCatching { LocalTime.parse(trimmed) }
                .getOrNull()
        }

        fun parseCategory(value: String?): ExpenseCategory? =
            value?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { ExpenseCategory.valueOf(raw) }.getOrNull()
            }

        fun parseKind(value: String?): ItemKind? =
            value?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { ItemKind.valueOf(raw) }.getOrNull()
            }

        fun parseStatus(value: String?): ItemStatus? =
            value?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { ItemStatus.valueOf(raw) }.getOrNull()
            }
    }
}
