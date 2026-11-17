package com.aitracking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aitracking.api.ApiException;
import com.aitracking.config.AuthProperties;
import com.aitracking.domain.UserAccount;
import com.aitracking.dto.AuthResponse;
import com.aitracking.dto.ChangePasswordRequest;
import com.aitracking.dto.LoginRequest;
import com.aitracking.dto.RegisterRequest;
import com.aitracking.repo.UserAccountRepository;
import com.aitracking.repo.UserActivityStateRepository;
import com.aitracking.security.PasswordHasher;
import com.aitracking.security.PasswordCipher;
import com.aitracking.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private UserActivityStateRepository stateRepository;

    private AuthService authService;
    private TokenService tokenService;
    private PasswordCipher passwordCipher;
    private final PasswordHasher hasher = new PasswordHasher();

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        AuthProperties properties = new AuthProperties();
        properties.setTokenTtlHours(1);
        tokenService = new TokenService(properties, new ObjectMapper(),
                "auth-test-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        passwordCipher = new PasswordCipher(tempDir.resolve("auth-rsa-private.key").toString());
        passwordCipher.init();
        authService = new AuthService(userRepository, stateRepository, hasher, passwordCipher, tokenService,
                new GameCatalog());
    }

    @Test
    void registersNewUserWithAllGames() throws Exception {
        when(userRepository.existsByUsernameKey("lucky")).thenReturn(false);

        AuthResponse response = authService.register(
                new RegisterRequest("Lucky", encrypt("pass123456")));

        assertNotNull(response.token());
        assertEquals("Lucky", response.user().username());
        assertEquals(3, response.user().games().size());
        assertTrue(tokenService.verify(response.token()).userId().startsWith("usr_"));
    }

    @Test
    void rejectsDuplicateUsernameCaseInsensitively() throws Exception {
        when(userRepository.existsByUsernameKey("lucky")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.register(new RegisterRequest("Lucky", encrypt("pass123456"))));

        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void logsInWithCorrectPassword() throws Exception {
        UserAccount user = new UserAccount();
        user.setId("usr_1");
        user.setUsername("lucky");
        user.setUsernameKey("lucky");
        user.setPasswordHash(hasher.hash("pass123456"));
        when(userRepository.findByUsernameKey("lucky")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(
                new LoginRequest("LUCKY", encrypt("pass123456")));

        assertNotNull(response.token());
        assertTrue(response.user().games().isEmpty());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        UserAccount user = new UserAccount();
        user.setId("usr_1");
        user.setPasswordHash(hasher.hash("pass123456"));
        when(userRepository.findByUsernameKey("lucky")).thenReturn(Optional.of(user));

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("lucky", encrypt("wrong-password"))));

        assertEquals(401, ex.getStatus().value());
        assertFalse(ex.getMessage().contains("pass"));
    }

    @Test
    void rejectsTooShortRegistrationPasswordAfterDecryption() throws Exception {
        when(userRepository.existsByUsernameKey("lucky")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.register(new RegisterRequest("Lucky", encrypt("12345"))));

        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void changesPasswordAfterVerifyingCurrentPassword() throws Exception {
        UserAccount user = new UserAccount();
        user.setId("usr_1");
        user.setUsername("lucky");
        user.setPasswordHash(hasher.hash("pass123456"));
        when(userRepository.findById("usr_1")).thenReturn(Optional.of(user));

        authService.changePassword("usr_1",
                new ChangePasswordRequest(encrypt("pass123456"), encrypt("Unique-Pass-2026!")));

        assertTrue(hasher.matches("Unique-Pass-2026!", user.getPasswordHash()));
        assertFalse(hasher.matches("pass123456", user.getPasswordHash()));
    }

    @Test
    void rejectsPasswordChangeWithWrongCurrentPassword() throws Exception {
        UserAccount user = new UserAccount();
        user.setId("usr_1");
        user.setPasswordHash(hasher.hash("pass123456"));
        when(userRepository.findById("usr_1")).thenReturn(Optional.of(user));

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.changePassword("usr_1",
                        new ChangePasswordRequest(encrypt("wrong-password"), encrypt("Unique-Pass-2026!"))));

        assertEquals(400, ex.getStatus().value());
        assertTrue(hasher.matches("pass123456", user.getPasswordHash()));
    }

    @Test
    void rejectsPasswordChangeToSamePassword() throws Exception {
        UserAccount user = new UserAccount();
        user.setId("usr_1");
        user.setPasswordHash(hasher.hash("pass123456"));
        when(userRepository.findById("usr_1")).thenReturn(Optional.of(user));

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.changePassword("usr_1",
                        new ChangePasswordRequest(encrypt("pass123456"), encrypt("pass123456"))));

        assertEquals(400, ex.getStatus().value());
    }

    private String encrypt(String password) throws Exception {
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(passwordCipher.publicKeyBase64())));
        SecureRandom random = new SecureRandom();
        byte[] aesKey = new byte[32];
        byte[] iv = new byte[12];
        random.nextBytes(aesKey);
        random.nextBytes(iv);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(128, iv));
        byte[] data = aes.doFinal(password.getBytes(StandardCharsets.UTF_8));

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, publicKey, new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] wrapped = rsa.doFinal(aesKey);

        Base64.Encoder encoder = Base64.getEncoder();
        return String.join(".",
                "v1",
                encoder.encodeToString(wrapped),
                encoder.encodeToString(iv),
                encoder.encodeToString(data));
    }
}
