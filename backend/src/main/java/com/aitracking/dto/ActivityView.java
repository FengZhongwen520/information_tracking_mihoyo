package com.aitracking.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public record ActivityView(
        String id,
        String game,
        String gameLabel,
        String name,
        String kind,
        String description,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String cover,
        List<ActivityRewardView> rewards,
        String version,
        String versionName,
        String challengeStage,
        String status,
        boolean completed,
        Instant completedAt) {
}
