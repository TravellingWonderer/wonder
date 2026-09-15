package com.wonder.provider.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp

/**
 * Talk mode. The rest of the app recedes and Wonder becomes a single listening presence —
 * a tap anywhere hands the utterance over.
 */
@Composable
fun VoiceOverlay(
    visible: Boolean,
    heardSoFar: String,
    level: Float,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val orbScale by animateFloatAsState(
            targetValue = 1f + level * 0.18f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
            label = "voice-orb"
        )
        val interaction = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onFinish
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                AmbientOrb(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(orbScale),
                    energy = level,
                    speaking = true
                )

                Text(
                    text = heardSoFar.ifBlank { "Listening…" },
                    style = if (heardSoFar.isBlank()) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    color = if (heardSoFar.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = if (heardSoFar.isBlank()) "Tap to cancel" else "Tap anywhere when you're done",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { if (heardSoFar.isBlank()) onCancel() else onFinish() }
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
