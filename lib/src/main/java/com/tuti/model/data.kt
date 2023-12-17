package com.tuti.model

import com.tuti.api.data.Card
import com.tuti.api.data.PaymentToken
import com.tuti.api.ebs.EBSResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor

@Serializable
data class SignInRequest(
        @SerialName("password")
        val password: String? = "",
        @SerialName("otp")
        val otp: String? = "",
        @SerialName("mobile")
        val mobile: String,
        @SerialName("authorization")
        val auth: String? = "",
        @SerialName("signature")
        val signature: String? = "",
        val message: String? = "",
)

@Serializable
data class SignupRequest(
        @SerialName("password")
        val password: String,
        @SerialName("otp")
        val otp: String? = "",
        @SerialName("mobile")
        val mobile: String,
        @SerialName("authorization")
        val auth: String? = "",
        @SerialName("signature")
        val signature: String? = "",
        val message: String? = "",
        val fullname: String,
        val password2: String? = "",
        @SerialName("user_pubkey")
        val pubkey: String,
)


@Serializable
data class GenerateOTP(
        val mobile: String,
        val password: String? = "",
        @SerialName("fullname") val name: String? = "",
        @SerialName("user_pubkey") val pubkey: String,
)


@Serializable
data class Notification(
        @SerialName("phone") val phone: String?,
        val type: String?,
        val to: String?,
        val body: String?,
        val date: Long?,
        val title: String?,
        val data: EBSResponse?,
        @SerialName("UUID") val uuid: String?,
        @SerialName("is_read") val isRead: Boolean?,
        @SerialName("call_to_action") val callToAction: String?,
        @SerialName("payment_request") val paymentToken: PaymentToken?,
)

data class NotificationFilters(
        val mobile: String,
        val getAll: Boolean = false,
)


@Serializable
data class User (
        val fullname: String,
        @SerialName("mobile")
        val mobile: String,
        val email: String,
        @SerialName("Cards")
        val cards: List<Card>,
)


@Serializable
data class DapiTransaction(
        val ID: Int,
        val CreatedAt: String,
        val UpdatedAt: String,
        val DeletedAt: String?,
        val responseCode: Int
)

@Serializable
data class DapiResponse(
        val data: List<DapiTransaction>
)

@Serializable
data class LedgerResponse(
        val data: List<Ledger>
)

@Serializable
data class Ledger(
        @SerialName("account_id")
        val AccountID: String,
        @SerialName("transaction_id")
        val TransactionID: String,
        @SerialName("amount")
        val Amount: Float,
        @SerialName("type")
        val Type: String,
        @SerialName("time")
        val Time: Long,
)

@Serializable
data class NoebsTransaction(
        @SerialName("account_id")
        val AccountID: String?,
        @SerialName("transaction_id")
        val TransactionID: String,
        @SerialName("amount")
        val Amount: Float,
        @SerialName("time")
        val Time: Long,
        @SerialName("comment")
        val comment: String?,
        @SerialName("from_account")
        val fromAccount: String?,
        @SerialName("to_account")
        val toAccount: String?,
)

