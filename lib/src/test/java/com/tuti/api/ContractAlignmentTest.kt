package com.tuti.api

import com.tuti.api.authentication.SignInResponse
import com.tuti.api.authentication.SignUpRequest
import com.tuti.api.authentication.SignUpResponse
import com.tuti.api.data.PaymentToken
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContractAlignmentTest {
    @Test
    fun signInResponse_decodesOtpResponsesAndUppercaseUserFields() {
        val response = TutiApiClient.Json.decodeFromString<SignInResponse>(
            """
            {
              "result": "ok",
              "pubkey": "pub-key",
              "user": {
                "ID": 42,
                "mobile": "0912345678",
                "fullname": "Jane Doe"
              }
            }
            """.trimIndent()
        )

        assertEquals("ok", response.result)
        assertEquals("pub-key", response.publicKey)
        assertEquals(42, response.user.id)
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
}
