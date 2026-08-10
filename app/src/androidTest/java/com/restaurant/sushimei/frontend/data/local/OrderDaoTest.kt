package com.restaurant.sushimei.frontend.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.restaurant.sushimei.frontend.data.model.CartItem
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
    private val sampleItem = CartItem(
        menuItem = MenuItem(
            id       = "1815495",
            nombre   = "California roll",
            categoria = "Clásicos",
            precio   = 79.0,
            emoji    = "🍣"
        ),
        cantidad = 2
    )
    private val sampleEntity = OrderEntity(
        id        = "TEST-001",
        itemsJson = CartItemTypeConverter.fromList(listOf(sampleItem)),
        total     = 158.0,
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

        val found = dao.findById("TEST-001")
        assertNotNull(found)
        assertEquals("TEST-001", found!!.id)
        assertEquals(158.0, found.total, 0.001)
        assertEquals(OrderStatus.PENDING.name, found.status)
    }

    @Test
    fun observeActiveOrders_excludesDispatched() = runTest {
        val dispatchedEntity = sampleEntity.copy(
            id     = "TEST-002",
            status = OrderStatus.DISPATCHED.name
        )
        dao.insert(sampleEntity)       // PENDING → debe aparecer
        dao.insert(dispatchedEntity)   // DISPATCHED → NO debe aparecer

        val active = dao.observeActiveOrders().first()
        assertEquals(1, active.size)
        assertEquals("TEST-001", active.first().id)
    }

    @Test
    fun updateStatus_pending_to_preparing() = runTest {
        dao.insert(sampleEntity)

        dao.updateStatus("TEST-001", OrderStatus.PREPARING.name)

        val updated = dao.findById("TEST-001")
        assertEquals(OrderStatus.PREPARING.name, updated?.status)
    }

    @Test
    fun updateStatus_preparing_to_ready() = runTest {
        dao.insert(sampleEntity.copy(status = OrderStatus.PREPARING.name))

        dao.updateStatus("TEST-001", OrderStatus.READY.name)

        val updated = dao.findById("TEST-001")
        assertEquals(OrderStatus.READY.name, updated?.status)
    }

    @Test
    fun updateStatus_ready_to_dispatched_removesFromActiveFlow() = runTest {
        dao.insert(sampleEntity.copy(status = OrderStatus.READY.name))
        assertEquals(1, dao.countActive())

        dao.updateStatus("TEST-001", OrderStatus.DISPATCHED.name)

        assertEquals(0, dao.countActive())
        val active = dao.observeActiveOrders().first()
        assertEquals(0, active.size)
    }

    @Test
    fun cartItemTypeConverter_serializesAndDeserializesCorrectly() = runTest {
        dao.insert(sampleEntity)

        val found = dao.findById("TEST-001")!!
        val items = CartItemTypeConverter.toList(found.itemsJson)

        assertEquals(1, items.size)
        assertEquals("California roll", items.first().menuItem.nombre)
        assertEquals(2, items.first().cantidad)
        assertEquals(158.0, items.first().subtotal, 0.001)
    }

    @Test
    fun multipleOrders_orderedByCreatedAtAscending() = runTest {
        val early = sampleEntity.copy(id = "EARLY", createdAt = 1000L)
        val late  = sampleEntity.copy(id = "LATE",  createdAt = 9000L)
        dao.insert(late)
        dao.insert(early)

        val active = dao.observeActiveOrders().first()
        assertEquals("EARLY", active[0].id)
        assertEquals("LATE",  active[1].id)
    }
}
