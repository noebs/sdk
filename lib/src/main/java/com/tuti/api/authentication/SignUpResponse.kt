package com.tuti.api.authentication

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class SignUpResponse (
    @SerialName("details")
    var user: User = User(),
    val result: String = "",
    val message: String = "",
    val code: String = "",
)
