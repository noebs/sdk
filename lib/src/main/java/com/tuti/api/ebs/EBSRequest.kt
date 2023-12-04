package com.tuti.api.ebs


import com.sun.jersey.core.util.Base64
import kotlinx.serialization.SerialName
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException


@kotlinx.serialization.Serializable
class EBSRequest {
    @SerialName("applicationId")
    val applicationId: String = "TutiPay"

    @SerialName("tranDateTime")
    val tranDateTime: String = getCurrentDate()

    @SerialName("UUID")
    val uuid: String = generateUUID()


    var pubKey: String? = null

    // mobile used in otp with payment card verification step
    var mobile: String? = null

    constructor(pubKey: String?, ipin: String?) {
        this.pubKey = pubKey
        IPIN = ipin
        setEncryptedIPIN(pubKey)
    }

    constructor() {
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

    private fun generateUUID(): String {
        val uuid = UUID.randomUUID()
        return uuid.toString()
    }

    fun setEncryptedIPIN(pubKey: String?) {
        IPIN = getIPINBlock(IPIN, pubKey, uuid)
    }

    private fun getIPINBlock(ipin: String?,
                             publicKey: String?, uuid: String): String? {
        // clear ipin = uuid +  IPIN
        var ipin = ipin
        val cleraIpin = uuid + ipin

        // prepare public key, get public key from its String representation as
        // base64
        val keyByte = Base64.decode(publicKey)
        // generate public key
        val s = X509EncodedKeySpec(keyByte)
        var factory: KeyFactory? = null
        try {
            factory = KeyFactory.getInstance("RSA")
        } catch (e: NoSuchAlgorithmException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        var pubKey: Key? = null
        try {
            pubKey = factory!!.generatePublic(s)
        } catch (e: InvalidKeySpecException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        try {
            // construct Cipher with encryption algrithm:RSA, cipher mode:ECB and padding:PKCS1Padding
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, pubKey)
            // calculate ipin, encryption then encoding to base64
            ipin = String(Base64.encode(cipher.doFinal(cleraIpin
                    .toByteArray())))
        } catch (e: NoSuchAlgorithmException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: NoSuchPaddingException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: InvalidKeyException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: IllegalBlockSizeException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: BadPaddingException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        return ipin
    }
}


@kotlinx.serialization.Serializable
data class NoebsTransfer(
    @SerialName("from_account") val fromAccount: String,
    @SerialName("to_account") val toAccount: String,
    @SerialName("amount") val amount: Double,
    @SerialName("signature") val signature: String
)