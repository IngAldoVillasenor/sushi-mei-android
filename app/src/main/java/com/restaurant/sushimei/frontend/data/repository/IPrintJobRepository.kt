package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.local.PrintAttemptEntity
import com.restaurant.sushimei.frontend.data.local.PrintJobEntity
import com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus
import com.restaurant.sushimei.frontend.data.model.PrintAttemptType
import kotlinx.coroutines.flow.Flow

interface IPrintJobRepository {
    suspend fun getJobByRequestId(requestId: String): PrintJobEntity?
    suspend fun getJobByDocument(documentType: com.restaurant.sushimei.frontend.data.model.PrintDocumentType, documentId: Long): PrintJobEntity?
    fun observeJobByDocument(documentType: com.restaurant.sushimei.frontend.data.model.PrintDocumentType, documentId: Long): Flow<PrintJobEntity?>
    fun observeAllJobs(): Flow<List<PrintJobEntity>>
    fun observeJobById(id: String): Flow<PrintJobEntity?>
    fun observeAllAttempts(): Flow<List<PrintAttemptEntity>>
    fun observeAttemptsForJob(jobId: String): Flow<List<PrintAttemptEntity>>
    suspend fun getJobById(id: String): PrintJobEntity?
    suspend fun getPendingJobs(): List<PrintJobEntity>
    suspend fun getAttemptsForJob(jobId: String): List<PrintAttemptEntity>
    suspend fun ensureReprintReadyJob(documentType: com.restaurant.sushimei.frontend.data.model.PrintDocumentType, documentId: Long, requestId: String): PrintJobEntity
    suspend fun enqueuePrint(documentType: com.restaurant.sushimei.frontend.data.model.PrintDocumentType, documentId: Long, requestId: String, snapshotPayload: String? = null): PrintJobEntity
    suspend fun markJobPrinted(jobId: String)
    suspend fun markJobFailed(jobId: String, error: String?)
    suspend fun markJobInterrupted(jobId: String)

    suspend fun reconcileOrphanedJobs()
    suspend fun beginAttempt(jobId: String, type: PrintAttemptType): PrintAttemptEntity?
    suspend fun finishAttempt(attemptId: String, status: PrintAttemptStatus, finishedAt: Long?, error: String?)
    suspend fun finalizeSuccess(attemptId: String, finishedAt: Long)
    suspend fun finalizeFailure(attemptId: String, finishedAt: Long, error: String?)
}
