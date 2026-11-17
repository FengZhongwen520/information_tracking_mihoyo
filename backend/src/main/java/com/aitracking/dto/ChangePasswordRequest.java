package com.aitracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 4096, message = "格式不正确")
        String currentPasswordCiphertext,

        @NotBlank(message = "不能为空")
        @Size(max = 4096, message = "格式不正确")
        String newPasswordCiphertext) {
}
