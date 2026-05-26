package com.tuti.api.wallet.v1

import com.tuti.util.LongAsStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * gRPC-Gateway error payload (google.rpc.Status).
 *
 * Example:
 * {"code":3,"message":"missing_tenant_id","details":[]}
 */
@Serializable
data class RpcStatus(
    val code: Int = 0,
    val message: String = "",
    val details: List<JsonElement> = emptyList(),
)

@Serializable
data class WorkflowRun(
    val workflowId: String = "",
    val runId: String = "",
)

@Serializable
data class Wallet(
    val id: String = "",
    val tenantId: String = "",
    val ownerType: String = "",
    val ownerId: String = "",
    val currency: String = "",
    @Serializable(with = LongAsStringSerializer::class)
    val balance: Long = 0,
    @Serializable(with = LongAsStringSerializer::class)
    val availableBalance: Long = 0,
    val status: String = "",
    val kycTier: String = "",
)

@Serializable
data class EnsureWalletRequest(
    val tenantId: String,
    @Serializable(with = LongAsStringSerializer::class)
    val userId: Long,
    val currency: String = "",
)

@Serializable
data class P2PTransferRequest(
    val tenantId: String,
    val idempotencyKey: String = "",
    val currency: String,
    val fromWalletId: String,
    val toWalletId: String,
    @Serializable(with = LongAsStringSerializer::class)
    val amount: Long,
    val description: String = "",
    val referenceId: String = "",
    val fromOwnerType: String,
    val fromOwnerId: String,
    val toOwnerType: String,
    val toOwnerId: String,
    @Serializable(with = LongAsStringSerializer::class)
    val userId: Long = 0,
    val walletPin: String = "",
    val twoFaCode: String = "",
)

@Serializable
data class DepositRequest(
    val tenantId: String,
    val clientReference: String,
    val providerCode: String,
    val walletId: String,
    val ownerType: String,
    val ownerId: String,
    @Serializable(with = LongAsStringSerializer::class)
    val amount: Long,
    val currency: String,
    @Serializable(with = LongAsStringSerializer::class)
    val feeAmount: Long? = null,
    @Serializable(with = LongAsStringSerializer::class)
    val netAmount: Long? = null,
    val idempotencyKey: String = "",
    val metadata: Map<String, JsonElement>? = null,
    val region: String = "",
)

@Serializable
data class ManualTransferRequest(
    val tenantId: String,
    val idempotencyKey: String,
    val transferType: String,
    val walletId: String,
    @Serializable(with = LongAsStringSerializer::class)
    val amount: Long,
    val currency: String,
    val reason: String,
    @Serializable(with = LongAsStringSerializer::class)
    val requestedBy: Long,
    val pspProvider: String = "",
    val pspReference: String = "",
    val approvalTimeoutSeconds: Int,
)

@Serializable
data class WithdrawalRequest(
    val tenantId: String,
    val clientReference: String,
    val providerCode: String,
    val walletId: String,
    @Serializable(with = LongAsStringSerializer::class)
    val amount: Long,
    val currency: String,
    @Serializable(with = LongAsStringSerializer::class)
    val userId: Long = 0,
    val ownerType: String,
    val ownerId: String,
    @Serializable(with = LongAsStringSerializer::class)
    val destinationId: Long = 0,
    val allowReturnToSource: Boolean? = null,
    val walletPin: String = "",
    val twoFaCode: String = "",
    val holdExpirySeconds: Int = 0,
    val approvalTimeoutSeconds: Int = 0,
    val verificationTimeoutSeconds: Int = 0,
    val idempotencyKey: String = "",
    val metadata: Map<String, JsonElement>? = null,
    val region: String = "",
)

@Serializable
data class FundingSource(
    @Serializable(with = LongAsStringSerializer::class)
    val id: Long = 0,
    val tenantId: String = "",
    val walletId: String = "",
    val sourceType: String = "",
    val pspProvider: String = "",
    val externalReference: String = "",
    val verificationStatus: String = "",
    val currency: String = "",
    val sourceDetails: Map<String, JsonElement>? = null,
    @Serializable(with = LongAsStringSerializer::class)
    val totalFunded: Long = 0,
    @Serializable(with = LongAsStringSerializer::class)
    val totalWithdrawn: Long = 0,
    val supportsWithdrawal: Boolean = false,
    val withdrawalMethod: Map<String, JsonElement>? = null,
)

@Serializable
data class FundingSourceList(
    val sources: List<FundingSource> = emptyList(),
)

@Serializable
data class WithdrawalDestination(
    @Serializable(with = LongAsStringSerializer::class)
    val id: Long = 0,
    val tenantId: String = "",
    val walletId: String = "",
    val destinationType: String = "",
    val pspProvider: String = "",
    val destinationDetails: Map<String, JsonElement>? = null,
    val displayName: String = "",
    val currency: String = "",
    val country: String = "",
    val ownershipStatus: String = "",
    val ownershipVerificationMethod: String = "",
    @Serializable(with = LongAsStringSerializer::class)
    val linkedFundingSourceId: Long = 0,
    val isReturnToSource: Boolean = false,
    val isActive: Boolean = false,
    @Serializable(with = LongAsStringSerializer::class)
    val totalWithdrawn: Long = 0,
)

@Serializable
data class WithdrawalDestinationList(
    val destinations: List<WithdrawalDestination> = emptyList(),
)

@Serializable
data class OwnershipVerification(
    @Serializable(with = LongAsStringSerializer::class)
    val id: Long = 0,
    val tenantId: String = "",
    @Serializable(with = LongAsStringSerializer::class)
    val destinationId: Long = 0,
    val verificationType: String = "",
    val status: String = "",
    val attempts: Int = 0,
    val maxAttempts: Int = 0,
    val workflowId: String = "",
    val referenceId: String = "",
)

@Serializable
data class User2FASetup(
    val tenantId: String = "",
    @Serializable(with = LongAsStringSerializer::class)
    val userId: Long = 0,
    val secret: String = "",
    val otpAuthUrl: String = "",
    val enabled: Boolean = false,
)

// Bodies for endpoints where the request message includes path variables.

@Serializable
data class CreateWithdrawalDestinationBody(
    val tenantId: String,
    val destinationType: String,
    val pspProvider: String = "",
    val destinationDetails: Map<String, JsonElement>,
    val displayName: String = "",
    val currency: String,
    val country: String = "",
    val ownershipVerificationMethod: String = "",
    @Serializable(with = LongAsStringSerializer::class)
    val linkedFundingSourceId: Long = 0,
    val isReturnToSource: Boolean = false,
)

@Serializable
data class DeactivateWithdrawalDestinationBody(
    val tenantId: String,
)

@Serializable
data class RequestOwnershipVerificationBody(
    val tenantId: String,
    val verificationType: String,
    val verificationTimeoutSeconds: Int,
    val workflowId: String = "",
    val referenceId: String = "",
)

@Serializable
data class CompleteOwnershipVerificationBody(
    val tenantId: String,
    val verified: Boolean,
    val reason: String = "",
)

@Serializable
data class SetWalletPINBody(
    val tenantId: String,
    val newPin: String,
    val currentPin: String = "",
)

@Serializable
data class SignalManualTransferDecisionBody(
    val approved: Boolean,
    @Serializable(with = LongAsStringSerializer::class)
    val approverId: Long,
    val reason: String,
    val proofOfPayment: String = "",
)

@Serializable
data class SignalWithdrawalApprovalBody(
    val approved: Boolean,
    @Serializable(with = LongAsStringSerializer::class)
    val approverId: Long,
    val reason: String,
    val proofOfPayment: String = "",
)

@Serializable
data class SignalWithdrawalVerificationBody(
    @Serializable(with = LongAsStringSerializer::class)
    val verificationId: Long,
    val verified: Boolean,
    val reason: String = "",
)

@Serializable
data class EnrollUser2FABody(
    val tenantId: String,
)

@Serializable
data class ConfirmUser2FABody(
    val tenantId: String,
    val code: String,
)

@Serializable
data class DisableUser2FABody(
    val tenantId: String,
    val code: String,
)

@Serializable
data class CreateWalletRequest(
    @SerialName("tenant_id")
    val tenantId: String? = null,
    @SerialName("user_id")
    val userId: Long? = null,
    val currency: String = "",
)

@Serializable
data class UserWallet(
    val id: String = "",
    @SerialName("tenant_id")
    val tenantId: String = "",
    @SerialName("owner_type")
    val ownerType: String = "",
    @SerialName("owner_id")
    val ownerId: String = "",
    @SerialName("user_id")
    val userId: Long? = null,
    val currency: String = "",
    val balance: Long = 0,
    @SerialName("available_balance")
    val availableBalance: Long = 0,
    val status: String = "",
    @SerialName("kyc_tier")
    val kycTier: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
)

@Serializable
data class WalletPaymentMethodQuery(
    val direction: String,
    val currency: String = "",
    val region: String = "",
    val amount: Long? = null,
    val tenantId: String = "",
    val limit: Int = 100,
    val offset: Int = 0,
)

@Serializable
data class WalletPaymentMethodList(
    val methods: List<WalletPaymentMethod> = emptyList(),
)

@Serializable
data class WalletPaymentMethod(
    @SerialName("provider_code")
    val providerCode: String = "",
    @SerialName("provider_name")
    val providerName: String = "",
    @SerialName("display_name")
    val displayName: String = "",
    @SerialName("method_type")
    val methodType: String = "",
    val direction: String = "",
    val currencies: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    @SerialName("min_amount")
    val minAmount: Long? = null,
    @SerialName("max_amount")
    val maxAmount: Long? = null,
    @SerialName("input_schema")
    val inputSchema: JsonElement? = null,
    val presentation: JsonElement? = null,
    @SerialName("supports_deposit")
    val supportsDeposit: Boolean = false,
    @SerialName("supports_withdrawal")
    val supportsWithdrawal: Boolean = false,
)

@Serializable
data class WalletTransactionList(
    val transactions: List<WalletTransaction> = emptyList(),
)

@Serializable
data class WalletTransaction(
    val id: Long = 0,
    @SerialName("tenant_id")
    val tenantId: String = "",
    @SerialName("transaction_id")
    val transactionId: Long = 0,
    @SerialName("wallet_id")
    val walletId: String = "",
    @SerialName("entry_type")
    val entryType: String = "",
    val amount: Long = 0,
    val currency: String = "",
    @SerialName("balance_after")
    val balanceAfter: Long = 0,
    @SerialName("wallet_sequence")
    val walletSequence: Long = 0,
    val status: String = "",
    @SerialName("reference_type")
    val referenceType: String = "",
    @SerialName("reference_id")
    val referenceId: String? = null,
    val description: String? = null,
    val metadata: JsonElement? = null,
    @SerialName("created_at")
    val createdAt: String = "",
)
