package com.tuti.api.data

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class Card(
        val name: String = "",
        @SerialName("exp_date")
        val expiryDate: String = "",
        @SerialName("pan")
        val PAN: String = "",
        val mobile: String = "",
        @SerialName("card_index")
        val cardIndex: String = "",
        @SerialName("user_pubkey")
        val pubkey: String = "",
        @SerialName("is_main")
        val isMain: Boolean = false,
        @SerialName("is_valid")
        val isValid: Boolean? = null,
)


@kotlinx.serialization.Serializable
data class SignUpCard(
        val name: String,
        @SerialName("exp_date")
        val expDate: String,
        @SerialName("pan")
        val pan: String,
        val mobile: String,
        val password: String,
        @SerialName("user_pubkey") val pubkey: String,
) {
        init {
                require(pan.length >= 16)
                require(expDate.length == 4)
                require(mobile.length == 10)
                require(password.length >= 8 )
        }
}

@kotlinx.serialization.Serializable
data class Cards (
        val cards: List<Card> = emptyList(),
        @SerialName("main_card")
        val mainCard: Card? = null,
)

@kotlinx.serialization.Serializable
data class UserCards (
        @SerialName("main_card")
        val pan: String = "",
        @SerialName("exp_date")
        val expDate: String = "",
        @SerialName("Cards")
        val cards: List<Card> = emptyList()

){
        /**
         * a helper method to returns a user main card
         * @throws Exception when a user doesn't have any cards
         * @return [Card]
         */
        fun getMainCard(): Card {
                return if (this.pan.isNotEmpty()) {
                        Card(PAN = this.pan, expiryDate = this.expDate)
                }else if (cards.isNotEmpty()) {
                        Card(PAN = this.cards.first().PAN, expiryDate = this.cards.first().expiryDate)
                } else {
                        throw Exception("user has no cards")
                }

        }
}
@kotlinx.serialization.Serializable
data class SetMainCardRequest(
        @SerialName("PAN")
        val PAN: String = "",
)

@kotlinx.serialization.Serializable
data class SetMainCardResponse(
        @SerialName("result")
        val result: String = "",
)
