package com.aitracking.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record UpdateGamesRequest(@NotNull(message = "不能为空") List<String> games) {
}
