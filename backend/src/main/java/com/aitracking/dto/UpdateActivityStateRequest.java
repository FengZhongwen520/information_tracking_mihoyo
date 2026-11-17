package com.aitracking.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateActivityStateRequest(@NotNull(message = "不能为空") Boolean completed) {
}
