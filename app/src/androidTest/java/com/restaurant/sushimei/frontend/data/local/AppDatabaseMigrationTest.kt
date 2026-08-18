package com.restaurant.sushimei.frontend.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5() {
        var db = helper.createDatabase(TEST_DB, 4)
        db.close()
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5)
    }

    @Test
    fun migrate5To6() {
        var db = helper.createDatabase(TEST_DB, 5)
        db.close()
        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6)

        var hasActiveAttemptId = false
        var cursor = db.query("PRAGMA table_info(print_jobs)")
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            if (name == "activeAttemptId") {
                hasActiveAttemptId = true
            }
        }
        cursor.close()
        assertEquals(true, hasActiveAttemptId)

        var hasIndex = false
        cursor = db.query("PRAGMA index_list(print_attempts)")
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            if (name == "index_print_attempts_active") {
                hasIndex = true
            }
        }
        cursor.close()
        assertEquals(false, hasIndex)
    }

    @Test
    fun migrate4To6() {
        var db = helper.createDatabase(TEST_DB, 4)
        // insert some data to verify it survives
        db.execSQL("INSERT INTO menu_items (id, nombre, categoria, precio, descripcion, emoji, activo, standaloneOrderable, tags) VALUES ('1', 'Maki', 'Rolls', 100.0, '', '🍣', 1, 1, '[]')")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)

        val cursor = db.query("SELECT * FROM menu_items WHERE id = '1'")
        assertEquals(true, cursor.moveToFirst())
        cursor.close()
    }
}
