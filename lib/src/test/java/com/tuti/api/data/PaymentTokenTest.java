package com.tuti.api.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

class PaymentTokenTest {

    @Test
    void parseQRToken() {
        // PaymentToken.ParseQRToken expects a base64-encoded JSON payload.
        // Use the current schema where `transaction` is an array (legacy payloads used an object).
        String json = "{"
            + "\"amount\":1000,"
            + "\"uuid\":\"b66fbd25-fb26-4cb1-b917-b62f1d3cabaa\","
            + "\"note\":\"This is working\","
            + "\"toCard\":\"3289329839829832983\","
            + "\"transaction\":[],"
            + "\"is_paid\":false"
            + "}";
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        PaymentToken pt = PaymentToken.ParseQRToken(b64);
        assertNotNull(pt);
        assertEquals(1000, pt.getAmount());
    }
}
