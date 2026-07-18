package com.tuti.util

import com.tuti.api.data.requireCanonicalUuid
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object IPINBlockGenerator {
    fun getIPINBlock(
        ipin: String,
        publicKey: String?, uuid: String
    ): String {
        require(ipin.isNotBlank()) { "IPIN must not be blank" }
        requireCanonicalUuid(uuid, "uuid")
        // clear ipin = uuid +  IPIN
        val clearIpin = uuid + ipin

        val publicKeyBase64 = requireNotNull(publicKey) {
            "an explicit EBS public key is required"
        }
        val pubKey = decodeCanonicalEbsPublicKey(publicKeyBase64).key

        // Encrypt then encode as base64.
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pubKey)
        val encryptedBytes = cipher.doFinal(clearIpin.toByteArray(Charsets.UTF_8))
        return encryptedBytes.toByteString().base64()
    }
}

internal data class DecodedEbsPublicKey(
    val key: RSAPublicKey,
    val encoded: ByteArray,
)

internal fun decodeCanonicalEbsPublicKey(value: String): DecodedEbsPublicKey {
    require(value.isNotBlank() && value == value.trim()) {
        "EBS public key must be normalized canonical base64"
    }
    val bytes = value.decodeBase64()?.toByteArray()
        ?: throw IllegalArgumentException("EBS public key must be canonical base64")
    require(bytes.toByteString().base64() == value) {
        "EBS public key must be canonical base64"
    }
    val key = runCatching {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes)) as? RSAPublicKey
    }.getOrNull()
    require(key != null && key.modulus.bitLength() in 2048..4096) {
        "EBS public key must be an RSA key between 2048 and 4096 bits"
    }
    return DecodedEbsPublicKey(key, bytes)
}
