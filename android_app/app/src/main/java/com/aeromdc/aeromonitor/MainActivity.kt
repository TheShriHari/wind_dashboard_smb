package com.aeromdc.aeromonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aeromdc.aeromonitor.ui.alerts.AlertsScreen
import com.aeromdc.aeromonitor.ui.alerts.AlertsViewModel
import com.aeromdc.aeromonitor.ui.dashboard.DashboardScreen
import com.aeromdc.aeromonitor.ui.dashboard.DashboardViewModel
import com.aeromdc.aeromonitor.ui.history.HistoryScreen
import com.aeromdc.aeromonitor.ui.history.HistoryViewModel
import com.aeromdc.aeromonitor.ui.settings.SettingsScreen
import com.aeromdc.aeromonitor.ui.theme.AeroMonitorTheme
import androidx.lifecycle.viewmodel.compose.viewModel

data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AeroMonitorTheme {
                AeroMonitorApp()
            }
        }
    }
}

@Composable
fun AeroMonitorApp() {
    val navController = rememberNavController()
    val dashboardVm: DashboardViewModel = viewModel()
    val alertsVm: AlertsViewModel = viewModel()
    val historyVm: HistoryViewModel = viewModel()

    val alertsState by alertsVm.uiState.collectAsState()
    val openCount = alertsState.counts.OPEN

    val navItems = listOf(
        NavItem("dashboard", "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        NavItem("alerts", "Alerts", Icons.Filled.NotificationsActive, Icons.Outlined.NotificationsNone, openCount),
        NavItem("history", "History", Icons.Filled.History, Icons.Outlined.History),
        NavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = androidx.compose.ui.unit.Dp(0f),
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                navItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        icon = {
                            if (item.badgeCount > 0) {
                                BadgedBox(badge = {
                                    Badge { Text(item.badgeCount.toString()) }
                                }) {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                )
                            }
                        },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("dashboard") { DashboardScreen(viewModel = dashboardVm) }
            composable("alerts") { AlertsScreen(viewModel = alertsVm) }
            composable("history") { HistoryScreen(viewModel = historyVm) }
            composable("settings") { SettingsScreen() }
        }
    }
}
