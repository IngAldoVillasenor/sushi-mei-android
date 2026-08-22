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
import com.restaurant.sushimei.frontend.ui.screens.AccountScreen
import com.restaurant.sushimei.frontend.ui.screens.DashboardScreen
import com.restaurant.sushimei.frontend.ui.screens.KitchenScreen
import com.restaurant.sushimei.frontend.ui.screens.MenuManagementScreen
import com.restaurant.sushimei.frontend.ui.screens.PosScreen
import com.restaurant.sushimei.frontend.data.repository.AuthRepository
import com.restaurant.sushimei.frontend.data.model.AuthenticatedUserDto
import com.restaurant.sushimei.frontend.data.model.ApplicationRole

@Composable
fun MainScreen(authRepository: AuthRepository, user: AuthenticatedUserDto) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // ROLE-AWARE UI FILTERING
    val allowedScreens = Screen.items.filter { screen ->
        when (screen) {
            Screen.MenuManagement -> user.role == ApplicationRole.OWNER || user.role == ApplicationRole.MANAGER
            Screen.Kitchen -> user.role != ApplicationRole.CASHIER
            Screen.Pos -> user.role != ApplicationRole.KITCHEN
            Screen.Dashboard -> user.role == ApplicationRole.OWNER || user.role == ApplicationRole.MANAGER
            Screen.Account -> true
            Screen.ChangePassword -> false
            Screen.Sessions -> false
            Screen.BusinessDay -> false
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // NavigationRail permanente en el lado izquierdo
        NavigationRail {
            allowedScreens.forEach { screen ->
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

        // Determine logical start destination based on role
        val initialRoute = when (user.role) {
            ApplicationRole.CASHIER -> Screen.Pos.route
            else -> Screen.Kitchen.route
        }

        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = initialRoute
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
                composable(Screen.Account.route) {
                    AccountScreen(
                        authRepository = authRepository,
                        user = user,
                        onNavigateToChangePassword = { navController.navigate(Screen.ChangePassword.route) },
                        onNavigateToSessions = { navController.navigate(Screen.Sessions.route) },
                        onNavigateToBusinessDay = { navController.navigate(Screen.BusinessDay.route) }
                    )
                }
                composable(Screen.BusinessDay.route) {
                    com.restaurant.sushimei.frontend.ui.businessday.BusinessDayScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.ChangePassword.route) {
                    com.restaurant.sushimei.frontend.ui.screens.ChangePasswordScreen(
                        authRepository = authRepository,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Sessions.route) {
                    com.restaurant.sushimei.frontend.ui.screens.SessionsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
