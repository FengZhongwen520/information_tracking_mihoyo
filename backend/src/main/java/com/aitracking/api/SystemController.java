package com.aitracking.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aitracking.service.DataBackupService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {

    private final JdbcTemplate jdbcTemplate;
    private final DataBackupService backupService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean databaseUp = false;
        try {
            Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
            databaseUp = result != null && result == 1;
        } catch (Exception ignored) {
            // The response below reports the degraded state.
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", databaseUp ? "ok" : "degraded");
        body.put("service", "ai-tracking-backend");
        body.put("database", databaseUp ? "up" : "down");
        body.put("last_backup_at", backupService.getLastBackupAt());
        body.put("time", Instant.now().toString());
        return ResponseEntity.status(databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }
}
