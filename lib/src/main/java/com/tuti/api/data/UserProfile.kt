package com.tuti.api.data

@kotlinx.serialization.Serializable
data class UserProfile(
    val fullname:String,
    val username:String,
    val email:String,
    val birthday:String,
    val gender:String,
)

@kotlinx.serialization.Serializable
data class UserProfileResult(
    val result:String,
)

@kotlinx.serialization.Serializable
data class UserLanguageResponse(
    val language: String = ""
)
