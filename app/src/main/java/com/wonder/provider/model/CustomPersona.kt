package com.wonder.provider.model

/** A user-created traveller persona stored on device. */
data class CustomPersona(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String?,
    val emoji: String?,
    val createdAtEpochMillis: Long
) {
    fun toStickyPanel(questions: List<String> = emptyList()): PersonaPanel =
        PersonaPanel.sticky(
            personaId = id,
            name = name,
            description = description,
            iconKey = iconKey,
            emoji = emoji,
            questions = questions
        )
}
