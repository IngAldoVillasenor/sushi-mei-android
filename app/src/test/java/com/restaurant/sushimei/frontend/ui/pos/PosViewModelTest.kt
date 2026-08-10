package com.restaurant.sushimei.frontend.ui.pos

import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import com.restaurant.sushimei.frontend.data.repository.IOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------------
// Fake order repository — stub sin comportamiento (PosViewModel solo llama placeOrder)
// ---------------------------------------------------------------------------
private class FakeOrderRepository : IOrderRepository {
    override val activeOrders: StateFlow<List<com.restaurant.sushimei.frontend.data.model.Order>> =
        MutableStateFlow(emptyList())
    override suspend fun placeOrder(items: List<ConfiguredProduct>, total: Double) { /* no-op */ }
    override suspend fun acceptOrder(orderId: String) { /* no-op */ }
    override suspend fun markReady(orderId: String) { /* no-op */ }
    override suspend fun dispatch(orderId: String) { /* no-op */ }
    override fun observeDispatchedToday(): kotlinx.coroutines.flow.Flow<List<com.restaurant.sushimei.frontend.data.model.Order>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
}


// ---------------------------------------------------------------------------
// Fake repository — datos reales del menú Sushi Mei (subset representativo)
// ---------------------------------------------------------------------------
private val FAKE_MENU = listOf(
    MenuItem(id = "1815495", nombre = "California roll",     categoria = "Clásicos",    precio = 79.0,  emoji = "🍣"),
    MenuItem(id = "1815494", nombre = "Empanizado roll",     categoria = "Clásicos",    precio = 79.0,  emoji = "🍣"),
    MenuItem(id = "1815496", nombre = "Philadelphia roll",   categoria = "Clásicos",    precio = 79.0,  emoji = "🍣"),
    MenuItem(id = "1815497", nombre = "Tampico roll",        categoria = "Clásicos",    precio = 79.0,  emoji = "🍣"),
    MenuItem(id = "1815498", nombre = "Banana roll",         categoria = "Clásicos",    precio = 79.0,  emoji = "🍣"),
    MenuItem(id = "1815500", nombre = "Chipotle roll",       categoria = "Especiales",  precio = 89.0,  emoji = "⭐"),
    MenuItem(id = "1815501", nombre = "Francés roll",        categoria = "Especiales",  precio = 89.0,  emoji = "⭐"),
    MenuItem(id = "1815574", nombre = "Coca Normal 355ml",   categoria = "Bebidas",     precio = 22.0,  emoji = "🥤"),
    MenuItem(id = "1815577", nombre = "Calpi 500ml",         categoria = "Bebidas",     precio = 25.0,  emoji = "🥤"),
)

private class FakeMenuRepository : IMenuRepository {
    // Flow que emite el menú completo de prueba
    override fun observeAll(): kotlinx.coroutines.flow.Flow<List<MenuItem>> =
        kotlinx.coroutines.flow.flowOf(FAKE_MENU)
    override fun observeActive(): kotlinx.coroutines.flow.Flow<List<MenuItem>> =
        kotlinx.coroutines.flow.flowOf(FAKE_MENU)
    override fun observeActiveCategories(): kotlinx.coroutines.flow.Flow<List<String>> =
        kotlinx.coroutines.flow.flowOf(FAKE_MENU.map { it.categoria }.distinct().sorted())
    override suspend fun getProducts(): List<MenuItem> = FAKE_MENU
    override suspend fun getCategories(): List<String> =
        FAKE_MENU.map { it.categoria }.distinct().sorted()
    override suspend fun saveProduct(item: MenuItem) { /* no-op */ }
    override suspend fun setActive(id: String, activo: Boolean) { /* no-op */ }
}


// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PosViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PosViewModel(FakeMenuRepository(), FakeOrderRepository())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Tests de carga del menú ---

    @Test
    fun loadMenu_populatesAllProducts() = runTest {
        advanceUntilIdle()
        assertEquals(FAKE_MENU.size, viewModel.filteredProducts.value.size)
    }

    @Test
    fun loadMenu_generatesCorrectCategories() = runTest {
        advanceUntilIdle()
        val cats = viewModel.categories.value
        // "Todos" siempre es el primero
        assertEquals("Todos", cats.first())
        // Debe haber una categoría por cada distinta en el fake, más "Todos"
        val expected = listOf("Todos") + FAKE_MENU.map { it.categoria }.distinct().sorted()
        assertEquals(expected, cats)
    }

    // --- Tests de filtrado por categoría ---

    @Test
    fun selectCategory_filtersProductsCorrectly() = runTest {
        advanceUntilIdle()

        viewModel.selectCategory("Clásicos")
        advanceUntilIdle()

        val filtered = viewModel.filteredProducts.value
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.categoria == "Clásicos" })
        assertEquals(5, filtered.size)
    }

    @Test
    fun selectCategory_todos_returnsAllProducts() = runTest {
        advanceUntilIdle()
        viewModel.selectCategory("Especiales")
        advanceUntilIdle()
        viewModel.selectCategory("Todos")
        advanceUntilIdle()

        assertEquals(FAKE_MENU.size, viewModel.filteredProducts.value.size)
    }

    // --- Tests de carrito (regresión — deben seguir pasando) ---

    @Test
    fun addToCart_addsNewItem() = runTest {
        advanceUntilIdle()
        val item = viewModel.filteredProducts.value.first()

        viewModel.addToCart(item)

        val cart = viewModel.currentCart.value
        assertEquals(1, cart.size)
        assertEquals(item.id, cart[0].menuItem.id)
        assertEquals(1, cart[0].cantidad)
        assertEquals(item.precio, viewModel.getTotal(), 0.001)
    }

    @Test
    fun addToCart_incrementsQuantity() = runTest {
        advanceUntilIdle()
        val item = viewModel.filteredProducts.value.first()

        viewModel.addToCart(item)
        viewModel.addToCart(item)

        val cart = viewModel.currentCart.value
        assertEquals(1, cart.size)
        assertEquals(2, cart[0].cantidad)
        assertEquals(item.precio * 2, viewModel.getTotal(), 0.001)
    }

    @Test
    fun removeFromCart_decreasesQuantityOrRemoves() = runTest {
        advanceUntilIdle()
        val item = viewModel.filteredProducts.value.first()
        viewModel.addToCart(item)
        viewModel.addToCart(item)

        viewModel.removeFromCart(viewModel.currentCart.value.first())
        var cart = viewModel.currentCart.value
        assertEquals(1, cart.size)
        assertEquals(1, cart[0].cantidad)
        assertEquals(item.precio, viewModel.getTotal(), 0.001)

        viewModel.removeFromCart(cart.first())
        cart = viewModel.currentCart.value
        assertTrue(cart.isEmpty())
        assertEquals(0.0, viewModel.getTotal(), 0.001)
    }

    @Test
    fun cartTotal_calculatesCorrectlyWithMultipleItems() = runTest {
        advanceUntilIdle()
        val products = viewModel.filteredProducts.value
        val clasico = products.first { it.categoria == "Clásicos" }  // $79.0
        val bebida = products.first { it.categoria == "Bebidas" }    // $22.0

        viewModel.addToCart(clasico)
        viewModel.addToCart(clasico)
        viewModel.addToCart(bebida)

        val expected = (clasico.precio * 2) + bebida.precio
        assertEquals(2, viewModel.currentCart.value.size)
        assertEquals(expected, viewModel.getTotal(), 0.001)
    }
}
