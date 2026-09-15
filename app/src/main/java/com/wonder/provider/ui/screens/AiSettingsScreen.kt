package com.wonder.provider.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wonder.provider.AppContainer
import com.wonder.provider.ai.AiProviderType
import com.wonder.provider.ai.EdgeGalleryLinks
import com.wonder.provider.ui.conversation.AmbientOrb
import com.wonder.provider.ui.theme.WonderColors

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = remember {
        AiSettingsViewModel(AppContainer.aiSettingsRepository, AppContainer.aiTourService)
    }
    val state by viewModel.uiState.collectAsState()
    val palette = WonderColors.current
    var showKey by remember { mutableStateOf(false) }
    var showHfToken by remember { mutableStateOf(false) }

    val pickModel = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onModelPicked(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 10.dp, end = 22.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to the conversation",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(12.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AmbientOrb(modifier = Modifier.size(72.dp))
                    Text(
                        text = "How Wonder thinks",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = state.sourceDescription,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionLabel("On this phone · free")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProviderType.onDevice.forEach { provider ->
                        ProviderRow(
                            name = provider.displayName,
                            hint = provider.subtitle,
                            selected = state.selectedProvider == provider,
                            onSelect = { viewModel.setProvider(provider) }
                        )
                    }
                }
            }

            if (state.selectedProvider == AiProviderType.GEMMA_LITERT) {
                item {
                    GemmaSetupCard(
                        galleryInstalled = state.galleryInstalled,
                        modelAttached = state.modelAttached,
                        modelFileName = state.modelFileName,
                        discovered = state.discoveredModels,
                        onOpenGallery = {
                            openUrl(context, EdgeGalleryLinks.PLAY_STORE)
                        },
                        onOpenWiki = {
                            openUrl(context, EdgeGalleryLinks.WIKI_IMPORT)
                        },
                        onOpenHuggingFace = {
                            openUrl(context, EdgeGalleryLinks.HF_GEMMA3_1B)
                        },
                        onPickModel = {
                            pickModel.launch(arrayOf("*/*"))
                        }
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.hfTokenInput,
                            onValueChange = viewModel::setHfToken,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Hugging Face token (optional)") },
                            placeholder = { Text("hf_… — only if a model asks for it") },
                            trailingIcon = {
                                TextButton(onClick = { showHfToken = !showHfToken }) {
                                    Text(if (showHfToken) "Hide" else "Show")
                                }
                            },
                            visualTransformation = if (showHfToken) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Text(
                            text = "Most Gemma bundles from Google AI Edge Gallery need no token. " +
                                "Add one only if you download a gated model yourself.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionLabel("Cloud · your subscription")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProviderType.cloud.forEach { provider ->
                        ProviderRow(
                            name = provider.displayName,
                            hint = provider.subtitle,
                            selected = state.selectedProvider == provider,
                            onSelect = { viewModel.setProvider(provider) }
                        )
                    }
                }
            }

            if (state.selectedProvider.kind == com.wonder.provider.ai.AiProviderKind.CLOUD) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.selectedProvider == AiProviderType.CUSTOM_OPENAI_COMPAT) {
                            OutlinedTextField(
                                value = state.customBaseUrlInput,
                                onValueChange = viewModel::setCustomBaseUrl,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("API base URL") },
                                placeholder = { Text("https://api.openrouter.ai/v1") },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.customModelInput,
                                onValueChange = viewModel::setCustomModel,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Model name") },
                                placeholder = { Text("gpt-4o-mini") },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = state.apiKeyInput,
                            onValueChange = viewModel::setApiKey,
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    if (state.selectedProvider == AiProviderType.CUSTOM_OPENAI_COMPAT) {
                                        "Access token"
                                    } else {
                                        "API key"
                                    }
                                )
                            },
                            placeholder = {
                                Text(
                                    if (state.selectedProvider == AiProviderType.CUSTOM_OPENAI_COMPAT) {
                                        "Paste your bearer token"
                                    } else {
                                        "Paste your ${state.selectedProvider.keyHint} key"
                                    }
                                )
                            },
                            trailingIcon = {
                                TextButton(onClick = { showKey = !showKey }) {
                                    Text(if (showKey) "Hide" else "Show")
                                }
                            },
                            visualTransformation = if (showKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Text(
                            text = when (state.selectedProvider) {
                                AiProviderType.CUSTOM_OPENAI_COMPAT ->
                                    "Works with any OpenAI-compatible endpoint — OpenRouter, Together, Ollama (/v1), LM Studio, etc. Keys stay encrypted on this device."
                                else ->
                                    "Keys stay encrypted on this device. Get one at ${state.selectedProvider.docsUrl}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                TravelSearchSettingsSection()
            }

            state.error?.let { error ->
                item {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium, color = palette.negative)
                }
            }

            if (state.saved) {
                item {
                    Text(
                        text = "Saved — Wonder will use this from your next message.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.positive
                    )
                }
            }

            if (state.settings.isConfigured) {
                item {
                    TextButton(onClick = viewModel::clearCredentials) {
                        Text(
                            text = "Reset to Wonder built-in",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp)
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.aurora[0],
                contentColor = Color.White
            )
        ) {
            Text(text = "Save", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GemmaSetupCard(
    galleryInstalled: Boolean,
    modelAttached: Boolean,
    modelFileName: String,
    discovered: List<com.wonder.provider.ai.DiscoveredModel>,
    onOpenGallery: () -> Unit,
    onOpenWiki: () -> Unit,
    onOpenHuggingFace: () -> Unit,
    onPickModel: () -> Unit
) {
    val palette = WonderColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.cardTint)
            .border(1.dp, palette.hairline, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Set up Gemma on this phone",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Wonder does not download models for you. Use Google AI Edge Gallery to fetch a Gemma bundle, then attach the same .litertlm file here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SetupStep(
            number = 1,
            title = if (galleryInstalled) "Google AI Edge Gallery is installed" else "Install Google AI Edge Gallery",
            body = "Download Gemma 3 1B or Gemma 4 in the Gallery app — Models → + → From Hugging Face or a local file."
        )
        if (!galleryInstalled) {
            OutlinedButton(onClick = onOpenGallery, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Open in Play Store", modifier = Modifier.padding(start = 8.dp))
            }
        }

        SetupStep(
            number = 2,
            title = "Download a .litertlm bundle",
            body = "In Gallery, pick a Gemma model and wait for the download to finish. Or grab one from Hugging Face LiteRT Community."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenWiki) { Text("Gallery import guide") }
            TextButton(onClick = onOpenHuggingFace) { Text("Gemma 3 1B on HF") }
        }

        SetupStep(
            number = 3,
            title = "Attach the model file in Wonder",
            body = "Choose the same .litertlm file — usually in your Downloads folder. Wonder copies it privately; nothing is uploaded."
        )

        if (discovered.isNotEmpty()) {
            Text(
                text = "Found in Downloads: ${discovered.joinToString { it.name }}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.aurora[1]
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPickModel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.aurora[0])
            ) {
                Text("Choose model file")
            }
            if (modelAttached) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = palette.positive,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = modelFileName.ifBlank { "Attached" },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.positive,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number.toString(), style = MaterialTheme.typography.labelMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ProviderRow(
    name: String,
    hint: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val palette = WonderColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) palette.cardTint else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) palette.aurora[0].copy(alpha = 0.5f) else palette.hairline,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        Brush.linearGradient(palette.aurora.take(2))
                    } else {
                        Brush.linearGradient(listOf(palette.hairline, palette.hairline))
                    }
                )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(text = hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun TravelSearchSettingsSection() {
    val context = LocalContext.current
    val travelSettings = AppContainer.travelApiSettings
    val savedToken by travelSettings.duffelToken.collectAsState(initial = travelSettings.getDuffelToken())
    var tokenInput by remember(savedToken) { mutableStateOf(savedToken) }
    var showToken by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Live flight prices")
        Text(
            text = "Google Flights has no public API. Wonder searches live fares through Duffel — free test tokens at duffel.com. Booking.com requires a separate affiliate partnership for hotels.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = tokenInput,
            onValueChange = {
                tokenInput = it
                saved = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Duffel access token") },
            placeholder = { Text("duffel_test_…") },
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Hide" else "Show")
                }
            },
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    travelSettings.saveDuffelToken(tokenInput.trim())
                    saved = true
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save flight API")
            }
            TextButton(onClick = { openUrl(context, "https://duffel.com/docs/guides/getting-started-with-flights") }) {
                Text("Duffel docs")
            }
        }
        if (saved && tokenInput.isNotBlank()) {
            Text(
                text = "Live flight search is ready — ask Wonder for flight prices in chat.",
                style = MaterialTheme.typography.labelMedium,
                color = WonderColors.current.positive
            )
        }
    }
}
