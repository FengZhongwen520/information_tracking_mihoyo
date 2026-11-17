package com.aitracking.dto;

import java.time.Instant;
import java.util.List;

public record UserView(String id, String username, List<String> games, Instant createdAt) {
}
