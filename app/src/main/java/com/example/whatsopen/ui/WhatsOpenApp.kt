package com.example.whatsopen.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Phone
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.whatsopen.R

sealed class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    data object ByNumber : Destination(
        route = "by_number",
        labelRes = R.string.nav_by_number,
        icon = Icons.Outlined.Phone,
        selectedIcon = Icons.Filled.Phone,
    )

    data object CallLogs : Destination(
        route = "call_logs",
        labelRes = R.string.nav_call_logs,
        icon = Icons.Outlined.History,
        selectedIcon = Icons.Filled.History,
    )

    data object Clipboard : Destination(
        route = "clipboard",
        labelRes = R.string.nav_clipboard,
        icon = Icons.Outlined.ContentPaste,
        selectedIcon = Icons.Filled.ContentPaste,
    )
}

private val destinations = listOf(Destination.ByNumber, Destination.CallLogs, Destination.Clipboard)

@Composable
fun WhatsOpenApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = { WhatsOpenBottomBar(navController, currentRoute) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.ByNumber.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.ByNumber.route) { Text("TODO: ByNumber") }
            composable(Destination.CallLogs.route) { Text("TODO: CallLogs") }
            composable(Destination.Clipboard.route) { Text("TODO: Clipboard") }
        }
    }
}

@Composable
private fun WhatsOpenBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        destinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.icon,
                        contentDescription = stringResource(dest.labelRes),
                    )
                },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}
