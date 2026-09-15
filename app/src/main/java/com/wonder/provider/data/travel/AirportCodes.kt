package com.wonder.provider.data.travel

/** Common city names → IATA airport codes for flight search. */
internal object AirportCodes {

    private val byCity = mapOf(
        "lisbon" to "LIS",
        "porto" to "OPO",
        "paris" to "CDG",
        "london" to "LHR",
        "barcelona" to "BCN",
        "rome" to "FCO",
        "madrid" to "MAD",
        "amsterdam" to "AMS",
        "berlin" to "BER",
        "munich" to "MUC",
        "frankfurt" to "FRA",
        "dublin" to "DUB",
        "brussels" to "BRU",
        "zurich" to "ZRH",
        "vienna" to "VIE",
        "prague" to "PRG",
        "copenhagen" to "CPH",
        "stockholm" to "ARN",
        "oslo" to "OSL",
        "helsinki" to "HEL",
        "athens" to "ATH",
        "istanbul" to "IST",
        "new york" to "JFK",
        "los angeles" to "LAX",
        "san francisco" to "SFO",
        "chicago" to "ORD",
        "miami" to "MIA",
        "toronto" to "YYZ",
        "dubai" to "DXB",
        "singapore" to "SIN",
        "tokyo" to "NRT",
        "bangkok" to "BKK",
        "sydney" to "SYD",
        "melbourne" to "MEL"
    )

    /** Resolve a city name or raw IATA code to a 3-letter airport code. */
    fun resolve(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.length == 3 && trimmed.all { it.isLetter() }) {
            return trimmed.uppercase()
        }
        val city = trimmed.lowercase().substringBefore(",").trim()
        return byCity.entries.firstOrNull { (key, _) -> city.contains(key) || key.contains(city) }?.value
    }
}
