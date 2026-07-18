package com.tuti.api.authentication

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class SignUpRequest(
    @SerialName("mobile")
    val mobileNumber: String,
    val password: String,
    @SerialName("user_pubkey")
    val userPubKey: String,
    val fullname: String,
    val username: String? = null,
    val birthday: String? = null,
    val email: String? = null,
)
