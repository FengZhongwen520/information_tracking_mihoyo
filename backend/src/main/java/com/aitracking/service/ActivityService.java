package com.aitracking.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aitracking.api.ApiException;
import com.aitracking.domain.GameActivity;
import com.aitracking.domain.UserAccount;
import com.aitracking.domain.UserActivityState;
import com.aitracking.dto.ActivityRewardView;
import com.aitracking.dto.ActivityView;
import com.aitracking.dto.GameView;
import com.aitracking.dto.SyncStatusView;
import com.aitracking.repo.GameActivityRepository;
import com.aitracking.repo.GameSyncStateRepository;
import com.aitracking.repo.UserActivityStateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final TypeReference<List<ActivityRewardView>> REWARDS = new TypeReference<>() {
    };

    private static final Comparator<ActivityView> ORDER = Comparator
            .comparingInt((ActivityView view) -> "ended".equals(view.status()) ? 1 : 0)
            .thenComparing(ActivityView::endAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ActivityView::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ActivityView::name);

    private final GameActivityRepository activityRepository;
    private final UserActivityStateRepository stateRepository;
    private final GameSyncStateRepository syncStateRepository;
    private final GameCatalog gameCatalog;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public List<ActivityView> list(String userId, String game) {
        boolean guest = userId == null || userId.isBlank();
        List<String> selected;
        Map<String, UserActivityState> states;
        if (guest) {
            selected = gameCatalog.allKeys();
            states = Map.of();
        } else {
            UserAccount user = authService.requireUser(userId);
            selected = new ArrayList<>(gameCatalog.ordered(user.getGames()));
            states = stateRepository.findByUserId(userId).stream()
                    .collect(Collectors.toMap(UserActivityState::getActivityId, Function.identity()));
        }
        if (selected.isEmpty()) {
            return List.of();
        }
        List<GameActivity> rows;
        if (game != null && !game.isBlank()) {
            String key = game.trim().toLowerCase();
            if (!selected.contains(key)) {
                return List.of();
            }
            rows = activityRepository.findByGameOrderByEndAtAsc(key);
        } else {
            rows = activityRepository.findByGameInOrderByEndAtAsc(selected);
        }
        OffsetDateTime now = OffsetDateTime.now(TimeSupport.ZONE);
        return rows.stream()
                .map(row -> toView(row, states.get(row.getId()), now))
                .sorted(ORDER)
                .toList();
    }

    public ActivityView setCompleted(String userId, String activityId, boolean completed) {
        UserAccount user = authService.requireUser(userId);
        GameActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> ApiException.notFound("活动不存在"));
        if (!gameCatalog.ordered(user.getGames()).contains(activity.getGame())) {
            throw ApiException.notFound("活动不存在");
        }
        Instant now = Instant.now();
        UserActivityState state = stateRepository.findByUserIdAndActivityId(userId, activityId)
                .orElseGet(() -> {
                    UserActivityState created = new UserActivityState();
                    created.setId(userId + ":" + activityId);
                    created.setUserId(userId);
                    created.setActivityId(activityId);
                    return created;
                });
        state.setCompleted(completed);
        state.setCompletedAt(completed ? now : null);
        state.setUpdatedAt(now);
        stateRepository.save(state);
        return toView(activity, state, OffsetDateTime.now(TimeSupport.ZONE));
    }

    public List<GameView> listGames(String userId) {
        if (userId == null || userId.isBlank()) {
            return toGameViews(Set.copyOf(gameCatalog.allKeys()));
        }
        UserAccount user = authService.requireUser(userId);
        return toGameViews(Set.copyOf(user.getGames()));
    }

    public List<GameView> updateGames(String userId, Collection<String> requested) {
        List<String> known = gameCatalog.all().stream().map(GameCatalog.GameDefinition::key).toList();
        if (requested == null || requested.stream().anyMatch(key -> key == null || !known.contains(key.trim().toLowerCase()))) {
            throw ApiException.badRequest("包含不支持的游戏，可选：" + String.join(", ", known));
        }
        UserAccount user = authService.requireUser(userId);
        Set<String> sanitized = gameCatalog.sanitize(requested);
        user.setGames(new LinkedHashSet<>(sanitized));
        authService.save(user);
        return toGameViews(Set.copyOf(user.getGames()));
    }

    private List<GameView> toGameViews(Set<String> selected) {
        return gameCatalog.all().stream()
                .map(definition -> {
                    var state = syncStateRepository.findById(definition.key()).orElse(null);
                    SyncStatusView sync = state == null
                            ? new SyncStatusView("never", null, null, null, 0, null)
                            : new SyncStatusView(
                                    state.getStatus(),
                                    state.getLastAttemptAt(),
                                    state.getLastSuccessAt(),
                                    state.getLastError(),
                                    state.getActivityCount(),
                                    state.getVersion());
                    return new GameView(
                            definition.key(),
                            definition.label(),
                            definition.shortLabel(),
                            selected.contains(definition.key()),
                            activityRepository.countByGame(definition.key()),
                            sync);
                })
                .toList();
    }

    private ActivityView toView(GameActivity activity, UserActivityState state, OffsetDateTime now) {
        boolean completed = state != null && state.isCompleted();
        return new ActivityView(
                activity.getId(),
                activity.getGame(),
                gameCatalog.label(activity.getGame()),
                activity.getName(),
                activity.getKind(),
                activity.getDescription(),
                activity.getStartAt(),
                activity.getEndAt(),
                activity.getCover(),
                readRewards(activity.getRewardsJson()),
                activity.getVersion(),
                activity.getVersionName(),
                activity.getChallengeStage(),
                statusOf(activity.getStartAt(), activity.getEndAt(), now),
                completed,
                state == null ? null : state.getCompletedAt());
    }

    private List<ActivityRewardView> readRewards(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, REWARDS);
        } catch (Exception ex) {
            return List.of();
        }
    }

    static String statusOf(OffsetDateTime startAt, OffsetDateTime endAt, OffsetDateTime now) {
        if (endAt != null && !now.isBefore(endAt)) {
            return "ended";
        }
        if (startAt != null && now.isBefore(startAt)) {
            return "upcoming";
        }
        if (endAt != null && Duration.between(now, endAt).toHours() < 48) {
            return "ending";
        }
        return endAt == null ? "unknown" : "active";
    }
}
