package com.aitracking.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.aitracking.api.ApiException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PasswordCipher {

    private static final String RSA_TRANSFORMATION =
            "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String PUBLIC_KEY_ALGORITHM = "RSA-OAEP-256";
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private static final OAEPParameterSpec OAEP_SHA_256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private final String keyPath;
    private KeyPair keyPair;

    public PasswordCipher(@Value("${app.auth.rsa-key-path:./data/auth-rsa-private.key}") String keyPath) {
        this.keyPath = keyPath;
    }

    @PostConstruct
    public void init() {
        try {
            Path path = Path.of(keyPath).toAbsolutePath().normalize();
            PrivateKey privateKey = Files.exists(path)
                    ? loadPrivateKey(path)
                    : createPrivateKey(path);
            PublicKey publicKey = derivePublicKey(privateKey);
            this.keyPair = new KeyPair(publicKey, privateKey);
            log.info("密码加密密钥已就绪：{}", path);
        } catch (Exception ex) {
            throw new IllegalStateException("初始化密码加密密钥失败", ex);
        }
    }

    public String algorithm() {
        return PUBLIC_KEY_ALGORITHM;
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    public String decrypt(String envelope) {
        if (envelope == null || envelope.isBlank()) {
            throw ApiException.badRequest("密码不能为空");
        }
        try {
            String[] parts = envelope.trim().split("\\.", -1);
            if (parts.length != 4 || !"v1".equals(parts[0])) {
                throw new IllegalArgumentException("unknown envelope");
            }
            byte[] wrappedKey = Base64.getDecoder().decode(parts[1]);
            byte[] iv = Base64.getDecoder().decode(parts[2]);
            byte[] encrypted = Base64.getDecoder().decode(parts[3]);
            if (iv.length != GCM_IV_BYTES) {
                throw new IllegalArgumentException("invalid iv");
            }

            Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
            rsa.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), OAEP_SHA_256);
            byte[] aesKey = rsa.doFinal(wrappedKey);
            if (aesKey.length * 8 != AES_KEY_BITS) {
                throw new IllegalArgumentException("invalid key length");
            }

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(aes.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw ApiException.badRequest("密码加密信息无效，请刷新页面后重试");
        }
    }

    private PrivateKey loadPrivateKey(Path path) throws Exception {
        String encoded = Files.readString(path, StandardCharsets.US_ASCII).trim();
        byte[] der = Base64.getDecoder().decode(encoded);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private PrivateKey createPrivateKey(Path path) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair generated = generator.generateKeyPair();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, Base64.getEncoder().encodeToString(
                        generated.getPrivate().getEncoded()),
                StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        return generated.getPrivate();
    }

    private static PublicKey derivePublicKey(PrivateKey privateKey) throws Exception {
        if (privateKey instanceof RSAPrivateCrtKey rsa) {
            RSAPublicKeySpec spec = new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent());
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        }
        throw new IllegalStateException("私钥不包含可推导的公钥信息");
    }
}
