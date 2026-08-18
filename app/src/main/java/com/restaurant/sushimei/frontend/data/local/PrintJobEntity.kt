package com.restaurant.sushimei.frontend.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.restaurant.sushimei.frontend.data.model.PrintJobStatus

@Entity(
    tableName = "print_jobs",
    indices = [
        Index(value = ["orderId"], unique = true),
        Index(value = ["requestId"], unique = true)
    ]
)
data class PrintJobEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val orderId: Long,
    val status: PrintJobStatus,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val printedAt: Long?,
    val activeAttemptId: String? = null
)
