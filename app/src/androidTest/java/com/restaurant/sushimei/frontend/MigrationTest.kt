package com.restaurant.sushimei.frontend

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.restaurant.sushimei.frontend.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate6To7() {
        // Seed v6 database
        var db = helper.createDatabase(TEST_DB, 6)

        // Insert a v6 print job
        db.execSQL(
            """
            INSERT INTO print_jobs (id, requestId, orderId, status, lastError, createdAt, updatedAt, printedAt, activeAttemptId)
            VALUES ('job-123', 'req-123', 99, 'PENDING', NULL, 1000, 1000, NULL, 'att-456')
            """.trimIndent()
        )

        // Insert a print attempt related to this job
        db.execSQL(
            """
            INSERT INTO print_attempts (id, printJobId, type, status, startedAt, finishedAt, error)
            VALUES ('att-456', 'job-123', 'PRIMARY', 'PRINTING', 1000, NULL, NULL)
            """.trimIndent()
        )

        db.close()

        // Run migration to v7
        db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)

        // Verify the migrated job
        val cursor = db.query("SELECT * FROM print_jobs WHERE id = 'job-123'")
        assertTrue(cursor.moveToFirst())

        assertEquals("job-123", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals("req-123", cursor.getString(cursor.getColumnIndexOrThrow("requestId")))
        assertEquals("ORDER", cursor.getString(cursor.getColumnIndexOrThrow("documentType")))
        assertEquals(99L, cursor.getLong(cursor.getColumnIndexOrThrow("documentId")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("snapshotPayload")))
        assertEquals("PENDING", cursor.getString(cursor.getColumnIndexOrThrow("status")))
        assertEquals("att-456", cursor.getString(cursor.getColumnIndexOrThrow("activeAttemptId")))

        cursor.close()

        // Verify the foreign key relationship still holds for print_attempts
        val attemptCursor = db.query("SELECT * FROM print_attempts WHERE id = 'att-456'")
        assertTrue(attemptCursor.moveToFirst())
        assertEquals("job-123", attemptCursor.getString(attemptCursor.getColumnIndexOrThrow("printJobId")))
        attemptCursor.close()

        // Verify a new BUSINESS_DAY_CLOSE job can coexist
        db.execSQL(
            """
            INSERT INTO print_jobs (id, requestId, documentType, documentId, snapshotPayload, status, createdAt, updatedAt)
            VALUES ('job-999', 'req-close-1', 'BUSINESS_DAY_CLOSE', 42, '{"fake":"payload"}', 'PENDING', 2000, 2000)
            """.trimIndent()
        )

        val closeCursor = db.query("SELECT * FROM print_jobs WHERE id = 'job-999'")
        assertTrue(closeCursor.moveToFirst())
        assertEquals("BUSINESS_DAY_CLOSE", closeCursor.getString(closeCursor.getColumnIndexOrThrow("documentType")))
        assertEquals(42L, closeCursor.getLong(closeCursor.getColumnIndexOrThrow("documentId")))
        assertEquals("{\"fake\":\"payload\"}", closeCursor.getString(closeCursor.getColumnIndexOrThrow("snapshotPayload")))
        closeCursor.close()
    }
}
