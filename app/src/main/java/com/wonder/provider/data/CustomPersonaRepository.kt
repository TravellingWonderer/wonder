package com.wonder.provider.data

import com.wonder.provider.data.db.CustomPersonaDao
import com.wonder.provider.data.db.CustomPersonaEntity
import com.wonder.provider.data.db.TripPersonaAttachmentEntity
import com.wonder.provider.model.CustomPersona
import com.wonder.provider.model.PersonaIconCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CustomPersonaRepository(private val dao: CustomPersonaDao) {

    fun observeAll(): Flow<List<CustomPersona>> =
        dao.observeAll().map { entities -> entities.map(::toModel) }

    fun observeAttachedToTrip(tripId: String): Flow<List<CustomPersona>> =
        dao.observeAttachedToTrip(tripId).map { entities -> entities.map(::toModel) }

    suspend fun getById(personaId: String): CustomPersona? =
        withContext(Dispatchers.IO) {
            dao.personaById(personaId)?.let(::toModel)
        }

    suspend fun findByName(name: String): CustomPersona? =
        withContext(Dispatchers.IO) {
            dao.personaByName(name.trim())?.let(::toModel)
        }

    suspend fun createFromPanel(panel: com.wonder.provider.model.PersonaPanel): CustomPersona =
        withContext(Dispatchers.IO) {
            val trimmedName = panel.persona.trim()
            require(trimmedName.length >= 2) { "Name must be at least 2 characters" }
            val trimmedDescription = describePanel(panel)
            val iconKey = panel.resolvedIconKey()
            val emoji = panel.resolvedEmoji()
            val entity = CustomPersonaEntity(
                id = "persona-${System.currentTimeMillis()}",
                name = trimmedName,
                description = trimmedDescription,
                iconKey = iconKey,
                emoji = emoji,
                createdAtEpochMillis = System.currentTimeMillis()
            )
            dao.upsertPersona(entity)
            toModel(entity)
        }

    suspend fun findOrCreateFromPanel(panel: com.wonder.provider.model.PersonaPanel): CustomPersona =
        findByName(panel.persona) ?: createFromPanel(panel)

    suspend fun create(name: String, description: String): CustomPersona = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        val trimmedDescription = description.trim()
        require(trimmedName.length >= 2) { "Name must be at least 2 characters" }
        require(trimmedDescription.length >= 8) { "Describe the persona in a few words" }
        val iconKey = PersonaIconCatalog.inferIconKey("$trimmedName $trimmedDescription")
        val emoji = PersonaIconCatalog.inferEmoji(trimmedName, iconKey)
        val entity = CustomPersonaEntity(
            id = "persona-${System.currentTimeMillis()}",
            name = trimmedName,
            description = trimmedDescription,
            iconKey = iconKey,
            emoji = emoji,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        dao.upsertPersona(entity)
        toModel(entity)
    }

    suspend fun delete(personaId: String) = withContext(Dispatchers.IO) {
        dao.deletePersona(personaId)
    }

    suspend fun attachToTrip(tripId: String, personaId: String) = withContext(Dispatchers.IO) {
        if (dao.isAttached(tripId, personaId) != null) return@withContext
        dao.attach(
            TripPersonaAttachmentEntity(
                tripId = tripId,
                personaId = personaId,
                sortOrder = 0,
                attachedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun detachFromTrip(tripId: String, personaId: String) = withContext(Dispatchers.IO) {
        dao.detach(tripId, personaId)
    }

    suspend fun isAttached(tripId: String, personaId: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.isAttached(tripId, personaId) != null
        }

    private fun toModel(entity: CustomPersonaEntity): CustomPersona =
        CustomPersona(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            iconKey = entity.iconKey,
            emoji = entity.emoji,
            createdAtEpochMillis = entity.createdAtEpochMillis
        )

    private fun describePanel(panel: com.wonder.provider.model.PersonaPanel): String {
        val sample = panel.questions.firstOrNull()?.take(100)?.trim().orEmpty()
        return if (sample.isNotBlank()) {
            "Speaks as ${panel.persona}. Example: \"$sample\""
        } else {
            "A curious traveller voice: ${panel.persona} — asks practical and playful trip questions."
        }
    }
}
