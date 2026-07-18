package com.tuti.api.wallet.v1

import com.tuti.api.TutiApiClient
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalletV1ModelsTest {
    private val operationUuid = "123e4567-e89b-12d3-a456-426614174001"

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
                amount = 100,
                currency = "SDG",
                idempotencyKey = operationUuid,
                feeAmount = null,
                netAmount = null,
            )
        )
        assertFalse(json.contains("feeAmount"))
        assertFalse(json.contains("netAmount"))
        assertTrue(json.contains("\"amount\":\"100\""))
    }

    @Test
    fun createWalletRequest_usesFiberRouteFieldNames() {
        val json = TutiApiClient.Json.encodeToString(
            CreateWalletRequest(
                tenantId = "tenant_1",
                userId = 123,
                currency = "SDG",
            )
        )

        assertTrue(json.contains("\"tenant_id\":\"tenant_1\""))
        assertTrue(json.contains("\"user_id\":123"))
        assertTrue(json.contains("\"currency\":\"SDG\""))
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
                idempotencyKey = operationUuid,
            )
        )
        assertFalse(json.contains("allowReturnToSource"))
    }

    @Test
    fun everyFinancialRequestRequiresAndSerializesACanonicalCallerUuid() {
        val p2p = P2PTransferRequest(
            tenantId = "tenant_1",
            idempotencyKey = operationUuid,
            currency = "SDG",
            fromWalletId = "wallet_1",
            toWalletId = "wallet_2",
            amount = 100,
            fromOwnerType = "user",
            fromOwnerId = "1",
            toOwnerType = "user",
            toOwnerId = "2",
        )
        val deposit = DepositRequest(
            tenantId = "tenant_1",
            clientReference = "deposit_1",
            providerCode = "provider_1",
            walletId = "wallet_1",
            ownerType = "user",
            ownerId = "1",
            amount = 100,
            currency = "SDG",
            idempotencyKey = operationUuid,
        )
        val withdrawal = WithdrawalRequest(
            tenantId = "tenant_1",
            clientReference = "withdrawal_1",
            providerCode = "provider_1",
            walletId = "wallet_1",
            amount = 100,
            currency = "SDG",
            ownerType = "user",
            ownerId = "1",
            idempotencyKey = operationUuid,
        )
        val manual = ManualTransferRequest(
            tenantId = "tenant_1",
            idempotencyKey = operationUuid,
            transferType = "credit",
            walletId = "wallet_1",
            amount = 100,
            currency = "SDG",
            reason = "fixture",
            requestedBy = 1,
            approvalTimeoutSeconds = 60,
        )

        listOf(p2p, deposit, withdrawal, manual).forEach { request ->
            val json = when (request) {
                is P2PTransferRequest -> TutiApiClient.Json.encodeToString(request)
                is DepositRequest -> TutiApiClient.Json.encodeToString(request)
                is WithdrawalRequest -> TutiApiClient.Json.encodeToString(request)
                is ManualTransferRequest -> TutiApiClient.Json.encodeToString(request)
                else -> error("unexpected request")
            }
            assertTrue(json.contains("\"idempotencyKey\":\"$operationUuid\""))
        }

        assertThrows(IllegalArgumentException::class.java) { p2p.copy(idempotencyKey = "") }
        assertThrows(IllegalArgumentException::class.java) {
            deposit.copy(idempotencyKey = operationUuid.uppercase())
        }
        assertThrows(IllegalArgumentException::class.java) {
            withdrawal.copy(idempotencyKey = "not-a-uuid")
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            manual.copy(idempotencyKey = " $operationUuid")
        }
        assertEquals("idempotencyKey must be a canonical lowercase UUID", error.message)
    }
}
