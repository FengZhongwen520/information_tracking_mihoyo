package com.aitracking.config;

import java.util.concurrent.CompletableFuture;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.aitracking.service.ActivitySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class HoyoverseSyncRunner implements ApplicationRunner {

    private final HoyoverseProperties properties;
    private final ActivitySyncService activitySyncService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("HoYoverse 同步已关闭（app.hoyoverse.enabled=false）");
            return;
        }
        CompletableFuture.runAsync(activitySyncService::syncAll)
                .exceptionally(ex -> {
                    log.warn("启动时同步 HoYoverse 数据失败", ex);
                    return null;
                });
    }
}
