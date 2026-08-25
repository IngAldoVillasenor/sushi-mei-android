package com.restaurant.sushimei.frontend.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.restaurant.sushimei.frontend.data.model.PrintAttemptType
import com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus
import com.restaurant.sushimei.frontend.data.model.PrintJobStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintJobDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJob(job: PrintJobEntity): Long

    @Query("SELECT * FROM print_jobs WHERE documentType = :documentType AND documentId = :documentId LIMIT 1")
    suspend fun getJobByDocument(documentType: com.restaurant.sushimei.frontend.data.model.PrintDocumentType, documentId: Long): PrintJobEntity?

    @Query("SELECT * FROM print_jobs WHERE id = :id LIMIT 1")
    suspend fun getJobById(id: String): PrintJobEntity?
    @Query("SELECT * FROM print_jobs WHERE requestId = :requestId LIMIT 1")
    suspend fun getJobByRequestId(requestId: String): PrintJobEntity?

    @Query("SELECT * FROM print_jobs ORDER BY createdAt DESC")
    fun observeAllJobs(): Flow<List<PrintJobEntity>>
    @Query("SELECT * FROM print_jobs WHERE id = :id LIMIT 1")
    fun observeJobById(id: String): Flow<PrintJobEntity?>

    @Query("SELECT * FROM print_jobs WHERE documentType = :documentType AND documentId = :documentId LIMIT 1")
    fun observeJobByDocument(documentType: com.restaurant.sushimei.frontend.data.model.PrintDocumentType, documentId: Long): Flow<PrintJobEntity?>
    @Query("SELECT * FROM print_attempts ORDER BY startedAt DESC")
    fun observeAllAttempts(): Flow<List<PrintAttemptEntity>>

    @Query("SELECT * FROM print_jobs WHERE status IN (:statuses)")
    suspend fun getJobsByStatus(statuses: List<PrintJobStatus>): List<PrintJobEntity>

    @Update
    suspend fun updateJob(job: PrintJobEntity)

    @Insert
    suspend fun insertAttempt(attempt: PrintAttemptEntity)

    @Update
    suspend fun updateAttempt(attempt: PrintAttemptEntity)

    @Query("SELECT * FROM print_attempts WHERE printJobId = :jobId ORDER BY startedAt DESC")
    suspend fun getAttemptsForJob(jobId: String): List<PrintAttemptEntity>

    @Query("SELECT * FROM print_attempts WHERE printJobId = :jobId ORDER BY startedAt DESC")
    fun observeAttemptsForJob(jobId: String): Flow<List<PrintAttemptEntity>>

    @Query("SELECT * FROM print_attempts WHERE id = :id LIMIT 1")
    suspend fun getAttemptById(id: String): PrintAttemptEntity?

    @Query("SELECT * FROM print_attempts WHERE status = :status")
    suspend fun getAttemptsByStatus(status: PrintAttemptStatus): List<PrintAttemptEntity>

    @Query("UPDATE print_jobs SET status = :status, updatedAt = :timestamp WHERE id = :jobId")
    suspend fun updateJobStatus(jobId: String, status: PrintJobStatus, timestamp: Long)

    @Query("UPDATE print_jobs SET activeAttemptId = :attemptId WHERE id = :jobId")
    suspend fun setJobActiveAttempt(jobId: String, attemptId: String?)

    @Transaction
    suspend fun beginAttempt(jobId: String, type: PrintAttemptType, attemptId: String, timestamp: Long): PrintAttemptEntity? {
        val job = getJobById(jobId) ?: return null

        if (job.activeAttemptId != null) return null

        when (type) {
            PrintAttemptType.ORIGINAL -> {
                if (job.status != PrintJobStatus.PENDING) return null
                updateJobStatus(jobId, PrintJobStatus.PRINTING, timestamp)
            }
            PrintAttemptType.RETRY -> {
                if (job.status != PrintJobStatus.FAILED && job.status != PrintJobStatus.INTERRUPTED) return null
                updateJobStatus(jobId, PrintJobStatus.PRINTING, timestamp)
            }
            PrintAttemptType.REPRINT -> {
                if (job.status != PrintJobStatus.PRINTED && job.status != PrintJobStatus.REPRINT_READY) return null
                // DO NOT change parent status for REPRINT
            }
            PrintAttemptType.INTERNAL_COPY -> {
                if (job.status != PrintJobStatus.PRINTED) return null
            }
        }

        setJobActiveAttempt(jobId, attemptId)

        val attempt = PrintAttemptEntity(
            id = attemptId,
            printJobId = jobId,
            type = type,
            status = PrintAttemptStatus.PRINTING,
            startedAt = timestamp,
            finishedAt = null,
            error = null
        )
        insertAttempt(attempt)
        return attempt
    }

    @Query("UPDATE print_attempts SET status = :status, finishedAt = :finishedAt, error = :error WHERE id = :attemptId")
    suspend fun updateAttemptStatus(attemptId: String, status: PrintAttemptStatus, finishedAt: Long?, error: String?)

    @Query("UPDATE print_jobs SET activeAttemptId = NULL WHERE id = :jobId AND activeAttemptId = :attemptId")
    suspend fun releaseActiveAttempt(jobId: String, attemptId: String)

    @Transaction
    suspend fun finishAttempt(attemptId: String, status: PrintAttemptStatus, finishedAt: Long?, error: String?) {
        updateAttemptStatus(attemptId, status, finishedAt, error)
        val attempt = getAttemptById(attemptId) ?: return
        releaseActiveAttempt(attempt.printJobId, attemptId)
    }

    @Transaction
    suspend fun finalizeSuccess(attemptId: String, finishedAt: Long) {
        val attempt = getAttemptById(attemptId) ?: return
        val job = getJobById(attempt.printJobId) ?: return

        updateAttemptStatus(attemptId, PrintAttemptStatus.SUCCEEDED, finishedAt, null)

        if (attempt.type == PrintAttemptType.ORIGINAL || attempt.type == PrintAttemptType.RETRY) {
            val printedAt = job.printedAt ?: finishedAt
            val newJob = job.copy(
                status = PrintJobStatus.PRINTED,
                printedAt = printedAt,
                updatedAt = finishedAt,
                lastError = null,
                activeAttemptId = null
            )
            updateJob(newJob)
        } else {
            releaseActiveAttempt(attempt.printJobId, attemptId)
        }
    }

    @Transaction
    suspend fun finalizeFailure(attemptId: String, finishedAt: Long, error: String?) {
        val attempt = getAttemptById(attemptId) ?: return
        val job = getJobById(attempt.printJobId) ?: return

        updateAttemptStatus(attemptId, PrintAttemptStatus.FAILED, finishedAt, error)
        if (attempt.type == PrintAttemptType.ORIGINAL || attempt.type == PrintAttemptType.RETRY) {
            val newJob = job.copy(
                status = PrintJobStatus.FAILED,
                updatedAt = finishedAt,
                lastError = error,
                activeAttemptId = null
            )
            updateJob(newJob)
        } else {
            releaseActiveAttempt(attempt.printJobId, attemptId)
        }
    }
}
