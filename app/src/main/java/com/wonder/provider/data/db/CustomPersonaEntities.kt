package com.wonder.provider.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "custom_personas")
data class CustomPersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val iconKey: String?,
    val emoji: String?,
    val createdAtEpochMillis: Long
)

@Entity(
    tableName = "trip_persona_attachments",
    primaryKeys = ["tripId", "personaId"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomPersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId"), Index("personaId")]
)
data class TripPersonaAttachmentEntity(
    val tripId: String,
    val personaId: String,
    val sortOrder: Int,
    val attachedAtEpochMillis: Long
)
