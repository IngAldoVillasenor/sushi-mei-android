package com.restaurant.sushimei.frontend.data.model

enum class PrintJobStatus {
    PENDING,
    PRINTING,
    PRINTED,
    FAILED,
    INTERRUPTED
}

enum class PrintAttemptType {
    ORIGINAL,
    RETRY,
    REPRINT
}

enum class PrintAttemptStatus {
    PRINTING,
    SUCCEEDED,
    FAILED,
    INTERRUPTED
}

data class PrintJobUiModel(
    val jobId: String,
    val orderId: Long,
    val status: PrintJobStatus,
    val lastError: String?,
    val printedAt: Long?,
    val activeAttemptId: String?,
    val lastReprintStatus: PrintAttemptStatus?,
    val lastReprintError: String?
)
