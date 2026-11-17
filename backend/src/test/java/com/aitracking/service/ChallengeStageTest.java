package com.aitracking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ChallengeStageTest {

    @Test
    void classifiesChallengeActivities() {
        assertEquals("虚构叙事", ActivitySyncService.resolveChallengeStage("虚构叙事•取象为文"));
        assertEquals("混沌回忆", ActivitySyncService.resolveChallengeStage("混沌回忆•物竞天择"));
        assertEquals("异相仲裁", ActivitySyncService.resolveChallengeStage("异相仲裁•隐藏关卡"));
        assertEquals("末日幻影", ActivitySyncService.resolveChallengeStage("末日幻影•领航冥茫"));
        assertEquals("幽境危战", ActivitySyncService.resolveChallengeStage("幽境危战"));
    }

    @Test
    void leavesRegularActivitiesUngrouped() {
        assertNull(ActivitySyncService.resolveChallengeStage("奇库寻遗"));
        assertNull(ActivitySyncService.resolveChallengeStage(null));
    }
}
