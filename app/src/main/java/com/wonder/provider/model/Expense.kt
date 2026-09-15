package com.wonder.provider.model

import java.time.LocalDate

enum class ExpenseCategory(val label: String, val emoji: String) {
    TRAVEL("Travel", "✈️"),
    STAY("Stay", "🛏️"),
    FOOD("Food", "🍽️"),
    ACTIVITIES("Activities", "🎟️"),
    SHOPPING("Shopping", "🛍️"),
    OTHER("Other", "•")
}

/**
 * Money that actually left someone's pocket. An expense tied to an [itemId] is the real cost of
 * something that was planned; one without is an extra the trip picked up along the way.
 */
data class Expense(
    val id: String,
    val label: String,
    val amount: Double,
    val category: ExpenseCategory,
    val date: LocalDate,
    val paidById: String,
    val itemId: String? = null,
    val note: String = ""
) {
    val isUnplanned: Boolean get() = itemId == null
}

data class CategorySpend(
    val category: ExpenseCategory,
    val spent: Double,
    val planned: Double
) {
    val variance: Double get() = planned - spent
}

/**
 * The whole money picture for a trip. [projected] is what the trip will cost if the rest of the
 * plan goes as written, which is the number that actually tells you whether to worry.
 */
data class BudgetSummary(
    val budget: Double,
    val spent: Double,
    val unplannedSpent: Double,
    val stillToPay: Double,
    val currency: String,
    val homeCurrency: String,
    val homeRate: Double,
    val byCategory: List<CategorySpend>
) {
    val remaining: Double get() = budget - spent
    val projected: Double get() = spent + stillToPay
    /** Positive means the trip is on track to come in under budget. */
    val variance: Double get() = budget - projected
    val isOverrun: Boolean get() = variance < 0
    val spentFraction: Float get() = if (budget <= 0) 0f else (spent / budget).toFloat()
    val projectedFraction: Float get() = if (budget <= 0) 0f else (projected / budget).toFloat()
    val inHomeCurrency: Double get() = spent * homeRate
}
