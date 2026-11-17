package com.aitracking.dto;

public record GameView(
        String key,
        String label,
        String shortLabel,
        boolean selected,
        long activityCount,
        SyncStatusView sync) {
}
