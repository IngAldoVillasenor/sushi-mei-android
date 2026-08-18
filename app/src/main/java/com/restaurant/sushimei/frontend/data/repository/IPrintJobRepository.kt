package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.local.PrintAttemptEntity
import com.restaurant.sushimei.frontend.data.local.PrintJobEntity
import com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus
import com.restaurant.sushimei.frontend.data.model.PrintAttemptType
import kotlinx.coroutines.flow.Flow

interface IPrintJobRepository {
    suspend fun getJobByRequestId(requestId: String): PrintJobEntity?
    fun observeAllJobs(): Flow<List<PrintJobEntity>>
    fun observeAllAttempts(): Flow<List<PrintAttemptEntity>>
    suspend fun getJobById(id: String): PrintJobEntity?
    suspend fun getPendingJobs(): List<PrintJobEntity>
    suspend fun getAttemptsForJob(jobId: String): List<PrintAttemptEntity>
    suspend fun enqueuePrint(orderId: Long, requestId: String): PrintJobEntity
    suspend fun markJobPrinted(jobId: String)
    suspend fun markJobFailed(jobId: String, error: String?)
    suspend fun markJobInterrupted(jobId: String)

    suspend fun reconcileOrphanedJobs()
    suspend fun beginAttempt(jobId: String, type: PrintAttemptType): PrintAttemptEntity?
    suspend fun finishAttempt(attemptId: String, status: PrintAttemptStatus, finishedAt: Long?, error: String?)
}
