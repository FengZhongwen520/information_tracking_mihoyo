package com.aitracking.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.hoyoverse")
public class HoyoverseProperties {

    private boolean enabled = true;
    private String baseUrl = "https://api.ennead.cc/mihoyo";
    private List<String> games = new ArrayList<>(List.of("sr", "ys", "zzz"));
    private String locale = "zh-cn";
    private long syncIntervalMs = 1_800_000L;
    private long initialDelayMs = 15_000L;
    private long manualSyncCooldownMs = 120_000L;
    private int timeoutSeconds = 30;
}
