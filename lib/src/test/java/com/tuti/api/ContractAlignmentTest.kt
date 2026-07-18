package com.tuti.api

import com.tuti.api.authentication.SignInResponse
import com.tuti.api.authentication.SignUpRequest
import com.tuti.api.authentication.SignUpResponse
import com.tuti.api.data.PaymentToken
import com.tuti.api.data.CardFundedOperationRef
import com.tuti.api.data.CardSummary
import com.tuti.api.data.IsUser
import com.tuti.api.data.IsUserResponse
import com.tuti.api.data.OperationClaim
import com.tuti.api.data.OperationIdentity
import com.tuti.model.Notification
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
              "is_main": true,
              "status": "active"
            }
            """.trimIndent()
        )

        assertEquals("123e4567-e89b-12d3-a456-426614174000", summary.cardId)
        assertEquals("****3456", summary.maskedPan)
        assertEquals("active", summary.status)
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
    fun fundedOperationSerializesStableIdentityAndOpaqueCardAuthorization() {
        val claim = OperationClaim.mobileTransfer(
            cardId = "123e4567-e89b-12d3-a456-426614174000",
            amountMinor = 1_250,
            currency = "SDG",
            phone = "0912345678",
        )
        val identity = OperationIdentity.create(
            uuid = "123e4567-e89b-12d3-a456-426614174001",
            claim = claim,
        )
        val request = CardFundedOperationRef.create(
            identity = identity,
            claim = claim,
            ipinBlock = "ZW5jcnlwdGVkLXByb29m",
        )

        val json = TutiApiClient.Json.encodeToString(request)

        assertTrue(json.contains("\"uuid\":\"123e4567-e89b-12d3-a456-426614174001\""))
        assertTrue(json.contains("\"request_claim\":\"v1:"))
        assertTrue(json.contains("\"card_authorization\":{"))
        assertTrue(json.contains("\"card_id\":\"123e4567-e89b-12d3-a456-426614174000\""))
        assertTrue(json.contains("\"rail_uuid\":\"123e4567-e89b-12d3-a456-426614174001\""))
        assertTrue(json.contains("\"ipin_block\":\"ZW5jcnlwdGVkLXByb29m\""))
        assertFalse(json.contains("pan", ignoreCase = true))
        assertFalse(json.contains("\"ipin\"", ignoreCase = true))

        assertThrows(IllegalArgumentException::class.java) {
            OperationIdentity.create(uuid = "rail-request-id", claim = claim)
        }
    }

    @Test
    fun notificationSeparatesLowercaseEventIdentityFromCanonicalTransactionIdentity() {
        val notification = TutiApiClient.Json.decodeFromString<Notification>(
            """
            {
              "phone": "0912345678",
              "type": "EBS",
              "to": "device",
              "body": "Transfer complete",
              "date": 1784419200,
              "title": "Paid",
              "data": null,
              "uuid": "123e4567-e89b-12d3-a456-426614174001:sender",
              "transaction_uuid": "123e4567-e89b-12d3-a456-426614174001",
              "is_read": false,
              "call_to_action": "transaction",
              "payment_request": null
            }
            """.trimIndent()
        )

        assertEquals("123e4567-e89b-12d3-a456-426614174001:sender", notification.uuid)
        assertEquals("123e4567-e89b-12d3-a456-426614174001", notification.transactionUuid)
    }

    @Test
    fun notificationNeverTreatsLegacyUppercaseEventUuidAsTransactionIdentity() {
        val notification = TutiApiClient.Json.decodeFromString<Notification>(
            """
            {
              "phone": null,
              "type": null,
              "to": null,
              "body": null,
              "date": null,
              "title": null,
              "data": null,
              "UUID": "123e4567-e89b-12d3-a456-426614174001",
              "is_read": null,
              "call_to_action": null,
              "payment_request": null
            }
            """.trimIndent()
        )

        assertEquals(null, notification.uuid)
        assertEquals(null, notification.transactionUuid)
    }
}
