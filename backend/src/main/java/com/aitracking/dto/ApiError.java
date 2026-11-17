package com.aitracking.dto;

public record ApiError(String code, String message, String requestId) {
}
