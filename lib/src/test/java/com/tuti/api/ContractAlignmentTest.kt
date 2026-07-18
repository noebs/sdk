package com.tuti.api

import com.tuti.api.authentication.SignInResponse
import com.tuti.api.authentication.SignUpRequest
import com.tuti.api.authentication.SignUpResponse
import com.tuti.api.data.PaymentToken
import com.tuti.api.data.CardFundedOperationRef
import com.tuti.api.data.CardSummary
import com.tuti.api.data.IsUser
import com.tuti.api.data.IsUserResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ContractAlignmentTest {
    @Test
    fun appConfig_requiresAnExplicitOpaqueCardCapability() {
        val absent = TutiApiClient.Json.decodeFromString<AppConfig>(
            """{"tenant_id":"tenant_1"}"""
        )
        val enabled = TutiApiClient.Json.decodeFromString<AppConfig>(
            """{"tenant_id":"tenant_1","features":{"opaque_card_ids":true}}"""
        )

        assertFalse(absent.features.opaqueCardIds)
        assertTrue(enabled.features.opaqueCardIds)
    }

    @Test
    fun signInResponse_decodesOtpResponsesAndUppercaseUserFields() {
        val response = TutiApiClient.Json.decodeFromString<SignInResponse>(
            """
            {
              "result": "ok",
              "pubkey": "pub-key",
              "user": {
                "ID": 42,
                "DeletedAt": null,
                "mobile": "0912345678",
                "fullname": "Jane Doe"
              }
            }
            """.trimIndent()
        )

        assertEquals("ok", response.result)
        assertEquals("pub-key", response.publicKey)
        assertEquals(42, response.user.id)
        assertEquals(null, response.user.deletedAt)
        assertEquals("0912345678", response.user.mobileNumber)
    }

    @Test
    fun signUpResponse_decodesStatusOnlyPayloads() {
        val response = TutiApiClient.Json.decodeFromString<SignUpResponse>(
            """
            {
              "result": "ok"
            }
            """.trimIndent()
        )

        assertEquals("ok", response.result)
    }

    @Test
    fun signUpRequest_serializesCurrentPublicKeyFieldName() {
        val request = SignUpRequest(
            password = "Password1!",
            userPubKey = "public-key",
            mobileNumber = "0912345678",
        )

        val json = TutiApiClient.Json.encodeToString(request)

        assertTrue(json.contains("\"user_pubkey\":\"public-key\""))
    }

    @Test
    fun paymentToken_decodesQuickPaymentResponse() {
        val response = TutiApiClient.Json.decodeFromString<PaymentToken>(
            """
            {
              "ebs_response": {
                "responseCode": 0,
                "responseMessage": "Approved"
              }
            }
            """.trimIndent()
        )

        assertEquals(0, response.ebsResponse.responseCode)
        assertEquals("Approved", response.ebsResponse.responseMessage)
    }

    @Test
    fun checkUser_decodesPhoneMembershipWithoutRetainingPan() {
        val response = TutiApiClient.Json.decodeFromString<IsUserResponse>(
            """
            {
              "result": [{
                "phone": "0912345678",
                "is_user": true,
                "PAN": "123456******3456"
              }]
            }
            """.trimIndent()
        )

        assertEquals(IsUser("0912345678", true), response.result.single())
        assertFalse(IsUser::class.java.declaredFields.any { it.name.equals("PAN", true) })
    }

    @Test
    fun cardSummary_acceptsOnlyOpaqueCanonicalIdsAndMaskedDisplayData() {
        val summary = TutiApiClient.Json.decodeFromString<CardSummary>(
            """
            {
              "card_id": "123e4567-e89b-12d3-a456-426614174000",
              "name": "Daily card",
              "masked_pan": "****3456",
              "exp_date": "2712",
              "is_main": true
            }
            """.trimIndent()
        )

        assertEquals("123e4567-e89b-12d3-a456-426614174000", summary.cardId)
        assertEquals("****3456", summary.maskedPan)
        assertThrows(IllegalArgumentException::class.java) {
            summary.copy(cardId = "123E4567-E89B-12D3-A456-426614174000")
        }
        assertThrows(IllegalArgumentException::class.java) {
            summary.copy(maskedPan = "1234567890123456")
        }
        assertThrows(IllegalArgumentException::class.java) {
            summary.copy(maskedPan = "123456*****3456")
        }
        assertThrows(IllegalArgumentException::class.java) {
            summary.copy(maskedPan = "••••3456")
        }
    }

    @Test
    fun fundedOperationSerializesCardIdWithoutPanOrClearIpin() {
        val request = CardFundedOperationRef(
            cardId = "123e4567-e89b-12d3-a456-426614174000",
            uuid = "123e4567-e89b-12d3-a456-426614174001",
            ipinBlock = "encrypted-proof",
        )

        val json = TutiApiClient.Json.encodeToString(request)

        assertTrue(json.contains("\"card_id\":\"123e4567-e89b-12d3-a456-426614174000\""))
        assertTrue(json.contains("\"ipin_block\":\"encrypted-proof\""))
        assertFalse(json.contains("pan", ignoreCase = true))
        assertFalse(json.contains("\"ipin\"", ignoreCase = true))

        assertThrows(IllegalArgumentException::class.java) {
            request.copy(uuid = "rail-request-id")
        }
        assertThrows(IllegalArgumentException::class.java) {
            request.copy(ipinBlock = " ")
        }
    }
}
