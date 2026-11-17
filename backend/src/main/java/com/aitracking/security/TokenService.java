package com.aitracking.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.aitracking.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TokenService {

    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Path FALLBACK_SECRET_PATH = Path.of("data", "auth-secret.key");

    private final ObjectMapper mapper;
    private final byte[] secret;
    private final long ttlSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public TokenService(AuthProperties properties, ObjectMapper mapper) {
        this(properties, mapper, resolveSecret(properties.getSecret()));
    }

    public TokenService(AuthProperties properties, ObjectMapper mapper, byte[] secret) {
        this.mapper = mapper;
        this.secret = secret;
        this.ttlSeconds = Math.max(1, properties.getTokenTtlHours()) * 3600;
    }

    public String issue(String userId) {
        return issue(userId, 0);
    }

    public String issue(String userId, int tokenVersion) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
            byte[] payload = mapper.writeValueAsBytes(Map.of(
                    "sub", userId,
                    "ver", tokenVersion,
                    "exp", expiresAt));
            String body = ENCODER.encodeToString(payload);
            return body + "." + sign(body);
        } catch (Exception ex) {
            throw new IllegalStateException("生成登录令牌失败", ex);
        }
    }

    public VerifiedToken verify(String token) {
        if (token == null) {
            return null;
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.')) {
            return null;
        }
        String body = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        if (!constantTimeEquals(sign(body), signature)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(DECODER.decode(body), Map.class);
            Object subject = payload.get("sub");
            Object version = payload.get("ver");
            Object expiry = payload.get("exp");
            if (!(subject instanceof String userId) || !(expiry instanceof Number seconds)) {
                return null;
            }
            if (Instant.now().getEpochSecond() >= seconds.longValue()) {
                return null;
            }
            int tokenVersion = version instanceof Number number ? number.intValue() : 0;
            return new VerifiedToken(userId, tokenVersion);
        } catch (Exception ex) {
            return null;
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return ENCODER.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("令牌签名失败", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public record VerifiedToken(String userId, int tokenVersion) {
    }

    private static byte[] resolveSecret(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        try {
            if (Files.exists(FALLBACK_SECRET_PATH)) {
                String stored = Files.readString(FALLBACK_SECRET_PATH, StandardCharsets.UTF_8).trim();
                if (stored.length() >= 32) {
                    return stored.getBytes(StandardCharsets.UTF_8);
                }
            }
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            String generated = ENCODER.encodeToString(random);
            Files.createDirectories(FALLBACK_SECRET_PATH.getParent());
            Files.writeString(FALLBACK_SECRET_PATH, generated, StandardCharsets.UTF_8);
            log.warn("AUTH_SECRET 未配置，已生成持久化密钥 {}", FALLBACK_SECRET_PATH.toAbsolutePath());
            return generated.getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("无法初始化登录密钥，请配置 AUTH_SECRET", ex);
        }
    }
}
