package com.aitracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "不能为空") String username,
        @NotBlank(message = "不能为空")
        @Size(max = 4096, message = "格式不正确")
        String passwordCiphertext) {
}
