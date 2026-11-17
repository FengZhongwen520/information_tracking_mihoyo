package com.aitracking.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void matchesOnlyTheOriginalPassword() {
        String encoded = hasher.hash("s3cret-pass");
        assertTrue(hasher.matches("s3cret-pass", encoded));
        assertFalse(hasher.matches("wrong-pass", encoded));
        assertFalse(hasher.matches("", encoded));
    }

    @Test
    void usesRandomSaltPerHash() {
        String first = hasher.hash("same-password");
        String second = hasher.hash("same-password");
        assertNotEquals(first, second);
        assertTrue(hasher.matches("same-password", first));
        assertTrue(hasher.matches("same-password", second));
    }

    @Test
    void rejectsMalformedEncoding() {
        assertFalse(hasher.matches("anything", null));
        assertFalse(hasher.matches("anything", "plain-text"));
        assertFalse(hasher.matches("anything", "pbkdf2$x$y$z"));
    }
}
