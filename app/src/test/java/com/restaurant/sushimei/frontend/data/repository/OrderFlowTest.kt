package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.OrderStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Items de carrito reutilizables
private val ITEM_CALIFORNIA = MenuItem(
    id = "1815495", nombre = "California roll", categoria = "Clásicos", precio = java.math.BigDecimal("79.0"), emoji = "🍣"
)
private val ITEM_COCA = MenuItem(
    id = "1815574", nombre = "Coca Normal 355ml", categoria = "Bebidas", precio = java.math.BigDecimal("22.0"), emoji = "🥤"
)
private val CART = listOf(
    ConfiguredProduct(menuItemId = ITEM_CALIFORNIA.id, name = ITEM_CALIFORNIA.nombre, quantity = 2, baseUnitPrice = ITEM_CALIFORNIA.precio),
    ConfiguredProduct(menuItemId = ITEM_COCA.id, name = ITEM_COCA.nombre, quantity = 1, baseUnitPrice = ITEM_COCA.precio)
)
private val TOTAL = java.math.BigDecimal("180.0")  // (79 * 2) + 22

@OptIn(ExperimentalCoroutinesApi::class)
class OrderFlowTest {

    @Before
    fun setUp() {
        // Garantizamos estado limpio entre tests
        MockOrderRepository.reset()
    }

    @Test
    fun placeOrder_appearsAsPending() = runTest {
        MockOrderRepository.placeOrder(CART, TOTAL)

        val orders = MockOrderRepository.activeOrdersFlow.value
        assertEquals(1, orders.size)
        assertEquals(OrderStatus.PENDING, orders.first().status)
        assertEquals(TOTAL, orders.first().total)
        assertEquals(CART.size, orders.first().items.size)
    }

    @Test
    fun acceptOrder_changesStatusToPreparing() = runTest {
        MockOrderRepository.placeOrder(CART, TOTAL)
        val orderId = MockOrderRepository.activeOrdersFlow.value.first().id

        MockOrderRepository.acceptOrder(orderId)

        val order = MockOrderRepository.activeOrdersFlow.value.first()
        assertEquals(OrderStatus.PREPARING, order.status)
    }

    @Test
    fun markReady_changesStatusToReady_and_staysInActiveList() = runTest {
        MockOrderRepository.placeOrder(CART, TOTAL)
        val orderId = MockOrderRepository.activeOrdersFlow.value.first().id
        MockOrderRepository.acceptOrder(orderId)

        MockOrderRepository.markReady(orderId)

        val activeOrders = MockOrderRepository.activeOrdersFlow.value
        assertEquals(1, activeOrders.size)
        assertEquals(OrderStatus.READY, activeOrders.first().status)
    }

    @Test
    fun dispatch_removesOrderFromActiveList() = runTest {
        MockOrderRepository.placeOrder(CART, TOTAL)
        val orderId = MockOrderRepository.activeOrdersFlow.value.first().id
        MockOrderRepository.acceptOrder(orderId)
        MockOrderRepository.markReady(orderId)

        MockOrderRepository.dispatch(orderId)

        assertTrue(MockOrderRepository.activeOrdersFlow.value.isEmpty())
    }

    @Test
    fun fullFlow_endToEnd() = runTest {
        // 1. Cajero cobra → orden creada como PENDING
        MockOrderRepository.placeOrder(CART, TOTAL)
        val orderId = MockOrderRepository.activeOrdersFlow.value.first().id
        assertEquals(OrderStatus.PENDING, MockOrderRepository.activeOrdersFlow.value.first().status)

        // 2. Cocina acepta → PREPARING
        MockOrderRepository.acceptOrder(orderId)
        assertEquals(OrderStatus.PREPARING, MockOrderRepository.activeOrdersFlow.value.first().status)

        // 3. Cocina termina → READY (desaparece de cocina pero sigue activa)
        MockOrderRepository.markReady(orderId)
        assertEquals(OrderStatus.READY, MockOrderRepository.activeOrdersFlow.value.first().status)
        assertEquals(1, MockOrderRepository.activeOrdersFlow.value.size) // aún activa

        // 4. Cliente recoge → DISPATCHED (desaparece de la lista activa)
        MockOrderRepository.dispatch(orderId)
        assertTrue(MockOrderRepository.activeOrdersFlow.value.isEmpty())
    }

    @Test
    fun multipleOrders_independentStatusTransitions() = runTest {
        MockOrderRepository.placeOrder(CART, TOTAL)
        MockOrderRepository.placeOrder(listOf(ConfiguredProduct(menuItemId = ITEM_COCA.id, name = ITEM_COCA.nombre, quantity = 3, baseUnitPrice = ITEM_COCA.precio)), java.math.BigDecimal("66.0"))

        val orders = MockOrderRepository.activeOrdersFlow.value
        assertEquals(2, orders.size)

        val firstId = orders[0].id
        val secondId = orders[1].id

        // Solo avanzar la primera
        MockOrderRepository.acceptOrder(firstId)

        val updated = MockOrderRepository.activeOrdersFlow.value
        assertEquals(OrderStatus.PREPARING, updated.first { it.id == firstId }.status)
        assertEquals(OrderStatus.PENDING, updated.first { it.id == secondId }.status)
    }
}
