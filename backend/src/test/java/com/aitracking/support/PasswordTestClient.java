package com.aitracking.support;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public final class PasswordTestClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordTestClient() {
    }

    public static String encrypt(String password, String publicKeyBase64) throws Exception {
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
        byte[] aesKey = new byte[32];
        byte[] iv = new byte[12];
        RANDOM.nextBytes(aesKey);
        RANDOM.nextBytes(iv);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(128, iv));
        byte[] ciphertext = aes.doFinal(password.getBytes(StandardCharsets.UTF_8));

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, publicKey, new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] wrapped = rsa.doFinal(aesKey);

        Base64.Encoder encoder = Base64.getEncoder();
        return String.join(".",
                "v1",
                encoder.encodeToString(wrapped),
                encoder.encodeToString(iv),
                encoder.encodeToString(ciphertext));
    }
}
