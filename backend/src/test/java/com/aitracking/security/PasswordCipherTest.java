package com.aitracking.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aitracking.api.ApiException;

class PasswordCipherTest {

    @Test
    void reusesPersistedKeyAcrossRestarts(@TempDir Path tempDir) {
        Path keyFile = tempDir.resolve("auth-rsa-private.key");
        PasswordCipher first = new PasswordCipher(keyFile.toString());
        first.init();
        PasswordCipher second = new PasswordCipher(keyFile.toString());
        second.init();

        assertEquals(first.publicKeyBase64(), second.publicKeyBase64());
    }

    @Test
    void rejectsMalformedEnvelope(@TempDir Path tempDir) {
        PasswordCipher cipher = new PasswordCipher(tempDir.resolve("key").toString());
        cipher.init();

        ApiException ex = assertThrows(ApiException.class, () -> cipher.decrypt("plain-text"));

        assertEquals(400, ex.getStatus().value());
    }
}
