package com.aitracking.service;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aitracking.api.ApiException;
import com.aitracking.domain.UserAccount;
import com.aitracking.dto.AuthResponse;
import com.aitracking.dto.ChangePasswordRequest;
import com.aitracking.dto.CreateRecoveryCodeRequest;
import com.aitracking.dto.DeleteAccountRequest;
import com.aitracking.dto.LoginRequest;
import com.aitracking.dto.RecoveryCodeView;
import com.aitracking.dto.RegisterRequest;
import com.aitracking.dto.ResetPasswordRequest;
import com.aitracking.dto.UserView;
import com.aitracking.repo.UserActivityStateRepository;
import com.aitracking.repo.UserAccountRepository;
import com.aitracking.security.PasswordCipher;
import com.aitracking.security.PasswordHasher;
import com.aitracking.security.TokenService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final char[] RECOVERY_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RECOVERY_RANDOM = new SecureRandom();

    private final UserAccountRepository userRepository;
    private final UserActivityStateRepository stateRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordCipher passwordCipher;
    private final TokenService tokenService;
    private final GameCatalog gameCatalog;

    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String key = username.toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameKey(key)) {
            throw ApiException.conflict("用户名已被占用");
        }
        String password = decryptPassword(request.passwordCiphertext(), true);
        UserAccount user = new UserAccount();
        user.setId("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        user.setUsername(username);
        user.setUsernameKey(key);
        user.setPasswordHash(passwordHasher.hash(password));
        user.setCreatedAt(Instant.now());
        user.setTokenVersion(0);
        user.setGames(new LinkedHashSet<>(gameCatalog.all().stream().map(GameCatalog.GameDefinition::key).toList()));
        String recoveryCode = generateRecoveryCode();
        user.setRecoveryCodeHash(passwordHasher.hash(normalizeRecoveryCode(recoveryCode)));
        user.setRecoveryCodeCreatedAt(Instant.now());
        userRepository.save(user);
        return new AuthResponse(
                tokenService.issue(user.getId(), user.getTokenVersion()),
                toView(user),
                recoveryCode);
    }

    public AuthResponse login(LoginRequest request) {
        String key = request.username().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userRepository.findByUsernameKey(key)
                .orElseThrow(() -> ApiException.unauthorized("用户名或密码错误"));
        String password = decryptPassword(request.passwordCiphertext(), false);
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return new AuthResponse(tokenService.issue(user.getId(), user.getTokenVersion()), toView(user));
    }

    public AuthResponse changePassword(String userId, ChangePasswordRequest request) {
        UserAccount user = requireUser(userId);
        String current = passwordCipher.decrypt(request.currentPasswordCiphertext());
        if (!passwordHasher.matches(current, user.getPasswordHash())) {
            throw ApiException.badRequest("当前密码不正确");
        }
        String next = decryptPassword(request.newPasswordCiphertext(), true);
        if (next.equals(current)) {
            throw ApiException.badRequest("新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordHasher.hash(next));
        invalidateSessions(user);
        userRepository.save(user);
        return new AuthResponse(
                tokenService.issue(user.getId(), user.getTokenVersion()),
                toView(user));
    }

    public RecoveryCodeView createRecoveryCode(String userId, CreateRecoveryCodeRequest request) {
        UserAccount user = requireUser(userId);
        String password = passwordCipher.decrypt(request.passwordCiphertext());
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("当前密码不正确");
        }
        String code = generateRecoveryCode();
        user.setRecoveryCodeHash(passwordHasher.hash(normalizeRecoveryCode(code)));
        user.setRecoveryCodeCreatedAt(Instant.now());
        userRepository.save(user);
        return new RecoveryCodeView(code);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String key = request.username().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userRepository.findByUsernameKey(key)
                .orElseThrow(() -> ApiException.badRequest("用户名或恢复码不正确"));
        if (user.getRecoveryCodeHash() == null
                || !passwordHasher.matches(normalizeRecoveryCode(request.recoveryCode()),
                        user.getRecoveryCodeHash())) {
            throw ApiException.badRequest("用户名或恢复码不正确");
        }
        String next = decryptPassword(request.newPasswordCiphertext(), true);
        user.setPasswordHash(passwordHasher.hash(next));
        user.setRecoveryCodeHash(null);
        user.setRecoveryCodeCreatedAt(null);
        invalidateSessions(user);
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(String userId, DeleteAccountRequest request) {
        UserAccount user = requireUser(userId);
        String password = passwordCipher.decrypt(request.passwordCiphertext());
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("密码不正确");
        }
        stateRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    private String decryptPassword(String ciphertext, boolean enforcePolicy) {
        String password = passwordCipher.decrypt(ciphertext);
        if (enforcePolicy && (password.length() < 6 || password.length() > 72)) {
            throw ApiException.badRequest("密码长度需为 6-72 位");
        }
        return password;
    }

    private void invalidateSessions(UserAccount user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    private static String generateRecoveryCode() {
        StringBuilder builder = new StringBuilder(16);
        for (int index = 0; index < 16; index += 1) {
            builder.append(RECOVERY_ALPHABET[RECOVERY_RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return builder.substring(0, 4) + "-" + builder.substring(4, 8) + "-"
                + builder.substring(8, 12) + "-" + builder.substring(12, 16);
    }

    private static String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
    }

    public UserView me(String userId) {
        return toView(requireUser(userId));
    }

    public UserAccount requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw ApiException.unauthorized("请先登录");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("登录已失效，请重新登录"));
    }

    public void save(UserAccount user) {
        userRepository.save(user);
    }

    public UserView toView(UserAccount user) {
        List<String> games = gameCatalog.ordered(user.getGames());
        Set<String> known = Set.copyOf(games);
        LinkedHashSet<String> merged = new LinkedHashSet<>(games);
        for (String game : user.getGames()) {
            if (!known.contains(game)) {
                merged.add(game);
            }
        }
        return new UserView(user.getId(), user.getUsername(), List.copyOf(merged), user.getCreatedAt());
    }
}
