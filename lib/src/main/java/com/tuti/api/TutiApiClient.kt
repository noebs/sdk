package com.tuti.api

import com.tuti.api.authentication.SignInRequest
import com.tuti.api.authentication.SignInResponse
import com.tuti.api.authentication.SignUpRequest
import com.tuti.api.authentication.SignUpResponse
import com.tuti.api.data.*
import com.tuti.api.ebs.EBSRequest
import com.tuti.api.ebs.EBSResponse
import com.tuti.model.*
import com.tuti.util.IPINBlockGenerator
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

class TutiApiClient {
    var serverURL: String? = null
        private set
    var isSingleThreaded = false
    var authToken: String = ""
    var ipinUsername: String = ""
    var ipinPassword: String = ""
    var ebsKey: String =
        "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBANx4gKYSMv3CrWWsxdPfxDxFvl+Is/0kc1dvMI1yNWDXI3AgdI4127KMUOv7gmwZ6SnRsHX/KAM0IPRe0+Sa0vMCAwEAAQ=="

    val entertainmentServer = "https://plus.2t.sd/"
    val dapiServer = "https://blue-violet-2528.fly.dev/"

    @Deprecated("")
    constructor(isDevelopment: Boolean) {
        serverURL = getServerURL(isDevelopment)
    }


    constructor() {
        serverURL = getServerURL(false)
    }

    private fun getServerURL(development: Boolean): String {
        val developmentHost = "https://beta.app.2t.sd/consumer/"
        val productionHost = "https://beta.app.2t.sd/consumer/"
        return if (development) developmentHost else productionHost
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
            serverURL + Operations.SIGN_IN,
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
            serverURL + Operations.SIGN_IN,
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
            serverURL + Operations.ChangePassword,
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

    fun getDumpTransactions(
        onResponse: (DapiResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(method = RequestMethods.GET, dapiServer+Operations.DAPI_GET_TRANSACTIONS,
            onResponse=onResponse, onError=onError, requestToBeSent = 1)
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
            serverURL + Operations.USER_PROFILE,
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
            serverURL + Operations.USER_PROFILE,
            userProfile,
            onResponse = onResponse,
            onError = onError,
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
            serverURL + Operations.SINGLE_SIGN_IN,
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
            serverURL + Operations.GENERATE_LOGIN_OTP,
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
            serverURL + Operations.GENERATE_LOGIN_OTP_INSECURE,
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
            serverURL + Operations.VERIFY_OTP,
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
            serverURL + Operations.OTP_2FA,
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
            serverURL + Operations.REFRESH_TOKEN,
            credentials,
            onResponse,
            onError
        )
    }

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
            serverURL + Operations.SIGN_UP,
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
            serverURL + Operations.SIGN_UP,
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
            serverURL + Operations.SIGN_UP_WITH_CARD,
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
            serverURL + Operations.NOTIFICATIONS,
            filters.mobile,
            onResponse,
            onError, null,
            runOnOwnThread = true,
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
    fun VerifyFirebase(
        signUpRequest: SignUpRequest?,
        onResponse: (SignUpResponse) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.POST,
            serverURL + Operations.VERIFY_FIREBASE,
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
            serverURL + Operations.GET_CARDS,
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
            serverURL + Operations.PUBLIC_KEY,
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
            serverURL + Operations.IPIN_key,
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
            serverURL + Operations.SUBMIT_CONTACTS,
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
            serverURL + Operations.BENEFICIARY,
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
            serverURL + Operations.BENEFICIARY,
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
            serverURL + Operations.BENEFICIARY,
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
            serverURL + Operations.ADD_CARD,
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
            serverURL + Operations.EDIT_CARD,
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
            serverURL + Operations.DELETE_CARD,
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
            serverURL + Operations.GET_BALANCE,
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
        runOnOwnThread: Boolean = true,
    ): Thread {
        return sendRequest(
            RequestMethods.POST,
            serverURL + Operations.Get_Bills,
            billInfo,
            onResponse,
            onError,
            runOnOwnThread = runOnOwnThread
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
            serverURL + Operations.CARD_TRANSFER,
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
            serverURL + Operations.P2P_MOBILE,
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
            serverURL + Operations.BILL_PAYMENT,
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
            serverURL + Operations.GENERATE_VOUCHER,
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
            serverURL + Operations.BILL_PAYMENT,
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
            serverURL + Operations.BILL_PAYMENT,
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
            serverURL + Operations.BILL_PAYMENT,
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
            serverURL + Operations.BILL_PAYMENT,
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
            serverURL + Operations.BILL_PAYMENT,
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
            serverURL + Operations.BILL_PAYMENT,
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
            serverURL + Operations.BILL_PAYMENT,
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
            serverURL + Operations.GUESS_Biller,
            "",
            onResponse,
            onError,
            null,
            runOnOwnThread = true,
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
            serverURL + Operations.GeneratePaymentToken,
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
            serverURL + Operations.PAYMENT_REQUEST,
            paymentRequest,
            onResponse,
            onError,
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
            serverURL + Operations.QuickPayment,
            request,
            onResponse,
            onError, null,
            runOnOwnThread = true,
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
            serverURL + Operations.QuickPayment,
            request,
            onResponse,
            onError, null, runOnOwnThread = true, "uuid", uuid
        )
    }

    fun getPaymentToken(
        uuid: String,
        onResponse: (PaymentToken) -> Unit,
        onError: (PaymentToken?, Exception?) -> Unit
    ) {
        sendRequest(
            RequestMethods.GET,
            serverURL + Operations.GetPaymentToken,
            "",
            onResponse,
            onError,
            null,
            runOnOwnThread = true,
            "uuid", uuid
        )
    }

    fun UpsertFirebase(
        token: String,
        onResponse: (TutiResponse?) -> Unit,
        onError: (TutiResponse?, Exception?) -> Unit
    ) {
        @Serializable
        data class data(@SerialName("token") val data: String)

        sendRequest(
            RequestMethods.POST,
            serverURL + Operations.UpsertFirebaseToken,
            data(token),
            onResponse,
            onError,
        )
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
            serverURL + Operations.START_IPIN,
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
            serverURL + Operations.CONFIRM_IPIN,
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
            serverURL + Operations.CHANGE_IPIN,
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
            serverURL + Operations.TRANSACTION_BY_ID,
            "",
            onResponse,
            onError,
            null,
            runOnOwnThread = true,
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
            serverURL + Operations.USER_CARDS,
            "",
            onResponse,
            onError,
            null,
            runOnOwnThread = true,
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
            serverURL + Operations.CHECK_USER,
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
            serverURL + Operations.SET_MAIN_CARD,
            card,
            onResponse,
            onError,
            null,
        )
    }

    inline fun <reified RequestType, reified ResponseType, reified ErrorType> sendRequest(
        method: RequestMethods,
        URL: String,
        requestToBeSent: RequestType?,
        crossinline onResponse: (ResponseType) -> Unit,
        crossinline onError: (ErrorType?, Exception?) -> Unit,
        headers: Map<String, String>? = null,
        runOnOwnThread: Boolean = true,
        vararg params: String
    ): Thread {
        val d = params.toList().chunked(2).map { it[0] to it[1] }
        val urlData = d.map { "${it.first}=${it.second}" }.joinToString("&", "?", "")
        val finalURL = URL + urlData
        val runnable = {
            val jsonObjectString = Json.encodeToString(requestToBeSent)
            //println(jsonObjectString)
            val requestBody: RequestBody = jsonObjectString.toRequestBody(JSON)
            val requestBuilder: Request.Builder = Request.Builder().url(finalURL)
            requestBuilder.header("Authorization", authToken)
            requestBuilder.header("Accept", "application/json")

            //add additional headers set by the user
            if (headers != null) {
                for (key in headers.keys) {
                    headers[key]?.let { requestBuilder.header(key, it) }
                }
            }

            //check for http method set by the user
            when (method) {
                RequestMethods.POST -> requestBuilder.post(requestBody)
                RequestMethods.DELETE -> requestBuilder.delete(requestBody)
                RequestMethods.PUT -> requestBuilder.put(requestBody)
                else -> requestBuilder.get()
            }

            val request: Request = requestBuilder.build()
            try {
                okHttpClient.newCall(request).execute().use { rawResponse ->
                    // check for http errors
                    val responseCode = rawResponse.code
                    val responseBody = rawResponse.body?.string() ?: ""
                    if (responseCode in 400..599) {
                        // call onError if request has failed
                        onError(parseResponse(responseBody), null)
                    } else {
                        // call onResponse if request has succeeded
                        onResponse(parseResponse(responseBody))
                    }
                }
            } catch (exception: Exception) {
                when (exception) {
                    is SerializationException -> {
                        exception.printStackTrace()
                        onError(null, exception)
                    }
                    is IOException -> {
                        exception.printStackTrace()
                        onError(null, exception)
                    }
                    else -> {
                        exception.printStackTrace()
                        throw exception
                    }
                }
            }
        }

        // unit testing concurrent code on multiple threads is hard
        val thread = Thread(runnable)
        if (runOnOwnThread) {
            thread.start()
        } else {
            thread.run()
        }
        return thread
    }


    inline fun <reified ResponseType> parseResponse(responseAsString: String): ResponseType {
        return when (ResponseType::class.java) {
            String::class.java -> {
                responseAsString as ResponseType
            }
            else -> {
                Json.decodeFromString(responseAsString)
            }
        }

    }

    companion object {
        val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
        private val logger: HttpLoggingInterceptor
            get() {
                val logging = HttpLoggingInterceptor()
                logging.setLevel(HttpLoggingInterceptor.Level.BODY)
                return logging
            }

        val okHttpClient: OkHttpClient =
            OkHttpClient.Builder().addInterceptor(logger).connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build();

        val Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
}
