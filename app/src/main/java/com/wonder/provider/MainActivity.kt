package com.wonder.provider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wonder.provider.navigation.Screen
import com.wonder.provider.ui.conversation.ConversationScreen
import com.wonder.provider.ui.explore.ExploreScreen
import com.wonder.provider.ui.explore.NewTripScreen
import com.wonder.provider.ui.expenses.ExpensesScreen
import com.wonder.provider.ui.plan.PlanScreen
import com.wonder.provider.ui.screens.AiSettingsScreen
import com.wonder.provider.ui.personas.PersonaLibraryScreen
import com.wonder.provider.ui.tripoverview.TripOverviewScreen
import com.wonder.provider.ui.theme.WonderTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppContainer.googleSignInBridge.onActivityResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(this)
        AppContainer.googleSignInBridge.attach { intent ->
            googleSignInLauncher.launch(intent)
        }
        enableEdgeToEdge()
        setContent {
            WonderTheme {
                WonderApp()
            }
        }
    }
}

@Composable
fun WonderApp() {
    val navController = rememberNavController()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Explore.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Explore.route) {
                ExploreScreen(
                    onOpenTrip = { navController.navigate(Screen.Conversation.route) },
                    onOpenTripOverview = { tripId ->
                        navController.navigate(Screen.TripOverview.build(tripId))
                    },
                    onOpenSettings = { navController.navigate(Screen.AiSettings.route) },
                    onOpenPersonas = { navController.navigate(Screen.PersonaLibrary.build()) },
                    onOpenNewTrip = { navController.navigate(Screen.NewTrip.route) }
                )
            }

            composable(
                route = Screen.NewTrip.route,
                enterTransition = {
                    fadeIn(tween(240)) + slideInVertically(tween(320)) { it / 10 }
                },
                exitTransition = {
                    fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 10 }
                },
                popEnterTransition = {
                    fadeIn(tween(220)) + slideInVertically(tween(280)) { -it / 14 }
                },
                popExitTransition = {
                    fadeOut(tween(180)) + slideOutVertically(tween(260)) { it / 8 }
                }
            ) {
                NewTripScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = {
                        navController.navigate(Screen.Conversation.route) {
                            popUpTo(Screen.Explore.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Conversation.route) {
                ConversationScreen(
                    onOpenExplore = {
                        navController.navigate(Screen.Explore.route) {
                            popUpTo(Screen.Explore.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenPlan = { date, itemId ->
                        navController.navigate(Screen.Plan.build(date, itemId))
                    },
                    onOpenExpenses = { navController.navigate(Screen.Expenses.route) },
                    onOpenSettings = { navController.navigate(Screen.AiSettings.route) }
                )
            }

            composable(
                route = Screen.Plan.route,
                arguments = listOf(
                    navArgument(Screen.Plan.ARG_DATE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Screen.Plan.ARG_ITEM) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 8 } },
                exitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 8 } }
            ) { entry ->
                val date = entry.arguments?.getString(Screen.Plan.ARG_DATE)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val itemId = entry.arguments?.getString(Screen.Plan.ARG_ITEM)?.takeIf { it.isNotBlank() }

                PlanScreen(
                    initialDate = date,
                    initialItemId = itemId,
                    onBack = { navController.popBackStack() },
                    onPlanChanged = { AppContainer.alarms.refresh() }
                )
            }

            composable(
                route = Screen.Expenses.route,
                enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 8 } },
                exitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 8 } }
            ) {
                ExpensesScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Screen.AiSettings.route,
                enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 8 } },
                exitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 8 } }
            ) {
                AiSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Screen.PersonaLibrary.route,
                arguments = listOf(
                    navArgument(Screen.PersonaLibrary.ARG_TRIP_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 8 } },
                exitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 8 } }
            ) { entry ->
                val tripId = entry.arguments?.getString(Screen.PersonaLibrary.ARG_TRIP_ID)
                    ?.takeIf { it.isNotBlank() }
                PersonaLibraryScreen(
                    tripId = tripId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.TripOverview.route,
                arguments = listOf(
                    navArgument(Screen.TripOverview.ARG_TRIP_ID) { type = NavType.StringType }
                ),
                enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 8 } },
                exitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 8 } }
            ) { entry ->
                val tripId = entry.arguments?.getString(Screen.TripOverview.ARG_TRIP_ID).orEmpty()
                TripOverviewScreen(
                    tripId = tripId,
                    onBack = { navController.popBackStack() },
                    onOpenConversation = { navController.navigate(Screen.Conversation.route) },
                    onManagePersonas = {
                        navController.navigate(Screen.PersonaLibrary.build(tripId))
                    }
                )
            }
        }
    }
}
