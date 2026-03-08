package com.tuti.api.data

import com.tuti.api.TutiApiClient
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TutiResponseTest {
    @Test
    fun decodesNumericCodeAndObjectDetails() {
        val response = TutiApiClient.Json.decodeFromString<TutiResponse>(
            """
            {
              "message": "ebs_error",
              "code": 502,
              "status": "EBS",
              "details": {
                "responseCode": 51,
                "responseMessage": "declined"
              }
            }
            """.trimIndent()
        )

        assertEquals("502", response.code)
        assertTrue(response.details.contains("responseCode"))
        assertTrue(response.details.contains("declined"))
        assertNotNull(response.detailsJson)
    }

    @Test
    fun decodesBillsAmountField() {
        val response = TutiApiClient.Json.decodeFromString<TutiResponse>(
            """
            {
              "due_amount": {
                "amount": "10",
                "due_amount": "8",
                "min_amount": "2",
                "paid_amount": "1"
              }
            }
            """.trimIndent()
        )

        assertEquals("10", response.dueAmount.amount)
        assertEquals("8", response.dueAmount.dueAmount)
    }
}
