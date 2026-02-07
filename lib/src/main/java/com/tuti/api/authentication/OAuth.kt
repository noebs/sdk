package com.tuti.api.authentication

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class GoogleAuthRequest(
    val code: String,
    @SerialName("code_verifier") val codeVerifier: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null,
)

@kotlinx.serialization.Serializable
data class CompleteProfileRequest(
    val mobile: String,
    val fullname: String? = null,
)

@kotlinx.serialization.Serializable
data class OAuthSignInResponse(
    @SerialName("authorization") val authorizationJWT: String = "",
    val user: User = User(),
    @SerialName("new_user") val newUser: Boolean = false,
)

@kotlinx.serialization.Serializable
data class AuthMeResponse(
    val user: User = User(),
)
