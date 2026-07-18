package com.tuti.api.data

import com.tuti.util.IPINBlockGenerator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

sealed class ProviderCode(val code: String) {
    object NetflixUS : ProviderCode("NYUS") // Netflix US
    object NetflixAE : ProviderCode("NYAE") // Netflix AE
    object SpotifyUS : ProviderCode("FHUS") // Spotify US
    object PubgUS : ProviderCode("PGUS") // PubG US
    object FreeFire : ProviderCode("FIUS") // Free Fire
    object PlayStationUS : ProviderCode("PNUS") // Playstation Network US
    object PlayStationAE : ProviderCode("PNAE") // Playstation Network AE
    object PlayStationSA : ProviderCode("PNSA") // Playstation Network SA
    object FifaAE : ProviderCode("FAAE") // FIFA AE
    object Roblox : ProviderCode("4RUS") // Roblox
    object NoonUAE : ProviderCode("3GAE") // Noon UAE
    object NoonKSA : ProviderCode("3GSA") // Noon KSA
    object GooglePlayKSA : ProviderCode("GPSA") // Google Play KSA
    object GooglePlayUS : ProviderCode("GPUS") // Google Play US
    object GooglePlayUAE : ProviderCode("GPAE") // Google Play UAE
    object AppleUS : ProviderCode("IAUS") // App store and iTunes US
    object AppleKSA : ProviderCode("IASA") // App store and iTunes KSA
    object AppleUAE : ProviderCode("IAAE") // App store and iTunes UAE
    object AmazonUAE : ProviderCode("2AAE") // Amazon UAE
    object AmazonKSA : ProviderCode("2ASA") // Amazon KSA
    object AmazonUS : ProviderCode("2AUS") // Amazon US
}

@Serializable
data class BalanceResponse(
        @SerialName("Balance")
        val balance: Float,
        @SerialName("CurrencyISO")
        val currencyISO: String
)

@Serializable
data class ProvidersResponse(
        @SerialName("Items")
        val providers: List<Provider>,
)

@Serializable
data class ProductsResponse(
        @SerialName("Items")
        val products: List<Product>,
)

@Serializable
data class Provider(
        @SerialName("CountryIso")
        val countryIso: String,

        @SerialName("LogoUrl")
        val logoUrl: String,

        @SerialName("Name")
        val name: String,

        @SerialName("ProviderCode")
        val providerCode: String,

        @SerialName("ValidationRegex")
        val validationRegex: String,
)

@Serializable
data class Product(
        @SerialName("Benefits")
        val benefits: List<String>,

        @SerialName("CommissionRate")
        val CommissionRate: Float,

        @SerialName("DefaultDisplayText")
        val DefaultDisplayText: String,

        @SerialName("LocalizationKey")
        val LocalizationKey: String,

        @SerialName("LookupBillsRequired")
        val LookupBillsRequired: Boolean,

        @SerialName("Maximum")
        val Maximum: AmountInfo,

        @SerialName("Minimum")
        val Minimum: AmountInfo,

        @SerialName("ProcessingMode")
        val ProcessingMode: String,

        @SerialName("ProviderCode")
        val ProviderCode: String,

        @SerialName("RedemptionMechanism")
        val RedemptionMechanism: String,

        @SerialName("RegionCode")
        val RegionCode: String,

        @SerialName("SkuCode")
        val SkuCode: String,
)

@Serializable
data class SendTransferRequest(
        @Transient
        val card: Card = Card(),
        @Transient
        val ipin: String = "",
        @Transient
        val ebsKey: String = "",


        val ProductID: String,
        val Amount: Float,
        val PAN: String = card.PAN,
        val ExpDate: String = card.expiryDate,
        val ApplicationId: String = "TutiPayCon",
        val TutiDiscount: Float = 0f,
        val BalanceUUID: String,
        val BalanceIPIN: String = IPINBlockGenerator.getIPINBlock(ipin, ebsKey, BalanceUUID),
        val BalanceTranDateTime: String = "050123141642",
        val CardTransferUUID: String,
        val CardTransferIPIN: String = IPINBlockGenerator.getIPINBlock(ipin, ebsKey, CardTransferUUID),
        val CardTransferTranDateTime: String = "050123141642",
        ) {
        init {
                requireCanonicalUuid(BalanceUUID, "BalanceUUID")
                requireCanonicalUuid(CardTransferUUID, "CardTransferUUID")
        }
}

@Serializable
data class SendTransferResponse(
        val uuid: String,
        val productCode: String,
        val sendValue: Float,
        val receiveValue: Float,
        val instructions: String,
        val params: Map<String, String>,
        val timestamp: String
)

@Serializable
data class AmountInfo(
        @SerialName("CustomerFee")
        val CustomerFee: Float,

        @SerialName("DistributorFee")
        val DistributorFee: Float,

        @SerialName("ReceiveCurrencyIso")
        val ReceiveCurrencyIso: String,

        @SerialName("ReceiveValue")
        val ReceiveValue: Float,

        @SerialName("ReceiveValueExcludingTax")
        val ReceiveValueExcludingTax: Float,

        @SerialName("SendCurrencyIso")
        val SendCurrencyIso: String,

        @SerialName("SendValue")
        val SendValue: Float,

        @SerialName("TaxRate")
        val TaxRate: Float,
)
