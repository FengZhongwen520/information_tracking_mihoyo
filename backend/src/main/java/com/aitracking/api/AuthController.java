package com.aitracking.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.aitracking.dto.AuthResponse;
import com.aitracking.dto.ChangePasswordRequest;
import com.aitracking.dto.CreateRecoveryCodeRequest;
import com.aitracking.dto.DeleteAccountRequest;
import com.aitracking.dto.LoginRequest;
import com.aitracking.dto.PublicKeyView;
import com.aitracking.dto.RecoveryCodeView;
import com.aitracking.dto.RegisterRequest;
import com.aitracking.dto.ResetPasswordRequest;
import com.aitracking.dto.UserView;
import com.aitracking.security.AuthInterceptor;
import com.aitracking.security.PasswordCipher;
import com.aitracking.security.RequireAuth;
import com.aitracking.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordCipher passwordCipher;

    @GetMapping("/public-key")
    public PublicKeyView publicKey() {
        return new PublicKeyView(passwordCipher.algorithm(), passwordCipher.publicKeyBase64());
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    @RequireAuth
    @PutMapping("/password")
    public AuthResponse changePassword(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(userId, request);
    }

    @RequireAuth
    @PostMapping("/recovery-code")
    public RecoveryCodeView createRecoveryCode(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId,
            @Valid @RequestBody CreateRecoveryCodeRequest request) {
        return authService.createRecoveryCode(userId, request);
    }

    @RequireAuth
    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId,
            @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(userId, request);
    }

    @RequireAuth
    @GetMapping("/me")
    public UserView me(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId) {
        return authService.me(userId);
    }
}
