package com.wonder.provider.data

import com.wonder.provider.model.Expense
import com.wonder.provider.model.ExpenseCategory
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.Traveller
import com.wonder.provider.model.Trip
import java.time.LocalDate
import java.time.LocalTime

/**
 * A trip already underway — day three of eight — so Wandering has a live schedule to work with
 * and Planner still has unshaped days ahead of it.
 */
object SampleTrip {

    private val today: LocalDate = LocalDate.now()
    private val start: LocalDate = today.minusDays(2)
    private val end: LocalDate = today.plusDays(5)

    val you = Traveller("t1", "You", "🙂")
    private val mira = Traveller("t2", "Mira", "🌻")
    private val sam = Traveller("t3", "Sam", "🎧")

    private val everyone = setOf(you.id, mira.id, sam.id)

    val trip = Trip(
        id = "trip-lisbon",
        title = "Portugal, slowly",
        destination = "Lisbon, Portugal",
        startDate = start,
        endDate = end,
        travellers = listOf(you, mira, sam),
        budget = 3600.0,
        currency = "€",
        homeCurrency = "$",
        homeRate = 1.09,
        coverEmoji = "🇵🇹",
        interests = setOf(
            TourInterest.FOOD,
            TourInterest.CULTURE,
            TourInterest.LOCAL,
            TourInterest.NATURE
        )
    )

    val items: List<ItineraryItem> = listOf(
        // Day 1 — arrival
        ItineraryItem(
            id = "i1",
            date = start,
            title = "Flight to Lisbon",
            kind = ItemKind.FLIGHT,
            startTime = LocalTime.of(7, 40),
            durationMinutes = 195,
            location = "Gatwick → Humberto Delgado",
            notes = "Bags drop closes 60 min before. Sam has the aisle.",
            estimatedCost = 168.0,
            costIsPerPerson = true,
            status = ItemStatus.DONE,
            paidAmount = 486.0,
            bookingRef = "TP1043",
            travellerIds = everyone
        ),
        ItineraryItem(
            id = "i2",
            date = start,
            title = "Casa do Bairro apartment",
            kind = ItemKind.STAY,
            startTime = LocalTime.of(15, 0),
            durationMinutes = 60,
            location = "Príncipe Real",
            notes = "Self check-in, code in the email. Third floor, no lift.",
            estimatedCost = 780.0,
            status = ItemStatus.BOOKED,
            paidAmount = 812.0,
            bookingRef = "BK-77213",
            travellerIds = everyone
        ),
        ItineraryItem(
            id = "i3",
            date = start,
            title = "Dinner at a tasca, wherever looks good",
            kind = ItemKind.FOOD,
            startTime = LocalTime.of(20, 0),
            durationMinutes = 90,
            location = "Bairro Alto",
            estimatedCost = 25.0,
            costIsPerPerson = true,
            status = ItemStatus.DONE,
            travellerIds = everyone
        ),

        // Day 2
        ItineraryItem(
            id = "i4",
            date = start.plusDays(1),
            title = "Alfama on foot",
            kind = ItemKind.OUTDOORS,
            startTime = LocalTime.of(9, 30),
            durationMinutes = 150,
            location = "Alfama",
            notes = "Start at Miradouro das Portas do Sol and drift downhill.",
            status = ItemStatus.DONE,
            travellerIds = everyone
        ),
        ItineraryItem(
            id = "i5",
            date = start.plusDays(1),
            title = "Time Out Market lunch",
            kind = ItemKind.FOOD,
            startTime = LocalTime.of(13, 0),
            durationMinutes = 75,
            location = "Cais do Sodré",
            estimatedCost = 22.0,
            costIsPerPerson = true,
            status = ItemStatus.DONE,
            travellerIds = everyone
        ),
        ItineraryItem(
            id = "i6",
            date = start.plusDays(1),
            title = "Fado at Tasca do Chico",
            kind = ItemKind.NIGHTLIFE,
            startTime = LocalTime.of(21, 30),
            durationMinutes = 120,
            location = "Bairro Alto",
            notes = "No reservations — turn up by nine to get a stool.",
            estimatedCost = 18.0,
            costIsPerPerson = true,
            status = ItemStatus.DONE,
            travellerIds = everyone
        ),

        // Today
        ItineraryItem(
            id = "i7",
            date = today,
            title = "Pastéis de Belém, before the queue",
            kind = ItemKind.FOOD,
            startTime = LocalTime.of(8, 45),
            durationMinutes = 45,
            location = "Belém",
            notes = "Sit inside — the queue outside is for takeaway.",
            estimatedCost = 9.0,
            costIsPerPerson = true,
            status = ItemStatus.PLANNED,
            travellerIds = everyone
        ),
        ItineraryItem(
            id = "i8",
            date = today,
            title = "Jerónimos Monastery",
            kind = ItemKind.ACTIVITY,
            startTime = LocalTime.of(10, 0),
            durationMinutes = 90,
            location = "Belém",
            estimatedCost = 12.0,
            costIsPerPerson = true,
            status = ItemStatus.BOOKED,
            paidAmount = 36.0,
            bookingRef = "JM-88401",
            travellerIds = everyone
        ),
        ItineraryItem(
            id = "i9",
            date = today,
            title = "Sunset kayak on the Tagus",
            kind = ItemKind.OUTDOORS,
            startTime = LocalTime.of(18, 30),
            durationMinutes = 150,
            location = "Doca de Belém",
            notes = "Mira is sitting this one out. Bring a dry bag.",
            estimatedCost = 65.0,
            costIsPerPerson = true,
            status = ItemStatus.BOOKED,
            paidAmount = 130.0,
            bookingRef = "KY-2231",
            travellerIds = setOf(you.id, sam.id)
        ),

        // Tomorrow
        ItineraryItem(
            id = "i10",
            date = today.plusDays(1),
            title = "Train to Sintra",
            kind = ItemKind.TRANSPORT,
            startTime = LocalTime.of(8, 20),
            durationMinutes = 45,
            location = "Rossio station",
            estimatedCost = 5.0,
            costIsPerPerson = true,
            status = ItemStatus.PLANNED,
            travellerIds = everyone
        ),
        ItineraryItem(
            id = "i11",
            date = today.plusDays(1),
            title = "Pena Palace",
            kind = ItemKind.ACTIVITY,
            startTime = LocalTime.of(10, 0),
            durationMinutes = 150,
            location = "Sintra",
            notes = "Timed entry — book before we go, it sells out.",
            estimatedCost = 20.0,
            costIsPerPerson = true,
            status = ItemStatus.PLANNED,
            travellerIds = everyone
        ),
        ItineraryItem(
            id = "i12",
            date = today.plusDays(1),
            title = "Quinta da Regaleira",
            kind = ItemKind.ACTIVITY,
            startTime = LocalTime.of(14, 30),
            durationMinutes = 120,
            location = "Sintra",
            estimatedCost = 15.0,
            costIsPerPerson = true,
            status = ItemStatus.IDEA,
            travellerIds = everyone
        ),

        // Day 5
        ItineraryItem(
            id = "i13",
            date = today.plusDays(2),
            title = "LX Factory, slow morning",
            kind = ItemKind.SHOPPING,
            startTime = LocalTime.of(11, 0),
            durationMinutes = 120,
            location = "Alcântara",
            status = ItemStatus.PLANNED,
            travellerIds = everyone
        ),
        ItineraryItem(
            id = "i14",
            date = today.plusDays(2),
            title = "Nothing planned — keep it that way?",
            kind = ItemKind.FREE,
            durationMinutes = 0,
            notes = "Sam wants one day with no alarm.",
            status = ItemStatus.IDEA,
            travellerIds = everyone
        ),

        // Day 6 — day trip
        ItineraryItem(
            id = "i15",
            date = today.plusDays(3),
            title = "Arrábida coast day",
            kind = ItemKind.OUTDOORS,
            startTime = LocalTime.of(9, 0),
            durationMinutes = 480,
            location = "Setúbal peninsula",
            notes = "Car hire needed. Nobody has booked it yet.",
            estimatedCost = 140.0,
            status = ItemStatus.IDEA,
            travellerIds = everyone
        ),

        // Day 8 — home
        ItineraryItem(
            id = "i16",
            date = end,
            title = "Flight home",
            kind = ItemKind.FLIGHT,
            startTime = LocalTime.of(17, 15),
            durationMinutes = 180,
            location = "Humberto Delgado → Gatwick",
            estimatedCost = 168.0,
            costIsPerPerson = true,
            status = ItemStatus.BOOKED,
            paidAmount = 504.0,
            bookingRef = "TP1108",
            travellerIds = everyone
        )
    )

    val expenses: List<Expense> = listOf(
        Expense("e1", "Flights out", 486.0, ExpenseCategory.TRAVEL, start.minusDays(40), you.id, "i1"),
        Expense("e2", "Flights home", 504.0, ExpenseCategory.TRAVEL, start.minusDays(40), you.id, "i16"),
        Expense("e3", "Apartment, full stay", 812.0, ExpenseCategory.STAY, start.minusDays(30), mira.id, "i2"),
        Expense("e4", "Airport metro", 12.0, ExpenseCategory.TRAVEL, start, sam.id, null, "Cheaper than the taxi queue"),
        Expense("e5", "Tasca dinner, night one", 81.0, ExpenseCategory.FOOD, start, sam.id, "i3"),
        Expense("e6", "Market lunch", 68.0, ExpenseCategory.FOOD, start.plusDays(1), you.id, "i5"),
        Expense("e7", "Fado, drinks included", 62.0, ExpenseCategory.ACTIVITIES, start.plusDays(1), mira.id, "i6"),
        Expense("e8", "Ginjinha, three rounds", 14.0, ExpenseCategory.FOOD, start.plusDays(1), sam.id, null, "Worth it"),
        Expense("e9", "Tile shop, Mira's souvenir", 46.0, ExpenseCategory.SHOPPING, start.plusDays(1), mira.id, null),
        Expense("e10", "Monastery tickets", 36.0, ExpenseCategory.ACTIVITIES, today, you.id, "i8"),
        Expense("e11", "Kayak deposit", 130.0, ExpenseCategory.ACTIVITIES, today.minusDays(1), you.id, "i9"),
        Expense("e12", "Sunscreen, emergency", 11.0, ExpenseCategory.OTHER, today, sam.id, null, "Forgot to pack it")
    )
}
