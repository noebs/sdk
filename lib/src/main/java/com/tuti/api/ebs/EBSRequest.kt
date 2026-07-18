package com.tuti.api.ebs


import com.tuti.api.data.OpaqueCardOperationRequiredException
import com.tuti.api.data.requireCanonicalUuid
import com.tuti.util.IPINBlockGenerator
import kotlinx.serialization.SerialName
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale


@kotlinx.serialization.Serializable
class EBSRequest(
    @SerialName("UUID")
    val uuid: String = "",
) {
    init {
        if (uuid.isNotEmpty()) {
            requireCanonicalUuid(uuid, "UUID")
        }
    }

    @SerialName("applicationId")
    val applicationId: String = "TutiPay"

    @SerialName("tranDateTime")
    val tranDateTime: String = getCurrentDate()

    var pubKey: String? = null

    // mobile used in otp with payment card verification step
    var mobile: String? = null

    /**
     * Builds a UUID-bound transient rail proof. The caller owns [operationUuid] and must persist it
     * with its request claim before the first attempt.
     */
    constructor(operationUuid: String, pubKey: String?, ipin: String?) : this(operationUuid) {
        this.pubKey = pubKey
        IPIN = ipin
        setEncryptedIPIN(pubKey)
    }

    /**
     * Kept so old applications still compile, but intentionally cannot create a financial request
     * with a fresh UUID. Use the operation-identity constructor above.
     */
    @Deprecated(
        message = "Financial EBS requests require a caller-persisted OperationIdentity and explicit operation UUID.",
    )
    constructor(pubKey: String?, ipin: String?) : this() {
        throw OpaqueCardOperationRequiredException("EBSRequest(pubKey, ipin)")
    }

    var authenticationType: String? = null
    var last4PanDigits: String? = null
    var listSize = 0
    var mobileNo: String? = null
    var merchantAccountType: String? = null
    var merchantAccountReference: String? = null
    var merchantName: String? = null
    var merchantCity: String? = null
    var idType: String? = null
    var idNo: String? = null
    var merchantCategoryCode: String? = null
    val postalCode: String? = null
    @SerialName("device_id")
    var deviceId: String? = ""

    var panCategory: String? = null

    @SerialName("PAN")
    var pan: String? = null
    var expDate: String? = null
    var IPIN: String? = null
    var newIPIN: String? = null
    var originalTranUUID: String? = null
    var otp: String? = null
    var entityId: String? = null
    var voucherNumber: String? = null
    var tranAmount: Float? = null
    var tranCurrencyCode: String? = null
    var userName: String? = null
    var password: String? = null
    var userPassword: String? = null
    var tranCurrency: String? = null
    var toCard: String? = null
    var toAccount: String? = null
    var payeeId: String? = null
    var paymentInfo: String? = null
    var serviceProviderId: String? = null
    var merchantID: String? = null

    /**
     * This is used to pass-on a payment token (scanned via eg QR)
     * @param quickPayToken
     */
    @SerialName("token")
    var quickPayToken: String? = null

    @SerialName("paymentDetails")
    var paymentDetailsList: List<PaymentDetails>? = null
    var qRCode: String? = null
    var phoneNo: String? = null
    var origUUID: String? = null
    var origTranID: String? = null
    var phoneNumber: String? = null

    @SerialName("pan")
    var otherPan: String? = null

    var dynamicFees = 0f
    var entityType = "Phone No"
    val entityGroup = "0"
    var registrationType = "01"
    var originalTransactionId: String? = null


    private fun getCurrentDate(): String {
        val dateFormat: DateFormat = SimpleDateFormat("ddMMyyHHmmss", Locale.US)
        val date = Date()
        return dateFormat.format(date)
    }

    fun setEncryptedIPIN(pubKey: String?) {
        check(uuid.isNotEmpty()) {
            "a caller-persisted operation UUID is required before creating an IPIN block"
        }
        IPIN = IPINBlockGenerator.getIPINBlock(
            ipin = requireNotNull(IPIN) { "IPIN is required" },
            publicKey = pubKey,
            uuid = uuid,
        )
    }
}


@kotlinx.serialization.Serializable
data class NoebsTransfer(
    @SerialName("from_account") val fromAccount: String?,
    @SerialName("to_account") val toAccount: String?,
    @SerialName("amount") val amount: Double?,
    @SerialName("signature") val signature: String?,
    @SerialName("uuid") val uuid: String?,
    @SerialName("signed_uuid") val signedUUid: String?,
    @SerialName("timestamp") val timestamp: String = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toString()
)
