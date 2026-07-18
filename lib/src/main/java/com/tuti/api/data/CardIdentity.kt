package com.tuti.api.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

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
        require(maskedPan.any { it == '*' || it == '\u2022' }) {
            "masked_pan must not contain a full PAN"
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
    }
}

internal fun requireCanonicalCardId(cardId: String) {
    val parsed = runCatching { UUID.fromString(cardId) }.getOrNull()
    require(parsed != null && parsed.toString() == cardId) {
        "card_id must be a canonical lowercase UUID"
    }
}
