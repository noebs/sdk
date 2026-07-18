package com.tuti.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.IllegalBlockSizeException

class IPINTest {
    @Test
    fun malformedKeyFailsWithoutReturningOrPrintingTheClearIpin() {
        val clearIpin = "8472"

        val stderr = captureStderr {
            assertThrows(IllegalArgumentException::class.java) {
                IPIN.getIPINBlock(clearIpin, "not-base64", operationUuid)
            }
        }

        assertEquals("", stderr)
    }

    @Test
    fun cipherFailureThrowsWithoutReturningOrPrintingTheClearInput() {
        val oversizedClearInput = "8".repeat(300)

        val stderr = captureStderr {
            assertThrows(IllegalBlockSizeException::class.java) {
                IPIN.getIPINBlock(oversizedClearInput, publicKey, operationUuid)
            }
        }

        assertEquals("", stderr)
    }

    @Test
    fun validInputReturnsOnlyCiphertext() {
        val clearIpin = "8472"

        val block = IPIN.getIPINBlock(clearIpin, publicKey, operationUuid)

        assertNotEquals(clearIpin, block)
        assertEquals(256, Base64.getDecoder().decode(block).size)
    }

    private fun captureStderr(block: () -> Unit): String {
        val original = System.err
        val capture = ByteArrayOutputStream()
        try {
            System.setErr(PrintStream(capture, true, Charsets.UTF_8.name()))
            block()
        } finally {
            System.setErr(original)
        }
        return capture.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val operationUuid = "123e4567-e89b-12d3-a456-426614174001"

        val publicKey: String by lazy {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            Base64.getEncoder().encodeToString(generator.generateKeyPair().public.encoded)
        }
    }
}
