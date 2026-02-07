package com.tuti.api.ebs

import com.tuti.api.TutiApiClient
import com.tuti.model.PayeeID
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EBSResponseTest {
    @Test
    fun getDueAmount() {
        val payload = """{"billInfo":{"totalAmount":"100"}}"""
        val ebsResponse = TutiApiClient.Json.decodeFromString<EBSResponse>(payload)
        assertEquals("100", ebsResponse.getDueAmount(PayeeID.ZainPostpaid))
    }
}

