package com.wonder.provider.ui.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wonder.provider.data.CustomPersonaRepository
import com.wonder.provider.model.CustomPersona
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PersonaLibraryViewModel(
    private val repository: CustomPersonaRepository,
    private val tripId: String?
) : ViewModel() {

    val personas: StateFlow<List<CustomPersona>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _attachedIds = MutableStateFlow<Set<String>>(emptySet())
    val attachedIds: StateFlow<Set<String>> = _attachedIds

    init {
        if (tripId != null) {
            viewModelScope.launch {
                repository.observeAttachedToTrip(tripId).collect { attached ->
                    _attachedIds.value = attached.map { it.id }.toSet()
                }
            }
        }
    }

    suspend fun create(name: String, description: String) {
        repository.create(name, description)
    }

    suspend fun delete(personaId: String) {
        repository.delete(personaId)
    }

    suspend fun toggleAttach(personaId: String) {
        val trip = tripId ?: return
        if (_attachedIds.value.contains(personaId)) {
            repository.detachFromTrip(trip, personaId)
        } else {
            repository.attachToTrip(trip, personaId)
        }
    }
}

class PersonaLibraryViewModelFactory(
    private val repository: CustomPersonaRepository,
    private val tripId: String?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PersonaLibraryViewModel(repository, tripId) as T
}
