package com.cardovia.merkon.app.ui

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
import com.cardovia.merkon.app.navigation.Screen
import com.cardovia.merkon.app.ui.screens.AccountScreen
import com.cardovia.merkon.app.ui.screens.DashboardScreen
import com.cardovia.merkon.app.ui.screens.KitchenScreen
import com.cardovia.merkon.app.ui.screens.MenuManagementScreen
import com.cardovia.merkon.app.ui.screens.PosScreen
import com.cardovia.merkon.app.data.repository.AuthRepository
import com.cardovia.merkon.app.data.model.AuthenticatedUserDto
import com.cardovia.merkon.app.data.model.ApplicationRole

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.flow.first
import com.cardovia.merkon.app.data.local.provideMenuRepository

@Composable
fun MainScreen(authRepository: AuthRepository, user: AuthenticatedUserDto) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current
    val menuRepository = remember { provideMenuRepository(context) }
    val bootstrapViewModel = remember(user) { MainBootstrapViewModel(menuRepository, user) }

    when (bootstrapViewModel.bootstrapState) {
        BootstrapState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        BootstrapState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error al cargar la configuración inicial del negocio.", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.Button(onClick = { bootstrapViewModel.retry() }) {
                        Text("Reintentar")
                    }
                }
            }
            return
        }
        else -> {
            // Proceed to render main navigation
        }
    }

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

        // Determine logical start destination based on role and catalog state
        val initialRoute = if (user.role == ApplicationRole.OWNER && bootstrapViewModel.bootstrapState == BootstrapState.Empty) {
            Screen.MenuManagement.route
        } else when (user.role) {
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
                    com.cardovia.merkon.app.ui.businessday.BusinessDayScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.ChangePassword.route) {
                    com.cardovia.merkon.app.ui.screens.ChangePasswordScreen(
                        authRepository = authRepository,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Sessions.route) {
                    com.cardovia.merkon.app.ui.screens.SessionsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
