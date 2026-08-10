package com.restaurant.sushimei.frontend.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO de productos del menú.
 *
 * [observeAll] y [observeActive] devuelven [Flow] reactivos que Room actualiza
 * automáticamente cuando la tabla cambia — el POS y la pantalla de Gestión
 * se actualizan en tiempo real sin reiniciar la app.
 */
@Dao
interface MenuDao {

    /**
     * Todos los productos (activos e inactivos), ordenados por categoría y nombre.
     * Usado por MenuManagementScreen para mostrar el catálogo completo.
     */
    @Query("SELECT * FROM menu_items ORDER BY categoria ASC, nombre ASC")
    fun observeAll(): Flow<List<MenuItemEntity>>

    /**
     * Solo productos activos. Usado por el POS para el catálogo de venta.
     */
    @Query("SELECT * FROM menu_items WHERE activo = 1 ORDER BY categoria ASC, nombre ASC")
    fun observeActive(): Flow<List<MenuItemEntity>>

    /**
     * Categorías únicas de productos activos, ordenadas alfabéticamente.
     */
    @Query("SELECT DISTINCT categoria FROM menu_items WHERE activo = 1 ORDER BY categoria ASC")
    fun observeActiveCategories(): Flow<List<String>>

    /**
     * Inserta una lista de productos. IGNORE evita sobreescribir ediciones
     * ya guardadas si el seed se llama accidentalmente más de una vez.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MenuItemEntity>)

    /** Inserta o actualiza un producto (upsert). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MenuItemEntity)

    /** Actualiza todos los campos de un producto existente. */
    @Update
    suspend fun update(item: MenuItemEntity)

    /** Activa o desactiva un producto sin tocar el resto de sus campos. */
    @Query("UPDATE menu_items SET activo = :activo WHERE id = :id")
    suspend fun setActive(id: String, activo: Boolean)

    /** Número total de productos (para detectar si la tabla necesita seed). */
    @Query("SELECT COUNT(*) FROM menu_items")
    suspend fun count(): Int

    /** Solo para tests. */
    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun findById(id: String): MenuItemEntity?
}
