package com.wonder.provider.ui.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wonder.provider.ui.theme.WonderColors

/**
 * Voice-first input: a centred mic is the main control; typing is tucked underneath for when
 * speaking isn't practical.
 */
@Composable
fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onListen: () -> Unit,
    micAvailable: Boolean,
    enabled: Boolean,
    isListening: Boolean = false,
    voiceLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    val hasDraft = draft.isNotBlank()
    var showTyping by rememberSaveable { mutableStateOf(hasDraft) }

    if (hasDraft) showTyping = true

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PrimaryVoiceButton(
            micAvailable = micAvailable,
            enabled = enabled && !isListening,
            isListening = isListening,
            voiceLevel = voiceLevel,
            onListen = onListen
        )

        Text(
            text = when {
                isListening -> "Listening…"
                !micAvailable -> "Speech not available on this device"
                else -> "Tap to speak to Wonder"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (showTyping || hasDraft) {
            SecondaryTextComposer(
                draft = draft,
                onDraftChange = onDraftChange,
                onSend = onSend,
                enabled = enabled,
                hasDraft = hasDraft
            )
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = enabled) { showTyping = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Type instead",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrimaryVoiceButton(
    micAvailable: Boolean,
    enabled: Boolean,
    isListening: Boolean,
    voiceLevel: Float,
    onListen: () -> Unit
) {
    val palette = WonderColors.current
    val pulse = rememberInfiniteTransition(label = "mic-pulse")
    val idlePulse by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic-idle-pulse"
    )
    val levelScale by animateFloatAsState(
        targetValue = 1f + voiceLevel * 0.22f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "mic-level"
    )
    val scale = when {
        isListening -> levelScale
        enabled && micAvailable -> idlePulse
        else -> 1f
    }

    Box(contentAlignment = Alignment.Center) {
        if (enabled && micAvailable && !isListening) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(scale * 1.08f)
                    .clip(CircleShape)
                    .background(palette.aurora[0].copy(alpha = 0.14f))
            )
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    if (micAvailable && (enabled || isListening)) {
                        Brush.linearGradient(palette.aurora.take(2))
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                )
                .clickable(enabled = enabled && micAvailable && !isListening, onClick = onListen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    !micAvailable -> Icons.Default.GraphicEq
                    isListening -> Icons.Default.GraphicEq
                    else -> Icons.Default.Mic
                },
                contentDescription = "Speak to Wonder",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun SecondaryTextComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    hasDraft: Boolean
) {
    val palette = WonderColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(1.dp, palette.hairline.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = draft,
                onValueChange = { value ->
                    if (value.contains('\n')) {
                        val spoken = value.replace("\n", "")
                        onDraftChange(spoken)
                        if (enabled && spoken.isNotBlank()) onSend()
                    } else {
                        onDraftChange(value)
                    }
                },
                modifier = Modifier
                    .heightIn(min = 36.dp, max = 96.dp)
                    .padding(vertical = 8.dp),
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                ),
                cursorBrush = SolidColor(palette.aurora[0]),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() })
            )
            if (draft.isEmpty()) {
                Text(
                    text = "Type a message…",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }

        AnimatedContent(
            targetState = hasDraft,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 0.7f))
            },
            label = "secondary-send"
        ) { showSend ->
            if (showSend) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = enabled, onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
