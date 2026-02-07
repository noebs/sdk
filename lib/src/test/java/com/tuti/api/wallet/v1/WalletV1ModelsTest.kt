package com.tuti.api.wallet.v1

import com.tuti.api.TutiApiClient
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalletV1ModelsTest {
    @Test
    fun ensureWallet_serializesInt64AsJsonString() {
        val json = TutiApiClient.Json.encodeToString(
            EnsureWalletRequest(
                tenantId = "tenant_1",
                userId = 123,
                currency = "SDG",
            )
        )
        assertTrue(json.contains("\"userId\":\"123\""))
    }

    @Test
    fun depositRequest_omitsNullOptionals() {
        val json = TutiApiClient.Json.encodeToString(
            DepositRequest(
                tenantId = "tenant_1",
                clientReference = "ref_1",
                providerCode = "provider_1",
                walletId = "wallet_1",
                ownerType = "user",
                ownerId = "user_1",
                pspTransactionId = "psp_1",
                amount = 100,
                currency = "SDG",
                feeAmount = null,
                netAmount = null,
            )
        )
        assertFalse(json.contains("feeAmount"))
        assertFalse(json.contains("netAmount"))
        assertTrue(json.contains("\"amount\":\"100\""))
    }

    @Test
    fun withdrawalRequest_defaultAllowReturnToSourceIsOmitted() {
        val json = TutiApiClient.Json.encodeToString(
            WithdrawalRequest(
                tenantId = "tenant_1",
                clientReference = "ref_1",
                providerCode = "provider_1",
                walletId = "wallet_1",
                amount = 100,
                currency = "SDG",
                ownerType = "user",
                ownerId = "user_1",
            )
        )
        assertFalse(json.contains("allowReturnToSource"))
    }
}

