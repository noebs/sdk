package com.tuti.api.data

import com.tuti.api.TutiApiClient
import com.tuti.api.TEST_EBS_PUBLIC_KEY
import com.tuti.api.ebs.EBSRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.ByteString.Companion.decodeBase64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OperationIdentityTest {
    private val cardId = "123e4567-e89b-12d3-a456-426614174000"
    private val operationUuid = "123e4567-e89b-12d3-a456-426614174001"
    private val ebsPublicKey = TEST_EBS_PUBLIC_KEY

    @Test
    fun mobileTransferClaim_matchesTheCrossLanguageCanonicalVector() {
        val claim = mobileTransferClaim()

        assertEquals(
            """{"amount_minor":1250,"card_id":"123e4567-e89b-12d3-a456-426614174000","currency":"SDG","operation":"mobile_transfer","target":{"phone":"0912345678"},"version":1}""",
            claim.canonicalJson,
        )
        assertEquals(
            "v1:707624aff13bcee534e211eb26797f6d2f65966deeb3d560a9abe54aefdaaf8c",
            claim.requestClaim,
        )
    }

    @Test
    fun balanceInquiry_matchesTheCrossLanguageClaimAndWireContract() {
        val balanceCardId = "123e4567-e89b-42d3-a456-426614174000"
        val identity = OperationIdentity.createBalanceInquiry(operationUuid, balanceCardId)
        val request = BalanceInquiryOperationRequest.create(
            identity = identity,
            cardId = balanceCardId,
            ipinBlock = "Y2lwaGVydGV4dA==",
        )

        assertEquals(
            """{"card_id":"123e4567-e89b-42d3-a456-426614174000","operation":"balance_inquiry","version":1}""",
            balanceInquiryCanonicalJson(balanceCardId),
        )
        assertEquals(
            "v1:156f27e07145ee6a12bfbd6ce2111f1c8ba5ba8fe0d9f728a1e07de867dcc07a",
            identity.requestClaim,
        )
        assertEquals(
            """{"uuid":"123e4567-e89b-12d3-a456-426614174001","request_claim":"v1:156f27e07145ee6a12bfbd6ce2111f1c8ba5ba8fe0d9f728a1e07de867dcc07a","card_authorization":{"card_id":"123e4567-e89b-42d3-a456-426614174000","rail_uuid":"123e4567-e89b-12d3-a456-426614174001","ipin_block":"Y2lwaGVydGV4dA=="}}""",
            TutiApiClient.Json.encodeToString(request),
        )

        assertThrows(OperationClaimMismatchException::class.java) {
            BalanceInquiryOperationRequest.create(
                identity,
                "123e4567-e89b-42d3-a456-426614174099",
                "Y2lwaGVydGV4dA==",
            )
        }

        val encrypted = BalanceInquiryOperationRequest.create(
            identity = identity,
            cardId = balanceCardId,
            ipin = "1234",
            publicKey = ebsPublicKey,
        )
        assertEquals(256, encrypted.cardAuthorization.ipinBlock.decodeBase64()?.size)
        assertThrows(IllegalArgumentException::class.java) {
            BalanceInquiryOperationRequest.create(identity, balanceCardId, "12x4", ebsPublicKey)
        }

        assertThrows(IllegalArgumentException::class.java) {
            BalanceAmounts(Double.NaN, 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BalanceAmounts(1.0, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun tokenAndBillClaims_matchTheCrossLanguageCanonicalVectors() {
        val token = OperationClaim.tokenPayment(
            cardId = cardId,
            amountMinor = 2_500,
            currency = "SDG",
            tokenId = "123e4567-e89b-12d3-a456-426614174099",
        )
        val bill = OperationClaim.billPayment(
            cardId = cardId,
            amountMinor = 500,
            currency = "SDG",
            billerId = "nec",
            accountReference = "meter-42",
        )

        assertEquals(
            """{"amount_minor":2500,"card_id":"123e4567-e89b-12d3-a456-426614174000","currency":"SDG","operation":"token_payment","target":{"token_id":"123e4567-e89b-12d3-a456-426614174099"},"version":1}""",
            token.canonicalJson,
        )
        assertEquals(
            "v1:2c8f681509dfec2db8b0c17773314c662022553ec94365b5c1544da12af060d6",
            token.requestClaim,
        )
        assertEquals(
            """{"amount_minor":500,"card_id":"123e4567-e89b-12d3-a456-426614174000","currency":"SDG","operation":"bill_payment","target":{"account_reference":"meter-42","biller_id":"nec"},"version":1}""",
            bill.canonicalJson,
        )
        assertEquals(
            "v1:98cd3031c20f446da0e6d9141eacd4a60381132f1cfe624f398fdd0a3c33c62e",
            bill.requestClaim,
        )
    }

    @Test
    fun persistedIdentity_reusesTheSameUuidAndClaimAcrossAttempts() {
        val firstClaim = mobileTransferClaim()
        val firstIdentity = OperationIdentity.create(operationUuid, firstClaim)
        val stored = TutiApiClient.Json.encodeToString(firstIdentity)
        val restored = TutiApiClient.Json.decodeFromString<OperationIdentity>(stored)
        val reconstructedClaim = mobileTransferClaim()

        val firstAttempt = MobileTransferOperationRequest.create(
            identity = firstIdentity,
            cardId = cardId,
            amountMinor = 1_250,
            currency = "SDG",
            phone = "0912345678",
            ipinBlock = "Zmlyc3QtY2lwaGVydGV4dA==",
        )
        val retry = MobileTransferOperationRequest.create(
            identity = restored,
            cardId = cardId,
            amountMinor = 1_250,
            currency = "SDG",
            phone = "0912345678",
            ipinBlock = "cmV0cnktY2lwaGVydGV4dA==",
        )

        assertEquals(firstAttempt.uuid, retry.uuid)
        assertEquals(firstAttempt.requestClaim, retry.requestClaim)
        assertEquals(firstAttempt.cardAuthorization.cardId, retry.cardAuthorization.cardId)
        assertEquals(firstAttempt.cardAuthorization.railUuid, retry.cardAuthorization.railUuid)
        assertNotEquals(firstAttempt.cardAuthorization.ipinBlock, retry.cardAuthorization.ipinBlock)
        assertEquals(firstClaim.requestClaim, reconstructedClaim.requestClaim)
    }

    @Test
    fun persistedIdentity_rejectsAChangedClaimBeforeSerializationOrNetwork() {
        val identity = OperationIdentity.create(operationUuid, mobileTransferClaim())
        val changedAmount = OperationClaim.mobileTransfer(
            cardId = cardId,
            amountMinor = 1_251,
            currency = "SDG",
            phone = "0912345678",
        )
        val changedRecipient = OperationClaim.mobileTransfer(
            cardId = cardId,
            amountMinor = 1_250,
            currency = "SDG",
            phone = "0999999999",
        )

        assertThrows(OperationClaimMismatchException::class.java) {
            MobileTransferOperationRequest.create(
                identity,
                cardId,
                changedAmount.amountMinor,
                changedAmount.currency,
                "0912345678",
                "Y2lwaGVydGV4dA==",
            )
        }
        assertThrows(OperationClaimMismatchException::class.java) {
            MobileTransferOperationRequest.create(
                identity,
                cardId,
                changedRecipient.amountMinor,
                changedRecipient.currency,
                "0999999999",
                "Y2lwaGVydGV4dA==",
            )
        }
    }

    @Test
    fun callerChosenDigestCannotRedefineTheSemanticClaim() {
        val forgedIdentity = OperationIdentity(
            uuid = operationUuid,
            requestClaim = "v1:${"0".repeat(64)}",
        )

        assertThrows(OperationClaimMismatchException::class.java) {
            CardFundedOperationRef.create(
                forgedIdentity,
                mobileTransferClaim(),
                "Y2lwaGVydGV4dA==",
            )
        }
    }

    @Test
    fun fundedOperationWireEnvelope_isExactAndRejectsDivergentRailUuid() {
        val claim = mobileTransferClaim()
        val identity = OperationIdentity.create(operationUuid, claim)
        val request = CardFundedOperationRef.create(identity, claim, "Y2lwaGVydGV4dA==")

        assertEquals(
            """{"uuid":"123e4567-e89b-12d3-a456-426614174001","request_claim":"v1:707624aff13bcee534e211eb26797f6d2f65966deeb3d560a9abe54aefdaaf8c","card_authorization":{"card_id":"123e4567-e89b-12d3-a456-426614174000","rail_uuid":"123e4567-e89b-12d3-a456-426614174001","ipin_block":"Y2lwaGVydGV4dA=="}}""",
            TutiApiClient.Json.encodeToString(request),
        )

        assertThrows(IllegalArgumentException::class.java) {
            TutiApiClient.Json.decodeFromString<CardFundedOperationRef>(
                """{"uuid":"123e4567-e89b-12d3-a456-426614174001","request_claim":"v1:707624aff13bcee534e211eb26797f6d2f65966deeb3d560a9abe54aefdaaf8c","card_authorization":{"card_id":"123e4567-e89b-12d3-a456-426614174000","rail_uuid":"123e4567-e89b-12d3-a456-426614174002","ipin_block":"Y2lwaGVydGV4dA=="}}"""
            )
        }
    }

    @Test
    fun mobileTransferWireSemanticsAndClaimComeFromOneTypedRequest() {
        val claim = mobileTransferClaim()
        val request = MobileTransferOperationRequest.create(
            identity = OperationIdentity.create(operationUuid, claim),
            cardId = cardId,
            amountMinor = 1_250,
            currency = "SDG",
            phone = "0912345678",
            ipinBlock = "Y2lwaGVydGV4dA==",
        )

        assertEquals(
            """{"uuid":"123e4567-e89b-12d3-a456-426614174001","request_claim":"v1:707624aff13bcee534e211eb26797f6d2f65966deeb3d560a9abe54aefdaaf8c","card_authorization":{"card_id":"123e4567-e89b-12d3-a456-426614174000","rail_uuid":"123e4567-e89b-12d3-a456-426614174001","ipin_block":"Y2lwaGVydGV4dA=="},"amount_minor":1250,"currency":"SDG","target":{"phone":"0912345678"}}""",
            TutiApiClient.Json.encodeToString(request),
        )
    }

    @Test
    fun claimFactories_requireCanonicalMoneyAndTargetValues() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationClaim.mobileTransfer(cardId, 0, "SDG", "0912345678")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationClaim.mobileTransfer(cardId, 1, "sdg", "0912345678")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationClaim.mobileTransfer(cardId, 1, "SDG", " 0912345678")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CardAuthorization(cardId, operationUuid, "not base64")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CardAuthorization(cardId, operationUuid, "YQ")
        }
        assertEquals(
            "YQ==",
            CardAuthorization(cardId, operationUuid, "YQ==").ipinBlock,
        )
        assertThrows(IllegalArgumentException::class.java) {
            OperationClaim.billPayment(cardId, 1, "SDG", " nec", "meter-42")
        }
    }

    @Test
    fun operationTargets_areCanonicalizedByKeyAndEscapedAsJcsStrings() {
        val claim = OperationClaim.billPayment(
            cardId = cardId,
            amountMinor = 500,
            currency = "SDG",
            billerId = "nec",
            accountReference = "meter\"\\\uD83D\uDCA1",
        )

        assertEquals(
            """{"amount_minor":500,"card_id":"123e4567-e89b-12d3-a456-426614174000","currency":"SDG","operation":"bill_payment","target":{"account_reference":"meter\"\\💡","biller_id":"nec"},"version":1}""",
            claim.canonicalJson,
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun ebsRequestSeparatesNonFinancialConstructionFromUuidBoundProofs() {
        assertEquals("", EBSRequest().uuid)
        assertThrows(OpaqueCardOperationRequiredException::class.java) {
            EBSRequest(ebsPublicKey, "1234")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EBSRequest(operationUuid, null, "1234")
        }

        val request = EBSRequest(
            operationUuid,
            ebsPublicKey,
            "1234",
        )

        assertEquals(operationUuid, request.uuid)
        assertTrue(request.IPIN.orEmpty().isNotEmpty())
    }

    private fun mobileTransferClaim(): OperationClaim = OperationClaim.mobileTransfer(
        cardId = cardId,
        amountMinor = 1_250,
        currency = "SDG",
        phone = "0912345678",
    )
}
