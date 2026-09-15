package com.driveguard.mobile.app

sealed class DriveGuardRoute(val route: String) {
    data object Home : DriveGuardRoute("home")
    data object DriveSession : DriveGuardRoute("drive-session")
    data object RecentEvents : DriveGuardRoute("recent-events")
    data object Settings : DriveGuardRoute("settings")
}
