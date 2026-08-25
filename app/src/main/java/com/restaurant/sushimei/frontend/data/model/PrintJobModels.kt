package com.restaurant.sushimei.frontend.data.model

enum class PrintDocumentType {
    ORDER,
    BUSINESS_DAY_CLOSE
}

enum class PrintJobStatus {
    PENDING,
    PRINTING,
    PRINTED,
    FAILED,
    INTERRUPTED,
    REPRINT_READY
}

enum class PrintAttemptType {
    ORIGINAL,
    RETRY,
    REPRINT,
    INTERNAL_COPY
}

enum class PrintAttemptStatus {
    PRINTING,
    SUCCEEDED,
    FAILED,
    INTERRUPTED
}

data class PrintJobUiModel(
    val jobId: String,
    val documentType: PrintDocumentType,
    val documentId: Long,
    val status: PrintJobStatus,
    val lastError: String?,
    val printedAt: Long?,
    val activeAttemptId: String?,
    val lastReprintStatus: PrintAttemptStatus?,
    val lastReprintError: String?
)
