package com.wonder.provider.data

import com.wonder.provider.model.TourInterest

internal data class PointOfInterest(
    val name: String,
    val description: String,
    val category: TourInterest,
    val durationMinutes: Int,
    val cost: Int,
    val emoji: String,
    val tip: String,
    val timePreference: Int, // 0=morning, 1=afternoon, 2=evening
    val location: String = ""
)

internal object CityCatalog {

    private val cities = mapOf(
        "lisbon" to lisbonPois(),
        "paris" to parisPois(),
        "barcelona" to barcelonaPois(),
        "tokyo" to tokyoPois(),
        "rome" to romePois(),
        "athens" to generateGenericPois("Athens"),
        "new york" to newYorkPois(),
        "london" to londonPois(),
        "amsterdam" to amsterdamPois()
    )

    fun resolveCity(input: String): String {
        val normalized = input.trim().lowercase()
        return cities.keys.firstOrNull { normalized.contains(it) || it.contains(normalized.split(",").first().trim()) }
            ?: normalized.split(",").first().trim().replaceFirstChar { it.uppercase() }
    }

    fun getPois(city: String): List<PointOfInterest> {
        val key = cities.keys.firstOrNull {
            city.lowercase().contains(it)
        }
        return if (key != null) cities[key]!! else generateGenericPois(city)
    }

    fun knownCities() = listOf(
        "Lisbon, Portugal",
        "Paris, France",
        "Barcelona, Spain",
        "Tokyo, Japan",
        "Rome, Italy",
        "Athens, Greece",
        "New York, USA",
        "London, UK",
        "Amsterdam, Netherlands"
    )

    private fun generateGenericPois(city: String): List<PointOfInterest> {
        val c = city.split(",").first().trim()
        return listOf(
            PointOfInterest(
                "$c Old Town Walk",
                "Explore the historic heart of $c with cobblestone streets and local architecture.",
                TourInterest.CULTURE, 90, 0, "🏛️", "Start early to beat the crowds.", 0
            ),
            PointOfInterest(
                "Local Market & Tasting",
                "Sample regional specialties at $c's best food market.",
                TourInterest.FOOD, 75, 25, "🍽️", "Ask vendors for their personal favourites.", 1
            ),
            PointOfInterest(
                "$c Viewpoint",
                "Panoramic views over the city skyline — perfect photo stop.",
                TourInterest.NATURE, 45, 0, "🌿", "Golden hour is magical here.", 1
            ),
            PointOfInterest(
                "Hidden Courtyard Café",
                "A locals-only spot tucked away from the tourist trail.",
                TourInterest.LOCAL, 60, 12, "💎", "Order whatever the person next to you is having.", 1
            ),
            PointOfInterest(
                "Contemporary Art Space",
                "Cutting-edge exhibitions from $c's vibrant art scene.",
                TourInterest.ART, 60, 18, "🎨", "Check if there's a free guided tour.", 1
            ),
            PointOfInterest(
                "Sunset Riverside Stroll",
                "Wind down along the waterfront as the city lights up.",
                TourInterest.NATURE, 60, 0, "🌅", "Bring a light jacket — evenings can be cool.", 2
            ),
            PointOfInterest(
                "Rooftop Bar Experience",
                "Craft cocktails with skyline views in $c.",
                TourInterest.NIGHTLIFE, 90, 35, "🌙", "Reserve ahead on weekends.", 2
            ),
            PointOfInterest(
                "Artisan Quarter",
                "Independent boutiques and maker studios in $c.",
                TourInterest.SHOPPING, 75, 0, "🛍️", "Many shops are cash-only — bring some.", 1
            ),
            PointOfInterest(
                "Adventure Outskirts",
                "Half-day excursion to natural wonders just outside $c.",
                TourInterest.ADVENTURE, 180, 45, "🧗", "Wear comfortable shoes — terrain varies.", 0
            ),
            PointOfInterest(
                "$c Museum Pass",
                "Curated museum circuit covering the city's top collections.",
                TourInterest.CULTURE, 120, 22, "🏺", "Buy a combined ticket to save 30%.", 0
            )
        )
    }

    private fun lisbonPois() = listOf(
        PointOfInterest("Alfama Morning Walk", "Wander Lisbon's oldest neighbourhood — fado echoes and azulejo tiles at every turn.", TourInterest.CULTURE, 90, 0, "🏘️", "Get lost on purpose — the best finds are off the main path.", 0, "Alfama, Lisbon"),
        PointOfInterest("Time Out Market Tasting", "Curated food hall with Portugal's best chefs under one roof.", TourInterest.FOOD, 75, 30, "🍷", "Share plates to try more vendors.", 1, "Cais do Sodré, Lisbon"),
        PointOfInterest("Belém Tower & Pastéis", "UNESCO landmark plus the original pastel de nata at Pastéis de Belém.", TourInterest.CULTURE, 90, 8, "🏰", "Queue moves fast — don't skip the cinnamon sugar.", 1, "Belém, Lisbon"),
        PointOfInterest("LX Factory Creative Hub", "Industrial complex turned art, design shops, and rooftop bars.", TourInterest.ART, 75, 0, "🎨", "Sunday brunch market is unmissable.", 1, "Alcântara, Lisbon"),
        PointOfInterest("Miradouro da Senhora do Monte", "Lisbon's highest viewpoint — 270° panorama over the Tagus.", TourInterest.NATURE, 45, 0, "🌅", "Bring a bottle of wine for sunset.", 2, "Graça, Lisbon"),
        PointOfInterest("Sunset Kayak on the Tagus", "Paddle past the 25 de Abril bridge as the sky turns gold.", TourInterest.ADVENTURE, 150, 65, "🛶", "No experience needed — guides are excellent.", 2, "Doca de Santo Amaro, Lisbon"),
        PointOfInterest("Bairro Alto Night Crawl", "Bar-hop through Lisbon's liveliest district.", TourInterest.NIGHTLIFE, 120, 40, "🌙", "Start at Park rooftop for views, end in a fado bar.", 2, "Bairro Alto, Lisbon"),
        PointOfInterest("Feira da Ladra Flea Market", "Treasure hunt at Lisbon's legendary Tuesday/Saturday flea market.", TourInterest.SHOPPING, 90, 0, "🛍️", "Haggle politely — it's part of the fun.", 0, "Campo de Santa Clara, Lisbon"),
        PointOfInterest("Sintra Day Trip", "Fairytale palaces and misty forests just 40 minutes away.", TourInterest.NATURE, 300, 25, "🏔️", "Book Pena Palace tickets online to skip the line.", 0, "Sintra, Portugal"),
        PointOfInterest("Hidden Tasca Dinner", "Family-run tavern serving bacalhau and vinho verde.", TourInterest.LOCAL, 90, 22, "💎", "No menu — the owner decides what's best today.", 2, "Mouraria, Lisbon")
    )

    private fun parisPois() = listOf(
        PointOfInterest("Montmartre & Sacré-Cœur", "Artists' quarter climb to the basilica — Paris at your feet.", TourInterest.CULTURE, 90, 0, "🎨", "Visit early morning before tour buses arrive.", 0),
        PointOfInterest("Le Marais Food Walk", "Jewish quarter falafel, macarons, and fromagerie tastings.", TourInterest.FOOD, 90, 35, "🥐", "L'As du Fallafel queue is worth it.", 1),
        PointOfInterest("Louvre Highlights", "Mona Lisa, Venus de Milo, and Winged Victory in 2 hours.", TourInterest.CULTURE, 120, 22, "🏛️", "Enter via Passage Richelieu to avoid the pyramid queue.", 1),
        PointOfInterest("Seine River Cruise", "See Notre-Dame, Eiffel Tower, and Musée d'Orsay from the water.", TourInterest.NATURE, 60, 18, "🚢", "Evening cruises with lights are magical.", 2),
        PointOfInterest("Le Marais Vintage Shops", "Curated vintage and concept stores in Paris's trendiest arrondissement.", TourInterest.SHOPPING, 90, 0, "👗", "Merci concept store is a must.", 1),
        PointOfInterest("Latin Quarter Jazz Club", "Intimate cellar jazz — Paris after dark at its finest.", TourInterest.NIGHTLIFE, 120, 45, "🎷", "Book Duc des Lombards in advance.", 2),
        PointOfInterest("Canal Saint-Martin Picnic", "Local favourite — picnic along the tree-lined canal.", TourInterest.LOCAL, 75, 15, "💎", "Buy wine, cheese, and baguette at nearby shops.", 1),
        PointOfInterest("Versailles Gardens", "Royal gardens and Hall of Mirrors — opulence on a grand scale.", TourInterest.CULTURE, 180, 27, "👑", "Rent a bike to explore the full estate.", 0)
    )

    private fun barcelonaPois() = listOf(
        PointOfInterest("Gaudí's Sagrada Família", "Gaudí's unfinished masterpiece — book timed entry.", TourInterest.ART, 90, 36, "⛪", "Nativity facade morning light is stunning.", 0),
        PointOfInterest("La Boqueria Market", "Vibrant market on La Rambla — tapas and fresh juice.", TourInterest.FOOD, 60, 20, "🍊", "Go before 10am to avoid peak crowds.", 1),
        PointOfInterest("Gothic Quarter Labyrinth", "Medieval streets, hidden plazas, and Roman ruins.", TourInterest.CULTURE, 90, 0, "🏰", "Plaça Reial is beautiful but touristy — explore beyond.", 1),
        PointOfInterest("Bunkers del Carmel", "Best free sunset viewpoint in Barcelona.", TourInterest.NATURE, 60, 0, "🌅", "Bring drinks and arrive 45 min before sunset.", 2),
        PointOfInterest("El Born Boutiques", "Independent designers and artisan crafts.", TourInterest.SHOPPING, 75, 0, "🛍️", "Carrer de la Princesa has the best finds.", 1),
        PointOfInterest("Barceloneta Beach & Chiringuito", "Mediterranean swim and seafood paella by the sea.", TourInterest.ADVENTURE, 120, 30, "🏖️", "Avoid restaurants on the boardwalk — walk one block inland.", 1),
        PointOfInterest("Poble Espanyol Night", "Open-air architectural museum with flamenco and live music.", TourInterest.NIGHTLIFE, 120, 25, "💃", "Friday nights have the best atmosphere.", 2),
        PointOfInterest("Gràcia Village Secrets", "Village-within-a-city — locals' favourite neighbourhood.", TourInterest.LOCAL, 90, 0, "💎", "Plaça del Sol is the heart — grab a vermut.", 1)
    )

    private fun tokyoPois() = listOf(
        PointOfInterest("Tsukiji Outer Market Breakfast", "Fresh sushi, tamagoyaki, and matcha at dawn.", TourInterest.FOOD, 90, 25, "🍣", "Arrive by 7am for the best selection.", 0),
        PointOfInterest("Senso-ji Temple & Asakusa", "Tokyo's oldest temple and traditional nakamise shopping street.", TourInterest.CULTURE, 90, 0, "⛩️", "Rent a kimono nearby for photos.", 0),
        PointOfInterest("Shibuya & Harajuku", "Crossing, fashion, and kawaii culture explosion.", TourInterest.SHOPPING, 120, 0, "🗼", "Takeshita Street for quirky finds.", 1),
        PointOfInterest("TeamLab Borderless", "Immersive digital art — walk through living light installations.", TourInterest.ART, 120, 38, "✨", "Book tickets weeks ahead.", 1),
        PointOfInterest("Meiji Shrine Forest Walk", "Serene Shinto shrine surrounded by 100,000 trees.", TourInterest.NATURE, 60, 0, "🌳", "Write a wish on an ema wooden plaque.", 0),
        PointOfInterest("Golden Gai Bar Hop", "Tiny bars in Shinjuku — each seats 5-8 people.", TourInterest.NIGHTLIFE, 120, 50, "🌙", "Cover charges vary — ask before sitting.", 2),
        PointOfInterest("Yanaka Old Tokyo", "Showa-era neighbourhood untouched by modern development.", TourInterest.LOCAL, 90, 0, "💎", "Cat Street has charming cafés.", 1),
        PointOfInterest("Day Trip to Nikko", "Ornate shrines and mountain scenery 2 hours north.", TourInterest.ADVENTURE, 360, 45, "🏔️", "JR Pass covers the train if you have one.", 0)
    )

    private fun romePois() = listOf(
        PointOfInterest("Colosseum & Roman Forum", "Ancient Rome's epicentre — gladiators and emperors.", TourInterest.CULTURE, 120, 24, "🏛️", "Book skip-the-line — saves 2 hours.", 0),
        PointOfInterest("Trastevere Food Tour", "Cacio e pepe, supplì, and gelato in Rome's soulful quarter.", TourInterest.FOOD, 90, 30, "🍝", "Da Enzo al 29 — book or queue early.", 1),
        PointOfInterest("Vatican Museums & Sistine", "Michelangelo's ceiling and Raphael rooms.", TourInterest.ART, 150, 21, "🎨", "Wednesday mornings are least crowded.", 0),
        PointOfInterest("Villa Borghese Gardens", "Rent a bike and explore Rome's Central Park.", TourInterest.NATURE, 90, 15, "🌿", "Galleria Borghese inside requires booking.", 1),
        PointOfInterest("Testaccio Market Lunch", "Real Roman market — where chefs shop and eat.", TourInterest.LOCAL, 75, 18, "💎", "Mordi e Vai sandwich is legendary.", 1),
        PointOfInterest("Sunset at Gianicolo", "Panoramic view over all of Rome — bells at 12pm daily.", TourInterest.NATURE, 45, 0, "🌅", "Less crowded than Pincio.", 2),
        PointOfInterest("Campo de' Fiori Aperitivo", "Spritz hour in Rome's liveliest square.", TourInterest.NIGHTLIFE, 90, 25, "🍸", "Fridays are packed — arrive early.", 2),
        PointOfInterest("Via del Corso Shopping", "Main shopping artery from Piazza Venezia to Piazza del Popolo.", TourInterest.SHOPPING, 90, 0, "🛍️", "Side streets have better deals.", 1)
    )

    private fun newYorkPois() = listOf(
        PointOfInterest("Central Park Morning", "Bethesda Fountain, Bow Bridge, and Strawberry Fields.", TourInterest.NATURE, 90, 0, "🌳", "Rent a rowboat at Loeb Boathouse.", 0),
        PointOfInterest("Chelsea Market & High Line", "Food hall then elevated park with Hudson River views.", TourInterest.FOOD, 120, 25, "🥯", "Los Tacos No.1 is the move.", 1),
        PointOfInterest("Metropolitan Museum", "World-class collection — plan 3 hours minimum.", TourInterest.ART, 180, 30, "🏛️", "Pay-what-you-wish for NY residents.", 1),
        PointOfInterest("Brooklyn Bridge Walk", "Iconic skyline views — walk Manhattan to DUMBO.", TourInterest.ADVENTURE, 60, 0, "🌉", "Sunrise is empty and spectacular.", 0),
        PointOfInterest("SoHo & Nolita Shopping", "Cast-iron buildings and independent boutiques.", TourInterest.SHOPPING, 90, 0, "👜", "Prince Street has the best density.", 1),
        PointOfInterest("Greenwich Village Jazz", "Village Vanguard or Blue Note — NYC jazz institutions.", TourInterest.NIGHTLIFE, 120, 55, "🎷", "Reserve weeks ahead for weekend sets.", 2),
        PointOfInterest("Arthur Avenue Little Italy", "The real Little Italy — in the Bronx.", TourInterest.LOCAL, 90, 20, "💎", "Mario's for cannoli, Egidio's for history.", 1),
        PointOfInterest("Statue of Liberty & Ellis Island", "Immigration history and harbour views.", TourInterest.CULTURE, 180, 25, "🗽", "First ferry of the day = smallest crowds.", 0)
    )

    private fun londonPois() = listOf(
        PointOfInterest("Borough Market Brunch", "London's oldest food market — artisan everything.", TourInterest.FOOD, 75, 22, "🧀", "Bread Ahead doughnuts are essential.", 0),
        PointOfInterest("British Museum Highlights", "Rosetta Stone, Parthenon marbles, Egyptian mummies — free entry.", TourInterest.CULTURE, 120, 0, "🏛️", "Arrive at opening to see the Rosetta Stone alone.", 0),
        PointOfInterest("South Bank Walk", "Thames path from Tower Bridge to Westminster.", TourInterest.NATURE, 90, 0, "🌊", "Street performers near the London Eye.", 1),
        PointOfInterest("Shoreditch Street Art", "Banksy, murals, and creative East London energy.", TourInterest.ART, 75, 0, "🎨", "Brick Lane on Sunday for the full market.", 1),
        PointOfInterest("Camden Market Explore", "Alternative culture, vintage, and global street food.", TourInterest.SHOPPING, 90, 0, "🎪", "Walk the canal to Regent's Park after.", 1),
        PointOfInterest("Soho Pub & Comedy", "Historic pubs and world-class comedy clubs.", TourInterest.NIGHTLIFE, 120, 35, "🍺", "Ronnie Scott's for jazz — book ahead.", 2),
        PointOfInterest("Columbia Road Flowers", "Sunday flower market — East London at its most charming.", TourInterest.LOCAL, 60, 0, "💐", "Only open Sundays 8am-2pm.", 0),
        PointOfInterest("Hampstead Heath Swim", "Wild swimming in the ponds with city views.", TourInterest.ADVENTURE, 90, 0, "🏊", "Mixed pond is most popular in summer.", 1)
    )

    private fun amsterdamPois() = listOf(
        PointOfInterest("Canal Ring Walk", "UNESCO canals — 17th-century gabled houses reflected in water.", TourInterest.CULTURE, 90, 0, "🚲", "Rent a bike — it's the local way.", 0),
        PointOfInterest("Albert Cuyp Market", "Stroopwafels, herring, and Dutch cheese tasting.", TourInterest.FOOD, 60, 15, "🧇", "Try fresh stroopwafel from the iron press.", 1),
        PointOfInterest("Rijksmuseum Masterpieces", "Rembrandt's Night Watch and Vermeer's Milkmaid.", TourInterest.ART, 120, 22, "🖼️", "Book online — walk-ins queue for hours.", 1),
        PointOfInterest("Vondelpark Picnic", "Amsterdam's green heart — locals jogging and picnicking.", TourInterest.NATURE, 75, 10, "🌿", "Blauwe Theehuis terrace for drinks.", 1),
        PointOfInterest("Nine Streets Shopping", "Nine narrow lanes of vintage, design, and concept stores.", TourInterest.SHOPPING, 90, 0, "🛍️", "Best area for unique souvenirs.", 1),
        PointOfInterest("Jordaan Hidden Courtyards", "Quiet hofjes (courtyards) locals keep secret.", TourInterest.LOCAL, 60, 0, "💎", "Respect privacy — these are residential.", 1),
        PointOfInterest("NDSM Wharf Sunset", "Former shipyard turned creative district across the IJ.", TourInterest.NIGHTLIFE, 120, 20, "🌅", "Free ferry from Central Station.", 2),
        PointOfInterest("Zaanse Schans Windmills", "Working windmills and clogs — 20 min from Amsterdam.", TourInterest.ADVENTURE, 180, 0, "🌬️", "Go on a weekday to avoid tour groups.", 0)
    )
}
