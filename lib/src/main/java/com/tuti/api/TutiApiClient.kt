package com.tuti.api

import com.tuti.api.authentication.AuthMeResponse
import com.tuti.api.authentication.CompleteProfileRequest
import com.tuti.api.authentication.GoogleAuthRequest
import com.tuti.api.authentication.OAuthSignInResponse
import com.tuti.api.authentication.SignInRequest
import com.tuti.api.authentication.SignInResponse
import com.tuti.api.authentication.SignUpRequest
import com.tuti.api.authentication.SignUpResponse
import com.tuti.api.data.*
import com.tuti.api.ebs.EBSRequest
import com.tuti.api.ebs.EBSResponse
import com.tuti.api.ebs.NoebsTransfer
import com.tuti.api.wallet.v1.CompleteOwnershipVerificationBody
import com.tuti.api.wallet.v1.ConfirmUser2FABody
import com.tuti.api.wallet.v1.CreateWalletRequest
import com.tuti.api.wallet.v1.CreateWithdrawalDestinationBody
import com.tuti.api.wallet.v1.DeactivateWithdrawalDestinationBody
import com.tuti.api.wallet.v1.DepositRequest
import com.tuti.api.wallet.v1.DisableUser2FABody
import com.tuti.api.wallet.v1.EnrollUser2FABody
import com.tuti.api.wallet.v1.EnsureWalletRequest
import com.tuti.api.wallet.v1.FundingSourceList
import com.tuti.api.wallet.v1.ManualTransferRequest
import com.tuti.api.wallet.v1.OwnershipVerification
import com.tuti.api.wallet.v1.P2PTransferRequest
import com.tuti.api.wallet.v1.RequestOwnershipVerificationBody
import com.tuti.api.wallet.v1.RpcStatus
import com.tuti.api.wallet.v1.SetWalletPINBody
import com.tuti.api.wallet.v1.SignalManualTransferDecisionBody
import com.tuti.api.wallet.v1.SignalWithdrawalApprovalBody
import com.tuti.api.wallet.v1.SignalWithdrawalVerificationBody
import com.tuti.api.wallet.v1.User2FASetup
import com.tuti.api.wallet.v1.UserWallet
import com.tuti.api.wallet.v1.Wallet
import com.tuti.api.wallet.v1.WalletPaymentMethodList
import com.tuti.api.wallet.v1.WalletPaymentMethodQuery
import com.tuti.api.wallet.v1.WalletTransactionList
import com.tuti.api.wallet.v1.WithdrawalDestination
import com.tuti.api.wallet.v1.WithdrawalDestinationList
import com.tuti.api.wallet.v1.WithdrawalRequest
import com.tuti.api.wallet.v1.WorkflowRun
import com.tuti.model.*
import com.tuti.util.DateSerializer
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit

private object SensitiveRequestBody

private data class TransportOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
)

/** Raised when a legacy SDK method would require direct transport to an external service. */
class ExternalServiceRetiredException(
    val operation: String,
) : IllegalStateException(
    "$operation is disabled: direct cross-origin SDK transport is retired"
)

/** Raised when a legacy Chat method cannot return the server's stable user identity. */
class ChatStableIdentityRequiredException(
    val operation: String,
) : IllegalStateException(
    "$operation is disabled: Chat requires tenant-scoped numeric user IDs"
)

/** Raised when a caller uses a retired signup contract with privileged client-owned fields. */
class SignupContractUpgradeRequiredException : IllegalStateException(
    "Legacy signup is disabled: use com.tuti.api.authentication.SignUpRequest"
)

class TutiApiClient(
    val serverURL: String = "https://api.noebs.sd/",
    val noebsServer: String = "https://api.noebs.sd/",
) {

    var isSingleThreaded = false
    @Volatile var authToken: String = ""
    @Volatile var defaultHeaders: Map<String, String> = emptyMap()
    @Volatile var appConfig: AppConfig? = null
        private set
    var ipinUsername: String = ""
    var ipinPassword: String = ""
    var ebsKey: String = ""

    @Deprecated("Direct cross-origin entertainment transport is retired.")
    val entertainmentServer = "https://plus.2t.sd/"

    private val serverBaseURL = validateBaseURL(serverURL, "serverURL")
    private val noebsBase = validateBaseURL(noebsServer, "noebsServer")
    private val trustedOrigins = setOf(serverBaseURL.origin(), noebsBase.origin())
    private val consumerURL = normalizeConsumerBase(serverBaseURL.toString())
    private val noebsBaseURL = noebsBase.toString()
    private val walletBaseURL = noebsBaseURL + "wallet"

    /**
     * Wallet API (gRPC-gateway) backing the `/wallet` endpoints (see `proto/noebs/wallet/v1/wallet.proto`).
     */
    val wallet: WalletApi = WalletApi()

    /** Opaque card enrollment and management API. */
    val cards: CardApi = CardApi()

    private fun ensureTrailingSlash(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun normalizeConsumerBase(url: String): String {
        val base = ensureTrailingSlash(url)
        return if (base.contains("/consumer/")) base else base + "consumer/"
    }

    private fun validateBaseURL(value: String, name: String): HttpUrl {
        val url = ensureTrailingSlash(value).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("$name must be an absolute HTTP URL")
        require(url.username.isEmpty() && url.password.isEmpty()) {
            "$name must not contain credentials"
        }
        require(url.query == null && url.fragment == null) {
            "$name must not contain a query or fragment"
        }
        require(url.scheme == "https" || isLoopback(url)) {
            "$name must use HTTPS outside loopback tests"
        }
        return url
    }

    private fun requireTrustedHttpURL(value: String): HttpUrl {
        val url = value.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("request URL must be absolute HTTP(S)")
        require(url.username.isEmpty() && url.password.isEmpty()) {
            "request URL must not contain credentials"
        }
        require(url.scheme == "https" || isLoopback(url)) {
            "request URL must use HTTPS outside loopback tests"
        }
        require(url.origin() in trustedOrigins) {
            "request URL must match a configured server origin"
        }
        return url
    }

    private fun requireTrustedWebSocketURL(value: String): String {
        val scheme = value.substringBefore(':').lowercase()
        require(scheme == "ws" || scheme == "wss") {
            "websocket URL must use ws or wss"
        }
        val suffix = value.substring(value.indexOf(':'))
        val httpEquivalent = (if (scheme == "ws") "http" else "https") + suffix
        val url = httpEquivalent.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("websocket URL must be absolute")
        require(url.username.isEmpty() && url.password.isEmpty()) {
            "websocket URL must not contain credentials"
        }
        require(scheme == "wss" || isLoopback(url)) {
            "websocket URL must use WSS outside loopback tests"
        }
        require(url.origin() in trustedOrigins) {
            "websocket URL must match a configured server origin"
        }
        return scheme + suffix
    }

    private fun HttpUrl.origin() = TransportOrigin(scheme, host, port)

    private fun isLoopback(url: HttpUrl): Boolean =
        url.host == "localhost" || url.host == "127.0.0.1" || url.host == "::1"

    fun start(
        onReady: (AppConfig) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit,
    ): Call {
        return loadAppConfig(onReady, onError)
    }

    fun loadAppConfig(
        onResponse: (AppConfig) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit,
    ): Call {
        return sendRequest<String, AppConfig, TutiResponse>(
            method = RequestMethods.GET,
            URL = noebsBaseURL + "app/config",
            requestToBeSent = "",
            onResponse = { config ->
                applyAppConfig(config)
                onResponse(config)
            },
            onError = onError,
        )
    }

    private fun applyAppConfig(config: AppConfig) {
        appConfig = config
        val tenantId = config.tenantId.trim()
        if (tenantId.isNotBlank()) {
            defaultHeaders = defaultHeaders + ("X-Tenant-ID" to tenantId)
        }
    }

    private fun configuredTenantId(): String {
        return appConfig?.tenantId?.trim().orEmpty()
    }

    private fun configuredWalletCurrency(): String {
        return appConfig?.wallet?.defaultCurrency?.trim().orEmpty()
    }

    private fun requireConfiguredWalletCurrency(): String {
        val currency = configuredWalletCurrency()
        check(currency.isNotBlank()) {
            "wallet default currency is required; call loadAppConfig/start before using card helpers"
        }
        return currency
    }

    private fun legacyFinancialOperationUnavailable(operation: String): Nothing =
        throw OpaqueCardOperationRequiredException(operation)

    @Suppress("UNUSED_PARAMETER")
    private fun fillRequestFields(card: Card, ipin: String, amount: Float): EBSRequest =
        legacyFinancialOperationUnavailable("legacy card-funded helper")

    private fun normalizeLegacyAuthRequest(
        credentials: SignInRequest?,
    ): com.tuti.model.SignInRequest? {
        if (credentials == null) {
            return null
        }
        return com.tuti.model.SignInRequest(
            password = credentials.password,
            otp = credentials.otp,
            mobile = credentials.mobile.ifBlank { credentials.username },
            auth = credentials.oldToken,
            signature = credentials.signature,
            message = credentials.message,
        )
    }

    @Deprecated(
        message = "Replace with SignIn with new kotlin classes instead.",
        replaceWith = ReplaceWith("SignIn")
    )
    fun SignIn(
        credentials: SignInRequest,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.SIGN_IN,
            normalizeLegacyAuthRequest(credentials),
            onResponse,
            onError
        )
    }

    fun SignIn(
        credentials: com.tuti.model.SignInRequest,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.SIGN_IN,
            credentials,
            onResponse,
            onError
        )
    }

    /** ChangePassword changes an existing user password. Should
     * be accessed behind a jwt active session
     */
    fun ChangePassword(
        credentials: SignInRequest?,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.ChangePassword,
            credentials,
            onResponse,
            onError,
        )
    }

    @Deprecated("Direct cross-origin entertainment transport is retired.")
    fun GetAllProviders(
        onResponse: (ProvidersResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        throw ExternalServiceRetiredException("GetAllProviders")
    }

    @Deprecated("Direct cross-origin entertainment transport is retired.")
    fun GetProviderProducts(
        providerCode: String,
        onResponse: (ProductsResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        throw ExternalServiceRetiredException("GetProviderProducts")
    }

    @Deprecated(
        message = "Use getTransactions() to call the /consumer/transactions endpoint.",
        replaceWith = ReplaceWith("getTransactions")
    )
    fun getDumpTransactions(
        onResponse: (DapiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(method = RequestMethods.GET, consumerURL + Operations.DAPI_GET_TRANSACTIONS,
            onResponse=onResponse, onError=onError, requestToBeSent = 1)
    }

    fun getTransactions(
        onResponse: (List<EBSResponse>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.DAPI_GET_TRANSACTIONS,
            "",
            onResponse,
            onError
        )
    }
    fun EntertainmentSendTranser(
        card: Card,
        ipin: String,
        productID: String,
        amount: Float,
        onResponse: (SendTransferResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("EntertainmentSendTranser")
    }

    fun TestDeploy(): String {
        return "from SDK"
    }


    fun getUserProfile(
        onResponse: (UserProfile) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.USER_PROFILE,
            "",
            onResponse = onResponse,
            onError = onError,
        )
    }



    fun setUserProfile(
        userProfile: UserProfile,
        onResponse: (UserProfileResult) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.PUT,
            consumerURL + Operations.USER_PROFILE,
            userProfile,
            onResponse = onResponse,
            onError = onError,
        )
    }

    fun getUserLanguage(
        onResponse: (UserLanguageResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.USER_LANGUAGE,
            "",
            onResponse,
            onError
        )
    }

    fun setUserLanguage(
        language: String,
        onResponse: (UserProfileResult) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.PUT,
            consumerURL + Operations.USER_LANGUAGE,
            "",
            onResponse,
            onError,
            null,
            "language",
            language
        )
    }

    /**
     * OneTimeSignIn allows tutipay users to sign in via a code we send to their phone numbers
     * Notice: this method ONLY works for tutipay registered devices, at the moment
     * it doesn't support a sign in from a new device, as it relies on the user
     * signing a message via their private key
     *
     * @param credentials
     * @param onResponse
     * @param onError
     */
    fun OneTimeSignIn(
        credentials: SignInRequest?,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.SINGLE_SIGN_IN,
            normalizeLegacyAuthRequest(credentials),
            onResponse,
            onError
        )
    }

    /**
     * GenerateOtpSignIn service used to request an otp to be sent to the user's registered sms phone number
     *
     * @param credentials
     * @param onResponse
     * @param onError
     */
    fun generateOtpSignIn(
        credentials: GenerateOTP,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.GENERATE_LOGIN_OTP,
            credentials,
            onResponse,
            onError
        )
    }

    @Deprecated(
        message = "Replace with GenerateOtpSignIn",
        replaceWith = ReplaceWith("generateOtpSignIn")
    )
            /**
             * GenerateOtpSignIn service used to request an otp to be sent to the user's registered sms phone number
             *
             * @param credentials
             * @param onResponse
             * @param onError
             */
    fun GenerateOtpInsecure(
        credentials: SignInRequest?,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.GENERATE_LOGIN_OTP_INSECURE,
            normalizeLegacyAuthRequest(credentials),
            onResponse,
            onError
        )
    }


    fun VerifyOtp(
        credentials: com.tuti.model.SignInRequest,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.VERIFY_OTP,
            credentials,
            onResponse,
            onError
        )
    }

    @Deprecated("PAN-selected balance recovery is retired; use opaque account recovery.")
    fun Otp2FA(
        credentials: EBSRequest,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("Otp2FA")
    }


    /**
     * RefreshToken used to refresh an existing token to keep user's session valid.
     *
     * @param credentials
     * @param onResponse  a method that is used to handle successful cases
     * @param onError     a method to handle on error cases
     */
    fun RefreshToken(
        credentials: SignInRequest?,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.REFRESH_TOKEN,
            normalizeLegacyAuthRequest(credentials),
            onResponse,
            onError
        )
    }

    fun googleAuth(
        request: GoogleAuthRequest,
        onResponse: (OAuthSignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.AUTH_GOOGLE,
            request,
            onResponse,
            onError
        )
    }

    fun completeProfile(
        request: CompleteProfileRequest,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.AUTH_COMPLETE_PROFILE,
            request,
            onResponse,
            onError
        )
    }

    fun authMe(
        onResponse: (AuthMeResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.AUTH_ME,
            "",
            onResponse,
            onError
        )
    }

    @Deprecated("Noebs wallet endpoints are not part of the current /consumer API.")
    fun inquireNoebsWallet(
        accountId: String,
        onResponse: (Double) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        // Since you mentioned the server uses SSE, we're assuming it's a GET request
        sendRequest<Unit, Double, TutiResponse>(
            method = RequestMethods.GET,
            URL = noebsBaseURL + Operations.NOEBS_WALLET_BALANCE,
            requestToBeSent = null,
            onResponse = { balanceResponse ->
                onResponse(balanceResponse)
            },
            onError = { _, exception ->
                onError(null, exception)
            }, params = arrayOf("account_id", accountId),
        )}

    /**
     * Wallet API client (v1) mapping to the gRPC-gateway `/wallet` endpoints.
     *
     * Notes:
     * - Wallet endpoints return gRPC status errors (google.rpc.Status), represented as [RpcStatus] here.
     * - Many wallet requests require `tenantId` explicitly.
     */
    inner class WalletApi {

        fun createWallet(
            request: CreateWalletRequest = CreateWalletRequest(),
            onResponse: (UserWallet) -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId ?: configuredTenantId().ifBlank { null },
                currency = request.currency.ifBlank { configuredWalletCurrency() },
            )
            sendRequest<CreateWalletRequest, UserWallet, TutiResponse>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/wallets",
                requestToBeSent = requestWithDefaults,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun getUserWallet(
            walletId: String,
            tenantId: String = "",
            onResponse: (UserWallet) -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ) {
            sendRequest<String, UserWallet, TutiResponse>(
                method = RequestMethods.GET,
                URL = "$walletBaseURL/wallets/$walletId",
                requestToBeSent = "",
                onResponse = onResponse,
                onError = onError,
                params = queryParams("tenant_id" to tenantId.ifBlank { configuredTenantId() }),
            )
        }

        fun listPaymentMethods(
            query: WalletPaymentMethodQuery,
            onResponse: (WalletPaymentMethodList) -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ) {
            sendRequest<String, WalletPaymentMethodList, TutiResponse>(
                method = RequestMethods.GET,
                URL = "$walletBaseURL/methods",
                requestToBeSent = "",
                onResponse = onResponse,
                onError = onError,
                params = queryParams(
                    "direction" to query.direction,
                    "currency" to query.currency.ifBlank { configuredWalletCurrency() },
                    "region" to query.region,
                    "amount" to query.amount?.toString().orEmpty(),
                    "tenant_id" to query.tenantId.ifBlank { configuredTenantId() },
                    "limit" to query.limit.toString(),
                    "offset" to query.offset.toString(),
                ),
            )
        }

        fun listTransactions(
            walletId: String,
            tenantId: String = "",
            entryType: String = "",
            limit: Int = 100,
            offset: Int = 0,
            onResponse: (WalletTransactionList) -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ) {
            sendRequest<String, WalletTransactionList, TutiResponse>(
                method = RequestMethods.GET,
                URL = "$walletBaseURL/wallets/$walletId/transactions",
                requestToBeSent = "",
                onResponse = onResponse,
                onError = onError,
                params = queryParams(
                    "tenant_id" to tenantId.ifBlank { configuredTenantId() },
                    "entry_type" to entryType,
                    "limit" to limit.toString(),
                    "offset" to offset.toString(),
                ),
            )
        }

        fun ensureWallet(
            request: EnsureWalletRequest,
            onResponse: (Wallet) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
                currency = request.currency.ifBlank { configuredWalletCurrency() },
            )
            sendRequest<EnsureWalletRequest, Wallet, RpcStatus>(
                method = RequestMethods.POST,
                URL = walletBaseURL,
                requestToBeSent = requestWithDefaults,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun getWallet(
            walletId: String,
            tenantId: String = "",
            onResponse: (Wallet) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<String, Wallet, RpcStatus>(
                method = RequestMethods.GET,
                URL = "$walletBaseURL/$walletId",
                requestToBeSent = "",
                onResponse = onResponse,
                onError = onError,
                params = queryParams("tenantId" to tenantId.ifBlank { configuredTenantId() }),
            )
        }

        fun listFundingSources(
            walletId: String,
            tenantId: String = "",
            onResponse: (FundingSourceList) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<String, FundingSourceList, RpcStatus>(
                method = RequestMethods.GET,
                URL = "$walletBaseURL/$walletId/funding_sources",
                requestToBeSent = "",
                onResponse = onResponse,
                onError = onError,
                params = queryParams("tenantId" to tenantId.ifBlank { configuredTenantId() }),
            )
        }

        fun listWithdrawalDestinations(
            walletId: String,
            tenantId: String = "",
            activeOnly: Boolean = false,
            onResponse: (WithdrawalDestinationList) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val params = queryParams("tenantId" to tenantId.ifBlank { configuredTenantId() }).toMutableList()
            if (activeOnly) {
                params.addAll(listOf("activeOnly", "true"))
            }
            sendRequest<String, WithdrawalDestinationList, RpcStatus>(
                method = RequestMethods.GET,
                URL = "$walletBaseURL/$walletId/destinations",
                requestToBeSent = "",
                onResponse = onResponse,
                onError = onError,
                params = params.toTypedArray(),
            )
        }

        fun createWithdrawalDestination(
            walletId: String,
            request: CreateWithdrawalDestinationBody,
            onResponse: (WithdrawalDestination) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
                currency = request.currency.ifBlank { configuredWalletCurrency() },
            )
            sendRequest<CreateWithdrawalDestinationBody, WithdrawalDestination, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/$walletId/destinations",
                requestToBeSent = requestWithDefaults,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun deactivateWithdrawalDestination(
            destinationId: Long,
            request: DeactivateWithdrawalDestinationBody,
            onResponse: () -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
            )
            sendRequest<DeactivateWithdrawalDestinationBody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/destinations/$destinationId/deactivate",
                requestToBeSent = requestWithDefaults,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }

        fun requestOwnershipVerification(
            destinationId: Long,
            request: RequestOwnershipVerificationBody,
            onResponse: (OwnershipVerification) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
            )
            sendRequest<RequestOwnershipVerificationBody, OwnershipVerification, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/destinations/$destinationId/verify",
                requestToBeSent = requestWithDefaults,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun completeOwnershipVerification(
            verificationId: Long,
            request: CompleteOwnershipVerificationBody,
            onResponse: () -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
            )
            sendRequest<CompleteOwnershipVerificationBody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/ownership_verifications/$verificationId/complete",
                requestToBeSent = requestWithDefaults,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }

        fun setWalletPin(
            walletId: String,
            request: SetWalletPINBody,
            onResponse: () -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
            )
            sendRequest<SetWalletPINBody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/$walletId/pin",
                requestToBeSent = requestWithDefaults,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }

        fun requestP2PTransfer(
            request: P2PTransferRequest,
            onResponse: (WorkflowRun) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
                currency = request.currency.ifBlank { configuredWalletCurrency() },
            )
            sendRequest<P2PTransferRequest, WorkflowRun, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/p2p",
                requestToBeSent = requestWithDefaults,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun requestDeposit(
            request: DepositRequest,
            onResponse: (WorkflowRun) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
                currency = request.currency.ifBlank { configuredWalletCurrency() },
            )
            sendRequest<DepositRequest, WorkflowRun, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/deposits",
                requestToBeSent = requestWithDefaults,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun requestWithdrawal(
            request: WithdrawalRequest,
            onResponse: (WorkflowRun) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
                currency = request.currency.ifBlank { configuredWalletCurrency() },
            )
            sendRequest<WithdrawalRequest, WorkflowRun, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/withdrawals",
                requestToBeSent = requestWithDefaults,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun signalWithdrawalApproval(
            workflowId: String,
            request: SignalWithdrawalApprovalBody,
            onResponse: () -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<SignalWithdrawalApprovalBody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/withdrawals/$workflowId/approval",
                requestToBeSent = request,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }

        fun signalWithdrawalVerification(
            workflowId: String,
            request: SignalWithdrawalVerificationBody,
            onResponse: () -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<SignalWithdrawalVerificationBody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/withdrawals/$workflowId/verification",
                requestToBeSent = request,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }

        fun requestManualTransfer(
            request: ManualTransferRequest,
            onResponse: (WorkflowRun) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
                currency = request.currency.ifBlank { configuredWalletCurrency() },
            )
            sendRequest<ManualTransferRequest, WorkflowRun, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/manual_transfers",
                requestToBeSent = requestWithDefaults,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun signalManualTransferDecision(
            workflowId: String,
            request: SignalManualTransferDecisionBody,
            onResponse: () -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<SignalManualTransferDecisionBody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/manual_transfers/$workflowId/decision",
                requestToBeSent = request,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }

        fun enrollUser2FA(
            userId: Long,
            request: EnrollUser2FABody,
            onResponse: (User2FASetup) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
            )
            sendRequest<EnrollUser2FABody, User2FASetup, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/users/$userId/2fa/enroll",
                requestToBeSent = requestWithDefaults,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun confirmUser2FA(
            userId: Long,
            request: ConfirmUser2FABody,
            onResponse: () -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
            )
            sendRequest<ConfirmUser2FABody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/users/$userId/2fa/confirm",
                requestToBeSent = requestWithDefaults,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }

        fun disableUser2FA(
            userId: Long,
            request: DisableUser2FABody,
            onResponse: () -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val requestWithDefaults = request.copy(
                tenantId = request.tenantId.ifBlank { configuredTenantId() },
            )
            sendRequest<DisableUser2FABody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/users/$userId/2fa/disable",
                requestToBeSent = requestWithDefaults,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }

        private fun queryParams(vararg pairs: Pair<String, String>): Array<String> {
            val params = mutableListOf<String>()
            pairs.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    params.add(key)
                    params.add(value)
                }
            }
            return params.toTypedArray()
        }
    }

    inner class CardApi {
        fun list(
            onResponse: (List<CardSummary>) -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ): Call = sendRequest<String, CardSummaries, TutiResponse>(
            method = RequestMethods.GET,
            URL = consumerURL + "cards",
            requestToBeSent = "",
            onResponse = { onResponse(it.cards) },
            onError = onError,
        )

        fun createEnrollmentIntent(
            onResponse: (CardEnrollmentIntent) -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ): Call = sendRequest<Map<String, String>, CardEnrollmentIntent, TutiResponse>(
            method = RequestMethods.POST,
            URL = consumerURL + "cards/enrollment-intents",
            requestToBeSent = emptyMap(),
            onResponse = onResponse,
            onError = onError,
        )

        fun confirmEnrollment(
            intent: CardEnrollmentIntent,
            confirmation: ConfirmCardEnrollmentRequest,
            onResponse: (CardSummary) -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ): Call {
            require(confirmation.railUuid == intent.railUuid) {
                "enrollment confirmation rail_uuid must match its intent"
            }
            return sendRequest<ConfirmCardEnrollmentRequest, CardSummary, TutiResponse>(
                method = RequestMethods.POST,
                URL = consumerURL + "cards/enrollment-intents/${intent.enrollmentId}/confirm",
                requestToBeSent = confirmation,
                onResponse = onResponse,
                onError = onError,
                sensitiveBody = true,
            )
        }

        fun balance(
            request: BalanceInquiryOperationRequest,
            onResponse: (BalanceInquiryResult) -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ): Call = sendRequest<BalanceInquiryOperationRequest, BalanceInquiryResult, TutiResponse>(
            method = RequestMethods.POST,
            URL = consumerURL + "balance",
            requestToBeSent = request,
            onResponse = { result ->
                if (result.uuid == request.uuid) {
                    onResponse(result)
                } else {
                    onError(null, OperationResponseMismatchException(request.uuid, result.uuid))
                }
            },
            onError = onError,
            sensitiveBody = true,
        )

        fun rename(
            card: CardRef,
            name: String,
            onResponse: () -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ): Call = sendRequest<UpdateCardMetadataRequest, Unit, TutiResponse>(
            method = RequestMethods.PATCH,
            URL = consumerURL + "cards/${card.cardId}",
            requestToBeSent = UpdateCardMetadataRequest(name),
            onResponse = { onResponse() },
            onError = onError,
        )

        fun retire(
            card: CardRef,
            onResponse: () -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ): Call = sendRequest<Map<String, String>, Unit, TutiResponse>(
            method = RequestMethods.DELETE,
            URL = consumerURL + "cards/${card.cardId}",
            requestToBeSent = emptyMap(),
            onResponse = { onResponse() },
            onError = onError,
        )

        fun setMain(
            card: CardRef,
            onResponse: () -> Unit,
            onError: (TutiResponse?, Exception?) -> Unit,
        ): Call = sendRequest<Map<String, String>, Unit, TutiResponse>(
            method = RequestMethods.PUT,
            URL = consumerURL + "cards/${card.cardId}/main",
            requestToBeSent = emptyMap(),
            onResponse = { onResponse() },
            onError = onError,
        )
    }

    /** Registers a user with the canonical public signup contract. */
    fun Signup(
        signUpRequest: SignUpRequest,
        onResponse: (SignUpResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.SIGN_UP,
            signUpRequest,
            onResponse,
            onError
        )
    }

    @Suppress("DEPRECATION", "UNUSED_PARAMETER")
    @Deprecated(
        message = "Legacy signup is retired; use com.tuti.api.authentication.SignUpRequest.",
    )
    fun Signup(
        signUpRequest: SignupRequest,
        onResponse: (SignUpResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        throw SignupContractUpgradeRequiredException()
    }

    @Deprecated("Use cards.createEnrollmentIntent and cards.confirmEnrollment after authentication.")
    fun SignupWithCard(
        signUpRequest: SignUpCard?,
        onResponse: (SignUpResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("SignupWithCard")
    }

    @Deprecated("Legacy PAN card issuance is retired; use cards.createEnrollmentIntent.")
    fun startCardRegistration(
        request: EBSRequest,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("startCardRegistration")
    }

    @Deprecated("Legacy PAN card completion is retired; use cards.confirmEnrollment.")
    fun completeCardRegistration(
        request: EBSRequest,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("completeCardRegistration")
    }

    fun getNotifications(
        filters: NotificationFilters,
        onResponse: (List<Notification>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.NOTIFICATIONS,
            filters.mobile,
            onResponse,
            onError, null,
            "mobile", filters.mobile, "all", if (filters.getAll) "true" else "false"
        )
    }

    /**
     * VerifyFirebase used to verify a verification ID token that was sent to a user. It sets is_activiated
     * flag as true for the selected user. This is basically an in-background operation, and as though it shouldn't
     * block the UI, nor does the implementation should care too much about the returned object.
     *
     * @param signUpRequest
     * @param onResponse
     * @param onError
     */
    @Deprecated("verify_firebase was removed from the noebs API; use VerifyOtp or auth flows instead.")
    fun VerifyFirebase(
        signUpRequest: SignUpRequest?,
        onResponse: (SignUpResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.VERIFY_FIREBASE,
            signUpRequest,
            onResponse,
            onError,
        )
    }

    fun sendEBSRequest(
        URL: String,
        ebsRequest: EBSRequest?,
        onResponse: (EBSResponse) -> Unit,
        onError: (EBSResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("sendEBSRequest")
    }

    @Deprecated("Use cards.list, which returns opaque CardSummary values.")
    fun getCards(
        onResponse: (Cards) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("getCards")
    }

    @Deprecated("Use cards.createEnrollmentIntent; its response contains the bound rail key.")
    fun getPublicKey(
        ebsRequest: EBSRequest?,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("getPublicKey(EBSRequest)")
    }

    @Deprecated("Use cards.createEnrollmentIntent; its response contains the bound rail key.")
    fun getIpinPublicKey(
        ebsRequest: EBSRequest,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("getIpinPublicKey(EBSRequest)")
    }

    @Deprecated("Use syncChatContacts for tenant-scoped numeric user identities.")
    fun syncContacts(
        contacts: List<Contact>,
        onResponse: (contacts: List<Contact>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        throw ChatStableIdentityRequiredException("syncContacts")
    }

    fun syncChatContacts(
        contacts: List<ChatContactRequest>,
        onResponse: (contacts: List<ResolvedChatContact>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit,
    ) {
        require(contacts.isNotEmpty() && contacts.size <= 50) {
            "between 1 and 50 contacts must be resolved at once"
        }
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.SUBMIT_CONTACTS,
            contacts,
            onResponse,
            onError,
        )
    }

    @Deprecated("The generic beneficiary contract is retired; use a typed recipient reference when available.")
    fun addBeneficiary(
        beneficiary: NoebsBeneficiary,
        onResponse: (TutiResponse?) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("addBeneficiary(NoebsBeneficiary)")
    }

    @Deprecated("The generic beneficiary contract is retired; use a typed recipient reference when available.")
    fun getBeneficiaries(
        onResponse: (List<NoebsBeneficiary>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("getBeneficiaries")
    }

    @Deprecated("The generic beneficiary contract is retired; use a typed recipient reference when available.")
    fun deleteBeneficiary(
        beneficiary: NoebsBeneficiary,
        onResponse: (String) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("deleteBeneficiary(NoebsBeneficiary)")
    }

    @Deprecated("Use cards.createEnrollmentIntent and cards.confirmEnrollment.")
    fun addCard(
        card: Card,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("addCard")
    }

    @Deprecated("Use cards.rename with CardRef.")
    fun editCard(
        card: Card?,
        onResponse: (String) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("editCard")
    }

    @Deprecated("Use cards.retire with CardRef.")
    fun deleteCard(
        card: Card?,
        onResponse: (String) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("deleteCard")
    }

    /**
     * @param card: Card represents a noebs payment card.
     * @param onResponse
     * @param onError
     */
    fun balanceInquiry(
        card: Card,
        ipin: String,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("balanceInquiry")
    }


    @Deprecated("The configured-PAN bills helper is retired; wait for the typed opaque-card bill contract.")
    fun getBills(
        billInfo: BillInfo,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit,
    ): Call {
        legacyFinancialOperationUnavailable("getBills")
    }

    @Deprecated("The configured-PAN bills helper is retired; wait for the typed opaque-card bill contract.")
    fun billInquiry(
        billInfo: BillInfo,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit,
    ): Call {
        legacyFinancialOperationUnavailable("billInquiry(BillInfo)")
    }

    /**
     * Direct bill inquiry against `/consumer/bill_inquiry` using an explicit EBS-style request.
     */
    fun billInquiry(
        request: EBSRequest,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit,
    ): Call {
        legacyFinancialOperationUnavailable("billInquiry(EBSRequest)")
    }

    fun cardTransfer(
        card: Card,
        ipin: String,
        receiverCard: Card,
        deviceId: String = "",
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.toCard = receiverCard.PAN
        request.deviceId = deviceId
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.CARD_TRANSFER,
            request,
            onResponse,
            onError,
        )
    }

    fun userAccountTransfer(
        card: Card,
        ipin: String,
        receiverPhoneNumber: String,
        deviceId: String = "",
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.mobile = receiverPhoneNumber
        request.deviceId = deviceId
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.P2P_MOBILE,
            request,
            onResponse,
            onError,
        )
    }

    fun purchaseBashairCredit(
        card: Card,
        ipin: String,
        bashairType: BashairTypes,
        paymentValue: String,
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.payeeId = TelecomIDs.Bashair.payeeID
        request.paymentInfo = bashairType.bashairInfo(paymentValue)
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.BILL_PAYMENT,
            request,
            onResponse,
            onError,
        )
    }

    fun generateVoucher(
        card: Card,
        ipin: String,
        voucherNumber: String,
        amount: Float,
        deviceId: String = "",
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.payeeId = TelecomIDs.E15.payeeID
        request.voucherNumber = voucherNumber
        request.deviceId = deviceId
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.GENERATE_VOUCHER,
            request,
            onResponse,
            onError
        )
    }

    fun payE15Invoice(
        card: Card,
        ipin: String,
        invoice: String,
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {

        val request = fillRequestFields(card, ipin, amount)
        request.payeeId = TelecomIDs.E15.payeeID
        request.paymentInfo = (E15(true, invoice, ""))
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.BILL_PAYMENT,
            request,
            onResponse,
            onError
        )
    }

    fun payCustomsInvoice(
        card: Card,
        ipin: String,
        bankCode: String,
        declarantCode: String,
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.payeeId = (TelecomIDs.CUSTOMS.payeeID)
        request.paymentInfo = (Customs(bankCode, declarantCode))

        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.BILL_PAYMENT,
            request,
            onResponse,
            onError,
        )
    }

    fun payMOHEArabFees(
        card: Card,
        ipin: String,
        courseId: CourseID,
        admissionType: AdmissionType,
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.payeeId = (TelecomIDs.MOHEArab.payeeID)
        request.paymentInfo = (MOHEArab("", "", courseId, admissionType))
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.BILL_PAYMENT,
            request,
            onResponse,
            onError,
        )
    }

    fun payMOHEFees(
        card: Card,
        ipin: String,
        seatNumber: String,
        courseId: CourseID,
        admissionType: AdmissionType,
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.payeeId = (TelecomIDs.MOHE.payeeID)
        request.paymentInfo = (MOHE(seatNumber, courseId, admissionType))
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.BILL_PAYMENT,
            request,
            onResponse,
            onError
        )
    }

    fun payEInvoice(
        card: Card,
        ipin: String,
        customerRef: String,
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.payeeId = (TelecomIDs.Einvoice.payeeID)
        request.paymentInfo = ("customerBillerRef=$customerRef")
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.BILL_PAYMENT,
            request,
            onResponse,
            onError
        )
    }

    fun buyPhoneCredit(
        card: Card,
        ipin: String,
        mobile: String,
        operator: Operator,
        carrierPlan: CarrierPlan,
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)

        when (operator) {
            Operator.ZAIN -> run {
                when (carrierPlan) {
                    CarrierPlan.PREPAID -> run {
                        request.payeeId = (TelecomIDs.ZAIN.payeeID)
                    }
                    CarrierPlan.POSTPAID -> run {
                        request.payeeId = (TelecomIDs.ZAIN_BILL.payeeID)
                    }
                }
            }

            Operator.SUDANI -> run {
                when (carrierPlan) {
                    CarrierPlan.PREPAID -> run {
                        request.payeeId = (TelecomIDs.SUDANI.payeeID)
                    }
                    CarrierPlan.POSTPAID -> run {
                        request.payeeId = (TelecomIDs.SUDANI_BILL.payeeID)
                    }
                }
            }

            Operator.MTN -> run {
                when (carrierPlan) {
                    CarrierPlan.PREPAID -> run {
                        request.payeeId = (TelecomIDs.MTN.payeeID)
                    }
                    CarrierPlan.POSTPAID -> run {
                        request.payeeId = (TelecomIDs.MTN_BILL.payeeID)
                    }
                }
            }
        }

        request.paymentInfo = ("MPHONE=$mobile")
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.BILL_PAYMENT,
            request,
            onResponse,
            onError,
        )
    }

    fun buyNECCredit(
        card: Card,
        ipin: String,
        meterNumber: String,
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.payeeId = (TelecomIDs.NEC.payeeID)
        request.paymentInfo = ("METER=$meterNumber")
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.BILL_PAYMENT,
            request,
            onResponse,
            onError,
        )
    }

    fun getCachedBillerId(
        mobile: String,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.BILLER,
            "",
            onResponse,
            onError,
            null,
            "mobile",
            mobile
        )
    }

    @Deprecated("PAN-backed payment tokens are retired; wait for the typed opaque-card token contract.")
    fun generatePaymentToken(
        request: PaymentToken,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("generatePaymentToken")
    }

    @Deprecated("The recipient-PAN payment request contract is retired.")
    fun sendPaymentRequest(
        paymentRequest: PaymentRequest,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("sendPaymentRequest")
    }

    @Deprecated("Legacy noebs transfer endpoint; not available in the current /consumer API.")
    fun noebsTransfer(
        request: NoebsTransfer,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("noebsTransfer")
    }


    /**
     * Performs a noebs payment via QR or a payment link. The api is still in beta and
     * as such it is subject to be change.
     */
    fun quickPayment(
        request: EBSRequest?,
        uuid: String = "",
        onResponse: (PaymentToken) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("quickPayment(EBSRequest)")
    }

    fun payByUUID(
        card: Card,
        ipin: String,
        uuid: String,
        amount: Float,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = fillRequestFields(card, ipin, amount)
        request.quickPayToken = uuid
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.QuickPayment,
            request,
            onResponse,
            onError, null, "uuid", uuid
        )
    }

    @Deprecated("PAN-backed payment tokens are retired; wait for the typed opaque-card token contract.")
    fun getPaymentToken(
        uuid: String,
        onResponse: (PaymentToken) -> Unit,
        onError: (PaymentToken?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("getPaymentToken")
    }

    /**
     * Upsert device token for push notifications.
     */
    fun UpsertFirebase(
        token: String,
        onResponse: (TutiResponse?) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        @Serializable
        data class data(@SerialName("token") val data: String)

        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.UpsertFirebaseToken,
            data(token),
            onResponse,
            onError,
        )
    }

    fun upsertDeviceToken(
        token: String,
        onResponse: (TutiResponse?) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        UpsertFirebase(token, onResponse, onError)
    }


    @Deprecated("Standalone PAN-selected IPIN generation is retired; use opaque card enrollment.")
    fun generateIpin(
        data: Ipin,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("generateIpin")
    }

    @Deprecated("Standalone PAN-selected IPIN generation is retired; use opaque card enrollment.")
    fun confirmIpinGeneration(
        data: IpinCompletion,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("confirmIpinGeneration")
    }


    fun changeIPIN(
        card: Card,
        oldIPIN: String,
        newIPIN: String,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("changeIPIN")
    }

    fun getTransctionByUUID(
        uuid: String,
        onResponse: (EBSResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.TRANSACTION_BY_ID,
            "",
            onResponse,
            onError,
            null,
            "uuid",
            uuid
        )
    }


    @Deprecated("Recipient cards are private; use phone membership discovery instead.")
    fun getUserCard(
        mobile: String,
        onResponse: (UserCards) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("getUserCard")
    }

    fun isUser(
        phones: IsUserRequest,
        onResponse: (List<IsUser>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.CHECK_USER,
            phones,
            onResponse,
            onError,
            null,
        )
    }

    @Deprecated("Use cards.setMain with CardRef.")
    fun setMainCard(
        card: SetMainCardRequest,
        onResponse: (SetMainCardResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        legacyFinancialOperationUnavailable("setMainCard")
    }


    @Deprecated("Legacy ledger endpoint; use getTransactions() for /consumer/transactions.")
    fun retrieveLedgerTransactions(
        accountId: String,
        onResponse: (List<Ledger>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            serverBaseURL.toString() + Operations.LEDGER_TRANSACTIONS,
            "",
            onResponse,
            onError,
            null,
            "account_id", accountId
        )
    }

    @Deprecated("Legacy ledger endpoint; use getTransactions() for /consumer/transactions.")
    fun retrieveNoebsTransactions(
        accountId: String,
        onResponse: (List<NoebsTransaction>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            serverBaseURL.toString() + Operations.NOEBS_TRANSACTIONS,
            "",
            onResponse,
            onError,
            null,
            "account_id", accountId
        )
    }

    fun setNoebsKYC(
        kyc: KYC,
        onResponse: (TutiResponse?) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            method=RequestMethods.POST,

            URL=consumerURL + Operations.NOEBS_KYC,
            requestToBeSent = kyc,
            onResponse = onResponse,
            onError=onError,
            headers = null,
        )
    }

    /**
     * openChatSocket opens a websocket connection to the noebs chat service.
     * The Authorization header must carry a valid JWT (token or "Bearer <token>").
     */
    fun openChatSocket(
        listener: WebSocketListener,
        token: String = authToken,
        wsBaseURL: String = noebsBaseURL,
        path: String = "/ws"
    ): WebSocket {
        val wsURL = requireTrustedWebSocketURL(buildWsURL(wsBaseURL, path))
        val requestBuilder = Request.Builder().url(wsURL)
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", token)
        }
        val headerSnapshot = defaultHeaders
        headerSnapshot.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        return okHttpClient.newWebSocket(requestBuilder.build(), listener)
    }

    private inline fun <reified RequestType, reified ResponseType, reified ErrorType> sendRequest(
        method: RequestMethods,
        URL: String,
        requestToBeSent: RequestType? = null,
        crossinline onResponse: (ResponseType) -> Unit,
        crossinline onError: (ErrorType?, Exception?) -> Unit,
        headers: Map<String, String>? = null,
        vararg params: String,
        sensitiveBody: Boolean = false,
    ): Call {
        require(params.size % 2 == 0) {
            "params must be an even number of key/value entries. Got ${params.size}."
        }

        val baseUrl = requireTrustedHttpURL(URL)
        val finalUrl = baseUrl.newBuilder().apply {
            params.toList().chunked(2).forEach { (key, value) ->
                addQueryParameter(key, value)
            }
        }.build()

        val requestBuilder = Request.Builder().url(finalUrl)
        val tokenSnapshot = authToken
        if (tokenSnapshot.isNotBlank()) {
            requestBuilder.header("Authorization", tokenSnapshot)
        }
        requestBuilder.header("Accept", "application/json")
        val defaultHeaderSnapshot = defaultHeaders
        defaultHeaderSnapshot.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        // add additional headers set by the user
        headers?.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        if (sensitiveBody) {
            requestBuilder.tag(SensitiveRequestBody::class.java, SensitiveRequestBody)
        }

        // check for http method set by the user
        val requestBody = if (method == RequestMethods.GET) {
            null
        } else {
            Json.encodeToString(requestToBeSent).toRequestBody(JSON)
        }

        when (method) {
            RequestMethods.POST -> requestBuilder.post(requireNotNull(requestBody))
            RequestMethods.DELETE -> requestBuilder.delete(requireNotNull(requestBody))
            RequestMethods.PUT -> requestBuilder.put(requireNotNull(requestBody))
            RequestMethods.PATCH -> requestBuilder.patch(requireNotNull(requestBody))
            else -> requestBuilder.get()
        }

        val request = requestBuilder.build()
        val call = okHttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { rawResponse ->
                    val responseCode = rawResponse.code
                    val responseBody = try {
                        rawResponse.body.string()
                    } catch (e: Exception) {
                        onError(null, e)
                        return
                    }

                    try {
                        if (responseCode in 400..599) {
                            onError(parseResponse(responseBody), null)
                        } else {
                            onResponse(parseResponse(responseBody))
                        }
                    } catch (e: Exception) {
                        onError(null, e)
                    }
                }
            }
        })
        return call
    }

    private fun buildWsURL(base: String, path: String): String {
        val trimmedBase = base.trimEnd('/')
        val trimmedPath = if (path.startsWith("/")) path else "/$path"
        val httpURL = trimmedBase + trimmedPath
        return when {
            httpURL.startsWith("https://") -> "wss://" + httpURL.removePrefix("https://")
            httpURL.startsWith("http://") -> "ws://" + httpURL.removePrefix("http://")
            else -> httpURL
        }
    }

    inline fun <reified ResponseType> parseResponse(responseAsString: String): ResponseType {
        val trimmed = responseAsString.trim()
        return when (ResponseType::class.java) {
            String::class.java -> {
                responseAsString as ResponseType
            }
            Unit::class.java -> {
                Unit as ResponseType
            }
            else -> {
                Json.decodeFromString(trimmed)
            }
        }

    }

    companion object {
        val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
        private val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("X-Admin-Key")
            redactHeader("X-Api-Key")
            redactHeader("X-Noebs-Signature")
            redactHeader("X-Workload-Signature")
            level = HttpLoggingInterceptor.Level.NONE
        }

        private val safeHttpLoggingInterceptor = Interceptor { chain ->
            if (chain.request().tag(SensitiveRequestBody::class.java) != null) {
                chain.proceed(chain.request())
            } else {
                httpLoggingInterceptor.intercept(chain)
            }
        }

        /**
         * Enable/disable HTTP logging. Default is [HttpLoggingInterceptor.Level.NONE].
         *
         * BODY logging is rejected because authentication and rail request bodies contain secrets.
         */
        @JvmStatic
        fun setHttpLoggingLevel(level: HttpLoggingInterceptor.Level) {
            require(level != HttpLoggingInterceptor.Level.BODY) {
                "BODY logging is unavailable because SDK requests contain secrets"
            }
            httpLoggingInterceptor.level = level
        }

        val okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(safeHttpLoggingInterceptor)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        @OptIn(ExperimentalSerializationApi::class)
        val Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
            serializersModule = SerializersModule { contextual(Date::class, DateSerializer) }
        }
    }
}
