package com.tuti.api.authentication

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class SignUpRequest (
    val username: String? = null,
    val password: String? = null,

    @SerialName("firebase_token")
    val firebaseVerificationID: String? = null,

    @SerialName("public_key")
    val userPubKey: String? = null,

    @SerialName("device_id")
    val deviceId: String? = null,
    val fullname: String? = null,
    val birthday: String? = null,
    val email: String? = null,

    @SerialName("mobile")
    val mobileNumber: String? = null,

    @SerialName("is_merchant")
    val isMerchant: Boolean = false
)