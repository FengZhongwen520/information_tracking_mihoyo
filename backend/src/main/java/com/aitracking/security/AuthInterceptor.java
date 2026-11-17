package com.aitracking.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.aitracking.domain.UserAccount;
import com.aitracking.dto.ApiError;
import com.aitracking.repo.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE = "auth.userId";

    private final TokenService tokenService;
    private final UserAccountRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            TokenService.VerifiedToken token = tokenService.verify(header.substring(7).trim());
            if (token != null) {
                UserAccount user = userRepository.findById(token.userId()).orElse(null);
                if (user != null && user.getTokenVersion() == token.tokenVersion()) {
                    request.setAttribute(USER_ID_ATTRIBUTE, token.userId());
                    return true;
                }
            }
        }
        if (!requiresAuth(handler)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new ApiError("unauthorized", "登录已失效，请重新登录", "req_" + System.currentTimeMillis()));
        return false;
    }

    private boolean requiresAuth(Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return false;
        }
        return method.hasMethodAnnotation(RequireAuth.class)
                || method.getBeanType().isAnnotationPresent(RequireAuth.class);
    }
}
