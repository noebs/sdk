package com.tuti.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tuti.api.authentication.SignInRequest
import com.tuti.api.data.BalanceInquiryOperationRequest
import com.tuti.api.data.Card
import com.tuti.api.data.CardEnrollmentIntent
import com.tuti.api.data.CardRef
import com.tuti.api.data.OpaqueCardOperationRequiredException
import com.tuti.api.data.OperationIdentity
import com.tuti.api.data.SetMainCardRequest
import com.tuti.api.data.SignUpCard
import com.tuti.api.wallet.v1.CreateWalletRequest
import com.tuti.api.wallet.v1.DepositRequest
import com.tuti.api.wallet.v1.WalletPaymentMethodQuery
import com.tuti.api.ebs.EBSRequest
import com.tuti.api.ebs.NoebsTransfer
import com.tuti.model.BillInfo
import kotlinx.serialization.decodeFromString
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TutiApiClientHttpContractTest {
    @Test
    fun legacyCardHelpersFailClosedBeforeConfigurationOrNetwork() {
        val client = TutiApiClient(serverURL = "http://127.0.0.1:1/")
        val card = Card(PAN = "6392561234567890", expiryDate = "2512")

        val err = assertThrows(OpaqueCardOperationRequiredException::class.java) {
            client.cardTransfer(
                card = card,
                ipin = "1234",
                receiverCard = card,
                amount = 10f,
                onResponse = {},
                onError = { _, _ -> },
            )
        }

        assertTrue(err.message.orEmpty().contains("opaque card IDs"))
    }

    @Test
    fun everyLegacyFinancialEntryPointFailsBeforeHttp() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl, noebsServer = serverUrl)
            val card = Card(PAN = "6392561234567890", expiryDate = "2512")

            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.balanceInquiry(card, "1234", {}, { _, _ -> })
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.quickPayment(EBSRequest(), onResponse = {}, onError = { _, _ -> })
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.sendEBSRequest(serverUrl, EBSRequest(), {}, { _, _ -> })
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.EntertainmentSendTranser(card, "1234", "product", 10f, {}, { _, _ -> })
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.noebsTransfer(
                    NoebsTransfer(null, null, null, null, operationUuidForTest, null),
                    {},
                    { _, _ -> },
                )
            }

            assertEquals("", capture.path.get())
            assertEquals("", capture.body.get())
        }
    }

    private val operationUuidForTest = "123e4567-e89b-12d3-a456-426614174001"

    @Test
    fun legacySignInOverload_sendsMobileField() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl)
            val latch = CountDownLatch(1)
            val auth = AtomicReference<String?>(null)
            val error = AtomicReference<Exception?>(null)

            client.SignIn(
                SignInRequest(username = "0912345678", password = "Password1!"),
                onResponse = {
                    auth.set(it.authorizationJWT)
                    latch.countDown()
                },
                onError = { _, ex ->
                    error.set(ex)
                    latch.countDown()
                },
            )

            waitFor(latch)
            assertNull(error.get())
            assertEquals("/consumer/login", capture.path.get())
            assertEquals("0912345678", capture.jsonBody("mobile"))
            assertEquals("Password1!", capture.jsonBody("password"))
            assertEquals("token", auth.get())
        }
    }

    @Test
    fun billsHelperRemainsAnInquiryWhileDirectPanInquiryFailsClosed() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl)
            val error = AtomicReference<Throwable?>(null)

            val billsLatch = CountDownLatch(1)
            client.getBills(
                BillInfo(phone = "0912345678", payeeId = "0010010002"),
                onResponse = { billsLatch.countDown() },
                onError = { _, ex ->
                    error.set(AssertionError("getBills failed", ex))
                    billsLatch.countDown()
                },
            )
            waitFor(billsLatch)
            assertNull(error.get())
            assertEquals("/consumer/bills", capture.path.get())

            val request = EBSRequest().apply {
                pan = "6392561234567890"
                expDate = "2512"
                IPIN = "encrypted"
                payeeId = "0010010002"
                paymentInfo = "MPHONE=249912345678"
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.billInquiry(
                    request,
                    onResponse = {},
                    onError = { _, _ -> },
                )
            }
            assertEquals("/consumer/bills", capture.path.get())
        }
    }

    @Test
    fun defaultHeaders_areSentWithHttpRequests() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl)
            client.defaultHeaders = mapOf(
                "X-Tenant-ID" to "tenant_1",
                "X-Admin-Key" to "secret",
            )
            val error = AtomicReference<Throwable?>(null)

            val latch = CountDownLatch(1)
            client.getTransactions(
                onResponse = { latch.countDown() },
                onError = { _, ex ->
                    error.set(AssertionError("getTransactions failed", ex))
                    latch.countDown()
                },
            )

            waitFor(latch)
            assertNull(error.get())
            assertEquals("tenant_1", capture.header("X-Tenant-ID"))
            assertEquals("secret", capture.header("X-Admin-Key"))
        }
    }

    @Test
    fun loadAppConfig_setsTenantHeaderAndWalletDefaults() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl, noebsServer = serverUrl)
            val error = AtomicReference<Throwable?>(null)

            val configLatch = CountDownLatch(1)
            client.loadAppConfig(
                onResponse = {
                    assertEquals("tenant_1", it.tenantId)
                    assertEquals("SDG", it.wallet.defaultCurrency)
                    configLatch.countDown()
                },
                onError = { _, ex ->
                    error.set(AssertionError("loadAppConfig failed", ex))
                    configLatch.countDown()
                },
            )
            waitFor(configLatch)
            assertNull(error.get())
            assertEquals("/app/config", capture.path.get())
            assertEquals("tenant_1", client.defaultHeaders["X-Tenant-ID"])

            val createLatch = CountDownLatch(1)
            client.wallet.createWallet(
                CreateWalletRequest(),
                onResponse = { createLatch.countDown() },
                onError = { _, ex ->
                    error.set(AssertionError("createWallet failed", ex))
                    createLatch.countDown()
                },
            )
            waitFor(createLatch)
            assertNull(error.get())
            assertEquals("/wallet/wallets", capture.path.get())
            assertEquals("tenant_1", capture.header("X-Tenant-ID"))
            assertEquals("tenant_1", capture.jsonBody("tenant_id"))
            assertEquals("SDG", capture.jsonBody("currency"))

            val methodsLatch = CountDownLatch(1)
            client.wallet.listPaymentMethods(
                WalletPaymentMethodQuery(direction = "deposit"),
                onResponse = { methodsLatch.countDown() },
                onError = { _, ex ->
                    error.set(AssertionError("listPaymentMethods failed", ex))
                    methodsLatch.countDown()
                },
            )
            waitFor(methodsLatch)
            assertNull(error.get())
            assertEquals("/wallet/methods", capture.path.get())
            assertTrue(capture.query.get().contains("tenant_id=tenant_1"))
            assertTrue(capture.query.get().contains("currency=SDG"))

            val pathBeforeLegacyCall = capture.path.get()
            val card = Card(PAN = "6392561234567890", expiryDate = "2512")
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.cardTransfer(
                    card = card,
                    ipin = "1234",
                    receiverCard = card,
                    amount = 10f,
                    onResponse = {},
                    onError = { _, _ -> },
                )
            }
            assertEquals(pathBeforeLegacyCall, capture.path.get())
        }
    }

    @Test
    fun walletUserRoutes_hitFrontendWalletEndpoints() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl, noebsServer = serverUrl)
            val error = AtomicReference<Throwable?>(null)

            val createLatch = CountDownLatch(1)
            client.wallet.createWallet(
                CreateWalletRequest(currency = "SDG"),
                onResponse = {
                    assertEquals("wallet_1", it.id)
                    assertEquals(42, it.userId)
                    createLatch.countDown()
                },
                onError = { _, ex ->
                    error.set(AssertionError("createWallet failed", ex))
                    createLatch.countDown()
                },
            )
            waitFor(createLatch)
            assertNull(error.get())
            assertEquals("/wallet/wallets", capture.path.get())
            assertEquals("SDG", capture.jsonBody("currency"))

            val getLatch = CountDownLatch(1)
            client.wallet.getUserWallet(
                walletId = "wallet_1",
                onResponse = {
                    assertEquals("wallet_1", it.id)
                    getLatch.countDown()
                },
                onError = { _, ex ->
                    error.set(AssertionError("getUserWallet failed", ex))
                    getLatch.countDown()
                },
            )
            waitFor(getLatch)
            assertNull(error.get())
            assertEquals("/wallet/wallets/wallet_1", capture.path.get())
        }
    }

    @Test
    fun walletMethodsAndHistory_includeExpectedQueryParams() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl, noebsServer = serverUrl)
            val error = AtomicReference<Throwable?>(null)

            val methodsLatch = CountDownLatch(1)
            client.wallet.listPaymentMethods(
                WalletPaymentMethodQuery(
                    direction = "deposit",
                    currency = "SDG",
                    region = "SD",
                    amount = 100,
                    limit = 25,
                    offset = 5,
                ),
                onResponse = {
                    assertEquals("psp_1", it.methods.first().providerCode)
                    methodsLatch.countDown()
                },
                onError = { _, ex ->
                    error.set(AssertionError("listPaymentMethods failed", ex))
                    methodsLatch.countDown()
                },
            )
            waitFor(methodsLatch)
            assertNull(error.get())
            assertEquals("/wallet/methods", capture.path.get())
            assertTrue(capture.query.get().contains("direction=deposit"))
            assertTrue(capture.query.get().contains("currency=SDG"))
            assertTrue(capture.query.get().contains("amount=100"))

            val historyLatch = CountDownLatch(1)
            client.wallet.listTransactions(
                walletId = "wallet_1",
                entryType = "credit",
                limit = 10,
                offset = 2,
                onResponse = {
                    assertEquals(99, it.transactions.first().id)
                    historyLatch.countDown()
                },
                onError = { _, ex ->
                    error.set(AssertionError("listTransactions failed", ex))
                    historyLatch.countDown()
                },
            )
            waitFor(historyLatch)
            assertNull(error.get())
            assertEquals("/wallet/wallets/wallet_1/transactions", capture.path.get())
            assertTrue(capture.query.get().contains("entry_type=credit"))
            assertTrue(capture.query.get().contains("limit=10"))
            assertTrue(capture.query.get().contains("offset=2"))
        }
    }

    @Test
    fun requestDeposit_doesNotSendProviderTransactionId() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl, noebsServer = serverUrl)
            val error = AtomicReference<Throwable?>(null)
            val latch = CountDownLatch(1)

            client.wallet.requestDeposit(
                DepositRequest(
                    tenantId = "tenant_1",
                    clientReference = "ref_1",
                    providerCode = "psp_1",
                    walletId = "wallet_1",
                    ownerType = "user",
                    ownerId = "42",
                    amount = 100,
                    currency = "SDG",
                    idempotencyKey = operationUuidForTest,
                    region = "SD",
                ),
                onResponse = {
                    assertEquals("workflow_1", it.workflowId)
                    latch.countDown()
                },
                onError = { _, ex ->
                    error.set(AssertionError("requestDeposit failed", ex))
                    latch.countDown()
                },
            )

            waitFor(latch)
            assertNull(error.get())
            assertEquals("/wallet/deposits", capture.path.get())
            assertEquals("ref_1", capture.jsonBody("clientReference"))
            assertEquals("psp_1", capture.jsonBody("providerCode"))
            assertFalse(capture.body.get().contains("pspTransactionId"))
            assertFalse(capture.body.get().contains("psp_transaction_id"))
        }
    }

    @Test
    fun opaqueCardApiCompletesEnrollmentAndCrudByOpaqueId() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl)
            client.authToken = "Bearer alpha-user"
            val error = AtomicReference<Throwable?>(null)
            val intentRef = AtomicReference<CardEnrollmentIntent>()

            val intentLatch = CountDownLatch(1)
            client.cards.createEnrollmentIntent(
                onResponse = {
                    intentRef.set(it)
                    intentLatch.countDown()
                },
                onError = { _, ex ->
                    error.set(ex ?: AssertionError("create enrollment intent failed"))
                    intentLatch.countDown()
                },
            )
            waitFor(intentLatch)
            assertNull(error.get())
            assertEquals("POST", capture.method.get())
            assertEquals("/consumer/cards/enrollment-intents", capture.path.get())
            assertEquals("Bearer alpha-user", capture.header("Authorization"))

            val intent = intentRef.get()
            val confirmation = intent.confirmation(
                pan = "4242424242424242",
                expiryDate = "2912",
                name = "Daily",
                ipin = "1234",
            )
            val confirmLatch = CountDownLatch(1)
            client.cards.confirmEnrollment(
                intent = intent,
                confirmation = confirmation,
                onResponse = {
                    assertEquals("123e4567-e89b-12d3-a456-426614174020", it.cardId)
                    assertEquals("****4242", it.maskedPan)
                    confirmLatch.countDown()
                },
                onError = { _, ex ->
                    error.set(ex ?: AssertionError("confirm enrollment failed"))
                    confirmLatch.countDown()
                },
            )
            waitFor(confirmLatch)
            assertNull(error.get())
            assertEquals("POST", capture.method.get())
            assertEquals(
                "/consumer/cards/enrollment-intents/${intent.enrollmentId}/confirm",
                capture.path.get(),
            )
            assertEquals("4242424242424242", capture.jsonBody("pan"))
            assertFalse(capture.body.get().contains("\"ipin\":"))
            assertTrue(capture.body.get().contains("\"ipin_block\":"))

            val listLatch = CountDownLatch(1)
            client.cards.list(
                onResponse = {
                    assertEquals(2, it.size)
                    assertEquals("****4242", it[0].maskedPan)
                    assertEquals("****4242", it[1].maskedPan)
                    assertFalse(it[0].cardId == it[1].cardId)
                    listLatch.countDown()
                },
                onError = { _, ex ->
                    error.set(ex ?: AssertionError("list cards failed"))
                    listLatch.countDown()
                },
            )
            waitFor(listLatch)
            assertNull(error.get())
            assertEquals("GET", capture.method.get())
            assertEquals("/consumer/cards", capture.path.get())

            val card = CardRef("123e4567-e89b-12d3-a456-426614174021")
            val renameLatch = CountDownLatch(1)
            client.cards.rename(card, "Trips", renameLatch::countDown) { _, ex ->
                error.set(ex ?: AssertionError("rename card failed"))
                renameLatch.countDown()
            }
            waitFor(renameLatch)
            assertNull(error.get())
            assertEquals("PATCH", capture.method.get())
            assertEquals("/consumer/cards/${card.cardId}", capture.path.get())
            assertEquals("Trips", capture.jsonBody("name"))

            val mainLatch = CountDownLatch(1)
            client.cards.setMain(card, mainLatch::countDown) { _, ex ->
                error.set(ex ?: AssertionError("set main card failed"))
                mainLatch.countDown()
            }
            waitFor(mainLatch)
            assertNull(error.get())
            assertEquals("PUT", capture.method.get())
            assertEquals("/consumer/cards/${card.cardId}/main", capture.path.get())

            val retireLatch = CountDownLatch(1)
            client.cards.retire(card, retireLatch::countDown) { _, ex ->
                error.set(ex ?: AssertionError("retire card failed"))
                retireLatch.countDown()
            }
            waitFor(retireLatch)
            assertNull(error.get())
            assertEquals("DELETE", capture.method.get())
            assertEquals("/consumer/cards/${card.cardId}", capture.path.get())

            val balanceCardId = "123e4567-e89b-42d3-a456-426614174000"
            val balanceRequest = BalanceInquiryOperationRequest.create(
                identity = OperationIdentity.createBalanceInquiry(operationUuidForTest, balanceCardId),
                cardId = balanceCardId,
                ipinBlock = "Y2lwaGVydGV4dA==",
            )
            val balanceLatch = CountDownLatch(1)
            client.cards.balance(
                request = balanceRequest,
                onResponse = {
                    assertEquals(operationUuidForTest, it.uuid)
                    assertEquals(1250.75, it.balance.available)
                    assertEquals(1200.25, it.balance.ledger)
                    balanceLatch.countDown()
                },
                onError = { _, ex ->
                    error.set(ex ?: AssertionError("balance inquiry failed"))
                    balanceLatch.countDown()
                },
            )
            waitFor(balanceLatch)
            assertNull(error.get())
            assertEquals("POST", capture.method.get())
            assertEquals("/consumer/balance", capture.path.get())
            assertEquals(
                """{"uuid":"123e4567-e89b-12d3-a456-426614174001","request_claim":"v1:156f27e07145ee6a12bfbd6ce2111f1c8ba5ba8fe0d9f728a1e07de867dcc07a","card_authorization":{"card_id":"123e4567-e89b-42d3-a456-426614174000","rail_uuid":"123e4567-e89b-12d3-a456-426614174001","ipin_block":"Y2lwaGVydGV4dA=="}}""",
                capture.body.get(),
            )
            assertFalse(capture.body.get().contains("\"pan\""))
            assertFalse(capture.body.get().contains("exp_date"))
        }
    }

    @Test
    fun bodyLoggingAndLegacyPanCardRoutesFailBeforeNetwork() {
        assertThrows(IllegalArgumentException::class.java) {
            TutiApiClient.setHttpLoggingLevel(HttpLoggingInterceptor.Level.BODY)
        }
        TutiApiClient.setHttpLoggingLevel(HttpLoggingInterceptor.Level.NONE)

        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl)
            val card = Card(PAN = "4242424242424242", expiryDate = "2912")
            val error: (com.tuti.api.data.TutiResponse?, Exception?) -> Unit = { _, _ -> }

            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.SignupWithCard(
                    SignUpCard("Daily", "2912", "4242424242424242", "0912345678", "Password1!", "pub"),
                    {},
                    error,
                )
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.startCardRegistration(EBSRequest(), {}, error)
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.completeCardRegistration(EBSRequest(), {}, error)
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.getCards({}, error)
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.addCard(card, {}, error)
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.editCard(card, {}, error)
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.deleteCard(card, {}, error)
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.getUserCard("0912345678", {}, error)
            }
            assertThrows(OpaqueCardOperationRequiredException::class.java) {
                client.setMainCard(SetMainCardRequest(PAN = card.PAN), {}, error)
            }
            assertEquals("", capture.path.get())
            assertEquals("", capture.body.get())
        }
    }

    private data class Capture(
        val method: AtomicReference<String> = AtomicReference(""),
        val path: AtomicReference<String> = AtomicReference(""),
        val query: AtomicReference<String> = AtomicReference(""),
        val body: AtomicReference<String> = AtomicReference(""),
        val headers: AtomicReference<Map<String, List<String>>> = AtomicReference(emptyMap()),
    ) {
        fun jsonBody(field: String): String? {
            val json = body.get()
            if (json.isBlank()) {
                return null
            }
            return TutiApiClient.Json.decodeFromString<Map<String, String>>(json)[field]
        }

        fun header(name: String): String? {
            return headers.get().entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
        }
    }

    private fun withServer(block: (String, Capture) -> Unit) {
        val capture = Capture()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            handle(exchange, capture)
        }
        server.start()
        try {
            val serverUrl = "http://127.0.0.1:${server.address.port}/"
            block(serverUrl, capture)
        } finally {
            server.stop(0)
        }
    }

    private fun handle(exchange: HttpExchange, capture: Capture) {
        capture.method.set(exchange.requestMethod)
        capture.path.set(exchange.requestURI.path)
        capture.query.set(exchange.requestURI.rawQuery ?: "")
        capture.body.set(exchange.requestBody.bufferedReader().use { it.readText() })
        capture.headers.set(exchange.requestHeaders.toMap())

        val responseBody = when (exchange.requestURI.path) {
            "/consumer/login" -> """{"authorization":"token","user":{"ID":1,"mobile":"0912345678"}}"""
            "/consumer/bills" -> """{"ebs_response":{},"due_amount":{"amount":"10"}}"""
            "/consumer/bill_inquiry" -> """{"ebs_response":{}}"""
            "/consumer/transactions" -> "[]"
            "/consumer/cards/enrollment-intents" ->
                """{"enrollment_id":"123e4567-e89b-12d3-a456-426614174010","rail_uuid":"123e4567-e89b-12d3-a456-426614174011","expires_at":"2026-07-18T20:10:30.123456Z","rail_key":{"algorithm":"rsa_pkcs1_v1_5","key_id":"$TEST_EBS_KEY_ID","public_key":"$TEST_EBS_PUBLIC_KEY"}}"""
            "/consumer/cards/enrollment-intents/123e4567-e89b-12d3-a456-426614174010/confirm" ->
                """{"card_id":"123e4567-e89b-12d3-a456-426614174020","name":"Daily","masked_pan":"****4242","exp_date":"2912","is_main":true,"status":"active"}"""
            "/consumer/cards" ->
                """{"cards":[{"card_id":"123e4567-e89b-12d3-a456-426614174020","name":"Daily","masked_pan":"****4242","exp_date":"2912","is_main":true,"status":"active"},{"card_id":"123e4567-e89b-12d3-a456-426614174021","name":"Travel","masked_pan":"****4242","exp_date":"3012","is_main":false,"status":"active"}]}"""
            "/consumer/balance" ->
                """{"uuid":"$operationUuidForTest","balance":{"available":1250.75,"ledger":1200.25}}"""
            "/app/config" -> """{"tenant_id":"tenant_1","wallet":{"enabled":true,"default_currency":"SDG","pin_required":true},"oauth":{}}"""
            "/wallet/wallets" -> walletResponse()
            "/wallet/wallets/wallet_1" -> walletResponse()
            "/wallet/deposits" -> """{"workflowId":"workflow_1","runId":"run_1"}"""
            "/wallet/methods" -> """{"methods":[{"provider_code":"psp_1","provider_name":"PSP One","method_type":"redirect","direction":"deposit","currencies":["SDG"],"regions":["SD"],"min_amount":100,"max_amount":1000,"supports_deposit":true,"supports_withdrawal":false}]}"""
            "/wallet/wallets/wallet_1/transactions" -> """{"transactions":[{"id":99,"tenant_id":"tenant_1","transaction_id":88,"wallet_id":"wallet_1","entry_type":"credit","amount":100,"currency":"SDG","balance_after":100,"wallet_sequence":1,"status":"completed","reference_type":"deposit","reference_id":"ref_1","description":"deposit","created_at":"2026-05-25T08:00:00Z"}]}"""
            else -> """{"result":"ok"}"""
        }

        exchange.responseHeaders.add("Content-Type", "application/json")
        val bytes = responseBody.toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun waitFor(latch: CountDownLatch) {
        val completed = latch.await(5, TimeUnit.SECONDS)
        if (!completed) {
            throw AssertionError("request did not complete in time")
        }
    }

    private fun walletResponse(): String {
        return """{"id":"wallet_1","tenant_id":"tenant_1","owner_type":"user","owner_id":"42","user_id":42,"currency":"SDG","balance":100,"available_balance":90,"status":"active","kyc_tier":"unverified","created_at":"2026-05-25T08:00:00Z","updated_at":"2026-05-25T08:00:00Z"}"""
    }
}
