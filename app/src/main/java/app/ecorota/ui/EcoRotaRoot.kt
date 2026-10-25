package app.ecorota.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ecorota.R
import app.ecorota.di.AppContainer
import app.ecorota.ui.history.HistoryRoute
import app.ecorota.ui.home.HomeRoute
import app.ecorota.ui.onboarding.OnboardingRoute
import app.ecorota.ui.paywall.PaywallRoute
import app.ecorota.ui.settings.SettingsRoute
import app.ecorota.ui.theme.EcoRotaTheme
import app.ecorota.ui.trip.TripDetailRoute

private const val ROUTE_HOME = "home"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TRIP = "trip/{tripId}"
private const val ROUTE_PAYWALL = "paywall"

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

@Composable
fun EcoRotaRoot(container: AppContainer, state: AppUiState) {
    EcoRotaTheme(themeMode = state.settings.themeMode) {
        if (state.settings.onboardingCompleted) {
            MainShell(container)
        } else {
            OnboardingRoute(container)
        }
    }
}

@Composable
private fun MainShell(container: AppContainer) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination
    val destinations = listOf(
        TopLevelDestination(ROUTE_HOME, R.string.nav_home, Icons.Filled.Home),
        TopLevelDestination(ROUTE_HISTORY, R.string.nav_history, Icons.Filled.History),
        TopLevelDestination(ROUTE_SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    )
    val showBottomBar = destinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_HOME) {
                HomeRoute(
                    container = container,
                    onOpenTrip = { navController.navigate("trip/${Uri.encode(it)}") },
                    onOpenHistory = { navController.navigateTopLevel(ROUTE_HISTORY) },
                    onOpenPaywall = { navController.navigate(ROUTE_PAYWALL) },
                    onOpenSettings = { navController.navigateTopLevel(ROUTE_SETTINGS) },
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryRoute(
                    container = container,
                    onOpenTrip = { navController.navigate("trip/${Uri.encode(it)}") },
                    onOpenPaywall = { navController.navigate(ROUTE_PAYWALL) },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsRoute(
                    container = container,
                    onOpenPaywall = { navController.navigate(ROUTE_PAYWALL) },
                )
            }
            composable(
                route = ROUTE_TRIP,
                arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
            ) { entry ->
                TripDetailRoute(
                    container = container,
                    tripId = entry.arguments?.getString("tripId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenPaywall = { navController.navigate(ROUTE_PAYWALL) },
                )
            }
            composable(ROUTE_PAYWALL) {
                PaywallRoute(
                    container = container,
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
