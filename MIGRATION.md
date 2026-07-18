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
- HTTP logging is now disabled by default; enable explicitly via `TutiApiClient.setHttpLoggingLevel(...)`.

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
- Legacy PAN-funded helpers remain source-compatible but throw
  `OpaqueCardOperationRequiredException` before generating a UUID, encrypting an IPIN, or sending
  HTTP. `EBSRequest()` is now non-financial and has no implicit UUID; UUID-bound IPIN construction
  requires an explicit operation UUID and EBS public key.

## Crypto/Base64

- IPIN encryption code no longer depends on jersey Base64 or commons-codec.
  - Base64 handling uses Okio (`okio.ByteString`) instead.
