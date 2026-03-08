package com.tuti.api.authentication

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class SignInResponse (
    @SerialName("authorization")
    val authorizationJWT: String = "",
    val user: User = User(),
    val message: String = "",
    val status: String = "",
    val result: String = "",
    val code: String = "",
    @SerialName("pubkey")
    val publicKey: String = "",
)
