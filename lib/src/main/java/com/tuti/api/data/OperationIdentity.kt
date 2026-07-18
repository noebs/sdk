package com.tuti.api.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

private val OPERATION_NAME = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")
private val CURRENCY = Regex("^[A-Z]{3}$")
private val REQUEST_CLAIM = Regex("^v1:[0-9a-f]{64}$")
private const val MAX_I_JSON_INTEGER = 9_007_199_254_740_991L

/**
 * Replay-sensitive facts for a card-funded operation.
 *
 * The claim deliberately excludes the IPIN block, timestamps, display data and server-computed
 * fees. Its fingerprint can be persisted with [OperationIdentity]; the clear target fields should
 * remain in the caller's normal operation state and must not be logged as part of the fingerprint.
 */
class OperationClaim private constructor(
    val operation: String,
    val cardId: String,
    val amountMinor: Long,
    val currency: String,
    private val target: Map<String, String>,
) {
    val requestClaim: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        "v1:${sha256(canonicalJson)}"
    }

    internal val canonicalJson: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        canonicalObject(
            mapOf(
                "version" to CanonicalValue.Integer(1),
                "operation" to CanonicalValue.Text(operation),
                "card_id" to CanonicalValue.Text(cardId),
                "amount_minor" to CanonicalValue.Integer(amountMinor),
                "currency" to CanonicalValue.Text(currency),
                "target" to CanonicalValue.Object(target.mapValues { CanonicalValue.Text(it.value) }),
            )
        )
    }

    init {
        require(OPERATION_NAME.matches(operation)) { "operation must be stable snake_case" }
        requireCanonicalCardId(cardId)
        require(amountMinor in 1..MAX_I_JSON_INTEGER) {
            "amount_minor must be a positive I-JSON safe integer"
        }
        require(CURRENCY.matches(currency)) { "currency must be an uppercase ISO-4217 code" }
        require(target.isNotEmpty()) { "operation target must not be empty" }
        target.forEach { (name, value) ->
            require(OPERATION_NAME.matches(name)) { "target field names must be stable snake_case" }
            requireCanonicalTargetValue(name, value)
        }
    }

    companion object {
        fun mobileTransfer(
            cardId: String,
            amountMinor: Long,
            currency: String,
            phone: String,
        ): OperationClaim = OperationClaim(
            operation = "mobile_transfer",
            cardId = cardId,
            amountMinor = amountMinor,
            currency = currency,
            target = mapOf("phone" to phone),
        )

        fun tokenPayment(
            cardId: String,
            amountMinor: Long,
            currency: String,
            tokenId: String,
        ): OperationClaim {
            requireCanonicalUuid(tokenId, "token_id")
            return OperationClaim(
                operation = "token_payment",
                cardId = cardId,
                amountMinor = amountMinor,
                currency = currency,
                target = mapOf("token_id" to tokenId),
            )
        }

        fun billPayment(
            cardId: String,
            amountMinor: Long,
            currency: String,
            billerId: String,
            accountReference: String,
        ): OperationClaim = OperationClaim(
            operation = "bill_payment",
            cardId = cardId,
            amountMinor = amountMinor,
            currency = currency,
            target = mapOf(
                "biller_id" to billerId,
                "account_reference" to accountReference,
            ),
        )
    }
}

/**
 * Stable state that callers persist and reuse for every attempt of one operation.
 *
 * [requestClaim] is a replay-integrity assertion, not identity or authority. The server normalizes
 * the submitted semantic fields, recomputes the claim and persists its own result.
 */
@Serializable
data class OperationIdentity(
    val uuid: String,
    @SerialName("request_claim")
    val requestClaim: String,
) {
    init {
        requireCanonicalUuid(uuid, "uuid")
        requireRequestClaim(requestClaim)
    }

    fun requireClaim(claim: OperationClaim): OperationIdentity {
        if (!MessageDigest.isEqual(
                requestClaim.toByteArray(StandardCharsets.US_ASCII),
                claim.requestClaim.toByteArray(StandardCharsets.US_ASCII),
            )
        ) {
            throw OperationClaimMismatchException(uuid)
        }
        return this
    }

    companion object {
        fun create(uuid: String, claim: OperationClaim): OperationIdentity =
            OperationIdentity(uuid = uuid, requestClaim = claim.requestClaim)
    }
}

class OperationClaimMismatchException(
    val operationUuid: String,
) : IllegalArgumentException("operation claim does not match the persisted operation identity")

/**
 * Raised by source-compatible legacy entry points that cannot safely satisfy the opaque-card and
 * durable-idempotency contract. No HTTP request is made when this exception is raised.
 */
class OpaqueCardOperationRequiredException(
    val operation: String,
) : IllegalStateException(
    "$operation is disabled: use opaque card IDs and a caller-persisted OperationIdentity for rail operations"
)

internal fun requireRequestClaim(value: String) {
    require(REQUEST_CLAIM.matches(value)) {
        "request_claim must be v1 followed by a lowercase SHA-256 digest"
    }
}

internal fun requireCanonicalTargetValue(fieldName: String, value: String) {
    require(value.isNotBlank() && value == value.trim()) {
        "$fieldName must be a non-blank normalized value"
    }
    require(value.none { it.code in 0x00..0x1f || it.code == 0x7f }) {
        "$fieldName must not contain control characters"
    }
    requireWellFormedUnicode(value, fieldName)
}

private sealed interface CanonicalValue {
    data class Text(val value: String) : CanonicalValue
    data class Integer(val value: Long) : CanonicalValue
    data class Object(val value: Map<String, CanonicalValue>) : CanonicalValue
}

private fun canonicalObject(fields: Map<String, CanonicalValue>): String = buildString {
    append('{')
    fields.entries.sortedBy { it.key }.forEachIndexed { index, (name, value) ->
        if (index > 0) append(',')
        append(canonicalString(name))
        append(':')
        append(canonicalValue(value))
    }
    append('}')
}

private fun canonicalValue(value: CanonicalValue): String = when (value) {
    is CanonicalValue.Text -> canonicalString(value.value)
    is CanonicalValue.Integer -> value.value.toString()
    is CanonicalValue.Object -> canonicalObject(value.value)
}

private fun canonicalString(value: String): String = buildString(value.length + 2) {
    append('"')
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\u000c' -> append("\\f")
            '\r' -> append("\\r")
            else -> when {
                char.code < 0x20 -> append("\\u%04x".format(Locale.ROOT, char.code))
                char.isHighSurrogate() -> {
                    require(index + 1 < value.length && value[index + 1].isLowSurrogate()) {
                        "canonical JSON requires well-formed Unicode"
                    }
                    append(char)
                    append(value[++index])
                }
                char.isLowSurrogate() -> throw IllegalArgumentException(
                    "canonical JSON requires well-formed Unicode"
                )
                else -> append(char)
            }
        }
        index++
    }
    append('"')
}

private fun requireWellFormedUnicode(value: String, fieldName: String) {
    var index = 0
    while (index < value.length) {
        when {
            value[index].isHighSurrogate() -> {
                require(index + 1 < value.length && value[index + 1].isLowSurrogate()) {
                    "$fieldName must contain well-formed Unicode"
                }
                index += 2
            }
            value[index].isLowSurrogate() -> throw IllegalArgumentException(
                "$fieldName must contain well-formed Unicode"
            )
            else -> index++
        }
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
