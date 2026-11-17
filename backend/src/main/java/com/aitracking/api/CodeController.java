package com.aitracking.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aitracking.dto.CodeView;
import com.aitracking.security.AuthInterceptor;
import com.aitracking.service.CodeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/codes")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    @GetMapping
    public List<CodeView> list(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId,
            @RequestParam(required = false) String game,
            @RequestParam(name = "active", defaultValue = "true") boolean active) {
        return codeService.list(userId, game, active);
    }
}
