package com.tuti.util

import com.tuti.api.data.requireCanonicalUuid
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyFactory
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

        // Prepare public key (base64 -> bytes -> RSA key).
        val publicKeyBase64 = requireNotNull(publicKey?.takeIf { it.isNotBlank() }) {
            "an explicit EBS public key is required"
        }
        val keyBytes =
            publicKeyBase64.decodeBase64()?.toByteArray()
                ?: throw IllegalArgumentException("Invalid base64 public key")
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))

        // Encrypt then encode as base64.
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pubKey)
        val encryptedBytes = cipher.doFinal(clearIpin.toByteArray(Charsets.UTF_8))
        return encryptedBytes.toByteString().base64()
    }
}
