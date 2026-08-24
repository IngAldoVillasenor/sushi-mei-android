package com.restaurant.sushimei.frontend

import com.restaurant.sushimei.frontend.data.model.PrintAttemptType
import com.restaurant.sushimei.frontend.data.repository.IOperationalOrderRepository
import com.restaurant.sushimei.frontend.data.repository.IPrintJobRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

class PrintManager(
    private val printJobRepository: IPrintJobRepository,
    private val operationalOrderRepository: IOperationalOrderRepository,
    private val printService: PrintService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
) {


    init {
        // Bootstrap: reconcile orphaned states
        coroutineScope.launch {
            printJobRepository.reconcileOrphanedJobs()
            // Discover persisted PENDING jobs and safely process them (one-time bootstrap)
            val pendingJobs = printJobRepository.getPendingJobs()
            for (job in pendingJobs) {
                processPrintJob(job.id, PrintAttemptType.ORIGINAL)
            }
        }
    }



    suspend fun enqueuePrintJob(
        documentType: com.restaurant.sushimei.frontend.data.model.PrintDocumentType,
        documentId: Long,
        requestId: String,
        snapshotPayload: String? = null
    ): com.restaurant.sushimei.frontend.data.local.PrintJobEntity {
        val job = printJobRepository.enqueuePrint(documentType, documentId, requestId, snapshotPayload)
        val attemptType = when (job.status) {
            com.restaurant.sushimei.frontend.data.model.PrintJobStatus.PRINTED -> PrintAttemptType.REPRINT
            com.restaurant.sushimei.frontend.data.model.PrintJobStatus.FAILED,
            com.restaurant.sushimei.frontend.data.model.PrintJobStatus.INTERRUPTED -> PrintAttemptType.RETRY
            else -> PrintAttemptType.ORIGINAL
        }
        coroutineScope.launch {
            processPrintJob(job.id, attemptType)
        }
        return job
    }


    fun retryPrintJob(jobId: String) {
        coroutineScope.launch {
            processPrintJob(jobId, PrintAttemptType.RETRY)
        }
    }

    fun printInternalCopy(jobId: String) {
        coroutineScope.launch {
            processPrintJob(jobId, PrintAttemptType.INTERNAL_COPY)
        }
    }

    fun reprintJob(jobId: String) {
        coroutineScope.launch {
            processPrintJob(jobId, PrintAttemptType.REPRINT)
        }
    }


    private suspend fun processPrintJob(jobId: String, attemptType: PrintAttemptType) {
        try {
            val job = printJobRepository.getJobById(jobId) ?: return

            // 1. Transactional Attempt Level Claim (includes Parent Job logic)
            val attempt = printJobRepository.beginAttempt(jobId, attemptType)
            if (attempt == null) {
                // Preempted or invalid state
                return
            }

            try {
                val isReprint = attemptType == PrintAttemptType.REPRINT
                val isInternalCopy = attemptType == PrintAttemptType.INTERNAL_COPY

                val printed = if (job.documentType == com.restaurant.sushimei.frontend.data.model.PrintDocumentType.ORDER) {
                    val orderDetail = operationalOrderRepository.getOperationalOrderDetail(job.documentId)
                    printService.printOperationalTicket(orderDetail, isReprint, isInternalCopy)
                } else {
                    val day = com.restaurant.sushimei.frontend.data.api.NetworkModule.configuredGson.fromJson(job.snapshotPayload, com.restaurant.sushimei.frontend.data.model.BusinessDayResponse::class.java)
                    printService.printClosingTicket(day)
                }

                try {
                    if (printed) {
                        printJobRepository.finalizeSuccess(attempt.id, System.currentTimeMillis())
                    } else {
                        printJobRepository.finalizeFailure(attempt.id, System.currentTimeMillis(), "Bluetooth Error / Not Connected")
                    }
                } catch (e: Exception) {
                    // Physical output succeeded/failed, but DB update failed.
                    // DO NOT rewrite physical state. Atomicity guarantees safe stranded state.
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown Error"
                try {
                    printJobRepository.finalizeFailure(attempt.id, System.currentTimeMillis(), errorMsg)
                } catch (dbEx: Exception) {
                    // Ignore
                }
            }
        } catch (e: Exception) {
            // Failsafe catch for the coroutine so one exception doesn't kill the SupervisorJob
        }
    }

}
