package com.aitracking.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BannerView(
        String id,
        String game,
        String gameLabel,
        String name,
        String version,
        String bannerType,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        List<BannerItemView> items,
        String status) {
}
