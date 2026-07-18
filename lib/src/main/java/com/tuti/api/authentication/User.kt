package com.tuti.api.authentication

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class User(
        @SerialName("ID")
        val id: Long = 0,
        @SerialName("CreatedAt")
        val createdAt: String = "",
        @SerialName("UpdatedAt")
        val updateAt: String = "",
        @SerialName("DeletedAt")
        val deletedAt: String? = null,
        val username: String = "",
        val fullname: String = "",
        val birthday: String = "",

        @SerialName("mobile")
        val mobileNumber: String = "",
        val email: String = "",

        @SerialName("is_merchant")
        val isMerchant: Boolean = false,
)
