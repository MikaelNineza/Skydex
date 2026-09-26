package com.skydex.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.skydex.app.ui.events.EventsScreen
import com.skydex.app.ui.profile.ProfileScreen
import com.skydex.app.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object ProfileRoute

@Serializable data object EventsRoute

@Serializable data object SettingsRoute

private enum class Tab(val route: Any, val label: String, val icon: ImageVector) {
    Profile(ProfileRoute, "Profile", Icons.Filled.Person),
    Events(EventsRoute, "Events", Icons.Filled.DateRange),
    Settings(SettingsRoute, "Settings", Icons.Filled.Settings),
}

@Composable
fun SkydexNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    for (tab in Tab.entries) {
                        val selected =
                            backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab.route) },
                            label = { Text(tab.label) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = ProfileRoute, modifier = Modifier.padding(padding)) {
            composable<ProfileRoute> { ProfileScreen() }
            composable<EventsRoute> { EventsScreen() }
            composable<SettingsRoute> {
                SettingsScreen(onChangePlayer = { navController.navigateToTab(ProfileRoute) })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
