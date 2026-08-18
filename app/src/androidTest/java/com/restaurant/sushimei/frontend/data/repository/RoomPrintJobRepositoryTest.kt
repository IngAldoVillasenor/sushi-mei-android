package com.restaurant.sushimei.frontend.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.restaurant.sushimei.frontend.data.local.AppDatabase
import com.restaurant.sushimei.frontend.data.local.PrintJobDao
import com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus
import com.restaurant.sushimei.frontend.data.model.PrintAttemptType
import com.restaurant.sushimei.frontend.data.model.PrintJobStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RoomPrintJobRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PrintJobDao
    private lateinit var repository: RoomPrintJobRepository

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.printJobDao()
        repository = RoomPrintJobRepository(dao)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun idempotency_sameIdentifiers_returnsSameJob() = runTest {
        val job1 = repository.enqueuePrint(1L, "req-1")
        val job2 = repository.enqueuePrint(1L, "req-1")
        assertEquals(job1.id, job2.id)
    }

    @Test
    fun idempotency_conflictingOrderId_fails() = runTest {
        repository.enqueuePrint(1L, "req-1")
        try {
            repository.enqueuePrint(2L, "req-1")
            fail("Expected exception")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Idempotency breach") == true)
        }
    }

    @Test
    fun idempotency_conflictingRequestId_fails() = runTest {
        repository.enqueuePrint(1L, "req-1")
        try {
            repository.enqueuePrint(1L, "req-2")
            fail("Expected exception")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Idempotency breach") == true)
        }
    }

    @Test
    fun beginAttempt_originalOnlyFromPending() = runTest {
        val job = repository.enqueuePrint(1L, "req-1")
        val attempt1 = repository.beginAttempt(job.id, PrintAttemptType.ORIGINAL)
        assertNotNull(attempt1)

        repository.finishAttempt(attempt1!!.id, PrintAttemptStatus.FAILED, System.currentTimeMillis(), "error")

        // Parent is FAILED now, so ORIGINAL should fail
        val attempt2 = repository.beginAttempt(job.id, PrintAttemptType.ORIGINAL)
        assertNull(attempt2)
    }

    @Test
    fun beginAttempt_retryOnlyFromFailedOrInterrupted() = runTest {
        val job = repository.enqueuePrint(1L, "req-1")

        // Parent is PENDING, so RETRY should fail
        var attempt = repository.beginAttempt(job.id, PrintAttemptType.RETRY)
        assertNull(attempt)

        repository.markJobFailed(job.id, "error")
        attempt = repository.beginAttempt(job.id, PrintAttemptType.RETRY)
        assertNotNull(attempt)
    }

    @Test
    fun beginAttempt_reprintOnlyFromPrinted() = runTest {
        val job = repository.enqueuePrint(1L, "req-1")
        repository.markJobPrinted(job.id)

        val attempt = repository.beginAttempt(job.id, PrintAttemptType.REPRINT)
        assertNotNull(attempt)
    }

    @Test
    fun beginAttempt_twoReprintCalls_oneSucceeds() = runTest {
        val job = repository.enqueuePrint(1L, "req-1")
        repository.markJobPrinted(job.id)

        val attempt1 = repository.beginAttempt(job.id, PrintAttemptType.REPRINT)
        assertNotNull(attempt1)
        val attempt2 = repository.beginAttempt(job.id, PrintAttemptType.REPRINT)
        assertNull(attempt2) // Because attempt1 is holding the lock
    }

    @Test
    fun orphanReprint_clearsLockButLeavesParentPrinted() = runTest {
        val job = repository.enqueuePrint(1L, "req-1")
        repository.markJobPrinted(job.id)

        val attempt = repository.beginAttempt(job.id, PrintAttemptType.REPRINT)
        assertNotNull(attempt)

        var updatedJob = repository.getJobById(job.id)
        assertEquals(PrintJobStatus.PRINTED, updatedJob!!.status)
        assertNotNull(updatedJob.activeAttemptId)

        repository.reconcileOrphanedJobs()

        updatedJob = repository.getJobById(job.id)
        assertEquals(PrintJobStatus.PRINTED, updatedJob!!.status)
        assertNull(updatedJob.activeAttemptId)

        val updatedAttempt = dao.getAttemptById(attempt!!.id)
        assertEquals(PrintAttemptStatus.INTERRUPTED, updatedAttempt!!.status)
    }
}
