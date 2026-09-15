package com.wonder.provider.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPersonaDao {

    @Query("SELECT * FROM custom_personas ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<CustomPersonaEntity>>

    @Query("SELECT * FROM custom_personas WHERE id = :personaId")
    suspend fun personaById(personaId: String): CustomPersonaEntity?

    @Query("SELECT * FROM custom_personas WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun personaByName(name: String): CustomPersonaEntity?

    @Query(
        """
        SELECT cp.* FROM custom_personas cp
        INNER JOIN trip_persona_attachments tpa ON tpa.personaId = cp.id
        WHERE tpa.tripId = :tripId
        ORDER BY tpa.sortOrder ASC, tpa.attachedAtEpochMillis ASC
        """
    )
    fun observeAttachedToTrip(tripId: String): Flow<List<CustomPersonaEntity>>

    @Query(
        """
        SELECT personaId FROM trip_persona_attachments
        WHERE tripId = :tripId AND personaId = :personaId
        LIMIT 1
        """
    )
    suspend fun isAttached(tripId: String, personaId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(entity: CustomPersonaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun attach(entity: TripPersonaAttachmentEntity)

    @Query("DELETE FROM custom_personas WHERE id = :personaId")
    suspend fun deletePersona(personaId: String)

    @Query("DELETE FROM trip_persona_attachments WHERE tripId = :tripId AND personaId = :personaId")
    suspend fun detach(tripId: String, personaId: String)
}
