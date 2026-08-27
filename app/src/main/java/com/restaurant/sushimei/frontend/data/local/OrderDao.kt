package com.restaurant.sushimei.frontend.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO de órdenes.
 *
 * [observeActiveOrders] devuelve un [Flow] que Room actualiza automáticamente
 * cada vez que cambia la tabla — la Cocina reacciona en tiempo real sin polling.
 *
 * Las órdenes DISPATCHED se excluyen de la consulta activa porque ya no son
 * accionables. Si en el futuro se necesita historial, se añade una segunda query.
 */
@Dao
interface OrderDao {

    /**
     * Observa todas las órdenes activas (PENDING, PREPARING, READY) ordenadas
     * de más antigua a más reciente. Room emite una nueva lista cada vez que
     * hay un cambio en la tabla.
     */
    @Query("SELECT * FROM orders WHERE status != 'DISPATCHED' ORDER BY createdAt ASC")
    fun observeActiveOrders(): Flow<List<OrderEntity>>

    /** Inserta una nueva orden. Si ya existe (mismo id), la reemplaza. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: OrderEntity)

    /** Actualiza el status de una orden por su id. */
    @Query("UPDATE orders SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    // --- Solo para tests ---
    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: Long): OrderEntity?

    @Query("SELECT COUNT(*) FROM orders WHERE status != 'DISPATCHED'")
    suspend fun countActive(): Int

    // --- Dashboard ---

    /**
     * Observa las órdenes DISPATCHED desde [sinceTimestamp] (epoch ms).
     * Usado por el Dashboard para calcular métricas del día actual.
     * Room emite una nueva lista cada vez que se despacha una orden nueva.
     */
    @Query("""
        SELECT * FROM orders
        WHERE status = 'DISPATCHED'
          AND createdAt >= :sinceTimestamp
        ORDER BY createdAt ASC
    """)
    fun observeDispatched(sinceTimestamp: Long): Flow<List<OrderEntity>>
}
