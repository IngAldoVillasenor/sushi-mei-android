package com.restaurant.sushimei.frontend.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Pos : Screen(
        route = "pos",
        title = "Punto de Venta",
        icon = Icons.Default.PointOfSale
    )

    object Kitchen : Screen(
        route = "kitchen",
        title = "Cocina",
        icon = Icons.Default.SoupKitchen
    )

    object MenuManagement : Screen(
        route = "menu_management",
        title = "Gestión de Menú",
        icon = Icons.Default.RestaurantMenu
    )

    object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        icon = Icons.Default.Dashboard
    )

    object Account : Screen(
        route = "account",
        title = "Mi Cuenta",
        icon = Icons.Default.Person
    )

    object ChangePassword : Screen(
        route = "change_password",
        title = "Cambiar ContraseÃ±a",
        icon = Icons.Default.Person
    )

    object Sessions : Screen(
        route = "sessions",
        title = "Mis Sesiones",
        icon = Icons.Default.Person
    )

    companion object {
        val items = listOf(Pos, Kitchen, MenuManagement, Dashboard, Account)
    }
}
