package com.tuti.api.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val name: String,
    val mobile: String,
)

/** Strict request shape for tenant-scoped Chat contact resolution. */
@Serializable
data class ChatContactRequest(
    val name: String,
    val mobile: String,
) {
    init {
        require(name == name.trim() && name.length <= 200) {
            "name must be normalized and at most 200 characters"
        }
        require(mobile.length == 10 && mobile.all { it in '0'..'9' }) {
            "mobile must contain exactly 10 digits"
        }
    }
}

/** A Chat contact resolved by the server to its tenant-scoped numeric identity. */
@Serializable
data class ResolvedChatContact(
    @SerialName("user_id")
    val userId: Long,
    val name: String,
    val mobile: String,
) {
    init {
        require(userId > 0) { "user_id must be positive" }
    }
}
