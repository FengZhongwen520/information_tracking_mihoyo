package com.aitracking.dto;

import java.util.List;

public record SyncResultView(boolean started, List<GameView> games, String message) {
    public SyncResultView(boolean started, List<GameView> games) {
        this(started, games, null);
    }
}
