package com.tuti.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    @SerialName("tenant_id")
    val tenantId: String = "",
    val wallet: AppWalletConfig = AppWalletConfig(),
    val oauth: AppOAuthConfig = AppOAuthConfig(),
)

@Serializable
data class AppWalletConfig(
    val enabled: Boolean = false,
    @SerialName("default_currency")
    val defaultCurrency: String = "",
    @SerialName("pin_required")
    val pinRequired: Boolean = false,
)

@Serializable
data class AppOAuthConfig(
    @SerialName("google_client_id")
    val googleClientId: String = "",
)
