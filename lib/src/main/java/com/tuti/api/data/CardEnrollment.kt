package com.tuti.api.data

import com.tuti.util.IPINBlockGenerator
import com.tuti.util.decodeCanonicalEbsPublicKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest

private val ENROLLMENT_KEY_ID = Regex("^sha256:[0-9a-f]{64}$")
private val ENROLLMENT_PAN = Regex("^(?:[0-9]{16}|[0-9]{19})$")
private val ENROLLMENT_EXPIRY = Regex("^[0-9]{4}$")
private val ENROLLMENT_IPIN = Regex("^[0-9]{4}$")
private val RFC3339_INSTANT = Regex(
    "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?Z$",
)

@Serializable
data class CardEnrollmentRailKey(
    val algorithm: String,
    @SerialName("key_id")
    val keyId: String,
    @SerialName("public_key")
    val publicKey: String,
) {
    init {
        require(algorithm == RSA_PKCS1_V1_5) {
            "unsupported card enrollment rail-key algorithm"
        }
        require(ENROLLMENT_KEY_ID.matches(keyId)) {
            "card enrollment key_id must be a lowercase SHA-256 identifier"
        }
        val encoded = decodeCanonicalEbsPublicKey(publicKey).encoded
        val expectedKeyId = "sha256:${MessageDigest.getInstance("SHA-256").digest(encoded).toHex()}"
        require(MessageDigest.isEqual(keyId.toByteArray(), expectedKeyId.toByteArray())) {
            "card enrollment key_id does not match public_key"
        }
    }

    companion object {
        const val RSA_PKCS1_V1_5 = "rsa_pkcs1_v1_5"
    }
}

@Serializable
data class CardEnrollmentIntent(
    @SerialName("enrollment_id")
    val enrollmentId: String,
    @SerialName("rail_uuid")
    val railUuid: String,
    @SerialName("expires_at")
    val expiresAt: String,
    @SerialName("rail_key")
    val railKey: CardEnrollmentRailKey,
) {
    init {
        requireCanonicalUuid(enrollmentId, "enrollment_id")
        requireCanonicalUuid(railUuid, "rail_uuid")
        require(RFC3339_INSTANT.matches(expiresAt)) {
            "expires_at must be a UTC RFC3339 instant"
        }
    }

    fun confirmation(
        pan: String,
        expiryDate: String,
        name: String,
        ipin: String,
    ): ConfirmCardEnrollmentRequest {
        require(ENROLLMENT_PAN.matches(pan)) { "PAN must contain exactly 16 or 19 digits" }
        require(ENROLLMENT_EXPIRY.matches(expiryDate)) { "exp_date must contain exactly four digits" }
        require(name == name.trim() && name.length <= 100) {
            "card name must be normalized and at most 100 characters"
        }
        require(ENROLLMENT_IPIN.matches(ipin)) { "IPIN must contain exactly four digits" }
        return ConfirmCardEnrollmentRequest(
            railUuid = railUuid,
            pan = pan,
            expiryDate = expiryDate,
            name = name,
            ipinBlock = IPINBlockGenerator.getIPINBlock(ipin, railKey.publicKey, railUuid),
        )
    }
}

/** Transient enrollment payload. Callers must not persist or log this value. */
@Serializable
class ConfirmCardEnrollmentRequest internal constructor(
    @SerialName("rail_uuid")
    val railUuid: String,
    val pan: String,
    @SerialName("exp_date")
    val expiryDate: String,
    val name: String,
    @SerialName("ipin_block")
    val ipinBlock: String,
) {
    init {
        requireCanonicalUuid(railUuid, "rail_uuid")
        require(ENROLLMENT_PAN.matches(pan)) { "PAN must contain exactly 16 or 19 digits" }
        require(ENROLLMENT_EXPIRY.matches(expiryDate)) { "exp_date must contain exactly four digits" }
        require(name == name.trim() && name.length <= 100) {
            "card name must be normalized and at most 100 characters"
        }
        requireCanonicalBase64(ipinBlock, "ipin_block")
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
