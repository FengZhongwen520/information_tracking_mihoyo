package com.aitracking.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aitracking.config.HoyoverseProperties;

@Component
public class GameCatalog {

    public record GameDefinition(String key, String label, String shortLabel) {
    }

    private static final List<GameDefinition> ALL = List.of(
            new GameDefinition("sr", "崩坏：星穹铁道", "星铁"),
            new GameDefinition("ys", "原神", "原神"),
            new GameDefinition("zzz", "绝区零", "绝区零"));

    public List<GameDefinition> all() {
        return ALL;
    }

    public List<String> allKeys() {
        return ALL.stream().map(GameDefinition::key).toList();
    }

    public Optional<GameDefinition> find(String key) {
        return ALL.stream().filter(game -> game.key().equals(key)).findFirst();
    }

    public String label(String key) {
        return find(key).map(GameDefinition::label).orElse(key);
    }

    public Set<String> sanitize(Collection<String> keys) {
        if (keys == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String trimmed = key.trim().toLowerCase();
            if (find(trimmed).isPresent()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public List<String> enabledGames(HoyoverseProperties properties) {
        List<String> configured = new ArrayList<>(sanitize(properties.getGames()));
        if (configured.isEmpty()) {
            return ALL.stream().map(GameDefinition::key).toList();
        }
        return configured;
    }

    public List<String> ordered(Collection<String> keys) {
        Set<String> present = keys == null ? Set.of() : Set.copyOf(keys);
        return ALL.stream().map(GameDefinition::key).filter(present::contains).toList();
    }
}
