package com.aitracking.dto;

public record AuthResponse(String token, UserView user, String recoveryCode) {
    public AuthResponse(String token, UserView user) {
        this(token, user, null);
    }
}
