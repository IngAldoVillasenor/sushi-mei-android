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
    override suspend fun placeOrder(items: List<ConfiguredProduct>, total: java.math.BigDecimal) { /* no-op */ }
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
    MenuItem(id = "1815495", nombre = "California roll",     categoria = "Clásicos",    precio = java.math.BigDecimal("79.0"),  emoji = "🍣"),
    MenuItem(id = "1815494", nombre = "Empanizado roll",     categoria = "Clásicos",    precio = java.math.BigDecimal("79.0"),  emoji = "🍣"),
    MenuItem(id = "1815496", nombre = "Philadelphia roll",   categoria = "Clásicos",    precio = java.math.BigDecimal("79.0"),  emoji = "🍣"),
    MenuItem(id = "1815497", nombre = "Tampico roll",        categoria = "Clásicos",    precio = java.math.BigDecimal("79.0"),  emoji = "🍣"),
    MenuItem(id = "1815498", nombre = "Banana roll",         categoria = "Clásicos",    precio = java.math.BigDecimal("79.0"),  emoji = "🍣"),
    MenuItem(id = "1815500", nombre = "Chipotle roll",       categoria = "Especiales",  precio = java.math.BigDecimal("89.0"),  emoji = "⭐"),
    MenuItem(id = "1815501", nombre = "Francés roll",        categoria = "Especiales",  precio = java.math.BigDecimal("89.0"),  emoji = "⭐"),
    MenuItem(id = "1815574", nombre = "Coca Normal 355ml",   categoria = "Bebidas",     precio = java.math.BigDecimal("22.0"),  emoji = "🥤"),
    MenuItem(id = "1815577", nombre = "Calpi 500ml",         categoria = "Bebidas",     precio = java.math.BigDecimal("25.0"),  emoji = "🥤"),
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
    override suspend fun getConfiguration(menuItemId: String): com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto = TODO()
    override suspend fun quoteItem(menuItemId: String, request: com.restaurant.sushimei.frontend.data.model.QuoteRequestDto): com.restaurant.sushimei.frontend.data.model.QuoteResponseDto {
        val menuItem = FAKE_MENU.first { it.id == menuItemId }
        val quantity = request.quantity
        val baseUnitPrice = menuItem.precio
        val unitTotal = baseUnitPrice
        val total = unitTotal * java.math.BigDecimal(quantity)
        return com.restaurant.sushimei.frontend.data.model.QuoteResponseDto(
            menuItemId = menuItemId,
            name = menuItem.nombre,
            quantity = quantity,
            baseUnitPrice = baseUnitPrice,
            baseTotal = baseUnitPrice * java.math.BigDecimal(quantity),
            unitAdjustmentTotal = java.math.BigDecimal.ZERO,
            unitTotal = unitTotal,
            total = total,
            groups = emptyList()
        )
    }
    override suspend fun getTags(): List<com.restaurant.sushimei.frontend.data.model.CatalogTagDto> = TODO()
    override suspend fun createTag(tag: com.restaurant.sushimei.frontend.data.model.CatalogTagDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto = TODO()
    override suspend fun updateTag(id: String, tag: com.restaurant.sushimei.frontend.data.model.CatalogTagDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto = TODO()
    override suspend fun deleteTag(id: String): Unit = TODO()
}

// ---------------------------------------------------------------------------
// Fake promotion repository — transparent pricing without promotions for tests
// ---------------------------------------------------------------------------
private class FakePromotionRepository : com.restaurant.sushimei.frontend.data.repository.IPromotionRepository {
    override fun observePromotions(): kotlinx.coroutines.flow.Flow<List<com.restaurant.sushimei.frontend.data.model.Promotion>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getPromotions(): List<com.restaurant.sushimei.frontend.data.model.Promotion> = emptyList()
    override suspend fun getPromotion(id: String): com.restaurant.sushimei.frontend.data.model.Promotion? = null
    override suspend fun createPromotion(promotion: com.restaurant.sushimei.frontend.data.model.Promotion): com.restaurant.sushimei.frontend.data.model.Promotion = promotion
    override suspend fun updatePromotion(promotion: com.restaurant.sushimei.frontend.data.model.Promotion): com.restaurant.sushimei.frontend.data.model.Promotion = promotion
    override suspend fun archivePromotion(id: String) {}
    override suspend fun quoteCart(cart: List<com.restaurant.sushimei.frontend.data.model.ConfiguredProduct>): com.restaurant.sushimei.frontend.data.model.OrderPricingPreview {
        val subtotal = cart.fold(java.math.BigDecimal.ZERO) { acc, item -> acc + item.total }
        return com.restaurant.sushimei.frontend.data.model.OrderPricingPreview(
            subtotal = subtotal,
            adjustments = emptyList(),
            rewardItems = emptyList(),
            total = subtotal
        )
    }
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
        viewModel = PosViewModel(FakeMenuRepository(), FakeOrderRepository(), FakePromotionRepository())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun getSuccessState(): com.restaurant.sushimei.frontend.ui.pos.PosUiState.Success {
        val state = viewModel.uiState.value
        if (state is com.restaurant.sushimei.frontend.ui.pos.PosUiState.Success) return state
        throw IllegalStateException("Expected PosUiState.Success but was $state")
    }


    // --- Tests de carga del menú ---

    @Test
    fun loadMenu_populatesAllProducts() = runTest {
        advanceUntilIdle()
        assertEquals(FAKE_MENU.size, getSuccessState().filteredProducts.size)
    }

    @Test
    fun loadMenu_generatesCorrectCategories() = runTest {
        advanceUntilIdle()
        val cats = getSuccessState().categories
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

        val filtered = getSuccessState().filteredProducts
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

        assertEquals(FAKE_MENU.size, getSuccessState().filteredProducts.size)
    }

    // --- Tests de carrito (regresión — deben seguir pasando) ---

    @Test
    fun addToCart_addsNewItem() = runTest {
        advanceUntilIdle()
        val item = getSuccessState().filteredProducts.first()
        viewModel.addToCart(item)
        advanceUntilIdle()

        val cart = getSuccessState().currentCart
        assertEquals(1, cart.size)
        assertEquals(item.id, cart[0].menuItemId)
        assertEquals(1, cart[0].quantity)
        assertEquals(item.precio, viewModel.getTotal())
    }

    @Test
    fun addToCart_incrementsQuantity() = runTest {
        advanceUntilIdle()
        val item = getSuccessState().filteredProducts.first()
        viewModel.addToCart(item)
        advanceUntilIdle()
        viewModel.addToCart(item)
        advanceUntilIdle()

        val cart = getSuccessState().currentCart
        assertEquals(1, cart.size)
        assertEquals(2, cart[0].quantity)
        assertEquals(item.precio * java.math.BigDecimal("2"), viewModel.getTotal())
    }

    @Test
    fun removeFromCart_decreasesQuantityOrRemoves() = runTest {
        advanceUntilIdle()
        val item = getSuccessState().filteredProducts.first()
        viewModel.addToCart(item)
        advanceUntilIdle()
        viewModel.addToCart(item)
        advanceUntilIdle()

        viewModel.removeFromCart(getSuccessState().currentCart.first())
        advanceUntilIdle()
        var cart = getSuccessState().currentCart
        assertEquals(1, cart.size)
        assertEquals(1, cart[0].quantity)
        assertEquals(item.precio, viewModel.getTotal())

        viewModel.removeFromCart(cart.first())
        advanceUntilIdle()
        cart = getSuccessState().currentCart
        assertTrue(cart.isEmpty())
        assertEquals(java.math.BigDecimal.ZERO, viewModel.getTotal())
    }

    @Test
    fun cartTotal_calculatesCorrectlyWithMultipleItems() = runTest {
        advanceUntilIdle()
        val products = getSuccessState().filteredProducts
        val clasico = products.first { it.categoria == "Clásicos" }  // $79.0
        val bebida = products.first { it.categoria == "Bebidas" }    // $22.0
        
        viewModel.addToCart(clasico)
        advanceUntilIdle()
        viewModel.addToCart(clasico)
        advanceUntilIdle()
        viewModel.addToCart(bebida)
        advanceUntilIdle()

        val expected = (clasico.precio * java.math.BigDecimal("2")) + bebida.precio
        assertEquals(2, getSuccessState().currentCart.size)
        assertEquals(expected, viewModel.getTotal())
    }
}
