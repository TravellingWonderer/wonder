package com.wonder.provider.data.db

import androidx.room.TypeConverter
import com.wonder.provider.model.ExpenseCategory
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.TripArchiveStatus
import com.wonder.provider.model.TripMode
import java.time.LocalDate
import java.time.LocalTime

class WonderTypeConverters {

    @TypeConverter fun fromDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter fun toDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter fun fromTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter fun toTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter fun fromTripMode(value: TripMode): String = value.name

    @TypeConverter fun toTripMode(value: String): TripMode = TripMode.valueOf(value)

    @TypeConverter fun fromArchiveStatus(value: TripArchiveStatus): String = value.name

    @TypeConverter fun toArchiveStatus(value: String): TripArchiveStatus = TripArchiveStatus.valueOf(value)

    @TypeConverter fun fromItemKind(value: ItemKind): String = value.name

    @TypeConverter fun toItemKind(value: String): ItemKind = ItemKind.valueOf(value)

    @TypeConverter fun fromItemStatus(value: ItemStatus): String = value.name

    @TypeConverter fun toItemStatus(value: String): ItemStatus = ItemStatus.valueOf(value)

    @TypeConverter fun fromExpenseCategory(value: ExpenseCategory): String = value.name

    @TypeConverter fun toExpenseCategory(value: String): ExpenseCategory = ExpenseCategory.valueOf(value)

    @TypeConverter fun fromTourInterest(value: TourInterest): String = value.name

    @TypeConverter fun toTourInterest(value: String): TourInterest = TourInterest.valueOf(value)

    @TypeConverter fun fromTravellerIds(value: Set<String>): String = value.joinToString(",")

    @TypeConverter fun toTravellerIds(value: String): Set<String> =
        if (value.isBlank()) emptySet() else value.split(",").filter { it.isNotBlank() }.toSet()
}
