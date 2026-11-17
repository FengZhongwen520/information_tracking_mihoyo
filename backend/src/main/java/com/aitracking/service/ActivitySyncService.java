package com.aitracking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.aitracking.config.HoyoverseProperties;
import com.aitracking.domain.GameActivity;
import com.aitracking.domain.GameBanner;
import com.aitracking.domain.GameCode;
import com.aitracking.domain.GameSyncState;
import com.aitracking.dto.ActivityRewardView;
import com.aitracking.dto.BannerItemView;
import com.aitracking.repo.GameActivityRepository;
import com.aitracking.repo.GameBannerRepository;
import com.aitracking.repo.GameCodeRepository;
import com.aitracking.repo.GameSyncStateRepository;
import com.aitracking.repo.UserActivityStateRepository;
import com.aitracking.service.HoyoverseClient.Banner;
import com.aitracking.service.HoyoverseClient.BannerItem;
import com.aitracking.service.HoyoverseClient.CalendarPayload;
import com.aitracking.service.HoyoverseClient.Challenge;
import com.aitracking.service.HoyoverseClient.CodePayload;
import com.aitracking.service.HoyoverseClient.CodesPayload;
import com.aitracking.service.HoyoverseClient.Event;
import com.aitracking.service.HoyoverseClient.Reward;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivitySyncService {

    private static final List<String> CHALLENGE_STAGES =
            List.of("虚构叙事", "混沌回忆", "异相仲裁", "末日幻影", "幽境危战");

    private static final Map<String, String> GAME_SLUGS = Map.of(
            "sr", "starrail",
            "ys", "genshin",
            "zzz", "zenless");

    private static final Map<String, String> STARRAIL_STAGES = Map.of(
            "ChallengeTypeStory", "虚构叙事",
            "ChallengeTypeChasm", "混沌回忆",
            "ChallengeTypePeak", "异相仲裁",
            "ChallengeTypeBoss", "末日幻影");

    private static final Map<String, String> GENSHIN_STAGES = Map.of(
            "ActTypeTower", "渊月螺旋",
            "ActTypeRoleCombat", "幻想真境剧诗",
            "ActTypeHardChallenge", "幽境危战");

    private static final TypeReference<List<ActivityRewardView>> ACTIVITY_REWARDS =
            new TypeReference<>() {
            };

    private static final TypeReference<List<BannerItemView>> BANNER_ITEMS =
            new TypeReference<>() {
            };

    private static final TypeReference<List<String>> CODE_REWARDS =
            new TypeReference<>() {
            };

    private final HoyoverseProperties properties;
    private final HoyoverseClient hoyoverseClient;
    private final GameCatalog gameCatalog;
    private final GameActivityRepository activityRepository;
    private final GameBannerRepository bannerRepository;
    private final GameCodeRepository codeRepository;
    private final GameSyncStateRepository syncStateRepository;
    private final UserActivityStateRepository stateRepository;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastManualTriggerAt = new AtomicLong(0);

    @Resource(name = "syncExecutor")
    private TaskExecutor syncExecutor;

    @Scheduled(fixedDelayString = "${app.hoyoverse.sync-interval-ms:1800000}",
            initialDelayString = "${app.hoyoverse.initial-delay-ms:15000}")
    public void scheduledSync() {
        if (!properties.isEnabled()) {
            return;
        }
        syncAll();
    }

    public boolean syncAll() {
        if (!properties.isEnabled()) {
            log.debug("HoYoverse 同步已关闭，跳过本次拉取");
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("HoYoverse 同步正在执行，跳过重复触发");
            return false;
        }
        try {
            doSyncAll();
            return true;
        } finally {
            running.set(false);
        }
    }

    public boolean triggerAsync() {
        if (!properties.isEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, properties.getManualSyncCooldownMs());
        if (cooldown > 0 && now - lastManualTriggerAt.get() < cooldown) {
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        lastManualTriggerAt.set(now);
        try {
            syncExecutor.execute(() -> {
                try {
                    doSyncAll();
                } catch (Exception ex) {
                    log.error("手动同步失败", ex);
                } finally {
                    running.set(false);
                }
            });
            return true;
        } catch (RuntimeException ex) {
            running.set(false);
            throw ex;
        }
    }

    private void doSyncAll() {
        for (String game : gameCatalog.enabledGames(properties)) {
            syncGame(game);
        }
    }

    void syncGame(String game) {
        String slug = GAME_SLUGS.get(game);
        if (slug == null) {
            return;
        }
        Instant now = Instant.now();
        GameSyncState state = syncStateRepository.findById(game).orElseGet(() -> {
            GameSyncState created = new GameSyncState();
            created.setGame(game);
            return created;
        });
        state.setLastAttemptAt(now);

        List<String> errors = new ArrayList<>();
        boolean calendarOk = false;
        boolean codesOk = false;
        int activityCount = state.getActivityCount();
        int bannerCount = 0;
        int codeCount = 0;
        String version = state.getVersion();

        try {
            CalendarPayload payload = hoyoverseClient.calendar(slug);
            if (isEmpty(payload.events()) && isEmpty(payload.challenges())) {
                errors.add("活动日历返回空数据，已保留旧记录");
            } else {
                CalendarOutcome outcome = applyCalendar(game, payload, now);
                activityCount = outcome.activityCount();
                bannerCount = outcome.bannerCount();
                if (outcome.version() != null) {
                    version = outcome.version();
                }
                calendarOk = true;
            }
        } catch (Exception ex) {
            errors.add("活动日历：" + message(ex));
            log.warn("HoYoverse 日历同步失败：{} - {}", game, ex.getMessage());
        }

        try {
            CodesPayload payload = hoyoverseClient.codes(slug);
            if (payload == null || (isEmpty(payload.active()) && isEmpty(payload.inactive()))) {
                errors.add("兑换码接口返回空数据，已保留旧记录");
            } else {
                codeCount = applyCodes(game, payload, now);
                codesOk = true;
            }
        } catch (Exception ex) {
            errors.add("兑换码：" + message(ex));
            log.warn("HoYoverse 兑换码同步失败：{} - {}", game, ex.getMessage());
        }

        state.setStatus(errors.isEmpty() ? "ok" : "error");
        state.setLastError(errors.isEmpty() ? null : truncate(String.join("；", errors), 900));
        if (calendarOk || codesOk) {
            state.setLastSuccessAt(now);
        }
        state.setActivityCount(activityCount);
        state.setVersion(version);
        syncStateRepository.save(state);

        if (calendarOk || codesOk) {
            log.info("HoYoverse 同步完成：{}（{} 个活动，{} 个卡池，{} 个兑换码，版本 {}）",
                    game, activityCount, bannerCount, codeCount, version);
        }
    }

    private CalendarOutcome applyCalendar(String game, CalendarPayload payload, Instant now) {
        List<Banner> banners = safe(payload.banners());
        String version = inferVersion(banners);

        Set<String> seenActivities = new HashSet<>();
        for (Event event : safe(payload.events())) {
            String id = upsertEvent(game, event, version, now);
            if (id != null) {
                seenActivities.add(id);
            }
        }
        for (Challenge challenge : safe(payload.challenges())) {
            String id = upsertChallenge(game, challenge, version, now);
            if (id != null) {
                seenActivities.add(id);
            }
        }
        if (!seenActivities.isEmpty()) {
            removeStaleActivities(game, seenActivities);
        }

        int bannerCount = 0;
        if (!banners.isEmpty()) {
            Set<String> seenBanners = new HashSet<>();
            for (Banner banner : banners) {
                String id = upsertBanner(game, banner, now);
                if (id != null) {
                    seenBanners.add(id);
                }
            }
            if (!seenBanners.isEmpty()) {
                removeStaleBanners(game, seenBanners);
            }
            bannerCount = seenBanners.size();
        }

        return new CalendarOutcome(seenActivities.size(), bannerCount, version);
    }

    private String upsertEvent(String game, Event source, String version, Instant now) {
        if (source == null || isBlank(source.name())) {
            return null;
        }
        String name = trim(source.name(), 300);
        OffsetDateTime startAt = TimeSupport.fromUnix(source.startTime());
        OffsetDateTime endAt = TimeSupport.fromUnix(source.endTime());
        String id = activityId(game, "event", source.id(), name, startAt);
        String stage = eventChallengeStage(game, source.typeName());

        GameActivity activity = findOrCreateActivity(id, game, now);
        List<ActivityRewardView> rewards = collectRewards(source.rewards(), source.specialReward(),
                source.polychrome());
        String description = cleanDescription(source.description());
        if (description == null && source.polychrome() != null && source.polychrome() > 0) {
            description = "完成活动可获得菲林 ×" + source.polychrome();
        }

        activity.setName(stage != null && !name.contains(stage)
                ? trim(stage + "•" + name, 300)
                : name);
        activity.setKind(trim(stage != null ? stage : source.typeName(), 80));
        activity.setDescription(description);
        activity.setStartAt(startAt);
        activity.setEndAt(endAt);
        activity.setCover(trim(source.imageUrl(), 1000));
        activity.setChallengeStage(trim(stage, 40));
        activity.setVersionName(null);
        if (version != null) {
            activity.setVersion(trim(version, 40));
        }
        activity.setRewardsJson(writeJson(rewards, ACTIVITY_REWARDS));
        activity.setLastSeenAt(now);
        activity.setSyncedAt(now);
        activityRepository.save(activity);
        return id;
    }

    private String upsertChallenge(String game, Challenge source, String version, Instant now) {
        if (source == null || isBlank(source.name())) {
            return null;
        }
        String stage = challengeStage(game, source.typeName(), source.name());
        String baseName = trim(source.name(), 300);
        String name = stage != null && !baseName.contains(stage)
                ? trim(stage + "•" + baseName, 300)
                : baseName;
        OffsetDateTime startAt = TimeSupport.fromUnix(source.startTime());
        OffsetDateTime endAt = TimeSupport.fromUnix(source.endTime());
        String id = activityId(game, "challenge", source.id(), name, startAt);

        GameActivity activity = findOrCreateActivity(id, game, now);
        activity.setName(name);
        activity.setKind(trim(stage != null ? stage : source.typeName(), 80));
        activity.setDescription(null);
        activity.setStartAt(startAt);
        activity.setEndAt(endAt);
        activity.setCover(null);
        activity.setChallengeStage(trim(stage, 40));
        activity.setVersionName(null);
        if (version != null) {
            activity.setVersion(trim(version, 40));
        }
        activity.setRewardsJson(writeJson(
                collectRewards(source.rewards(), source.specialReward(), null), ACTIVITY_REWARDS));
        activity.setLastSeenAt(now);
        activity.setSyncedAt(now);
        activityRepository.save(activity);
        return id;
    }

    private String upsertBanner(String game, Banner source, Instant now) {
        if (source == null) {
            return null;
        }
        List<BannerItemView> items = collectBannerItems(source);
        String type = bannerTypeLabel(game, source, items);
        String name = isBlank(source.name()) ? type : trim(source.name(), 200);
        OffsetDateTime startAt = TimeSupport.fromUnix(source.startTime());
        OffsetDateTime endAt = TimeSupport.fromUnix(source.endTime());
        String id = bannerId(game, source, type, name, startAt, endAt, items);

        GameBanner banner = bannerRepository.findById(id).orElseGet(() -> {
            GameBanner created = new GameBanner();
            created.setId(id);
            created.setGame(game);
            created.setFirstSeenAt(now);
            return created;
        });
        banner.setExternalId(source.id() == null ? null : trim(String.valueOf(source.id()), 40));
        banner.setName(name);
        if (!isBlank(source.version())) {
            banner.setVersion(trim(source.version(), 40));
        }
        banner.setBannerType(trim(type, 80));
        banner.setStartAt(startAt);
        banner.setEndAt(endAt);
        banner.setItemsJson(writeJson(items, BANNER_ITEMS));
        banner.setLastSeenAt(now);
        banner.setSyncedAt(now);
        bannerRepository.save(banner);
        return id;
    }

    private int applyCodes(String game, CodesPayload payload, Instant now) {
        Map<String, Boolean> entries = new LinkedHashMap<>();
        for (CodePayload code : safe(payload.inactive())) {
            putCode(entries, code, false);
        }
        for (CodePayload code : safe(payload.active())) {
            putCode(entries, code, true);
        }

        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : entries.entrySet()) {
            String value = entry.getKey();
            String id = codeId(game, value);
            GameCode code = codeRepository.findById(id).orElseGet(() -> {
                GameCode created = new GameCode();
                created.setId(id);
                created.setGame(game);
                created.setCode(value);
                created.setFirstSeenAt(now);
                return created;
            });
            code.setActive(entry.getValue());
            code.setRewardsJson(writeJson(findCodeRewards(payload, value), CODE_REWARDS));
            code.setLastSeenAt(now);
            code.setSyncedAt(now);
            codeRepository.save(code);
            seen.add(id);
        }
        if (!seen.isEmpty()) {
            removeStaleCodes(game, seen);
        }
        return seen.size();
    }

    private static void putCode(Map<String, Boolean> entries, CodePayload code, boolean active) {
        if (code == null || isBlank(code.code())) {
            return;
        }
        String value = code.code().trim();
        entries.put(value, active);
    }

    private static List<String> findCodeRewards(CodesPayload payload, String value) {
        List<CodePayload> all = new ArrayList<>();
        all.addAll(safe(payload.active()));
        all.addAll(safe(payload.inactive()));
        for (CodePayload code : all) {
            if (code != null && value.equals(code.code() == null ? null : code.code().trim())) {
                if (code.rewards() == null) {
                    return List.of();
                }
                return code.rewards().stream()
                        .filter(item -> item != null && !item.isBlank())
                        .map(String::trim)
                        .toList();
            }
        }
        return List.of();
    }

    private void removeStaleActivities(String game, Set<String> seenIds) {
        List<String> staleIds = new ArrayList<>();
        for (GameActivity existing : activityRepository.findByGameOrderByEndAtAsc(game)) {
            if (!seenIds.contains(existing.getId())) {
                staleIds.add(existing.getId());
            }
        }
        if (!staleIds.isEmpty()) {
            stateRepository.deleteByActivityIdIn(staleIds);
            activityRepository.deleteAllById(staleIds);
            log.info("清理 {} 个已下线的 {} 活动", staleIds.size(), game);
        }
    }

    private void removeStaleBanners(String game, Set<String> seenIds) {
        List<String> staleIds = bannerRepository.findByGameOrderByEndAtAsc(game).stream()
                .map(GameBanner::getId)
                .filter(id -> !seenIds.contains(id))
                .toList();
        if (!staleIds.isEmpty()) {
            bannerRepository.deleteAllById(staleIds);
            log.info("清理 {} 个已下线的 {} 卡池", staleIds.size(), game);
        }
    }

    private void removeStaleCodes(String game, Set<String> seenIds) {
        List<String> staleIds = codeRepository.findByGameOrderByCodeAsc(game).stream()
                .map(GameCode::getId)
                .filter(id -> !seenIds.contains(id))
                .toList();
        if (!staleIds.isEmpty()) {
            codeRepository.deleteAllById(staleIds);
            log.info("清理 {} 个已下线的 {} 兑换码", staleIds.size(), game);
        }
    }

    private GameActivity findOrCreateActivity(String id, String game, Instant now) {
        return activityRepository.findById(id).orElseGet(() -> {
            GameActivity created = new GameActivity();
            created.setId(id);
            created.setGame(game);
            created.setFirstSeenAt(now);
            return created;
        });
    }

    private static List<ActivityRewardView> collectRewards(
            List<Reward> rewards, Reward specialReward, Integer polychrome) {
        Map<String, ActivityRewardView> collected = new LinkedHashMap<>();
        if (rewards != null) {
            for (Reward reward : rewards) {
                putReward(collected, reward);
            }
        }
        putReward(collected, specialReward);
        if (polychrome != null && polychrome > 0) {
            collected.putIfAbsent("菲林", new ActivityRewardView("菲林", polychrome, null, null));
        }
        return List.copyOf(collected.values());
    }

    private static void putReward(Map<String, ActivityRewardView> target, Reward reward) {
        if (reward == null || isBlank(reward.name())) {
            return;
        }
        String name = reward.name().trim();
        ActivityRewardView view = new ActivityRewardView(
                name,
                reward.amount(),
                reward.icon(),
                reward.rarity());
        ActivityRewardView existing = target.get(name);
        if (existing == null || isZero(existing.amount()) && !isZero(view.amount())) {
            target.put(name, view);
        }
    }

    private static boolean isZero(Integer value) {
        return value == null || value == 0;
    }

    private static List<BannerItemView> collectBannerItems(Banner banner) {
        List<BannerItemView> items = new ArrayList<>();
        addBannerItems(items, banner.characters());
        addBannerItems(items, banner.lightCones());
        addBannerItems(items, banner.weapons());
        addBannerItems(items, banner.agents());
        addBannerItems(items, banner.wEngines());
        return items;
    }

    private static void addBannerItems(List<BannerItemView> target, List<BannerItem> source) {
        if (source == null) {
            return;
        }
        for (BannerItem item : source) {
            if (item == null || isBlank(item.name())) {
                continue;
            }
            target.add(new BannerItemView(
                    item.name().trim(),
                    item.icon(),
                    item.rarity(),
                    item.element(),
                    item.path(),
                    item.profession()));
        }
    }

    private static String bannerTypeLabel(String game, Banner banner, List<BannerItemView> items) {
        if ("sr".equals(game)) {
            return banner.lightCones() != null && !banner.lightCones().isEmpty()
                    ? "光锥活动跃迁"
                    : "角色活动跃迁";
        }
        if ("zzz".equals(game)) {
            if (banner.bannerType() != null && banner.bannerType().contains("WEAPON")) {
                return "音擎活动调频";
            }
            if (!items.isEmpty() || banner.bannerType() != null) {
                return "角色活动调频";
            }
        }
        if (banner.name() != null && banner.name().contains("武器")) {
            return "武器活动祈愿";
        }
        return "角色活动祈愿";
    }

    private static String challengeStage(String game, String typeName, String name) {
        if ("sr".equals(game)) {
            String mapped = STARRAIL_STAGES.get(typeName);
            return mapped != null ? mapped : resolveChallengeStage(name);
        }
        if ("ys".equals(game)) {
            return GENSHIN_STAGES.getOrDefault(typeName, name);
        }
        if ("zzz".equals(game)) {
            return name;
        }
        return resolveChallengeStage(name);
    }

    private static String eventChallengeStage(String game, String typeName) {
        if ("ys".equals(game) && "ActTypeHardChallenge".equals(typeName)) {
            return "幽境危战";
        }
        return null;
    }

    private static String inferVersion(List<Banner> banners) {
        return banners.stream()
                .filter(banner -> banner != null && !isBlank(banner.version()))
                .max((left, right) -> {
                    long leftStart = left.startTime() == null ? 0 : left.startTime();
                    long rightStart = right.startTime() == null ? 0 : right.startTime();
                    int byStart = Long.compare(leftStart, rightStart);
                    return byStart != 0 ? byStart : left.version().compareTo(right.version());
                })
                .map(banner -> banner.version().trim())
                .orElse(null);
    }

    private String activityId(String game, String bucket, Long sourceId,
                              String name, OffsetDateTime startAt) {
        String key = sourceId != null && sourceId != 0 ? "id:" + sourceId : "name:" + name;
        return hash(game + "|" + bucket + "|" + key + "|"
                + (startAt == null ? "" : startAt.toInstant()));
    }

    private String bannerId(String game, Banner banner, String type, String name,
                            OffsetDateTime startAt, OffsetDateTime endAt, List<BannerItemView> items) {
        String key = banner.id() != null && banner.id() != 0
                ? "id:" + banner.id()
                : "name:" + name + "|items:" + items.stream()
                        .limit(3)
                        .map(BannerItemView::name)
                        .reduce("", (left, right) -> left + "," + right);
        return hash(game + "|banner|" + type + "|" + key + "|" + startAt + "|" + endAt);
    }

    private String codeId(String game, String code) {
        return hash(game + "|code|" + code);
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (Exception ex) {
            throw new IllegalStateException("生成数据 ID 失败", ex);
        }
    }

    private <T> String writeJson(T value, TypeReference<T> type) {
        try {
            return objectMapper.writerFor(type).writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化数据失败", ex);
        }
    }

    private static String cleanDescription(String value) {
        if (value == null) {
            return null;
        }
        String text = value.replace("\\n", "\n").trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    static String resolveChallengeStage(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (String stage : CHALLENGE_STAGES) {
            if (name.contains(stage)) {
                return stage;
            }
        }
        return null;
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "未知错误";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record CalendarOutcome(int activityCount, int bannerCount, String version) {
    }
}
