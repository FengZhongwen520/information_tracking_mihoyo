package com.aitracking.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aitracking.dto.GameView;
import com.aitracking.dto.SyncResultView;
import com.aitracking.dto.UpdateGamesRequest;
import com.aitracking.security.AuthInterceptor;
import com.aitracking.security.RequireAuth;
import com.aitracking.service.ActivityService;
import com.aitracking.service.ActivitySyncService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GameController {

    private final ActivityService activityService;
    private final ActivitySyncService activitySyncService;

    @GetMapping("/games")
    public List<GameView> games(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId) {
        return activityService.listGames(userId);
    }

    @RequireAuth
    @PutMapping("/games")
    public List<GameView> updateGames(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId,
            @Valid @RequestBody UpdateGamesRequest request) {
        return activityService.updateGames(userId, request.games());
    }

    @RequireAuth
    @PostMapping("/sync")
    public SyncResultView sync(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId) {
        boolean started = activitySyncService.triggerAsync();
        String message = started
                ? "同步已在后台开始，稍后刷新即可看到最新数据"
                : "同步正在执行，或刚刚执行过，请稍后再试";
        return new SyncResultView(started, activityService.listGames(userId), message);
    }
}
