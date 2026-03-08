package com.tuti.api.data

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class DueAmount (
    @SerialName("amount")
    val amount: String = "",

    @SerialName("due_amount")
    val dueAmount: String = "",

    @SerialName("total_amount")
    val totalAmount: String = "",

    @SerialName("min_amount")
    val minAmount: String = "",

    @SerialName("paid_amount")
    val paidAmount: String = "",
)
