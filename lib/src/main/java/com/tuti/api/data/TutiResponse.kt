package com.tuti.api.data

import com.tuti.api.ebs.EBSResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.Serial
import java.util.*

/**
 * TutiResponse class the encapsulates all noebs responses. Reference class to get
 * values for about any field
 */

@kotlinx.serialization.Serializable
data class TutiResponse(
        val message: String = "",
        val code: String = "",
        val uuid: String = "",
        val result: String = "",
        @SerialName("biller_id")
        val billerId: String = "",
        val eBSFailedResponse: EBSResponse = EBSResponse(),

        val token: String = "",

        val status: String = "",
        val details: String = "",
        @SerialName("data") val data: Data?=null,

        @SerialName("due_amount")
        val dueAmount: DueAmount = DueAmount(),
        val fees: Fees? = Fees(),
        @SerialName("payment_link")
        val paymentLink: String? = null,

        @SerialName("ebs_response")
        val ebsResponse: EBSResponse = EBSResponse()
    //TODO(adonese): some of dapi / noebs fields are not exposed here, transaction_id from noebs wallet
) {

    fun getRawPaymentToken(): String {
        return result;
    }

    // getPaymentTokenUUID in payment token api, we use uuid field to denote
    // the payment token
    fun getPaymentTokenUUID(): String {
        return uuid;
    }

    val encodedQRToken: ByteArray
        get() = Base64.getDecoder().decode(result)

    fun getQRToken(): PaymentToken? {
        val json = Json { ignoreUnknownKeys = true }

//        val gson = Gson()
        val initialArray = encodedQRToken
//        val targetReader: Reader = StringReader(String(initialArray))
        return try {
//            val pt = gson.fromJson(targetReader, PaymentToken::class.java
            val pt = json.decodeFromString<PaymentToken>(String(initialArray))
//            targetReader.close()

            pt
        } catch (e: Exception) {
            null
        }
    }

    // get EBS public key for IPIN generation
    fun getiPINKey(): String? {
        return this.ebsResponse.pubKeyValue
    }

    /**
     * get EBS public key for encryption (used throughout the code to encrypt all transactions except for IPIN generation. Check {@link #getiPINKey()} instead
     */
    fun getKey(): String? {
        return this.ebsResponse.pubKeyValue
    }
}

@Serializable
data class Data (
    @SerialName("transaction_id") val transactionId: String = "",
    @SerialName("from_account") val fromAccount: String = "",
    @SerialName("to_account") val toAccount: String = "",
    @SerialName("amount") val amount: String = "",
    @SerialName("uuid") val uuid: String = "",
    @SerialName("signed_uuid") val signedUUID: String = "",
)
