package com.aitracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_\\-\\u4e00-\\u9fa5]{3,24}$",
                message = "需为 3-24 位字母、数字、下划线、短横线或中文")
        String username,

        @NotBlank(message = "不能为空")
        @Size(max = 4096, message = "格式不正确")
        String passwordCiphertext) {
}
