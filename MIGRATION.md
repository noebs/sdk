# Migration Notes

This SDK has been modernized and contains breaking changes. Highlights below are organized by area.

## Build And Tooling

- Gradle wrapper upgraded to `9.3.1` (requires Java 17+ to run the build).
- Kotlin upgraded to `2.3.0`.
- Dependency versions are centralized in `gradle/libs.versions.toml` (Gradle Version Catalog).
- `jcenter()` removed; builds resolve from Maven Central via `settings.gradle`.

## Dependencies

- OkHttp upgraded to `5.3.2`.
- kotlinx.serialization-json upgraded to `1.10.0`.
- Test stack upgraded to JUnit Jupiter `5.14.2` + AssertJ `3.27.7`.
- Removed unused/legacy deps: `jersey-core`, `commons-codec`, `guava`, `jackson-*`, `moshi`, `commons-math3`.

## Networking And Concurrency

- `TutiApiClient.sendRequest(...)` no longer creates a dedicated `Thread` per request.
  - Requests now use OkHttp async `Call.enqueue(...)`.
  - Query params are now URL-encoded via `HttpUrl` (no more manual `?k=v` concatenation).
- `runOnOwnThread` was removed.
- `TutiApiClient.billInquiry(...)` now returns `okhttp3.Call` (instead of `Thread`).
- `TutiApiClient.authToken` is now `@Volatile` to avoid stale reads across threads.
- HTTP logging is disabled by default. BASIC/HEADERS may be enabled explicitly; credential headers
  are redacted, sensitive enrollment requests bypass logging, and BODY logging is rejected.
- The generic authenticated `sendRequest(...)` escape hatch is now private. HTTP and WebSocket
  targets must match a configured server origin, redirects are disabled, and non-loopback traffic
  requires HTTPS/WSS. Exact loopback hosts remain available for local tests.

## Default Base URL

- `TutiApiClient()` now defaults to `https://api.noebs.sd/` for both `serverURL` (consumer API) and `noebsServer` (wallet/ws/root routes).

## Durable Card-Funded Operations

- A financial attempt now starts with a caller-created canonical UUID and an `OperationClaim`.
  Persist the resulting `OperationIdentity` before the first HTTP attempt and reuse both its
  `uuid` and `requestClaim` after timeouts, retries, and process recreation.
- Build endpoint bodies with `MobileTransferOperationRequest`, `TokenPaymentOperationRequest`, or
  `BillPaymentOperationRequest`. Each factory derives the transmitted target and RFC 8785/JCS
  request claim from the same typed values and rejects a changed retry locally.
- `request_claim` is a replay-integrity assertion, not authorization. The server normalizes the
  semantic request, recomputes the claim, and owns replay acceptance.
- Balance inquiry is available through `client.cards.balance`. Create and persist its identity with
  `OperationIdentity.createBalanceInquiry`, then build a transient
  `BalanceInquiryOperationRequest`; its response contains only typed `available` and `ledger`
  values. Exact retries reuse the same UUID and claim.
- Legacy PAN-funded helpers remain source-compatible but throw
  `OpaqueCardOperationRequiredException` before generating a UUID, encrypting an IPIN, or sending
  HTTP. `EBSRequest()` is now non-financial and has no implicit UUID; UUID-bound IPIN construction
  requires an explicit operation UUID and EBS public key.

## Opaque Card Enrollment And CRUD

- Use `client.cards.createEnrollmentIntent`, `confirmEnrollment`, `list`, `rename`, `retire`, and
  `setMain`. Every durable selector is a canonical server-issued `card_id`.
- `CardEnrollmentIntent.confirmation(...)` validates the server RSA key and its SHA-256 key ID,
  creates a UUID-bound encrypted IPIN block, and returns a transient confirmation payload. Never
  persist that payload, the entered PAN, or the clear/encrypted IPIN value.
- `SignupWithCard`, legacy issuance/completion, `getCards`, PAN card CRUD, `getUserCard(mobile)`, and
  PAN-based main-card selection now fail before HTTP. There is no fallback to retired routes.

## Retired Sensitive Compatibility Contracts

- Standalone `generateIpin`/`confirmIpinGeneration` and PAN-selected `Otp2FA` are terminal. IPIN is
  established only inside the transient opaque enrollment flow.
- Generic `NoebsBeneficiary` CRUD, configured-PAN bills, PAN-backed payment tokens and payment
  requests are terminal until typed opaque recipient/card contracts replace them.
- The old `getPublicKey(EBSRequest)` and `getIpinPublicKey(EBSRequest)` helpers are terminal because
  their mutable request type can carry PAN and IPIN fields. Opaque enrollment supplies its bound
  rail key in `CardEnrollmentIntent`.
- These methods remain source-compatible and throw `OpaqueCardOperationRequiredException`
  synchronously, before serialization or HTTP.
- Direct entertainment reads are also terminal until the service is exposed through the configured
  Noebs origin. They throw `ExternalServiceRetiredException` instead of contacting a hardcoded
  third-party origin.

## Wallet Idempotency Keys

- `P2PTransferRequest`, `DepositRequest`, `WithdrawalRequest`, and `ManualTransferRequest` now
  require an explicit canonical lowercase UUID in `idempotencyKey`; the empty defaults were
  removed. Persist and reuse that UUID for an exact retry.

## Chat Contact Resolution

- Use `syncChatContacts(List<ChatContactRequest>, ...)`. The request serializes only `name` and
  `mobile`; the typed `ResolvedChatContact` response adds a positive tenant-scoped `userId`.
- The deprecated `syncContacts(List<Contact>, ...)` remains source-compatible but throws
  `ChatStableIdentityRequiredException` synchronously. It cannot expose the numeric identity
  required by Chat v2 and never sends its mobile-only contract over HTTP.

## Crypto/Base64

- IPIN encryption code no longer depends on jersey Base64 or commons-codec.
  - Base64 handling uses Okio (`okio.ByteString`) instead.
- The deprecated Java `IPIN.getIPINBlock` bridge now delegates to the validated generator. Invalid
  keys and cipher failures throw; clear input is never returned or printed.
