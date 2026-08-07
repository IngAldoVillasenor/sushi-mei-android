package com.restaurant.sushimei.frontend.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SoupKitchen
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

    companion object {
        val items = listOf(Pos, Kitchen, MenuManagement, Dashboard)
    }
}
