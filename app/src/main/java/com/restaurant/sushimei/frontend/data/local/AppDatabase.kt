package com.restaurant.sushimei.frontend.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import com.restaurant.sushimei.frontend.data.repository.IOrderRepository
import com.restaurant.sushimei.frontend.data.repository.RoomMenuRepository
import com.restaurant.sushimei.frontend.data.repository.RoomOrderRepository

/**
 * Base de datos Room de la aplicacion Sushi Mei.
 *
 * Versiones:
 *   - v1: tabla `orders`
 *   - v2: + tabla `menu_items` (migracion MIGRATION_1_2)
 *   - v3: (desarrollo) cambio de IDs a String
 *   - v4: (desarrollo) migracion destructiva forzada de IDs a Long (Phase 6A2/6A3)
 *
 * Al agregar nuevas entidades: incrementar [version] y proveer la [Migration]
 * correspondiente para no perder datos existentes (excepto en dev si es destructiva).
 */
@androidx.room.Database(
    entities = [OrderEntity::class, MenuItemEntity::class],
    version = 4,
    exportSchema = false
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao
    abstract fun menuDao(): MenuDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migración 1 → 2: agrega la tabla `menu_items`.
         * Se ejecuta una sola vez en dispositivos que ya tenían v1 instalada.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS menu_items (
                        id          TEXT    NOT NULL PRIMARY KEY,
                        nombre      TEXT    NOT NULL,
                        categoria   TEXT    NOT NULL,
                        precio      REAL    NOT NULL,
                        descripcion TEXT    NOT NULL DEFAULT '',
                        emoji       TEXT    NOT NULL DEFAULT '🍣',
                        activo      INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Singleton seguro para multi-hilo (double-checked locking).
         * Usar siempre esta función — nunca instanciar [AppDatabase] directamente.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sushimei_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .apply {
                        if (com.restaurant.sushimei.frontend.BuildConfig.DEBUG) {
                            fallbackToDestructiveMigration(dropAllTables = true)
                        }
                    }
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Funciones de provisión — DI manual (sin Hilt)
// ─────────────────────────────────────────────────────────────────────────────

private var orderRepositoryInstance: IOrderRepository? = null

/**
 * Devuelve siempre el mismo [RoomOrderRepository] singleton.
 * En el futuro (con Hilt): reemplazar por @Provides en un @Module.
 */
fun provideOrderRepository(context: Context): IOrderRepository {
    return orderRepositoryInstance ?: synchronized(AppDatabase::class.java) {
        orderRepositoryInstance ?: RoomOrderRepository(
            dao = AppDatabase.getInstance(context).orderDao()
        ).also { orderRepositoryInstance = it }
    }
}

private var menuRepositoryInstance: IMenuRepository? = null

/**
 * Devuelve siempre el mismo [RoomMenuRepository] singleton.
 * El primer acceso hace el seed automático desde assets/menu.json si la tabla está vacía.
 */
fun provideMenuRepository(context: Context): IMenuRepository {
    return menuRepositoryInstance ?: synchronized(AppDatabase::class.java) {
        menuRepositoryInstance ?: com.restaurant.sushimei.frontend.data.repository.RemoteMenuRepository(
            api = com.restaurant.sushimei.frontend.data.api.NetworkModule.sushiMeiApi
        ).also { menuRepositoryInstance = it }
    }
}

private var promotionRepositoryInstance: com.restaurant.sushimei.frontend.data.repository.IPromotionRepository? = null

/**
 * Devuelve siempre el RemotePromotionRepository en tiempo de ejecución.
 */
fun providePromotionRepository(context: Context): com.restaurant.sushimei.frontend.data.repository.IPromotionRepository {
    return promotionRepositoryInstance ?: synchronized(AppDatabase::class.java) {
        promotionRepositoryInstance ?: com.restaurant.sushimei.frontend.data.repository.RemotePromotionRepository(
            api = com.restaurant.sushimei.frontend.data.api.NetworkModule.sushiMeiApi
        ).also { promotionRepositoryInstance = it }
    }
}

private var authRepositoryInstance: com.restaurant.sushimei.frontend.data.repository.AuthRepository? = null

/**
 * Devuelve siempre el AuthRepository singleton.
 */
fun provideAuthRepository(context: Context): com.restaurant.sushimei.frontend.data.repository.AuthRepository {
    return authRepositoryInstance ?: synchronized(AppDatabase::class.java) {
        authRepositoryInstance ?: com.restaurant.sushimei.frontend.data.repository.AuthRepository(
            publicApi = com.restaurant.sushimei.frontend.data.api.NetworkModule.publicSushiMeiApi,
            sessionStore = com.restaurant.sushimei.frontend.data.local.SecureSessionStore(context.applicationContext),
            deviceIdentityManager = com.restaurant.sushimei.frontend.data.local.DeviceIdentityManager(context.applicationContext)
        ).also {
            authRepositoryInstance = it
            // Inicializar el NetworkModule con el AuthRepository
            com.restaurant.sushimei.frontend.data.api.NetworkModule.initAuthRepository(it)
        }
    }
}
