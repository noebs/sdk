package com.tuti.api.data

import com.tuti.api.ebs.EBSResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.*

/**
 * PaymentToken represents a payment order that a user generates such that it can be charged back
 */
@kotlinx.serialization.Serializable
class PaymentToken(
        val amount: Int = 0,

        @SerialName("cart_id")
        val cartId: String = "",

        @SerialName("uuid")
        val uuid: String = "",

        val mobile: String = "",

        @SerialName("note")
        val paymentNote: String = "",

        @SerialName("toCard")
        val cardTobePaid: String = "",

        @SerialName("transaction")
        val transactions: List<EBSResponse> = emptyList(),

        @SerialName("is_paid")
        val isPaymentSuccessful: Boolean = false,

        ) {
    @Deprecated("Use transactions instead.")
    val transaction: EBSResponse
        get() = transactions.firstOrNull() ?: EBSResponse()

    companion object {
        /**
         * ParseQRToken creates a PaymentToken object from a base64 encoded QR code. This is method
         * can be used when showing a tuti encoded QR token to a user, the user can then scan that
         * and creates a [PaymentToken] object from the QR.
         * @param b64Token
         * @return
         */
        @JvmStatic
        fun ParseQRToken(b64Token: String?): PaymentToken? {
            val json = Json { ignoreUnknownKeys = true }
            val parsedToken = Base64.getDecoder().decode(b64Token)
//            val gson = Gson()
//            val targetReader: Reader = StringReader(String(parsedToken))
            return try {
//                val pt = gson.fromJson(targetReader, PaymentToken::class.java)
//                targetReader.close()
                val pt = json.decodeFromString<PaymentToken>(String(parsedToken))
                pt
            } catch (e: Exception) {
                null
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class PaymentRequest(
    val mobile:String,
    val toCard:String,
    val amount:Long,
)
