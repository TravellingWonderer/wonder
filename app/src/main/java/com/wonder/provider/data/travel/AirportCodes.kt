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
        "nyc" to "JFK",
        "los angeles" to "LAX",
        "san francisco" to "SFO",
        "chicago" to "ORD",
        "miami" to "MIA",
        "toronto" to "YYZ",
        "vancouver" to "YVR",
        "montreal" to "YUL",
        "dubai" to "DXB",
        "singapore" to "SIN",
        "tokyo" to "NRT",
        "bangkok" to "BKK",
        "sydney" to "SYD",
        "melbourne" to "MEL",
        "hong kong" to "HKG",
        "seoul" to "ICN",
        "milan" to "MXP",
        "venice" to "VCE",
        "florence" to "FLR",
        "naples" to "NAP",
        "seville" to "SVQ",
        "valencia" to "VLC",
        "nice" to "NCE",
        "lyon" to "LYS",
        "marseille" to "MRS",
        "edinburgh" to "EDI",
        "manchester" to "MAN",
        "budapest" to "BUD",
        "warsaw" to "WAW",
        "krakow" to "KRK",
        "lisboa" to "LIS",
        "sintra" to "LIS"
    )

    /** Resolve a city name or raw IATA code to a 3-letter airport code. */
    fun resolve(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.length == 3 && trimmed.all { it.isLetter() }) {
            return trimmed.uppercase()
        }
        val city = trimmed.lowercase().substringBefore(",").trim()
        if (city.isBlank()) return null
        byCity[city]?.let { return it }
        return byCity.entries.firstOrNull { (key, _) ->
            city.contains(key) || key.contains(city)
        }?.value
    }
}
