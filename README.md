# Noebs Java Client        

[![](https://jitpack.io/v/sd.noebs/java-sdk.svg)](https://jitpack.io/#sd.noebs/java-sdk)

### Add it in your root build.gradle

```sh
  repositories {
			...
			maven { url 'https://jitpack.io' }
		}
 ```


  ### Add the dependency
  
```sh
dependencies {
	         implementation 'com.github.tutipay:java-sdk:-SNAPSHOT' # add this stage always use the bleeding edge version
	}
```

## Build (SDK Development)

- Requires Java 17+ to run `./gradlew` (Gradle 9 wrapper).
- Dependency versions are managed via Gradle Version Catalog: `gradle/libs.versions.toml`.
- See `MIGRATION.md` for breaking-change notes.

 ## How does the api work

 We are trying to simplify the API as much as we can, while the API is currently rapidly changing we are using it in production capacities.

### How to use noebs sdk

1. Create an instance of the client

`val tutiApiClient = TutiApiClient()` (defaults to `https://api.noebs.sd/`)

2. Load deployment app config during application startup. This pulls the configured tenant and public defaults from `/app/config` and applies `X-Tenant-ID` to SDK requests:

```kotlin
tutiApiClient.start(
    onReady = { config ->
        println("tenant=${config.tenantId} currency=${config.wallet.defaultCurrency}")
    },
    onError = { error, ex ->
        println(error?.message ?: ex?.message)
    },
)
```

3. Depending on which services you are using, some services need to be authenticated first, we do that this way:
`tutiApiClient.authToken = token`

4. Use the typed APIs exposed by `client.cards` and `client.wallet`. Opaque card enrollment returns
its own short-lived, operation-bound rail key; applications must not fetch a generic key and build
an `EBSRequest` containing PAN or IPIN fields.

### HTTP Logging

HTTP logging is disabled by default. BASIC or HEADERS logging can be enabled for transport debugging; secret-bearing headers are redacted and BODY logging is rejected:

```kotlin
import okhttp3.logging.HttpLoggingInterceptor

TutiApiClient.setHttpLoggingLevel(HttpLoggingInterceptor.Level.BASIC)
```

SDK transport is limited to the configured `serverURL` and `noebsServer` origins. Remote bases
must use HTTPS, WebSockets must use WSS, and redirects are not followed. Plain HTTP/WS is accepted
only for exact loopback hosts (`localhost`, `127.0.0.1`, or `::1`) so local capture tests remain
possible without creating a production cleartext escape hatch.

### Opaque card enrollment and management

Authenticated card operations live under `client.cards`. Cards are selected only by canonical
`card_id`; masks and names are display data and may collide.

```kotlin
client.cards.createEnrollmentIntent(
    onResponse = { intent ->
        val confirmation = intent.confirmation(
            pan = panEnteredOnScreen,
            expiryDate = expiryEnteredOnScreen,
            name = displayName,
            ipin = ipinEnteredOnScreen,
        )
        client.cards.confirmEnrollment(intent, confirmation, onCardEnrolled, onError)
    },
    onError = onError,
)

client.cards.list(
    onResponse = { cards -> cards.forEach { println("${it.cardId}: ${it.maskedPan}") } },
    onError = onError,
)
```

The confirmation object contains one transient PAN and encrypted IPIN rail block. Do not persist,
log, place in navigation state, or reuse it as card identity. Rename, retire, and main-card methods
accept `CardRef(cardId)` and never accept a PAN selector. Legacy PAN card helpers throw
`OpaqueCardOperationRequiredException` before network I/O.

Standalone IPIN generation, generic beneficiaries, configured-PAN bills, PAN-backed payment
tokens/requests, and generic `EBSRequest` key helpers are also terminal. Their source-compatible
methods throw `OpaqueCardOperationRequiredException` synchronously and make no HTTP request.

The first funded opaque-card operation is balance inquiry. Persist its `OperationIdentity` before
the first request and reuse it after a timeout; the SDK binds the encrypted IPIN block to that same
UUID and rejects a retry with another `card_id` locally.

```kotlin
val identity = OperationIdentity.createBalanceInquiry(operationUuid, card.cardId)
val request = BalanceInquiryOperationRequest.create(
    identity = identity,
    cardId = card.cardId,
    ipin = ipinEnteredOnScreen,
    publicKey = verifiedEbsPublicKey,
)
client.cards.balance(request, onBalance, onError)
```

Do not persist the clear IPIN, encrypted block, or complete request. The response exposes only the
typed `available` and `ledger` amounts; it never exposes arbitrary rail fields.

### Chat contact resolution

Resolve phone-book entries to tenant-scoped numeric Chat identities with separate request and
response types. A resolved `user_id` can never be serialized by the strict request type.

```kotlin
client.syncChatContacts(
    contacts = listOf(ChatContactRequest(name = "Alpha Tester", mobile = "0912345678")),
    onResponse = { contacts -> contacts.forEach { println(it.userId) } },
    onError = onError,
)
```

The deprecated `syncContacts(List<Contact>, ...)` method is terminal and throws
`ChatStableIdentityRequiredException` before HTTP because its response cannot provide a stable
tenant-scoped identity.

# Wallet API (v1)

`noebs` exposes wallet APIs under `client.wallet`. Frontend-facing user routes use the authenticated `/wallet/wallets`, `/wallet/methods`, and `/wallet/wallets/{id}/transactions` endpoints. Workflow routes such as deposits, withdrawals, P2P, funding sources, destinations, PIN, and 2FA use the gRPC-gateway `/wallet/*` endpoints.

Wallet endpoints return gRPC status errors (google.rpc.Status). In the SDK these are represented as `com.tuti.api.wallet.v1.RpcStatus` in the `onError` callback.

```kotlin
import com.tuti.api.TutiApiClient
import com.tuti.api.wallet.v1.EnsureWalletRequest

val client = TutiApiClient(noebsServer = "http://localhost:8000/")
client.authToken = "Bearer <jwt>" // required for /wallet routes on current noebs
client.defaultHeaders = mapOf(
    // Optional: needed when your deployment relies on extra headers such as tenant or admin key.
    "X-Tenant-ID" to "tenant_1",
    // "X-Admin-Key" to "<admin-key>",
)

client.wallet.ensureWallet(
    EnsureWalletRequest(
        tenantId = "tenant_1",
        userId = 123,
        currency = "SDG",
    ),
    onResponse = { wallet ->
        println("walletId=${wallet.id} balance=${wallet.balance}")
    },
    onError = { status, ex ->
        println("wallet error: code=${status?.code} message=${status?.message} ex=${ex?.message}")
    },
)
```

Frontend wallet routes use the JWT identity and let the API boundary apply configured defaults:

```kotlin
import com.tuti.api.wallet.v1.CreateWalletRequest
import com.tuti.api.wallet.v1.WalletPaymentMethodQuery

client.wallet.createWallet(
    CreateWalletRequest(currency = "SDG"),
    onResponse = { wallet -> println(wallet.id) },
    onError = { error, ex -> println(error?.message ?: ex?.message) },
)

client.wallet.listPaymentMethods(
    WalletPaymentMethodQuery(direction = "deposit", currency = "SDG", region = "SD"),
    onResponse = { methods -> println(methods.methods) },
    onError = { error, ex -> println(error?.message ?: ex?.message) },
)

client.wallet.listTransactions(
    walletId = "wallet-id",
    onResponse = { history -> println(history.transactions) },
    onError = { error, ex -> println(error?.message ?: ex?.message) },
)
```

Deposits are started with the frontend/business reference. The provider transaction id is not part of the FE request; it is captured later from PSP webhooks or status responses.

```kotlin
import com.tuti.api.wallet.v1.DepositRequest

client.wallet.requestDeposit(
    DepositRequest(
        tenantId = "tenant_1",
        clientReference = "deposit_123",
        providerCode = "psp_1",
        walletId = "wallet-id",
        ownerType = "user",
        ownerId = "123",
        amount = 5000,
        currency = "SDG",
        idempotencyKey = persistedOperationUuid,
        region = "SD",
    ),
    onResponse = { run -> println(run.workflowId) },
    onError = { status, ex -> println(status?.message ?: ex?.message) },
)
```

`idempotencyKey` is a required canonical lowercase UUID for every wallet P2P, deposit,
withdrawal, and manual-transfer request. Create it once, persist it before the first attempt, and
reuse it unchanged after timeouts or process recreation.

# Chat WebSocket (Authorization required)

The `/ws` endpoint now requires a valid JWT in the `Authorization` header (token or `Bearer <token>`). Use the JWT you receive after login or OTP verification.

```kotlin
val client = TutiApiClient()
client.authToken = token // or "Bearer $token"

val ws = client.openChatSocket(object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
        // handle message
    }
})
```

# Deployment notes

Public tagged releases are exposed through JitPack, and the same tagged version is published to GitHub Packages by GitHub Actions.

## noebs sdk versioning

We are using CalVer (`YY.MM.patch`). JitPack relies on git tags, so releases should be tagged as `vYY.MM.patch`.

## how to deploy noebs to Github Packages

- Update `gradle.properties` to the next CalVer release.
- Run `./gradlew test`.
- Commit the release, tag it as `v<version>`, and push `master` plus the tag.
- GitHub Actions publishes `noebs:lib:<version>` to GitHub Packages when the tag is pushed.
- For local manual publishing, use your GitHub username plus a personal access token with package write access.

```groovy
repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/noebs/sdk")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
            }
        }
    }
```

- That is it actually! 

### how to use noebs published Github Package

- Update your `build.gradle` files to include github package

```groovy
        maven {
            url = uri("https://maven.pkg.github.com/noebs/sdk")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
            }
        }
```
- add to your app's gradle file the implementation, which is `implementation 'noebs:lib:<version>'`
