package com.aitracking.dto;

import java.time.Instant;

public record SyncStatusView(
        String status,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        String lastError,
        int activityCount,
        String version) {
}
