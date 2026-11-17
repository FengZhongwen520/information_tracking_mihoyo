package com.aitracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 24, message = "格式不正确")
        String username,

        @NotBlank(message = "不能为空")
        @Size(max = 40, message = "格式不正确")
        String recoveryCode,

        @NotBlank(message = "不能为空")
        @Size(max = 4096, message = "格式不正确")
        String newPasswordCiphertext) {
}
