package com.driveguard.mobile.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.driveguard.mobile.drive.session.ui.DriveSessionRoute
import com.driveguard.mobile.drive.session.ui.RecentEventsRoute
import com.driveguard.mobile.home.ui.HomeRoute
import com.driveguard.mobile.settings.data.BackendSettingsRepository
import com.driveguard.mobile.settings.ui.BackendSettingsRoute

@Composable
fun DriveGuardAndroidApp() {
    val navController = rememberNavController()
    val destinations = listOf(
        TopLevelDestination(
            route = DriveGuardRoute.Home,
            label = "Home",
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Home,
                    contentDescription = null,
                )
            },
        ),
        TopLevelDestination(
            route = DriveGuardRoute.DriveSession,
            label = "Drive",
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                )
            },
        ),
        TopLevelDestination(
            route = DriveGuardRoute.RecentEvents,
            label = "Events",
            icon = {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                )
            },
        ),
        TopLevelDestination(
            route = DriveGuardRoute.Settings,
            label = "Settings",
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                )
            },
        ),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                destinations.forEach { destination ->
                    val isSelected = currentDestination?.hierarchy?.any {
                        it.route == destination.route.route
                    } == true

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(destination.route.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = destination.icon,
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DriveGuardRoute.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(DriveGuardRoute.Home.route) {
                HomeRoute(
                    repository = BackendSettingsRepository.fromCurrentContext(),
                    onOpenDriveSession = {
                        navController.navigate(DriveGuardRoute.DriveSession.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenRecentEvents = {
                        navController.navigate(DriveGuardRoute.RecentEvents.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(DriveGuardRoute.DriveSession.route) {
                DriveSessionRoute()
            }
            composable(DriveGuardRoute.RecentEvents.route) {
                RecentEventsRoute()
            }
            composable(DriveGuardRoute.Settings.route) {
                BackendSettingsRoute(
                    repository = BackendSettingsRepository.fromCurrentContext(),
                )
            }
        }
    }
}

private data class TopLevelDestination(
    val route: DriveGuardRoute,
    val label: String,
    val icon: @Composable () -> Unit,
)
