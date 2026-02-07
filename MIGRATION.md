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

## Crypto/Base64

- IPIN encryption code no longer depends on jersey Base64 or commons-codec.
  - Base64 handling uses Okio (`okio.ByteString`) instead.
