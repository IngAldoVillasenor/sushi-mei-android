package com.restaurant.sushimei.frontend.data.model

import com.google.gson.annotations.SerializedName

class OrderRecord (
    val id: Long,
    val phoneNumber: String,
    val deliveryType: String,
    val deliveryAddress: String?,
    val paymentNotes: String?,
    val orderDetails: String,
    val totalAmount: Double,
    val status: String,
    val transferReceiptPath: String?,
    // val createdAt: String
)

data class RejectRequest(
    val reason: String
)