package com.tuti.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tuti.api.authentication.SignInRequest
import com.tuti.api.ebs.EBSRequest
import com.tuti.model.BillInfo
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private data class Capture(
        val path: AtomicReference<String> = AtomicReference(""),
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
        capture.path.set(exchange.requestURI.path)
        capture.body.set(exchange.requestBody.bufferedReader().use { it.readText() })
        capture.headers.set(exchange.requestHeaders.toMap())

        val responseBody = when (exchange.requestURI.path) {
            "/consumer/login" -> """{"authorization":"token","user":{"ID":1,"mobile":"0912345678"}}"""
            "/consumer/bills" -> """{"ebs_response":{},"due_amount":{"amount":"10"}}"""
            "/consumer/bill_inquiry" -> """{"ebs_response":{}}"""
            "/consumer/transactions" -> "[]"
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
}
