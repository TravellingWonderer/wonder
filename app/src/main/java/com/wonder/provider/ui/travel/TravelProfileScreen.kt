package com.wonder.provider.ui.travel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wonder.provider.AppContainer
import com.wonder.provider.ui.conversation.AmbientOrb
import com.wonder.provider.ui.theme.WonderColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TravelProfileScreen(onBack: () -> Unit) {
    val viewModel: TravelProfileViewModel = viewModel(factory = TravelProfileViewModelFactory())
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = WonderColors.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(viewModel) {
        viewModel.bindSignInBridge()
        onDispose { viewModel.unbindSignInBridge() }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.needsDriveConsent) {
        if (state.needsDriveConsent) {
            viewModel.onDriveConsentLaunched()
            viewModel.launchGoogleSignIn(forDriveSync = true)
        }
    }

    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFile(uri)
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
                contentDescription = "Back",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(12.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        state.status?.let { status ->
            StatusBanner(
                message = status.message,
                isError = status.isError,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
            )
        }

        if (state.isWorking) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 22.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Working…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmbientOrb(modifier = Modifier.size(64.dp))
                    Text(
                        text = "Your travel history",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Connect Google Maps so Wonder knows where you've been and what you tend to enjoy. Everything stays on this phone.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                val signedInEmail = state.signedInEmail ?: state.summary?.googleAccountEmail
                val signedInName = state.signedInName ?: state.summary?.googleDisplayName
                InfoCard(
                    title = if (signedInEmail != null) "Signed in" else "Google account",
                    body = signedInEmail?.let { email ->
                        buildString {
                            signedInName?.let { append("$it · ") }
                            append(email)
                        }
                    } ?: "Sign in to search your Google Drive for Timeline or Takeout exports."
                )
            }

            if (!state.oauthConfigured) {
                item {
                    InfoCard(
                        title = "Drive sync needs Google Cloud setup",
                        body = "This debug build has no OAuth client configured, so account sign-in may not complete. " +
                            "Import Timeline file works right now without sign-in."
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.launchGoogleSignIn(forDriveSync = false) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !state.isWorking
                    ) {
                        Text("Sign in with Google")
                    }

                    OutlinedButton(
                        onClick = { viewModel.beginDriveSync() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !state.isWorking && state.isSignedIn
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Sync from Google Drive", modifier = Modifier.padding(start = 8.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            importFile.launch(
                                arrayOf(
                                    "application/json",
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !state.isWorking
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Import Timeline file", modifier = Modifier.padding(start = 8.dp))
                    }

                    Text(
                        text = "From Google Maps: Settings → Personal content → Export Timeline data. " +
                            "Or use takeout.google.com → Location History. Pick the .json or .zip here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.summary?.takeIf { it.hasData }?.let { summary ->
                item {
                    InfoCard(
                        title = "What Wonder learned",
                        body = buildString {
                            append("${summary.totalVisits} visits across ${summary.cityCount} cities.\n")
                            if (summary.topCities.isNotEmpty()) {
                                append("Knows: ${summary.topCities.take(5).joinToString(", ")}\n")
                            }
                            if (summary.topCategories.isNotEmpty()) {
                                append("Often visits: ${summary.topCategories.joinToString(", ")}\n")
                            }
                            if (summary.lastImportedAtEpochMillis > 0L) {
                                val whenLabel = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
                                    .format(
                                        Instant.ofEpochMilli(summary.lastImportedAtEpochMillis)
                                            .atZone(ZoneId.systemDefault())
                                    )
                                append("Last updated $whenLabel")
                            }
                        }
                    )
                }
            }

            if (state.isSignedIn || state.summary?.hasData == true) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.isSignedIn) {
                            TextButton(onClick = viewModel::signOut) {
                                Text("Sign out")
                            }
                        }
                        if (state.summary?.hasData == true) {
                            TextButton(onClick = viewModel::clearHistory) {
                                Text("Clear local history")
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Google does not expose Timeline through a public API. Wonder imports your export file or searches Drive for Takeout archives you already saved there.",
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusBanner(message: String, isError: Boolean, modifier: Modifier = Modifier) {
    val palette = WonderColors.current
    val background = if (isError) palette.negative.copy(alpha = 0.12f) else palette.positive.copy(alpha = 0.12f)
    val border = if (isError) palette.negative.copy(alpha = 0.35f) else palette.positive.copy(alpha = 0.35f)
    val textColor = if (isError) palette.negative else palette.positive

    Text(
        text = message,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    val palette = WonderColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardTint)
            .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
