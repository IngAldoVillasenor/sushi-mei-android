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



    suspend fun enqueuePrintJob(orderId: Long, requestId: String): com.restaurant.sushimei.frontend.data.local.PrintJobEntity {
        val job = printJobRepository.enqueuePrint(orderId, requestId)
        coroutineScope.launch {
            processPrintJob(job.id, PrintAttemptType.ORIGINAL)
        }
        return job
    }


    fun retryPrintJob(jobId: String) {
        coroutineScope.launch {
            processPrintJob(jobId, PrintAttemptType.RETRY)
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
                // Fetch Operational Order Detail
                val orderDetail = operationalOrderRepository.getOperationalOrderDetail(job.orderId)

                // Print
                val isReprint = attemptType == PrintAttemptType.REPRINT
                val printed = printService.printOperationalTicket(orderDetail, isReprint)

                if (printed) {
                    printJobRepository.finishAttempt(attempt.id, com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus.SUCCEEDED, System.currentTimeMillis(), null)
                    if (attemptType != PrintAttemptType.REPRINT) {
                        printJobRepository.markJobPrinted(jobId)
                    }
                } else {
                    printJobRepository.finishAttempt(attempt.id, com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus.FAILED, System.currentTimeMillis(), "Bluetooth Error / Not Connected")
                    if (attemptType != PrintAttemptType.REPRINT) {
                        printJobRepository.markJobFailed(jobId, "Bluetooth Error / Not Connected")
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown Error"
                printJobRepository.finishAttempt(attempt.id, com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus.FAILED, System.currentTimeMillis(), errorMsg)
                if (attemptType != PrintAttemptType.REPRINT) {
                    printJobRepository.markJobFailed(jobId, errorMsg)
                }
            }
        } catch (e: Exception) {
            // Failsafe catch for the coroutine so one exception doesn't kill the SupervisorJob
        }
    }

}
