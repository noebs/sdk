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
import com.tuti.api.wallet.v1.Wallet
import com.tuti.api.wallet.v1.WithdrawalDestination
import com.tuti.api.wallet.v1.WithdrawalDestinationList
import com.tuti.api.wallet.v1.WithdrawalRequest
import com.tuti.api.wallet.v1.WorkflowRun
import com.tuti.model.*
import com.tuti.util.DateSerializer
import com.tuti.util.IPINBlockGenerator
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

class TutiApiClient(val serverURL: String = "https://dapi.nil.sd/",
    val noebsServer: String = "https://dapi.nil.sd/") {

    var isSingleThreaded = false
    @Volatile var authToken: String = ""
    var ipinUsername: String = ""
    var ipinPassword: String = ""
    var ebsKey: String = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBANx4gKYSMv3CrWWsxdPfxDxFvl+Is/0kc1dvMI1yNWDXI3AgdI4127KMUOv7gmwZ6SnRsHX/KAM0IPRe0+Sa0vMCAwEAAQ=="

    val entertainmentServer = "https://plus.2t.sd/"
    val dapiServer = "https://dapi.nil.sd/"

    private val consumerURL = normalizeConsumerBase(serverURL)
    private val noebsBaseURL = ensureTrailingSlash(noebsServer)
    private val walletBaseURL = noebsBaseURL + "wallet"

    /**
     * Wallet API (gRPC-gateway) backing the `/wallet` endpoints (see `proto/noebs/wallet/v1/wallet.proto`).
     */
    val wallet: WalletApi = WalletApi()

    private fun ensureTrailingSlash(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun normalizeConsumerBase(url: String): String {
        val base = ensureTrailingSlash(url)
        return if (base.contains("/consumer/")) base else base + "consumer/"
    }

    private fun fillRequestFields(card: Card, ipin: String, amount: Float): EBSRequest {
        val request = EBSRequest()
        val encryptedIPIN: String = IPINBlockGenerator.getIPINBlock(ipin, ebsKey, request.uuid)
        request.tranAmount = amount
        request.tranCurrencyCode = "SDG"
        request.pan = card.PAN
        request.expDate = card.expiryDate
        request.IPIN = encryptedIPIN
        return request
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
            credentials,
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

    fun GetAllProviders(
        onResponse: (ProvidersResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            entertainmentServer + Operations.GET_PROVIDERS,
            "",
            onResponse,
            onError
        )
    }

    fun GetProviderProducts(
        providerCode: String,
        onResponse: (ProductsResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            entertainmentServer + Operations.GET_PROVIDER_PRODUCTS,
            "",
            onResponse = onResponse,
            onError = onError,
            params = arrayOf("provider", providerCode)
        )
    }

    @Deprecated(
        message = "Use getTransactions() to call the /consumer/transactions endpoint.",
        replaceWith = ReplaceWith("getTransactions")
    )
    fun getDumpTransactions(
        onResponse: (DapiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(method = RequestMethods.GET, dapiServer+Operations.DAPI_GET_TRANSACTIONS,
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
        val request = SendTransferRequest(
            card = card,
            ipin = ipin,
            ebsKey = ebsKey,
            ProductID = productID,
            Amount = amount
        )
        sendRequest(
            RequestMethods.POST,
            entertainmentServer + Operations.ENTERTAINMENT_SEND_TRANSFER,
            requestToBeSent = request,
            onResponse = onResponse,
            onError = onError,
        )
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
            credentials,
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
            credentials,
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

    fun Otp2FA(
        credentials: EBSRequest,
        onResponse: (SignInResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.OTP_2FA,
            credentials,
            onResponse,
            onError
        )
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
            credentials,
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
            URL = "$dapiServer${Operations.NOEBS_WALLET_BALANCE}",
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

        fun ensureWallet(
            request: EnsureWalletRequest,
            onResponse: (Wallet) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<EnsureWalletRequest, Wallet, RpcStatus>(
                method = RequestMethods.POST,
                URL = walletBaseURL,
                requestToBeSent = request,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun getWallet(
            walletId: String,
            tenantId: String,
            onResponse: (Wallet) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<String, Wallet, RpcStatus>(
                method = RequestMethods.GET,
                URL = "$walletBaseURL/$walletId",
                requestToBeSent = "",
                onResponse = onResponse,
                onError = onError,
                params = arrayOf("tenantId", tenantId),
            )
        }

        fun listFundingSources(
            walletId: String,
            tenantId: String,
            onResponse: (FundingSourceList) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<String, FundingSourceList, RpcStatus>(
                method = RequestMethods.GET,
                URL = "$walletBaseURL/$walletId/funding_sources",
                requestToBeSent = "",
                onResponse = onResponse,
                onError = onError,
                params = arrayOf("tenantId", tenantId),
            )
        }

        fun listWithdrawalDestinations(
            walletId: String,
            tenantId: String,
            activeOnly: Boolean = false,
            onResponse: (WithdrawalDestinationList) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            val params = mutableListOf("tenantId", tenantId)
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
            sendRequest<CreateWithdrawalDestinationBody, WithdrawalDestination, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/$walletId/destinations",
                requestToBeSent = request,
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
            sendRequest<DeactivateWithdrawalDestinationBody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/destinations/$destinationId/deactivate",
                requestToBeSent = request,
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
            sendRequest<RequestOwnershipVerificationBody, OwnershipVerification, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/destinations/$destinationId/verify",
                requestToBeSent = request,
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
            sendRequest<CompleteOwnershipVerificationBody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/ownership_verifications/$verificationId/complete",
                requestToBeSent = request,
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
            sendRequest<SetWalletPINBody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/$walletId/pin",
                requestToBeSent = request,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }

        fun requestP2PTransfer(
            request: P2PTransferRequest,
            onResponse: (WorkflowRun) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<P2PTransferRequest, WorkflowRun, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/p2p",
                requestToBeSent = request,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun requestDeposit(
            request: DepositRequest,
            onResponse: (WorkflowRun) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<DepositRequest, WorkflowRun, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/deposits",
                requestToBeSent = request,
                onResponse = onResponse,
                onError = onError,
            )
        }

        fun requestWithdrawal(
            request: WithdrawalRequest,
            onResponse: (WorkflowRun) -> Unit,
            onError: (RpcStatus?, Exception?) -> Unit,
        ) {
            sendRequest<WithdrawalRequest, WorkflowRun, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/withdrawals",
                requestToBeSent = request,
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
            sendRequest<ManualTransferRequest, WorkflowRun, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/manual_transfers",
                requestToBeSent = request,
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
            sendRequest<EnrollUser2FABody, User2FASetup, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/users/$userId/2fa/enroll",
                requestToBeSent = request,
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
            sendRequest<ConfirmUser2FABody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/users/$userId/2fa/confirm",
                requestToBeSent = request,
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
            sendRequest<DisableUser2FABody, Unit, RpcStatus>(
                method = RequestMethods.POST,
                URL = "$walletBaseURL/users/$userId/2fa/disable",
                requestToBeSent = request,
                onResponse = { _ -> onResponse() },
                onError = onError,
            )
        }
    }


    @Deprecated(
        message = "Replace with SignUp with new kotlin classes instead.",
        replaceWith = ReplaceWith("SignUp")
    )
            /**
             * @param signUpRequest
             * @param onResponse
             * @param onError
             */
    fun Signup(
        signUpRequest: SignUpRequest?,
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

    fun Signup(
        signUpRequest: SignupRequest,
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

    fun SignupWithCard(
        signUpRequest: SignUpCard?,
        onResponse: (SignUpResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.SIGN_UP_WITH_CARD,
            signUpRequest,
            onResponse,
            onError,
        )
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
        sendRequest(
            RequestMethods.POST,
            URL,
            ebsRequest,
            onResponse,
            onError,
        )
    }

    fun getCards(
        onResponse: (Cards) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.GET_CARDS,
            "",
            onResponse,
            onError,
        )
    }

    fun getPublicKey(
        ebsRequest: EBSRequest?,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.PUBLIC_KEY,
            ebsRequest,
            onResponse,
            onError,
        )
    }

    fun getIpinPublicKey(
        ebsRequest: EBSRequest,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.IPIN_key,
            ebsRequest,
            onResponse,
            onError
        )
    }

    fun syncContacts(
        contacts: List<Contact>,
        onResponse: (contacts: List<Contact>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.SUBMIT_CONTACTS,
            contacts,
            onResponse,
            onError,
        )
    }

    fun addBeneficiary(
        beneficiary: NoebsBeneficiary,
        onResponse: (TutiResponse?) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {

        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.BENEFICIARY,
            beneficiary,
            onResponse,
            onError,
        )
    }

    fun getBeneficiaries(
        onResponse: (List<NoebsBeneficiary>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.BENEFICIARY,
            "",
            onResponse,
            onError,
        )
    }

    fun deleteBeneficiary(
        beneficiary: NoebsBeneficiary,
        onResponse: (String) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.DELETE,
            consumerURL + Operations.BENEFICIARY,
            beneficiary,
            onResponse,
            onError,
        )
    }

    fun addCard(
        card: Card,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.ADD_CARD,
            listOf(card),
            onResponse,
            onError,
        )
    }

    fun editCard(
        card: Card?,
        onResponse: (String) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.PUT,
            consumerURL + Operations.EDIT_CARD,
            card,
            onResponse,
            onError,
        )
    }

    fun deleteCard(
        card: Card?,
        onResponse: (String) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.DELETE,
            consumerURL + Operations.DELETE_CARD,
            card,
            onResponse,
            onError,
        )
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
        val request = EBSRequest()
        val encryptedIPIN: String = IPINBlockGenerator.getIPINBlock(ipin, ebsKey, request.uuid)
        request.pan = card.PAN
        request.expDate = card.expiryDate
        request.IPIN = encryptedIPIN
        //println(request.tranDateTime)
        //println(request.applicationId)

        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.GET_BALANCE,
            request,
            onResponse,
            onError,
        )
    }


    /**
     * billInfo gets a bill from EBS using a pre-stored data, only send applicable bill fields
     * plus the type of transaction
     *
     * @param billInfo
     * @param onResponse
     * @param onError
     */
    fun billInquiry(
        billInfo: BillInfo,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit,
    ): Call {
        return sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.Get_Bills,
            billInfo,
            onResponse,
            onError,
        )
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

    fun guessBillerId(
        mobile: String,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.GUESS_Biller,
            "",
            onResponse,
            onError,
            null,
            "mobile",
            mobile
        )
    }

    fun generatePaymentToken(
        request: PaymentToken,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.GeneratePaymentToken,
            request,
            onResponse,
            onError,
        )
    }

    fun sendPaymentRequest(
        paymentRequest: PaymentRequest,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.PAYMENT_REQUEST,
            paymentRequest,
            onResponse,
            onError,
        )
    }

    @Deprecated("Legacy noebs transfer endpoint; not available in the current /consumer API.")
    fun noebsTransfer(
        request: NoebsTransfer,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            dapiServer + Operations.NOEBS_CARD_TRANSFER,
            request,
            onResponse, onError
        )
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
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.QuickPayment,
            request,
            onResponse,
            onError, null,
            "uuid", uuid
        )
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

    fun getPaymentToken(
        uuid: String,
        onResponse: (PaymentToken) -> Unit,
        onError: (PaymentToken?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.GetPaymentToken,
            "",
            onResponse,
            onError,
            null,
            "uuid", uuid
        )
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


    /**
     * generateIpin the first step into generating a new IPIN
     */
    fun generateIpin(
        data: Ipin,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = EBSRequest()
        request.expDate = data.expDate
        request.phoneNumber = "249" + data.phone.removePrefix("0")
        request.pan = data.pan
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.START_IPIN,
            request,
            onResponse,
            onError
        )
    }

    /**
     * Second step for ipin generation, user will be prompted to enter the otp
     * alongside other data for complete ipin generation step to take place
     */
    fun confirmIpinGeneration(
        data: IpinCompletion,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        val request = EBSRequest()
        request.otherPan = data.pan
        request.IPIN = (data.ipin)
        request.otp = (data.otp)
        request.expDate = data.expDate
        request.phoneNumber = "249" + data.phone.removePrefix("0")
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.CONFIRM_IPIN,
            request,
            onResponse,
            onError
        )
    }


    fun changeIPIN(
        card: Card,
        oldIPIN: String,
        newIPIN: String,
        onResponse: (TutiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {

        val request = EBSRequest()

        val oldIPINEncrypted: String =
            IPINBlockGenerator.getIPINBlock(oldIPIN, ebsKey, request.uuid)
        val newIPINEncrypted: String =
            IPINBlockGenerator.getIPINBlock(newIPIN, ebsKey, request.uuid)

        request.expDate = card.expiryDate
        request.IPIN = (oldIPINEncrypted)
        request.newIPIN = (newIPINEncrypted)
        request.pan = card.PAN

        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.CHANGE_IPIN,
            request,
            onResponse,
            onError
        )
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


    fun getUserCard(
        mobile: String,
        onResponse: (UserCards) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            consumerURL + Operations.USER_CARDS,
            "",
            onResponse,
            onError,
            null,
            "mobile",
            mobile
        )
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

    fun setMainCard(
        card: SetMainCardRequest,
        onResponse: (SetMainCardResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            consumerURL + Operations.SET_MAIN_CARD,
            card,
            onResponse,
            onError,
            null,
        )
    }


    @Deprecated("Legacy ledger endpoint; use getTransactions() for /consumer/transactions.")
    fun retrieveLedgerTransactions(
        accountId: String,
        onResponse: (List<Ledger>) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            serverURL + Operations.LEDGER_TRANSACTIONS,
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
            serverURL + Operations.NOEBS_TRANSACTIONS,
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
        val wsURL = buildWsURL(wsBaseURL, path)
        val requestBuilder = Request.Builder().url(wsURL)
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", token)
        }
        return okHttpClient.newWebSocket(requestBuilder.build(), listener)
    }

    inline fun <reified RequestType, reified ResponseType, reified ErrorType> sendRequest(
        method: RequestMethods,
        URL: String,
        requestToBeSent: RequestType? = null,
        crossinline onResponse: (ResponseType) -> Unit,
        crossinline onError: (ErrorType?, Exception?) -> Unit,
        headers: Map<String, String>? = null,
        vararg params: String
    ): Call {
        require(params.size % 2 == 0) {
            "params must be an even number of key/value entries. Got ${params.size}."
        }

        val baseUrl = URL.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: $URL")
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

        // add additional headers set by the user
        headers?.forEach { (key, value) ->
            requestBuilder.header(key, value)
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
        private val httpLoggingInterceptor: HttpLoggingInterceptor =
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE)

        /**
         * Enable/disable HTTP logging. Default is [HttpLoggingInterceptor.Level.NONE].
         *
         * Warning: BODY logging may leak sensitive data (JWTs, PANs, IPIN-related payloads) into app logs.
         */
        @JvmStatic
        fun setHttpLoggingLevel(level: HttpLoggingInterceptor.Level) {
            httpLoggingInterceptor.level = level
        }

        val okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(httpLoggingInterceptor)
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
