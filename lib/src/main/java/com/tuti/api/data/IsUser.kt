package com.tuti.api.data

@kotlinx.serialization.Serializable
data class IsUser (
    val phone: String,
    val is_user: Boolean,
)

@kotlinx.serialization.Serializable
data class IsUserResponse (
    val result: List<IsUser>
)

@kotlinx.serialization.Serializable
data class IsUserRequest(
        val phones:List<String>
)
