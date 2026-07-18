package com.tuti.api.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

private val MASKED_PAN = Regex("^\\*{4}[0-9]{4}$")

/**
 * A card enrollment selector. It is an authenticated resource identifier, not a bearer secret and
 * never a derivative of the PAN.
 */
@Serializable
data class CardRef(
    @SerialName("card_id")
    val cardId: String,
) {
    init {
        requireCanonicalCardId(cardId)
    }
}

/** Safe card data suitable for lists and local display caches. */
@Serializable
data class CardSummary(
    @SerialName("card_id")
    val cardId: String,
    val name: String,
    @SerialName("masked_pan")
    val maskedPan: String,
    @SerialName("exp_date")
    val expiryDate: String,
    @SerialName("is_main")
    val isMain: Boolean = false,
    @SerialName("is_valid")
    val isValid: Boolean? = null,
    val status: String? = null,
) {
    init {
        requireCanonicalCardId(cardId)
        require(MASKED_PAN.matches(maskedPan)) {
            "masked_pan must contain exactly four asterisks followed by the last four digits"
        }
    }
}

@Serializable
data class CardSummaries(
    val cards: List<CardSummary> = emptyList(),
)

@Serializable
data class UpdateCardMetadataRequest(
    val name: String,
)

@Serializable
data class CardFundedOperationRef(
    @SerialName("card_id")
    val cardId: String,
    val uuid: String,
    @SerialName("ipin_block")
    val ipinBlock: String,
) {
    init {
        requireCanonicalCardId(cardId)
        requireCanonicalUuid(uuid, "uuid")
        require(ipinBlock.isNotBlank()) { "ipin_block must not be blank" }
    }
}

internal fun requireCanonicalCardId(cardId: String) {
    requireCanonicalUuid(cardId, "card_id")
}

private fun requireCanonicalUuid(value: String, fieldName: String) {
    val parsed = runCatching { UUID.fromString(value) }.getOrNull()
    require(parsed != null && parsed.toString() == value) {
        "$fieldName must be a canonical lowercase UUID"
    }
}
