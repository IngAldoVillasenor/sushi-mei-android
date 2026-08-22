package com.restaurant.sushimei.frontend.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.restaurant.sushimei.frontend.data.model.PrintJobStatus

import com.restaurant.sushimei.frontend.data.model.PrintDocumentType

@Entity(
    tableName = "print_jobs",
    indices = [
        Index(value = ["documentType", "documentId"], unique = true),
        Index(value = ["requestId"], unique = true)
    ]
)
data class PrintJobEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val documentType: PrintDocumentType,
    val documentId: Long,
    val snapshotPayload: String?,
    val status: PrintJobStatus,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val printedAt: Long?,
    val activeAttemptId: String? = null
)
