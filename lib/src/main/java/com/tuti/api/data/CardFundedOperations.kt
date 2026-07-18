package com.tuti.api.data

import com.tuti.util.IPINBlockGenerator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.decodeBase64

private val FUNDED_IPIN = Regex("^[0-9]{4}$")

/** Transient rail proof. It is bound to the public operation UUID and must never be persisted. */
@Serializable
data class CardAuthorization(
    @SerialName("card_id")
    val cardId: String,
    @SerialName("rail_uuid")
    val railUuid: String,
    @SerialName("ipin_block")
    val ipinBlock: String,
) {
    init {
        requireCanonicalCardId(cardId)
        requireCanonicalUuid(railUuid, "rail_uuid")
        requireCanonicalBase64(ipinBlock, "ipin_block")
    }
}

/** The common wire envelope for every opaque-card funded endpoint. */
@Serializable
class CardFundedOperationRef private constructor(
    val uuid: String,
    @SerialName("request_claim")
    val requestClaim: String,
    @SerialName("card_authorization")
    val cardAuthorization: CardAuthorization,
) {
    init {
        requireCanonicalUuid(uuid, "uuid")
        requireRequestClaim(requestClaim)
        require(cardAuthorization.railUuid == uuid) {
            "card_authorization.rail_uuid must equal uuid"
        }
    }

    companion object {
        fun create(
            identity: OperationIdentity,
            claim: OperationClaim,
            ipinBlock: String,
        ): CardFundedOperationRef {
            identity.requireClaim(claim)
            return CardFundedOperationRef(
                uuid = identity.uuid,
                requestClaim = identity.requestClaim,
                cardAuthorization = CardAuthorization(
                    cardId = claim.cardId,
                    railUuid = identity.uuid,
                    ipinBlock = ipinBlock,
                ),
            )
        }
    }
}

/** A balance inquiry whose durable claim is bound only to the selected opaque card. */
@Serializable
class BalanceInquiryOperationRequest private constructor(
    val uuid: String,
    @SerialName("request_claim")
    val requestClaim: String,
    @SerialName("card_authorization")
    val cardAuthorization: CardAuthorization,
) {
    init {
        require(cardAuthorization.railUuid == uuid) {
            "card_authorization.rail_uuid must equal uuid"
        }
        OperationIdentity(uuid, requestClaim).requireBalanceInquiry(cardAuthorization.cardId)
    }

    companion object {
        fun create(
            identity: OperationIdentity,
            cardId: String,
            ipin: String,
            publicKey: String,
        ): BalanceInquiryOperationRequest {
            require(FUNDED_IPIN.matches(ipin)) { "IPIN must contain exactly four digits" }
            return create(
                identity = identity,
                cardId = cardId,
                ipinBlock = IPINBlockGenerator.getIPINBlock(ipin, publicKey, identity.uuid),
            )
        }

        fun create(
            identity: OperationIdentity,
            cardId: String,
            ipinBlock: String,
        ): BalanceInquiryOperationRequest = BalanceInquiryOperationRequest(
            uuid = identity.uuid,
            requestClaim = identity.requireBalanceInquiry(cardId).requestClaim,
            cardAuthorization = CardAuthorization(cardId, identity.uuid, ipinBlock),
        )
    }
}

@Serializable
data class BalanceAmounts(
    val available: Double,
    val ledger: Double,
) {
    init {
        require(available.isFinite()) { "available balance must be finite" }
        require(ledger.isFinite()) { "ledger balance must be finite" }
    }
}

@Serializable
data class BalanceInquiryResult(
    val uuid: String,
    val balance: BalanceAmounts,
) {
    init {
        requireCanonicalUuid(uuid, "uuid")
    }
}

@Serializable
data class MobileTransferTarget(val phone: String) {
    init {
        requireCanonicalTargetValue("phone", phone)
    }
}

@Serializable
data class TokenPaymentTarget(
    @SerialName("token_id")
    val tokenId: String,
) {
    init {
        requireCanonicalUuid(tokenId, "token_id")
    }
}

@Serializable
data class BillPaymentTarget(
    @SerialName("biller_id")
    val billerId: String,
    @SerialName("account_reference")
    val accountReference: String,
) {
    init {
        requireCanonicalTargetValue("biller_id", billerId)
        requireCanonicalTargetValue("account_reference", accountReference)
    }
}

/** A mobile transfer body whose transport target and replay claim are derived together. */
@Serializable
class MobileTransferOperationRequest private constructor(
    val uuid: String,
    @SerialName("request_claim")
    val requestClaim: String,
    @SerialName("card_authorization")
    val cardAuthorization: CardAuthorization,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String,
    val target: MobileTransferTarget,
) {
    init {
        requireMatchingEnvelope(
            uuid,
            requestClaim,
            cardAuthorization,
            OperationClaim.mobileTransfer(
                cardAuthorization.cardId,
                amountMinor,
                currency,
                target.phone,
            ),
        )
    }

    companion object {
        fun create(
            identity: OperationIdentity,
            cardId: String,
            amountMinor: Long,
            currency: String,
            phone: String,
            ipinBlock: String,
        ): MobileTransferOperationRequest = MobileTransferOperationRequest(
            identity.uuid,
            identity.requestClaim,
            CardAuthorization(cardId, identity.uuid, ipinBlock),
            amountMinor,
            currency,
            MobileTransferTarget(phone),
        )
    }
}

/** A payment-token body whose transport target and replay claim are derived together. */
@Serializable
class TokenPaymentOperationRequest private constructor(
    val uuid: String,
    @SerialName("request_claim")
    val requestClaim: String,
    @SerialName("card_authorization")
    val cardAuthorization: CardAuthorization,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String,
    val target: TokenPaymentTarget,
) {
    init {
        requireMatchingEnvelope(
            uuid,
            requestClaim,
            cardAuthorization,
            OperationClaim.tokenPayment(
                cardAuthorization.cardId,
                amountMinor,
                currency,
                target.tokenId,
            ),
        )
    }

    companion object {
        fun create(
            identity: OperationIdentity,
            cardId: String,
            amountMinor: Long,
            currency: String,
            tokenId: String,
            ipinBlock: String,
        ): TokenPaymentOperationRequest = TokenPaymentOperationRequest(
            identity.uuid,
            identity.requestClaim,
            CardAuthorization(cardId, identity.uuid, ipinBlock),
            amountMinor,
            currency,
            TokenPaymentTarget(tokenId),
        )
    }
}

/** A bill-payment body whose transport target and replay claim are derived together. */
@Serializable
class BillPaymentOperationRequest private constructor(
    val uuid: String,
    @SerialName("request_claim")
    val requestClaim: String,
    @SerialName("card_authorization")
    val cardAuthorization: CardAuthorization,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String,
    val target: BillPaymentTarget,
) {
    init {
        requireMatchingEnvelope(
            uuid,
            requestClaim,
            cardAuthorization,
            OperationClaim.billPayment(
                cardAuthorization.cardId,
                amountMinor,
                currency,
                target.billerId,
                target.accountReference,
            ),
        )
    }

    companion object {
        fun create(
            identity: OperationIdentity,
            cardId: String,
            amountMinor: Long,
            currency: String,
            billerId: String,
            accountReference: String,
            ipinBlock: String,
        ): BillPaymentOperationRequest = BillPaymentOperationRequest(
            identity.uuid,
            identity.requestClaim,
            CardAuthorization(cardId, identity.uuid, ipinBlock),
            amountMinor,
            currency,
            BillPaymentTarget(billerId, accountReference),
        )
    }
}

private fun requireMatchingEnvelope(
    uuid: String,
    requestClaim: String,
    cardAuthorization: CardAuthorization,
    claim: OperationClaim,
) {
    require(cardAuthorization.railUuid == uuid) {
        "card_authorization.rail_uuid must equal uuid"
    }
    OperationIdentity(uuid, requestClaim).requireClaim(claim)
}

internal fun requireCanonicalBase64(value: String, fieldName: String) {
    require(value.isNotEmpty() && value == value.trim()) { "$fieldName must not be blank" }
    val decoded = value.decodeBase64()
    require(decoded != null && decoded.size > 0 && decoded.base64() == value) {
        "$fieldName must be canonical non-empty base64"
    }
}
