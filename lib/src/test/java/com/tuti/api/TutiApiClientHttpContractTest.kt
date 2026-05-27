package com.tuti.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tuti.api.authentication.SignInRequest
import com.tuti.api.wallet.v1.CreateWalletRequest
import com.tuti.api.wallet.v1.DepositRequest
import com.tuti.api.wallet.v1.WalletPaymentMethodQuery
import com.tuti.api.ebs.EBSRequest
import com.tuti.model.BillInfo
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TutiApiClientHttpContractTest {
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
    fun billsHelper_and_directBillInquiry_hitDistinctRoutes() {
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

            val directLatch = CountDownLatch(1)
            val request = EBSRequest().apply {
                pan = "6392561234567890"
                expDate = "2512"
                IPIN = "encrypted"
                payeeId = "0010010002"
                paymentInfo = "MPHONE=249912345678"
            }
            client.billInquiry(
                request,
                onResponse = { directLatch.countDown() },
                onError = { _, ex ->
                    error.set(AssertionError("billInquiry failed", ex))
                    directLatch.countDown()
                },
            )
            waitFor(directLatch)
            assertNull(error.get())
            assertEquals("/consumer/bill_inquiry", capture.path.get())
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

    private data class Capture(
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

    @Test
    fun cardRegistrationStartAndCompletion_hitEbsAdapterRoutes() {
        withServer { serverUrl, capture ->
            val client = TutiApiClient(serverURL = serverUrl)
            val error = AtomicReference<Throwable?>(null)

            val startLatch = CountDownLatch(1)
            client.startCardRegistration(
                EBSRequest().apply {
                    panCategory = "01"
                    phoneNo = "249912345678"
                    registrationType = "01"
                },
                onResponse = { startLatch.countDown() },
                onError = { _, ex ->
                    error.set(AssertionError("startCardRegistration failed", ex))
                    startLatch.countDown()
                },
            )
            waitFor(startLatch)
            assertNull(error.get())
            assertEquals("/consumer/cards/new", capture.path.get())
            assertEquals("249912345678", capture.jsonBody("phoneNo"))

            val completeLatch = CountDownLatch(1)
            client.completeCardRegistration(
                EBSRequest().apply {
                    otp = "123456"
                    IPIN = "encrypted-ipin"
                    originalTranUUID = "original-uuid"
                    userPassword = "ebs-password"
                    password = "local-password"
                    mobile = "0912345678"
                },
                onResponse = { completeLatch.countDown() },
                onError = { _, ex ->
                    error.set(AssertionError("completeCardRegistration failed", ex))
                    completeLatch.countDown()
                },
            )
            waitFor(completeLatch)
            assertNull(error.get())
            assertEquals("/consumer/cards/complete", capture.path.get())
            assertEquals("original-uuid", capture.jsonBody("originalTranUUID"))
            assertEquals("ebs-password", capture.jsonBody("userPassword"))
            assertEquals("local-password", capture.jsonBody("password"))
            assertEquals("0912345678", capture.jsonBody("mobile"))
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
        capture.path.set(exchange.requestURI.path)
        capture.query.set(exchange.requestURI.rawQuery ?: "")
        capture.body.set(exchange.requestBody.bufferedReader().use { it.readText() })
        capture.headers.set(exchange.requestHeaders.toMap())

        val responseBody = when (exchange.requestURI.path) {
            "/consumer/login" -> """{"authorization":"token","user":{"ID":1,"mobile":"0912345678"}}"""
            "/consumer/bills" -> """{"ebs_response":{},"due_amount":{"amount":"10"}}"""
            "/consumer/bill_inquiry" -> """{"ebs_response":{}}"""
            "/consumer/transactions" -> "[]"
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
