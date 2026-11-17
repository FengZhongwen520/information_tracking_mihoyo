package com.aitracking.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aitracking.dto.ActivityView;
import com.aitracking.dto.UpdateActivityStateRequest;
import com.aitracking.security.AuthInterceptor;
import com.aitracking.security.RequireAuth;
import com.aitracking.service.ActivityService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public List<ActivityView> list(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId,
            @RequestParam(required = false) String game) {
        return activityService.list(userId, game);
    }

    @RequireAuth
    @PatchMapping("/{id}")
    public ActivityView update(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId,
            @PathVariable String id,
            @Valid @RequestBody UpdateActivityStateRequest request) {
        return activityService.setCompleted(userId, id, request.completed());
    }
}
