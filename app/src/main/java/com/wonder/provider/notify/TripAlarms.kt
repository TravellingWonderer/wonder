package com.wonder.provider.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.TripMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Wandering mode is time-aware, so the day arrives as nudges rather than something you have to
 * keep checking. Nothing is scheduled while the trip is still being planned.
 */
class TripAlarms(
    private val context: Context,
    private val trips: TripRepository
) {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val scheduled = mutableSetOf<Int>()

    /** Rebuilt from scratch whenever the plan or the mode changes — simpler than diffing. */
    fun refresh() {
        cancelAll()
        if (trips.mode.value != TripMode.WANDERING) return

        val now = LocalDateTime.now()
        val horizon = now.plusDays(2)

        trips.items.value
            .asSequence()
            .filter { it.startTime != null && it.status != ItemStatus.IDEA && it.status != ItemStatus.DONE }
            .map { it to LocalDateTime.of(it.date, it.startTime) }
            .filter { (_, start) -> start.isAfter(now) && start.isBefore(horizon) }
            .forEach { (item, start) ->
                val fireAt = start.minusMinutes(LEAD_MINUTES)
                if (fireAt.isAfter(now)) {
                    schedule(
                        id = alarmId(trips.trip.value.id, item.id),
                        at = fireAt,
                        title = item.title,
                        body = buildString {
                            append("Starts at ${item.startTime!!.format(CLOCK)}")
                            if (item.location.isNotBlank()) append(" · ${item.location}")
                            if (item.notes.isNotBlank()) append("\n${item.notes}")
                        }
                    )
                }
            }

        scheduleMorningBriefing(now)
    }

    private fun scheduleMorningBriefing(now: LocalDateTime) {
        val trip = trips.trip.value
        val briefingDate = if (now.toLocalTime().isBefore(BRIEFING_TIME)) {
            LocalDate.now()
        } else {
            LocalDate.now().plusDays(1)
        }
        if (!trip.covers(briefingDate)) return

        val items = trips.itemsOn(briefingDate).filter { it.status != ItemStatus.IDEA }
        if (items.isEmpty()) return

        val first = items.firstOrNull { it.startTime != null }
        schedule(
            id = briefingAlarmId(trips.trip.value.id),
            at = LocalDateTime.of(briefingDate, BRIEFING_TIME),
            title = "Day ${trip.dayNumber(briefingDate)} in ${trip.destination.substringBefore(",")}",
            body = buildString {
                append("${items.size} ${if (items.size == 1) "thing" else "things"} today")
                first?.let { append(", starting with ${it.title} at ${it.startTime!!.format(CLOCK)}") }
            }
        )
    }

    private fun schedule(id: Int, at: LocalDateTime, title: String, body: String) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, TripAlarmReceiver::class.java).apply {
            putExtra(TripAlarmReceiver.EXTRA_TITLE, title)
            putExtra(TripAlarmReceiver.EXTRA_BODY, body)
            putExtra(TripAlarmReceiver.EXTRA_ID, id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // A window rather than an exact alarm: accurate enough for a nudge, and it avoids
        // asking the traveller for exact-alarm permission.
        alarms?.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MILLIS, pending)
        scheduled += id
    }

    fun cancelAll() {
        scheduled.forEach { id ->
            val pending = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, TripAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pending?.let { alarms?.cancel(it) }
        }
        scheduled.clear()
    }

    private fun alarmId(tripId: String, itemId: String): Int = "$tripId:$itemId".hashCode()

    private fun briefingAlarmId(tripId: String): Int = "$tripId:briefing".hashCode()

    companion object {
        const val CHANNEL_ID = "wonder_schedule"
        private const val LEAD_MINUTES = 30L
        private const val WINDOW_MILLIS = 5 * 60 * 1000L
        private val BRIEFING_TIME: LocalTime = LocalTime.of(8, 0)
        private val CLOCK = java.time.format.DateTimeFormatter.ofPattern("h:mm a")

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip schedule",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Nudges before things on your itinerary start"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
