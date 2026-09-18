package com.wonder.provider.data.travel

/** Maps ISO currency codes and common symbols for display in the trip UI. */
internal object CurrencyCodes {

    fun display(raw: String): String = when (raw.trim().uppercase()) {
        "EUR", "€" -> "€"
        "USD", "$", "US$" -> "$"
        "GBP", "£" -> "£"
        else -> raw.trim().ifBlank { "€" }
    }

    /** Duffel / ISO form when an API needs a three-letter code. */
    fun iso(raw: String): String = when (raw.trim().uppercase()) {
        "€", "EUR" -> "EUR"
        "$", "USD", "US$" -> "USD"
        "£", "GBP" -> "GBP"
        else -> raw.trim().uppercase().takeIf { it.length == 3 } ?: "EUR"
    }
}
