package com.wonder.provider.ui.personas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wonder.provider.AppContainer
import com.wonder.provider.model.CustomPersona
import com.wonder.provider.ui.conversation.PersonaAvatars
import com.wonder.provider.ui.theme.WonderColors
import kotlinx.coroutines.launch

@Composable
fun PersonaLibraryScreen(
    tripId: String? = null,
    onBack: () -> Unit
) {
    val viewModel: PersonaLibraryViewModel = viewModel(
        factory = PersonaLibraryViewModelFactory(
            repository = AppContainer.customPersonas,
            tripId = tripId
        )
    )
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val attachedIds by viewModel.attachedIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Personas", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (tripId != null) {
                        "Create personas and attach them to this trip"
                    } else {
                        "Saved traveller voices — attach them per trip"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    showCreate = !showCreate
                    error = null
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (showCreate) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(if (showCreate) "Close" else "New")
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AnimatedVisibility(
                    visible = showCreate,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    CreatePersonaPanel(
                        name = name,
                        description = description,
                        error = error,
                        onNameChange = { name = it },
                        onDescriptionChange = { description = it },
                        onCancel = {
                            showCreate = false
                            error = null
                        },
                        onCreate = {
                            scope.launch {
                                runCatching {
                                    viewModel.create(name, description)
                                    name = ""
                                    description = ""
                                    showCreate = false
                                    error = null
                                }.onFailure { error = it.message ?: "Could not save persona" }
                            }
                        }
                    )
                }
            }
            if (personas.isEmpty()) {
                item {
                    EmptyPersonaState(onCreate = { showCreate = true })
                }
            } else {
                item {
                    Text(
                        text = "${personas.size} saved ${if (personas.size == 1) "voice" else "voices"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(personas, key = { it.id }) { persona ->
                    PersonaLibraryRow(
                        persona = persona,
                        attached = persona.id in attachedIds,
                        showAttach = tripId != null,
                        onAttachToggle = {
                            scope.launch {
                                viewModel.toggleAttach(persona.id)
                            }
                        },
                        onDelete = {
                            scope.launch { viewModel.delete(persona.id) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaLibraryRow(
    persona: CustomPersona,
    attached: Boolean,
    showAttach: Boolean,
    onAttachToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = WonderColors.current
    val panel = persona.toStickyPanel()
    val style = PersonaAvatars.styleFor(panel)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, palette.hairline, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
            .size(44.dp)
                .clip(CircleShape)
            .background(style.tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = style.emoji ?: "✨", style = MaterialTheme.typography.titleMedium)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                persona.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                persona.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        if (showAttach) {
            TextButton(onClick = onAttachToggle) {
                Text(if (attached) "Added" else "Add")
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete persona",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreatePersonaPanel(
    name: String,
    description: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit
) {
    val palette = WonderColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, palette.hairline, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Create a traveller voice", style = MaterialTheme.typography.titleMedium)
        Text(
            "Give Wonder a point of view to bring into your trip.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Persona name") },
            placeholder = { Text("e.g. The Food Scout") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Their travel style") },
            placeholder = { Text("Picky about hotels, loves street food, always late") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onCreate,
                enabled = name.isNotBlank() && description.isNotBlank()
            ) {
                Text("Save persona")
            }
        }
    }
}

@Composable
private fun EmptyPersonaState(onCreate: () -> Unit) {
    val palette = WonderColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, palette.hairline, RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text("✦", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text("Make your first persona", style = MaterialTheme.typography.titleSmall)
        Text(
            "Create a distinct travel voice, then add it to any trip.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onCreate) { Text("Create persona") }
    }
}

@Composable
fun TripPersonasShortcut(
    tripId: String,
    onManagePersonas: () -> Unit,
    modifier: Modifier = Modifier
) {
    val attached by AppContainer.customPersonas.observeAttachedToTrip(tripId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val palette = WonderColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onManagePersonas)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Trip personas", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (attached.isEmpty()) {
                    "Attach custom voices that stay on this trip"
                } else {
                    "${attached.size} attached · tap to manage"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (attached.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                attached.take(4).forEach { persona ->
                    Text(
                        text = persona.emoji ?: "✨",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(palette.aurora[0].copy(alpha = 0.12f))
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}
