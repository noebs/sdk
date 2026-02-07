package com.tuti.model;

import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

import okio.ByteString;

public class IPIN {

    public static String getIPINBlock(String encryptedIPIN,
                                      String publicKey, String uuid) {
        // clear ipin = uuid +  IPIN
        String clearIPIN = uuid + encryptedIPIN;

        // prepare public key, get public key from its String representation as
        // base64
        byte[] keyRawBytes = ByteString.decodeBase64(publicKey).toByteArray();
        // generate public key
        X509EncodedKeySpec encodeKeySpecs = new X509EncodedKeySpec(keyRawBytes);
        KeyFactory rsaKeyFactory;
        try {
            rsaKeyFactory = KeyFactory.getInstance("RSA");
        } catch (Exception e) {
            e.printStackTrace();
            return encryptedIPIN;
        }

        Key pubKey;
        try {
            pubKey = rsaKeyFactory.generatePublic(encodeKeySpecs);
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
            return encryptedIPIN;
        }

        try {
            // construct Cipher with encryption algrithm:RSA, cipher mode:ECB and padding:PKCS1Padding
            Cipher rsaCipherInstance = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipherInstance.init(Cipher.ENCRYPT_MODE, pubKey);
            // calculate ipin, encryption then encoding to base64
            encryptedIPIN = ByteString.of(rsaCipherInstance.doFinal(clearIPIN.getBytes())).base64();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encryptedIPIN;
    }
}
