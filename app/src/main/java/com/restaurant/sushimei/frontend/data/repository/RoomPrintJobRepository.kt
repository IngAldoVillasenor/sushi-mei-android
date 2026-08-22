package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.local.PrintAttemptEntity
import com.restaurant.sushimei.frontend.data.local.PrintJobDao
import com.restaurant.sushimei.frontend.data.local.PrintJobEntity
import com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus
import com.restaurant.sushimei.frontend.data.model.PrintAttemptType
import com.restaurant.sushimei.frontend.data.model.PrintJobStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoomPrintJobRepository(private val dao: PrintJobDao) : IPrintJobRepository {
    override fun observeAllJobs(): Flow<List<PrintJobEntity>> = dao.observeAllJobs()
    override fun observeJobById(id: String): Flow<PrintJobEntity?> = dao.observeJobById(id)
    override fun observeAllAttempts(): Flow<List<PrintAttemptEntity>> = dao.observeAllAttempts()
    override fun observeAttemptsForJob(jobId: String): Flow<List<PrintAttemptEntity>> = dao.observeAttemptsForJob(jobId)

    override suspend fun getJobById(id: String): PrintJobEntity? = dao.getJobById(id)

    override suspend fun getPendingJobs(): List<PrintJobEntity> = dao.getJobsByStatus(listOf(PrintJobStatus.PENDING))

    override suspend fun getAttemptsForJob(jobId: String): List<PrintAttemptEntity> = dao.getAttemptsForJob(jobId)

    override suspend fun getJobByRequestId(requestId: String): PrintJobEntity? = dao.getJobByRequestId(requestId)

    override suspend fun enqueuePrint(
        documentType: com.restaurant.sushimei.frontend.data.model.PrintDocumentType,
        documentId: Long,
        requestId: String,
        snapshotPayload: String?
    ): PrintJobEntity {
        val newJob = PrintJobEntity(
            id = UUID.randomUUID().toString(),
            requestId = requestId,
            documentType = documentType,
            documentId = documentId,
            snapshotPayload = snapshotPayload,
            status = PrintJobStatus.PENDING,
            lastError = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            printedAt = null,
            activeAttemptId = null
        )

        val rowId = dao.insertJob(newJob)
        if (rowId == -1L) {
            val byDocument = dao.getJobByDocument(documentType, documentId)
            val byRequestId = dao.getJobByRequestId(requestId)

            if (byDocument == null || byRequestId == null || byDocument.id != byRequestId.id) {
                throw IllegalStateException("Insert ignored but identifiers do not map to the same persisted job. Idempotency breach.")
            }
            return byDocument
        }

        return dao.getJobByDocument(documentType, documentId) ?: throw IllegalStateException("Failed to retrieve persisted job.")
    }

    override suspend fun markJobPrinted(jobId: String) {
        dao.getJobById(jobId)?.let { job ->
            val printedAt = job.printedAt ?: System.currentTimeMillis()
            dao.updateJob(job.copy(status = PrintJobStatus.PRINTED, printedAt = printedAt, updatedAt = System.currentTimeMillis(), lastError = null, activeAttemptId = null))
        }
    }

    override suspend fun markJobFailed(jobId: String, error: String?) {
        dao.getJobById(jobId)?.let { job ->
            dao.updateJob(job.copy(status = PrintJobStatus.FAILED, lastError = error, updatedAt = System.currentTimeMillis(), activeAttemptId = null))
        }
    }

    override suspend fun markJobInterrupted(jobId: String) {
        dao.getJobById(jobId)?.let { job ->
            dao.updateJob(job.copy(status = PrintJobStatus.INTERRUPTED, updatedAt = System.currentTimeMillis(), activeAttemptId = null))
        }
    }

    override suspend fun beginAttempt(jobId: String, type: PrintAttemptType): PrintAttemptEntity? {
        val attemptId = UUID.randomUUID().toString()
        return dao.beginAttempt(jobId, type, attemptId, System.currentTimeMillis())
    }

    override suspend fun finishAttempt(attemptId: String, status: PrintAttemptStatus, finishedAt: Long?, error: String?) {
        dao.finishAttempt(attemptId, status, finishedAt, error)
    }

    override suspend fun finalizeSuccess(attemptId: String, finishedAt: Long) {
        dao.finalizeSuccess(attemptId, finishedAt)
    }

    override suspend fun finalizeFailure(attemptId: String, finishedAt: Long, error: String?) {
        dao.finalizeFailure(attemptId, finishedAt, error)
    }

    override suspend fun reconcileOrphanedJobs() {
        val orphanedAttempts = dao.getAttemptsByStatus(PrintAttemptStatus.PRINTING)
        for (attempt in orphanedAttempts) {
            finishAttempt(attempt.id, PrintAttemptStatus.INTERRUPTED, System.currentTimeMillis(), "Interrupted by restart")
        }
        val orphanedJobs = dao.getJobsByStatus(listOf(PrintJobStatus.PRINTING))
        for (job in orphanedJobs) {
            markJobInterrupted(job.id)
        }
    }
}
