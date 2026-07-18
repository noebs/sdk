package com.tuti.api.data

import com.tuti.api.TEST_EBS_KEY_ID
import com.tuti.api.TEST_EBS_PUBLIC_KEY
import com.tuti.api.TutiApiClient
import kotlinx.serialization.encodeToString
import okio.ByteString.Companion.decodeBase64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardEnrollmentTest {
    private val intent = CardEnrollmentIntent(
        enrollmentId = "123e4567-e89b-12d3-a456-426614174010",
        railUuid = "123e4567-e89b-12d3-a456-426614174011",
        expiresAt = "2026-07-18T20:10:30.123456Z",
        railKey = CardEnrollmentRailKey(
            algorithm = CardEnrollmentRailKey.RSA_PKCS1_V1_5,
            keyId = TEST_EBS_KEY_ID,
            publicKey = TEST_EBS_PUBLIC_KEY,
        ),
    )

    @Test
    fun confirmationCarriesOnlyTransientRailFields() {
        val confirmation = intent.confirmation(
            pan = "4242424242424242",
            expiryDate = "2912",
            name = "Daily",
            ipin = "1234",
        )

        val encoded = TutiApiClient.Json.encodeToString(confirmation)
        val block = confirmation.ipinBlock.decodeBase64()

        assertEquals(intent.railUuid, confirmation.railUuid)
        assertEquals(256, block?.size)
        assertTrue(encoded.contains("\"rail_uuid\":\"${intent.railUuid}\""))
        assertTrue(encoded.contains("\"pan\":\"4242424242424242\""))
        assertTrue(encoded.contains("\"ipin_block\":"))
        assertFalse(encoded.contains("\"ipin\":"))
        assertFalse(encoded.contains("public_key"))
        assertFalse(encoded.contains("key_id"))
    }

    @Test
    fun intentRejectsDowngradedOrMismatchedRailKeys() {
        assertThrows(IllegalArgumentException::class.java) {
            intent.railKey.copy(algorithm = "rsa_weak")
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent.railKey.copy(keyId = "sha256:${"0".repeat(64)}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent.railKey.copy(publicKey = "$TEST_EBS_PUBLIC_KEY\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent.railKey.copy(
                publicKey =
                    "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBANx4gKYSMv3CrWWsxdPfxDxFvl+Is/0kc1dvMI1yNWDXI3AgdI4127KMUOv7gmwZ6SnRsHX/KAM0IPRe0+Sa0vMCAwEAAQ==",
            )
        }
    }

    @Test
    fun confirmationRejectsNoncanonicalInputBeforeEncryption() {
        for (pan in listOf(" 4242424242424242", "4242", "424242424242424a")) {
            assertThrows(IllegalArgumentException::class.java) {
                intent.confirmation(pan, "2912", "Daily", "1234")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent.confirmation("4242424242424242", "29/12", "Daily", "1234")
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent.confirmation("4242424242424242", "2912", " Daily", "1234")
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent.confirmation("4242424242424242", "2912", "Daily", "12345")
        }
    }

    @Test
    fun intentIdentifiersAndExpiryAreExact() {
        assertThrows(IllegalArgumentException::class.java) {
            intent.copy(enrollmentId = " ${intent.enrollmentId}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent.copy(railUuid = intent.railUuid.uppercase())
        }
        assertThrows(IllegalArgumentException::class.java) {
            intent.copy(expiresAt = "2026-07-18T20:10:30+00:00")
        }
    }
}
