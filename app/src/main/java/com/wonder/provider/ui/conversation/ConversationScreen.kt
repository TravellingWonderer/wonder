package com.wonder.provider.ui.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wonder.provider.data.TripRepository
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wonder.provider.model.PersonaPanel
import com.wonder.provider.model.ChatTurn
import com.wonder.provider.model.Speaker
import com.wonder.provider.model.TripMode
import com.wonder.provider.model.TurnPhase
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.wonder.provider.ui.theme.WonderColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ConversationScreen(
    onOpenExplore: () -> Unit,
    onOpenPlan: (LocalDate?, String?) -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ConversationViewModel = viewModel(
        factory = remember { conversationViewModelFactory(context) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startListening() }

    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Wandering leans on nudges, so ask for them the first time the traveller is in that mode.
    LaunchedEffect(state.mode) {
        if (state.mode == TripMode.WANDERING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val listen = {
        keyboard?.hide()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val actions = remember(state.mode) {
        CardActions(
            onOpenPlan = { onOpenPlan(null, null) },
            onOpenExpenses = onOpenExpenses,
            onOpenSettings = onOpenSettings,
            onEditItem = { itemId -> onOpenPlan(null, itemId) },
            onAddRecommendation = viewModel::addRecommendation,
            onOpenAddToTrip = viewModel::openAddToTripSheet,
            onAcceptDraft = viewModel::acceptDraft,
            onOpenDay = { date -> onOpenPlan(date, null) },
            onAddFlightOffer = viewModel::addFlightOffer
        )
    }

    val lastTurn = state.turns.lastOrNull()
    LaunchedEffect(state.turns.size, lastTurn?.text?.length?.div(24), lastTurn?.cards?.size) {
        if (state.turns.isNotEmpty()) {
            listState.animateScrollToItem(state.turns.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientBackdrop(
            modifier = Modifier.fillMaxSize(),
            alive = state.isThinking || state.isListening
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Presence(
                state = state,
                onToggleMode = viewModel::toggleMode,
                onQuieten = viewModel::stopReadingAloud,
                onOpenExplore = onOpenExplore,
                onOpenExpenses = onOpenExpenses,
                onOpenSettings = onOpenSettings
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                items(state.turns, key = { it.id }) { turn ->
                    TurnView(turn = turn, actions = actions)
                }
            }

            PersonaWhispers(
                panels = state.allPersonaPanels,
                loadingMore = state.isLoadingMoreSuggestions,
                loadingStickyPersonaId = state.loadingStickyPersonaId,
                pinningPersonaKey = state.pinningPersonaKey,
                onPick = { viewModel.send(it, spoken = false) },
                onLoadMore = viewModel::loadMoreSuggestions,
                onExpandSticky = viewModel::expandStickyPersona,
                onPin = viewModel::pinPersona,
                onUnpin = viewModel::unpinPersona,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 18.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Composer(
                    draft = state.draft,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = {
                        keyboard?.hide()
                        viewModel.sendDraft()
                    },
                    onListen = listen,
                    micAvailable = state.micAvailable,
                    enabled = !state.isThinking,
                    isListening = state.isListening,
                    voiceLevel = state.voiceLevel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        VoiceOverlay(
            visible = state.isListening,
            heardSoFar = state.heardSoFar,
            level = state.voiceLevel,
            onFinish = viewModel::stopListening,
            onCancel = viewModel::cancelListening
        )

        state.suggestionSheet?.let { sheet ->
            AddToTripSheet(
                state = sheet,
                onDismiss = viewModel::dismissAddToTripSheet,
                onAdd = { pick, date ->
                    viewModel.addRecommendation(pick, date)
                    viewModel.dismissAddToTripSheet()
                }
            )
        }
    }
}

/**
 * The header carries the trip, not navigation: which mode you're in, where you are in the days,
 * and what you've spent. Tapping the money line opens the tracker.
 */
@Composable
private fun Presence(
    state: ConversationUiState,
    onToggleMode: () -> Unit,
    onQuieten: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = WonderColors.current
    val connectedModel = state.connectedModel

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AmbientOrb(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .then(if (state.isReadingAloud) Modifier.clickable(onClick = onQuieten) else Modifier),
                thinking = state.isThinking,
                speaking = state.isReadingAloud
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.tripTitle.ifBlank { "Wonder" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                val statusLine = when {
                    state.isThinking -> "thinking"
                    state.isReadingAloud -> "speaking · tap to quieten"
                    else -> state.dayLabel
                }
                if (statusLine.isNotBlank()) {
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (connectedModel != null) {
                    Text(
                        text = connectedModel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }

            ModeChip(mode = state.mode, onClick = onToggleMode)

            Icon(
                imageVector = Icons.Outlined.Explore,
                contentDescription = "Explore",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenExplore)
                    .padding(8.dp)
                    .size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = "Model settings",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSettings)
                    .padding(8.dp)
                    .size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // The money line only earns its place once the trip is underway and spending is real.
        val budget = state.budget
        if (budget != null && state.mode == TripMode.WANDERING) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenExpenses)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = TripRepository.format(budget.spent, budget.currency),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "of ${TripRepository.format(budget.budget, budget.currency)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (budget.isOverrun) {
                            "· ${TripRepository.format(-budget.variance, budget.currency)} over"
                        } else {
                            "· ${TripRepository.format(budget.variance, budget.currency)} spare"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (budget.isOverrun) palette.negative else palette.positive
                    )
                }
                BudgetBar(budget)
            }
        }
    }
}

@Composable
private fun ModeChip(mode: TripMode, onClick: () -> Unit) {
    val palette = WonderColors.current
    val wandering = mode == TripMode.WANDERING
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (wandering) {
                    palette.aurora[0].copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(onClick = onClick)
            .padding(start = 11.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = mode.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (wandering) palette.aurora[0] else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = "Switch mode",
            modifier = Modifier.size(14.dp),
            tint = if (wandering) palette.aurora[0] else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TurnView(turn: ChatTurn, actions: CardActions) {
    when (turn.speaker) {
        Speaker.YOU -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            val palette = WonderColors.current
            Text(
                text = turn.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                    .background(palette.userBubble)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onUserBubble
            )
        }

        Speaker.WONDER -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (turn.phase == TurnPhase.THINKING) {
                ThinkingDots()
            } else {
                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            turn.cards.forEachIndexed { index, card ->
                val appearance = remember(turn.id, index) {
                    MutableTransitionState(false).apply { targetState = true }
                }
                AnimatedVisibility(
                    visibleState = appearance,
                    enter = fadeIn(tween(420, delayMillis = index * 90)) +
                        slideInVertically(tween(420, delayMillis = index * 90)) { it / 6 }
                ) {
                    AgentCardView(card = card, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val pulse by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(pulse)
                    .clip(CircleShape)
                    .background(WonderColors.current.aurora[index % 3])
            )
        }
    }
}

/**
 * Persona avatars above the composer — tap to expand questions inline in the chat pane.
 */
@Composable
private fun PersonaWhispers(
    panels: List<PersonaPanel>,
    loadingMore: Boolean,
    loadingStickyPersonaId: String?,
    pinningPersonaKey: String?,
    onPick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onExpandSticky: (String) -> Unit,
    onPin: (PersonaPanel) -> Unit,
    onUnpin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedPersona by remember { mutableStateOf<String?>(null) }

    AnimatedVisibility(
        visible = panels.isNotEmpty() || loadingMore,
        enter = fadeIn(tween(400)) + slideInVertically { it / 3 },
        exit = fadeOut(tween(200))
    ) {
        Column(
            modifier = modifier.padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                panels.forEach { panel ->
                    PersonaAvatarButton(
                        panel = panel,
                        selected = expandedPersona == panel.persona,
                        onClick = {
                            if (panel.isSticky) {
                                panel.customPersonaId?.let(onExpandSticky)
                            }
                            expandedPersona = if (expandedPersona == panel.persona) null else panel.persona
                        }
                    )
                }
                MoreIdeasAvatar(loading = loadingMore, onClick = onLoadMore)
                Spacer(modifier = Modifier.width(6.dp))
            }

            AnimatedVisibility(
                visible = expandedPersona != null,
                enter = fadeIn(tween(260, delayMillis = 40)) +
                    slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { it / 3 }) + scaleOut(targetScale = 0.95f)
            ) {
                val panel = panels.find { it.persona == expandedPersona }
                if (panel != null) {
                    val loading = panel.isSticky &&
                        (panel.customPersonaId == loadingStickyPersonaId || panel.persona == pinningPersonaKey)
                    PersonaExpandedPanel(
                        panel = panel,
                        loading = loading,
                        onPick = { question ->
                            expandedPersona = null
                            onPick(question)
                        },
                        onPin = { onPin(panel) },
                        onUnpin = { panel.customPersonaId?.let(onUnpin) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaAvatarButton(
    panel: PersonaPanel,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    val style = PersonaAvatars.styleFor(panel)
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "personaSelectedScale"
    )
    val borderColor = when {
        selected -> style.tint
        panel.isSticky -> style.tint.copy(alpha = 0.55f)
        else -> palette.hairline
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = bounce.value * selectedScale
                scaleY = bounce.value * selectedScale
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = CircleShape)
                .background(
                    if (selected) style.tint.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
                .clickable {
                    scope.launch {
                        bounce.animateTo(0.82f, spring(stiffness = Spring.StiffnessHigh))
                        bounce.animateTo(1.08f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            if (!style.emoji.isNullOrBlank()) {
                Text(
                    text = style.emoji,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Icon(
                    imageVector = style.icon,
                    contentDescription = panel.persona,
                    tint = style.tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (panel.isSticky) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(style.tint)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned persona",
                    tint = Color.White,
                    modifier = Modifier.size(8.dp)
                )
            }
        }
    }
}

@Composable
private fun PersonaExpandedPanel(
    panel: PersonaPanel,
    loading: Boolean,
    onPick: (String) -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit
) {
    val palette = WonderColors.current
    val style = PersonaAvatars.styleFor(panel)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        style.tint.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    )
                )
            )
            .border(1.dp, style.tint.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = panel.persona,
                    style = MaterialTheme.typography.labelLarge,
                    color = style.tint
                )
                if (panel.isSticky && panel.personaDescription != null) {
                    Text(
                        text = panel.personaDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                } else if (!panel.isSticky) {
                    Text(
                        text = "Tap a question — or pin this voice to keep it on this trip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = if (panel.isSticky) "Unpin persona" else "Pin persona",
                tint = if (panel.isSticky) style.tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !loading) {
                        if (panel.isSticky) onUnpin() else onPin()
                    }
                    .padding(6.dp)
                    .size(18.dp)
            )
        }

        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            },
            label = "personaQuestions"
        ) { isLoading ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = style.tint
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    panel.questions.forEachIndexed { index, question ->
                        val accent = palette.aurora[index % palette.aurora.size]
                        Text(
                            text = question,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(question) }
                                .background(accent.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreIdeasAvatar(loading: Boolean, onClick: () -> Unit) {
    val palette = WonderColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, palette.hairline, CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "More personas",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
