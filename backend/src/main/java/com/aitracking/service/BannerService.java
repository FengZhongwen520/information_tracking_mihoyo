package com.aitracking.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.aitracking.domain.GameBanner;
import com.aitracking.domain.UserAccount;
import com.aitracking.dto.BannerItemView;
import com.aitracking.dto.BannerView;
import com.aitracking.repo.GameBannerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BannerService {

    private static final TypeReference<List<BannerItemView>> ITEM_LIST = new TypeReference<>() {
    };

    private final GameBannerRepository bannerRepository;
    private final GameCatalog gameCatalog;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public List<BannerView> list(String userId, String game) {
        List<String> selected;
        if (userId == null || userId.isBlank()) {
            selected = gameCatalog.allKeys();
        } else {
            UserAccount user = authService.requireUser(userId);
            selected = gameCatalog.ordered(user.getGames());
        }
        if (selected.isEmpty()) {
            return List.of();
        }
        if (game != null && !game.isBlank()) {
            String key = game.trim().toLowerCase();
            if (!selected.contains(key)) {
                return List.of();
            }
            selected = List.of(key);
        }

        List<BannerView> views = new ArrayList<>();
        for (String gameKey : selected) {
            for (GameBanner banner : bannerRepository.findByGameOrderByEndAtAsc(gameKey)) {
                views.add(toView(banner));
            }
        }
        views.sort(Comparator
                .comparingInt((BannerView view) -> rank(view.status()))
                .thenComparing(BannerView::endAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BannerView::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BannerView::name, Comparator.nullsLast(Comparator.naturalOrder())));
        return views;
    }

    private BannerView toView(GameBanner banner) {
        return new BannerView(
                banner.getId(),
                banner.getGame(),
                gameCatalog.label(banner.getGame()),
                banner.getName(),
                banner.getVersion(),
                banner.getBannerType(),
                banner.getStartAt(),
                banner.getEndAt(),
                readItems(banner.getItemsJson()),
                ActivityService.statusOf(banner.getStartAt(), banner.getEndAt(),
                        java.time.OffsetDateTime.now(TimeSupport.ZONE)));
    }

    private List<BannerItemView> readItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, ITEM_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static int rank(String status) {
        return switch (status) {
            case "active", "ending" -> 0;
            case "upcoming" -> 1;
            case "unknown" -> 2;
            default -> 3;
        };
    }
}
