package com.wonder.provider.ui.explore

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.wonder.provider.AppContainer
import com.wonder.provider.data.NearbyLocationStatus
import com.wonder.provider.data.TripRepository
import com.wonder.provider.model.ExploreFeed
import com.wonder.provider.model.ExplorePlacePick
import com.wonder.provider.model.ExploreFeedKind
import com.wonder.provider.model.FoodFavorite
import com.wonder.provider.model.LocalGuide
import com.wonder.provider.model.LocalTake
import com.wonder.provider.model.SmallTripIdea
import com.wonder.provider.model.TripArchiveStatus
import com.wonder.provider.model.ItemKind
import com.wonder.provider.model.ItemStatus
import com.wonder.provider.model.ItineraryItem
import com.wonder.provider.model.Recommendation
import com.wonder.provider.model.TripIdea
import com.wonder.provider.model.TripSummary
import com.wonder.provider.ui.conversation.AddToTripSheet
import com.wonder.provider.ui.conversation.AddToTripSheetState
import com.wonder.provider.ui.conversation.AmbientBackdrop
import com.wonder.provider.ui.conversation.AmbientOrb
import com.wonder.provider.ui.theme.WonderColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val GATHERED_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val TRIP_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@Composable
fun ExploreScreen(
    onOpenTrip: () -> Unit,
    onOpenTripOverview: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPersonas: () -> Unit,
    onOpenNewTrip: () -> Unit
) {
    val explore = AppContainer.explore
    val trips = AppContainer.trips
    val scope = rememberCoroutineScope()
    val feed by explore.feed.collectAsStateWithLifecycle()
    val refreshing by explore.isRefreshing.collectAsStateWithLifecycle()
    val trip by trips.trip.collectAsStateWithLifecycle()
    val catalog by trips.catalog.collectAsStateWithLifecycle()
    val planned = catalog.filter { it.archiveStatus == TripArchiveStatus.PLANNED }
    val archived = catalog.filter { it.archiveStatus == TripArchiveStatus.ARCHIVED }
    var showArchived by remember { mutableStateOf(false) }
    var tripToDelete by remember { mutableStateOf<TripSummary?>(null) }
    var directionsPick by remember { mutableStateOf<ExplorePlacePick?>(null) }
    var addToTripSheet by remember { mutableStateOf<AddToTripSheetState?>(null) }
    var pendingTripIdea by remember { mutableStateOf<TripIdea?>(null) }
    val hasActiveTrip = catalog.any { it.isActive }
    val nearbyExplore = AppContainer.nearbyExplore
    val nearbyFeed by nearbyExplore.feed.collectAsStateWithLifecycle()
    val nearbyPlace by nearbyExplore.placeLabel.collectAsStateWithLifecycle()
    val nearbyRefreshing by nearbyExplore.isRefreshing.collectAsStateWithLifecycle()
    val nearbyStatus by nearbyExplore.status.collectAsStateWithLifecycle()
    var browsingNearby by remember { mutableStateOf(!hasActiveTrip) }
    val isNearbyMode = browsingNearby || !hasActiveTrip
    val tripNeedsCity = hasActiveTrip && !isNearbyMode &&
        !TripRepository.hasDecidedDestination(trip.destination)
    val displayFeed = when {
        isNearbyMode -> nearbyFeed
        tripNeedsCity -> null
        hasActiveTrip -> feed
        else -> null
    }
    val displayRefreshing = if (isNearbyMode) nearbyRefreshing else refreshing
    val feedFreshToday = displayFeed?.isFreshFor() == true

    LaunchedEffect(hasActiveTrip) {
        if (!hasActiveTrip) browsingNearby = true
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch { nearbyExplore.ensureFeed(force = true) }
        } else {
            scope.launch { nearbyExplore.ensureFeed(force = false) }
        }
    }

    fun activateNearby(forceRefresh: Boolean = true) {
        browsingNearby = true
        if (nearbyExplore.hasLocationPermission()) {
            if (forceRefresh) nearbyExplore.refresh()
            else scope.launch { nearbyExplore.ensureFeed(force = false) }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(hasActiveTrip, trip.id, browsingNearby, lifecycleOwner) {
        // Only generate while Explore is on-screen — opening a trip must not kick AI.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (hasActiveTrip && !browsingNearby) explore.ensureFeed(force = false)
        }
    }

    LaunchedEffect(isNearbyMode) {
        if (isNearbyMode) {
            activateNearby(forceRefresh = false)
        }
    }

    fun openAddIdeaToTrip(idea: TripIdea) {
        pendingTripIdea = idea
        addToTripSheet = AddToTripSheetState(
            pick = idea.toRecommendation(),
            currency = trip.currency,
            tripDates = trip.dates.filter { date -> !date.isBefore(LocalDate.now()) }
                .ifEmpty { trip.dates }
        )
    }

    fun addIdeaToTrip(pick: Recommendation, date: LocalDate) {
        val idea = pendingTripIdea
        trips.upsertItem(
            ItineraryItem(
                id = trips.newItemId(),
                date = date,
                title = pick.title,
                kind = pick.kind,
                durationMinutes = pick.durationMinutes,
                location = idea?.destination?.takeIf { it.isNotBlank() }
                    ?: trip.destination.substringBefore(","),
                notes = idea?.summary ?: pick.tip,
                estimatedCost = pick.estimatedCost,
                costIsPerPerson = true,
                status = ItemStatus.IDEA,
                travellerIds = trip.travellers.map { it.id }.toSet()
            )
        )
        pendingTripIdea = null
        addToTripSheet = null
    }

    fun deleteTrip(summary: TripSummary) {
        AppContainer.wonderAgent.clearSession(summary.id)
        trips.deleteTripAsync(summary.id)
        tripToDelete = null
    }

    directionsPick?.let { pick ->
        ExploreDirectionsSheet(
            pick = pick,
            onDismiss = { directionsPick = null }
        )
    }

    addToTripSheet?.let { sheet ->
        AddToTripSheet(
            state = sheet,
            onDismiss = {
                addToTripSheet = null
                pendingTripIdea = null
            },
            onAdd = { pick, date -> addIdeaToTrip(pick, date) }
        )
    }

    tripToDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            title = { Text("Delete ${summary.title}?") },
            text = {
                Text(
                    text = if (summary.isActive) {
                        "This trip and everything in it will be gone. Wonder will switch to your next trip."
                    } else {
                        "This trip and everything in it will be gone permanently."
                    } + if (catalog.size == 1) {
                        " You'll see local ideas near you until you plan a new trip."
                    } else {
                        ""
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteTrip(summary) }) {
                    Text("Delete", color = WonderColors.current.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) { Text("Keep") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AmbientBackdrop(modifier = Modifier.fillMaxSize(), alive = displayRefreshing)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
                item(key = "header") {
                    ExploreHeader(
                        tripTitle = when {
                            isNearbyMode -> "Explore nearby"
                            hasActiveTrip -> trip.title
                            else -> "No trip yet"
                        },
                        destination = when {
                            isNearbyMode -> nearbyPlace ?: "Finding your location…"
                            hasActiveTrip -> trip.destination
                            else -> "Create one to get started"
                        },
                        coverEmoji = when {
                            isNearbyMode -> "📍"
                            hasActiveTrip -> trip.coverEmoji
                            else -> "✈️"
                        },
                        gatheredLabel = displayFeed?.gatheredLabel().orEmpty(),
                        sourceLabel = displayFeed?.sourceLabel.orEmpty(),
                        refreshing = displayRefreshing,
                        refreshEnabled = !displayRefreshing && (
                            (isNearbyMode && !feedFreshToday) ||
                                (!isNearbyMode && hasActiveTrip && !tripNeedsCity && !feedFreshToday)
                        ),
                        onRefresh = {
                            if (isNearbyMode) nearbyExplore.refresh()
                            else if (hasActiveTrip && !tripNeedsCity && !feedFreshToday) explore.refresh()
                        },
                        heroSubtitle = when {
                            isNearbyMode -> "Ideas near you · from your GPS"
                            hasActiveTrip && tripNeedsCity -> "Pick a city to unlock ideas"
                            hasActiveTrip -> "Your trip · talk to Wonder"
                            else -> "Your trip · talk to Wonder"
                        },
                        onHeroClick = {
                            when {
                                isNearbyMode -> activateNearby(forceRefresh = true)
                                hasActiveTrip -> onOpenTrip()
                                else -> onOpenNewTrip()
                            }
                        },
                        showChatIcon = hasActiveTrip && !isNearbyMode,
                        onOpenSettings = onOpenSettings,
                        onOpenPersonas = onOpenPersonas
                    )
                }

                if (isNearbyMode && nearbyStatus == NearbyLocationStatus.PERMISSION_DENIED) {
                    item(key = "location-nudge") {
                        LocationPermissionNudge(
                            onRequestPermission = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        )
                    }
                }

                if (planned.isNotEmpty() || !hasActiveTrip || isNearbyMode) {
                    item(key = "your-trips-label") {
                        SectionHeader(
                            title = "Your trips",
                            subtitle = if (hasActiveTrip) {
                                "Tap a trip for the map — or Explore nearby for local ideas"
                            } else {
                                "Name a trip to start planning"
                            }
                        )
                    }
                    item(key = "your-trips-row") {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ExploreNearbyChip(
                                selected = isNearbyMode,
                                loading = isNearbyMode && nearbyRefreshing,
                                placeLabel = nearbyPlace,
                                onClick = { activateNearby(forceRefresh = true) }
                            )
                            planned.forEach { summary ->
                                TripPickerChip(
                                    summary = summary,
                                    selected = !isNearbyMode && summary.isActive,
                                    onClick = {
                                        browsingNearby = false
                                        if (!summary.isActive) {
                                            trips.switchActiveTripAsync(summary.id)
                                        }
                                        onOpenTripOverview(summary.id)
                                    },
                                    onDelete = { tripToDelete = summary }
                                )
                            }
                            NewTripChip(
                                loading = false,
                                onClick = onOpenNewTrip
                            )
                        }
                    }
                }

                if (hasActiveTrip || isNearbyMode) {
                if (tripNeedsCity) {
                    item(key = "needs-city") {
                        NeedsCityPrompt(
                            onOpenTrip = onOpenTrip,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                } else if (displayFeed == null && displayRefreshing) {
                    item(key = "feed-loading") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(
                                text = if (isNearbyMode) {
                                    "Finding places near ${nearbyPlace ?: "you"}…"
                                } else {
                                    "Gathering ideas for ${trip.destination.split(",").first()}…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                displayFeed?.let { content ->
                    val areaContext = when {
                        isNearbyMode -> nearbyPlace ?: content.destination
                        trip.destination != TripRepository.DEFAULT_DESTINATION -> trip.destination
                        else -> content.destination
                    }
                    ExploreFeedSections(
                        content = content,
                        areaContext = areaContext,
                        nearbyMode = isNearbyMode,
                        onTripIdeaClick = if (isNearbyMode) null else ({ openAddIdeaToTrip(it) }),
                        onDirections = { directionsPick = it }
                    )
                }
                }
                }

                if (archived.isNotEmpty()) {
                    item(key = "archived-label") {
                        SectionHeader(
                            title = "Archived trips",
                            subtitle = if (showArchived) {
                                "Finished trips — read-only"
                            } else {
                                "${archived.size} past ${if (archived.size == 1) "trip" else "trips"}"
                            }
                        )
                    }
                    item(key = "archived-toggle") {
                        Text(
                            text = if (showArchived) "Hide archived" else "Show archived",
                            modifier = Modifier
                                .padding(horizontal = 22.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showArchived = !showArchived }
                                .padding(vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (showArchived) {
                        items(archived, key = { it.id }) { summary ->
                            ArchivedTripRow(
                                summary = summary,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                                onClick = { onOpenTripOverview(summary.id) },
                                onDelete = { tripToDelete = summary }
                            )
                        }
                    }
                }
            }
        }
    }

private fun TripIdea.toRecommendation(): Recommendation = Recommendation(
    id = id,
    title = title,
    why = vibe,
    emoji = emoji,
    kind = ItemKind.ACTIVITY,
    estimatedCost = 0.0,
    durationMinutes = (durationDays.coerceAtLeast(1) * 180).coerceAtMost(480),
    tip = summary
)

private fun ExploreFeed.gatheredLabel(): String {
    val date = Instant.ofEpochMilli(gatheredAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val prefix = when (feedKind) {
        ExploreFeedKind.LIVE_TRIP -> "Today's picks"
        ExploreFeedKind.NEARBY -> "Near you"
        ExploreFeedKind.DISCOVERY -> "Gathered"
    }
    return "$prefix ${GATHERED_FORMAT.format(date)}"
}

private fun LazyListScope.ExploreFeedSections(
    content: ExploreFeed,
    areaContext: String,
    nearbyMode: Boolean,
    onTripIdeaClick: ((TripIdea) -> Unit)?,
    onDirections: (ExplorePlacePick) -> Unit
) {
    val context = areaContext
    item(key = "ideas-label") {
        SectionHeader(
            title = if (nearbyMode) "Things to do" else "Trip ideas",
            subtitle = if (nearbyMode) {
                "Picked for where you are right now"
            } else {
                "Tap an idea to add it to this trip"
            }
        )
    }
    item(key = "ideas-row") {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content.tripIdeas.forEach { idea ->
                TripIdeaCard(
                    idea = idea,
                    modifier = Modifier.width(280.dp),
                    onClick = onTripIdeaClick?.let { click -> { click(idea) } },
                    onDirections = {
                        onDirections(
                            ExplorePlacePick(
                                title = idea.title,
                                query = "${idea.title}, ${idea.destination}",
                                context = context
                            )
                        )
                    }
                )
            }
        }
    }

    item(key = "food-label") {
        SectionHeader(
            title = "Local food",
            subtitle = if (nearbyMode) "Where to eat around here" else "Where residents actually eat"
        )
    }
    items(content.foodFavorites, key = { it.id }) { food ->
        FoodCard(
            food = food,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            onDirections = {
                onDirections(
                    ExplorePlacePick(
                        title = food.name,
                        query = "${food.name}, ${food.place}",
                        context = context
                    )
                )
            }
        )
    }

    item(key = "guides-label") {
        SectionHeader(
            title = "Local guides",
            subtitle = "People who know the back streets"
        )
    }
    item(key = "guides-row") {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content.localGuides.forEach { guide ->
                GuideCard(
                    guide = guide,
                    modifier = Modifier.width(240.dp),
                    onDirections = {
                        onDirections(
                            ExplorePlacePick(
                                title = guide.name,
                                query = "${guide.specialty}, $context",
                                context = context
                            )
                        )
                    }
                )
            }
        }
    }

    item(key = "small-label") {
        SectionHeader(
            title = if (nearbyMode) "Half-day ideas" else "Small trip ideas",
            subtitle = if (nearbyMode) "Easy escapes from where you are" else "Half-days and easy escapes nearby"
        )
    }
    items(content.smallTrips, key = { it.id }) { small ->
        SmallTripCard(
            trip = small,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            onDirections = {
                onDirections(
                    ExplorePlacePick(
                        title = small.title,
                        query = "${small.title}, ${small.location}",
                        context = context
                    )
                )
            }
        )
    }

    item(key = "takes-label") {
        SectionHeader(
            title = "Ask a local",
            subtitle = "What residents really think"
        )
    }
    items(content.localTakes, key = { it.id }) { take ->
        LocalTakeCard(
            take = take,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            onDirections = {
                onDirections(
                    ExplorePlacePick(
                        title = take.place,
                        query = "${take.place}, $context",
                        context = context
                    )
                )
            }
        )
    }
}

@Composable
private fun ExploreHeader(
    tripTitle: String,
    destination: String,
    coverEmoji: String,
    gatheredLabel: String,
    sourceLabel: String,
    refreshing: Boolean,
    refreshEnabled: Boolean,
    onRefresh: () -> Unit,
    heroSubtitle: String,
    onHeroClick: () -> Unit,
    showChatIcon: Boolean,
    onOpenSettings: () -> Unit,
    onOpenPersonas: () -> Unit
) {
    val palette = WonderColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AmbientOrb(modifier = Modifier.size(34.dp), thinking = refreshing, speaking = false)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Explore",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = listOfNotNull(
                        destination.split(",").first().trim().ifBlank { null },
                        gatheredLabel.ifBlank { null },
                        sourceLabel.ifBlank { null }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Outlined.Face,
                contentDescription = "Personas",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenPersonas)
                    .padding(8.dp)
                    .size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = if (refreshEnabled) "Refresh ideas" else "Fresh for today",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = refreshEnabled, onClick = onRefresh)
                    .padding(8.dp)
                    .size(19.dp),
                tint = if (refreshEnabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                }
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

        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(palette.aurora.take(2)))
                .clickable(onClick = onHeroClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = coverEmoji, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tripTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = heroSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            if (showChatIcon) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Open trip",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun NeedsCityPrompt(
    onOpenTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = WonderColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.cardTint)
            .border(1.dp, palette.hairline, RoundedCornerShape(22.dp))
            .clickable(onClick = onOpenTrip)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Trip ideas",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Fill up at least one city inside the trip to generate ideas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Ask Wonder to set a destination — then picks will show up here.",
            style = MaterialTheme.typography.labelMedium,
            color = palette.aurora[0]
        )
    }
}

@Composable
private fun LocationPermissionNudge(onRequestPermission: () -> Unit) {
    val palette = WonderColors.current
    Row(
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.cardTint)
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onRequestPermission)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Turn on location",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Wonder uses GPS to show food, things to do, and ideas near you",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExploreCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = WonderColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.cardTint)
            .border(1.dp, palette.hairline, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun TripPickerChip(
    summary: TripSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = WonderColors.current
    Box(
        modifier = Modifier.width(132.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) {
                        Brush.linearGradient(
                            listOf(
                                palette.aurora[0].copy(alpha = 0.18f),
                                palette.aurora[1].copy(alpha = 0.10f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(palette.cardTint, palette.cardTint)
                        )
                    }
                )
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) palette.aurora[0] else palette.hairline,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onClick)
                .padding(12.dp)
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = summary.coverEmoji, style = MaterialTheme.typography.titleMedium)
            Text(
                text = summary.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2
            )
            Text(
                text = "${TRIP_DATE_FORMAT.format(summary.startDate)} – ${TRIP_DATE_FORMAT.format(summary.endDate)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Delete trip",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .clickable(onClick = onDelete)
                .padding(6.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun ExploreNearbyChip(
    selected: Boolean,
    loading: Boolean,
    placeLabel: String?,
    onClick: () -> Unit
) {
    val palette = WonderColors.current
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (selected) {
                    Modifier
                        .background(Brush.linearGradient(palette.aurora))
                        .border(1.5.dp, Color.Transparent, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                        .background(palette.cardTint)
                        .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = if (selected) Color.White else MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = if (selected) Color.White else palette.aurora[0],
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "Explore nearby",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2
        )
        Text(
            text = when {
                loading -> "Locating…"
                !placeLabel.isNullOrBlank() -> placeLabel
                else -> "Use GPS"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White.copy(alpha = 0.88f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun NewTripChip(loading: Boolean, onClick: () -> Unit) {
    val palette = WonderColors.current
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = "New trip",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            Text(
                text = "Start planning",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ArchivedTripRow(
    summary: TripSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ExploreCard(modifier.clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = summary.coverEmoji, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${summary.destination.split(",").first()} · ended ${TRIP_DATE_FORMAT.format(summary.endDate)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Delete trip",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun ExploreDirectionsButton(onClick: () -> Unit) {
    val palette = WonderColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.aurora[0].copy(alpha = 0.12f))
            .border(1.dp, palette.aurora[0].copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Directions,
            contentDescription = null,
            tint = palette.aurora[0],
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "Map & directions",
            style = MaterialTheme.typography.labelLarge,
            color = palette.aurora[0],
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TripIdeaCard(
    idea: TripIdea,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDirections: () -> Unit
) {
    ExploreCard(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = idea.emoji, style = MaterialTheme.typography.titleLarge)
            Text(
                text = idea.vibe.uppercase(Locale.ENGLISH),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = idea.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = idea.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "${idea.durationDays} days",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = idea.budgetHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ExploreDirectionsButton(onClick = onDirections)
    }
}

@Composable
private fun FoodCard(
    food: FoodFavorite,
    modifier: Modifier = Modifier,
    onDirections: () -> Unit
) {
    ExploreCard(modifier) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = food.emoji, style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = food.place,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = food.description,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = food.priceHint,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        ExploreDirectionsButton(onClick = onDirections)
    }
}

@Composable
private fun GuideCard(
    guide: LocalGuide,
    modifier: Modifier = Modifier,
    onDirections: () -> Unit
) {
    ExploreCard(modifier) {
        Text(text = guide.emoji, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = guide.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = guide.specialty,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = guide.bio,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (guide.tip.isNotBlank()) {
            Text(
                text = "Tip · ${guide.tip}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        ExploreDirectionsButton(onClick = onDirections)
    }
}

@Composable
private fun SmallTripCard(
    trip: SmallTripIdea,
    modifier: Modifier = Modifier,
    onDirections: () -> Unit
) {
    ExploreCard(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = trip.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(text = trip.emoji, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = trip.duration,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = trip.location,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = trip.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExploreDirectionsButton(onClick = onDirections)
    }
}

@Composable
private fun LocalTakeCard(
    take: LocalTake,
    modifier: Modifier = Modifier,
    onDirections: () -> Unit
) {
    val palette = WonderColors.current
    ExploreCard(modifier) {
        Text(
            text = take.place,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = take.category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "\"${take.opinion}\"",
            modifier = Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.cardTint.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = take.residentName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            if (take.residentDetail.isNotBlank()) {
                Text(
                    text = "· ${take.residentDetail}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ExploreDirectionsButton(onClick = onDirections)
    }
}
