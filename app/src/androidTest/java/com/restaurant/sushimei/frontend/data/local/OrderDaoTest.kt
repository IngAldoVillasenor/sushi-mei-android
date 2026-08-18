package com.restaurant.sushimei.frontend.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.OrderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de integración del DAO usando Room en memoria.
 *
 * Corren en el dispositivo/emulador porque necesitan el runtime de Android.
 * Ejecutar con: ./gradlew :app:connectedDebugAndroidTest
 *
 * La base de datos in-memory se destruye al cerrar cada test (@After),
 * garantizando aislamiento total entre tests.
 */
@RunWith(AndroidJUnit4::class)
class OrderDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: OrderDao

    // Datos de prueba
    private val sampleItem = com.restaurant.sushimei.frontend.data.model.ConfiguredProduct(
        id = java.util.UUID.randomUUID().toString(),
        menuItemId = 1L,
        name = "California roll",
        quantity = 2,
        baseUnitPrice = java.math.BigDecimal("79.0"),
        unitTotal = java.math.BigDecimal("79.0"),
        total = java.math.BigDecimal("158.0"),
        groups = emptyList()
    )
    private val sampleEntity = OrderEntity(
        id        = 1L,
        itemsJson = com.restaurant.sushimei.frontend.data.local.ConfiguredProductTypeConverter.fromList(listOf(sampleItem)),
        total     = java.math.BigDecimal("158.0"),
        createdAt = System.currentTimeMillis(),
        status    = OrderStatus.PENDING.name
    )

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.orderDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun insert_and_findById() = runTest {
        dao.insert(sampleEntity)

        val found = dao.getOrderById(1L)
        assertNotNull(found)
        assertEquals(1L, found!!.id)
        assertEquals(java.math.BigDecimal("158.0"), found.total)
        assertEquals(OrderStatus.PENDING.name, found.status)
    }

    @Test
    fun getActiveOrders_shouldReturnPendingPreparingAndReady() = runTest {
        val pendingOrder = sampleEntity.copy(id = 1L, status = OrderStatus.PENDING.name)
        val preparingOrder = sampleEntity.copy(id = 2L, status = OrderStatus.PREPARING.name)
        val readyOrder = sampleEntity.copy(id = 3L, status = OrderStatus.READY.name)
        val deliveredOrder = sampleEntity.copy(id = 4L, status = OrderStatus.DISPATCHED.name)

        dao.insert(pendingOrder)
        dao.insert(preparingOrder)
        dao.insert(readyOrder)
        dao.insert(deliveredOrder)

        val activeOrders = dao.observeActiveOrders().first()

        assertEquals(3, activeOrders.size)
        val activeIds = activeOrders.map { it.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L), activeIds)
    }

    @Test
    fun updateStatus_pending_to_preparing() = runTest {
        dao.insert(sampleEntity)

        dao.updateStatus(1L, OrderStatus.PREPARING.name)

        val updated = dao.getOrderById(1L)
        assertEquals(OrderStatus.PREPARING.name, updated?.status)
    }

    @Test
    fun updateStatus_preparing_to_ready() = runTest {
        dao.insert(sampleEntity.copy(status = OrderStatus.PREPARING.name))

        dao.updateStatus(1L, OrderStatus.READY.name)

        val updated = dao.getOrderById(1L)
        assertEquals(OrderStatus.READY.name, updated?.status)
    }

    @Test
    fun updateStatus_ready_to_dispatched_removesFromActiveFlow() = runTest {
        dao.insert(sampleEntity.copy(status = OrderStatus.READY.name))
        assertEquals(1, dao.countActive())

        dao.updateStatus(1L, OrderStatus.DISPATCHED.name)

        assertEquals(0, dao.countActive())
        val active = dao.observeActiveOrders().first()
        assertEquals(0, active.size)
    }

    @Test
    fun cartItemTypeConverter_serializesAndDeserializesCorrectly() = runTest {
        dao.insert(sampleEntity)

        val found = dao.getOrderById(1L)!!
        val items = com.restaurant.sushimei.frontend.data.local.ConfiguredProductTypeConverter.toList(found.itemsJson)

        assertEquals(1, items.size)
        assertEquals("California roll", items.first().name)
        assertEquals(2, items.first().quantity)
        assertEquals(java.math.BigDecimal("158.0"), items.first().total)
    }

    @Test
    fun multipleOrders_orderedByCreatedAtAscending() = runTest {
        val early = sampleEntity.copy(id = 1L, createdAt = 1000L)
        val late  = sampleEntity.copy(id = 2L,  createdAt = 9000L)
        dao.insert(late)
        dao.insert(early)

        val active = dao.observeActiveOrders().first()
        assertEquals(1L, active[0].id)
        assertEquals(2L,  active[1].id)
    }
}
