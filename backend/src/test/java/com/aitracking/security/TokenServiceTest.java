package com.aitracking.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.aitracking.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

class TokenServiceTest {

    private TokenService service(String secret) {
        AuthProperties properties = new AuthProperties();
        properties.setTokenTtlHours(2);
        return new TokenService(properties, new ObjectMapper(), secret.getBytes(UTF_8));
    }

    @Test
    void issuesAndVerifiesToken() {
        TokenService tokens = service("unit-test-secret-0123456789abcdef");
        String token = tokens.issue("usr_123");
        assertEquals("usr_123", tokens.verify(token).userId());
        assertEquals(0, tokens.verify(token).tokenVersion());
    }

    @Test
    void rejectsTamperedPayload() {
        TokenService tokens = service("unit-test-secret-0123456789abcdef");
        String token = tokens.issue("usr_123");
        String tampered = token.substring(0, token.indexOf('.')) + "x." + token.substring(token.indexOf('.') + 1);
        assertNull(tokens.verify(tampered));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String token = service("secret-one-0123456789abcdefgh").issue("usr_123");
        assertNull(service("secret-two-0123456789abcdefgh").verify(token));
    }

    @Test
    void rejectsGarbage() {
        TokenService tokens = service("unit-test-secret-0123456789abcdef");
        assertNull(tokens.verify(null));
        assertNull(tokens.verify(""));
        assertNull(tokens.verify("no-dot"));
        assertNull(tokens.verify("a.b.c"));
        assertNotEquals(tokens.issue("a"), tokens.issue("b"));
    }
}
