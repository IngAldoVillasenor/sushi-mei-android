package com.restaurant.sushimei.frontend.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus
import com.restaurant.sushimei.frontend.data.model.PrintAttemptType

@Entity(
    tableName = "print_attempts",
    foreignKeys = [
        ForeignKey(
            entity = PrintJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["printJobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["printJobId"])
    ]
)
data class PrintAttemptEntity(
    @PrimaryKey val id: String,
    val printJobId: String,
    val type: PrintAttemptType,
    val status: PrintAttemptStatus,
    val startedAt: Long,
    val finishedAt: Long?,
    val error: String?
)
