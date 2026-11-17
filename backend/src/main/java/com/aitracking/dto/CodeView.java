package com.aitracking.dto;

import java.util.List;

public record CodeView(
        String id,
        String game,
        String gameLabel,
        String code,
        List<String> rewards) {
}
