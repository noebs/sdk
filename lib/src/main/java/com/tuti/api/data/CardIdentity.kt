package com.tuti.api.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

private val MASKED_PAN = Regex("^\\*{4}[0-9]{4}$")
private val CARD_EXPIRY = Regex("^[0-9]{4}$")

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
    val status: String,
) {
    init {
        requireCanonicalCardId(cardId)
        require(name == name.trim() && name.length <= 100) {
            "card name must be normalized and at most 100 characters"
        }
        require(MASKED_PAN.matches(maskedPan)) {
            "masked_pan must contain exactly four asterisks followed by the last four digits"
        }
        require(CARD_EXPIRY.matches(expiryDate)) {
            "exp_date must contain exactly four digits"
        }
        require(status.isNotBlank() && status == status.trim()) {
            "status must be a normalized non-blank value"
        }
    }
}

@Serializable
data class CardSummaries(
    val cards: List<CardSummary> = emptyList(),
) {
    init {
        require(cards.map(CardSummary::cardId).distinct().size == cards.size) {
            "cards must not contain duplicate card_id values"
        }
    }
}

@Serializable
data class UpdateCardMetadataRequest(
    val name: String,
) {
    init {
        require(name == name.trim() && name.length <= 100) {
            "card name must be normalized and at most 100 characters"
        }
    }
}

internal fun requireCanonicalCardId(cardId: String) {
    requireCanonicalUuid(cardId, "card_id")
}

internal fun requireCanonicalUuid(value: String, fieldName: String) {
    val parsed = runCatching { UUID.fromString(value) }.getOrNull()
    require(parsed != null && parsed.toString() == value) {
        "$fieldName must be a canonical lowercase UUID"
    }
}
