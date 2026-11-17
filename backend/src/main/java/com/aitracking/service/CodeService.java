package com.aitracking.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.aitracking.domain.GameCode;
import com.aitracking.domain.UserAccount;
import com.aitracking.dto.CodeView;
import com.aitracking.repo.GameCodeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CodeService {

    private static final TypeReference<List<String>> REWARD_LIST = new TypeReference<>() {
    };

    private final GameCodeRepository codeRepository;
    private final GameCatalog gameCatalog;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public List<CodeView> list(String userId, String game, boolean activeOnly) {
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

        List<CodeView> views = new ArrayList<>();
        for (String gameKey : selected) {
            List<GameCode> rows = activeOnly
                    ? codeRepository.findByGameAndActiveTrueOrderByCodeAsc(gameKey)
                    : codeRepository.findByGameOrderByCodeAsc(gameKey);
            for (GameCode code : rows) {
                views.add(new CodeView(
                        code.getId(),
                        code.getGame(),
                        gameCatalog.label(code.getGame()),
                        code.getCode(),
                        readRewards(code.getRewardsJson())));
            }
        }
        return views;
    }

    private List<String> readRewards(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, REWARD_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
