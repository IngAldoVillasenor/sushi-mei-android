package com.restaurant.sushimei.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.restaurant.sushimei.frontend.navigation.Screen
import com.restaurant.sushimei.frontend.ui.screens.DashboardScreen
import com.restaurant.sushimei.frontend.ui.screens.KitchenScreen
import com.restaurant.sushimei.frontend.ui.screens.MenuManagementScreen
import com.restaurant.sushimei.frontend.ui.screens.PosScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Row(modifier = Modifier.fillMaxSize()) {
        // NavigationRail permanente en el lado izquierdo
        NavigationRail {
            Screen.items.forEach { screen ->
                val selected = currentRoute == screen.route
                NavigationRailItem(
                    selected = selected,
                    onClick = {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
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
                            imageVector = screen.icon,
                            contentDescription = screen.title
                        )
                    },
                    label = {
                        Text(text = screen.title)
                    }
                )
            }
        }

        // El contenido principal ocupa el resto de la pantalla (a la derecha)
        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Kitchen.route
            ) {
                composable(Screen.Pos.route) {
                    PosScreen()
                }
                composable(Screen.Kitchen.route) {
                    KitchenScreen()
                }
                composable(Screen.MenuManagement.route) {
                    MenuManagementScreen()
                }
                composable(Screen.Dashboard.route) {
                    DashboardScreen()
                }
            }
        }
    }
}
