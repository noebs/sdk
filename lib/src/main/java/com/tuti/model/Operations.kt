package com.tuti.model

object Operations {
    const val ENTERTAINMENT_SEND_TRANSFER = "SendTransfer"
    const val GET_PROVIDER_PRODUCTS = "GetProviderProducts"
    const val GET_PROVIDERS = "GetAllProviders"
    const val PUBLIC_KEY = "key"
    const val GET_BALANCE = "balance"
    const val PURCHASE = "purchase"
    const val CARD_TRANSFER = "p2p"
    const val ACCOUNT_TRANSFER = "account"
    const val CHANGE_IPIN = "ipin"
    const val SIGN_IN = "login"
    const val SINGLE_SIGN_IN = "otp/login"
    const val GENERATE_LOGIN_OTP = "otp/generate"
    const val GENERATE_LOGIN_OTP_INSECURE = "otp/generate"
    const val VERIFY_OTP = "otp/verify"
    const val OTP_2FA = "otp/balance"
    const val SIGN_UP = "register"
    const val SIGN_UP_WITH_CARD = "register_with_card"
    const val GET_CARDS = "get_cards"
    const val ADD_CARD = "add_card"
    const val DELETE_CARD = "delete_card"
    const val EDIT_CARD = "edit_card"
    const val BENEFICIARY = "beneficiary"
    const val BILL_PAYMENT = "bill_payment"
    const val Get_Bills = "bills"
    const val GUESS_Biller = "guess_biller"
    const val BILL_INQUIRY = "bill_inquiry"
    const val CARD_ISSUANCE = "cards/new"
    const val CARD_COMPLETION = "cards/complete"
    const val GENERATE_VOUCHER = "vouchers/generate"
    const val QR_PAYMENT = "qr_payment"
    const val QR_REFUND = "qr_refund"
    const val REFRESH_TOKEN = "refresh"
    const val START_IPIN = "generate_ipin"
    const val CONFIRM_IPIN = "complete_ipin"
    const val QR_INFO = "https://qr.noebs.dev"
    const val Cash_In = "cashIn"
    const val Cash_Out = "cashOut"
    const val STATUS = "status"
    const val QR_GENERATE = "generate_qr"
    const val IPIN_key = "ipin_key"
    const val QR_STATUS = "qr_status"
    const val QR_COMPLETE = "qr_complete"
    const val VERIFY_FIREBASE = "verify_firebase"
    const val GeneratePaymentToken = "payment_token"
    const val QuickPayment = "payment_token/quick_pay"
    const val UpsertFirebaseToken = "user/firebase"
    const val GetPaymentToken = "payment_token"
    const val ChangePassword = "change_password"
    const val TRANSACTION_BY_ID = "transaction"
    const val USER_CARDS = "users/cards"
    const val NOTIFICATIONS = "notifications"
    const val SUBMIT_CONTACTS = "submit_contacts"
    const val CHECK_USER = "check_user"
    const val P2P_MOBILE = "p2p_mobile"
    const val SET_MAIN_CARD = "cards/set_main"
}