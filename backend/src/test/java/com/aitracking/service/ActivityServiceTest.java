package com.aitracking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aitracking.api.ApiException;
import com.aitracking.domain.GameActivity;
import com.aitracking.domain.UserAccount;
import com.aitracking.domain.UserActivityState;
import com.aitracking.dto.ActivityView;
import com.aitracking.repo.GameActivityRepository;
import com.aitracking.repo.GameSyncStateRepository;
import com.aitracking.repo.UserActivityStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private GameActivityRepository activityRepository;

    @Mock
    private UserActivityStateRepository stateRepository;

    @Mock
    private GameSyncStateRepository syncStateRepository;

    @Mock
    private AuthService authService;

    private ActivityService activityService;
    private final GameCatalog gameCatalog = new GameCatalog();

    @BeforeEach
    void setUp() {
        activityService = new ActivityService(
                activityRepository, stateRepository, syncStateRepository, gameCatalog, authService,
                new ObjectMapper());
    }

    @Test
    void computesStatusWindows() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-17T12:00:00+08:00");

        assertEquals("upcoming", ActivityService.statusOf(now.plusHours(2), now.plusDays(3), now));
        assertEquals("active", ActivityService.statusOf(now.minusDays(1), now.plusDays(5), now));
        assertEquals("ending", ActivityService.statusOf(now.minusDays(5), now.plusHours(30), now));
        assertEquals("ended", ActivityService.statusOf(now.minusDays(5), now.minusMinutes(1), now));
        assertEquals("unknown", ActivityService.statusOf(null, null, now));
    }

    @Test
    void mergesCompletionAndSortsEndedLast() {
        OffsetDateTime now = OffsetDateTime.now(TimeSupport.ZONE);
        UserAccount user = user("usr_1", "sr");
        when(authService.requireUser("usr_1")).thenReturn(user);

        GameActivity active = activity("a_active", "sr", "进行中", now.minusDays(1), now.plusHours(30));
        GameActivity ended = activity("a_ended", "sr", "已结束", now.minusDays(9), now.minusDays(2));
        when(activityRepository.findByGameInOrderByEndAtAsc(anyList())).thenReturn(List.of(ended, active));

        UserActivityState state = new UserActivityState();
        state.setActivityId("a_active");
        state.setCompleted(true);
        when(stateRepository.findByUserId("usr_1")).thenReturn(List.of(state));

        List<ActivityView> views = activityService.list("usr_1", null);

        assertEquals(2, views.size());
        assertEquals("a_active", views.get(0).id());
        assertTrue(views.get(0).completed());
        assertEquals("ending", views.get(0).status());
        assertEquals("a_ended", views.get(1).id());
        assertEquals("ended", views.get(1).status());
        assertFalse(views.get(1).completed());
    }

    @Test
    void ignoresGameOutsideSubscriptions() {
        when(authService.requireUser("usr_1")).thenReturn(user("usr_1", "ys"));
        List<ActivityView> views = activityService.list("usr_1", "zzz");
        assertTrue(views.isEmpty());
    }

    @Test
    void completionDoesNotLeakIntoNextChallengePeriod() {
        OffsetDateTime now = OffsetDateTime.now(TimeSupport.ZONE);
        when(authService.requireUser("usr_1")).thenReturn(user("usr_1", "sr"));

        GameActivity current = activity("a_current", "sr", "虚构叙事•取象为文",
                now.minusDays(10), now.plusDays(2));
        current.setChallengeStage("虚构叙事");
        GameActivity next = activity("a_next", "sr", "虚构叙事•虚事成理",
                now.plusDays(2), now.plusDays(20));
        next.setChallengeStage("虚构叙事");
        when(activityRepository.findByGameInOrderByEndAtAsc(anyList()))
                .thenReturn(List.of(current, next));

        UserActivityState state = new UserActivityState();
        state.setActivityId("a_current");
        state.setCompleted(true);
        when(stateRepository.findByUserId("usr_1")).thenReturn(List.of(state));

        List<ActivityView> views = activityService.list("usr_1", null);
        ActivityView currentView = views.stream()
                .filter(view -> view.id().equals("a_current")).findFirst().orElseThrow();
        ActivityView nextView = views.stream()
                .filter(view -> view.id().equals("a_next")).findFirst().orElseThrow();

        assertTrue(currentView.completed());
        assertFalse(nextView.completed());
        assertEquals("虚构叙事", nextView.challengeStage());
    }

    @Test
    void rejectsUnsupportedGameKeys() {
        ApiException ex = assertThrows(ApiException.class,
                () -> activityService.updateGames("usr_1", List.of("genshin")));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void sanitizesAndSavesSubscriptions() {
        UserAccount user = user("usr_1", "sr");
        when(authService.requireUser("usr_1")).thenReturn(user);
        when(activityRepository.countByGame(org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);

        activityService.updateGames("usr_1", List.of("ys", "ys", "zzz"));

        assertEquals(List.of("ys", "zzz"), List.copyOf(user.getGames()));
    }

    private UserAccount user(String id, String game) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setGames(new LinkedHashSet<>(List.of(game)));
        return user;
    }

    private GameActivity activity(String id, String game, String name,
                                  OffsetDateTime start, OffsetDateTime end) {
        GameActivity activity = new GameActivity();
        activity.setId(id);
        activity.setGame(game);
        activity.setName(name);
        activity.setStartAt(start);
        activity.setEndAt(end);
        return activity;
    }
}
