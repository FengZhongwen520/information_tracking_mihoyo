package com.aitracking.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.aitracking.config.BackupProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataBackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final JdbcTemplate jdbcTemplate;
    private final BackupProperties properties;

    @Value("${app.auth.rsa-key-path:./data/auth-rsa-private.key}")
    private String rsaKeyPath;

    private volatile Instant lastBackupAt;

    @EventListener(ApplicationReadyEvent.class)
    public void backupOnStartup() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            backupNow();
        } catch (Exception ex) {
            log.error("启动备份失败，服务继续运行", ex);
        }
    }

    @Scheduled(cron = "${app.backup.cron:0 20 3 * * *}", zone = "Asia/Shanghai")
    public void scheduledBackup() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            backupNow();
        } catch (Exception ex) {
            log.error("定时备份失败", ex);
        }
    }

    public synchronized Path backupNow() throws IOException {
        Path root = Path.of(properties.getDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        restrict(root, true);

        String stamp = STAMP.format(Instant.now());
        Path target = root.resolve("data-" + stamp);
        int suffix = 1;
        while (Files.exists(target)) {
            target = root.resolve("data-" + stamp + "-" + suffix);
            suffix += 1;
        }
        Files.createDirectories(target);
        restrict(target, true);

        Path databaseBackup = target.resolve("tracker.zip");
        String sqlPath = databaseBackup.toString().replace("'", "''");
        jdbcTemplate.execute("BACKUP TO '" + sqlPath + "'");

        copyIfExists(Path.of("app.env"), target.resolve("app.env"));
        copyIfExists(Path.of(rsaKeyPath), target.resolve("auth-rsa-private.key"));
        copyIfExists(Path.of("data", "auth-secret.key"), target.resolve("auth-secret.key"));
        Files.writeString(target.resolve("MANIFEST.txt"),
                "created_at=" + Instant.now() + System.lineSeparator()
                        + "database=tracker.zip" + System.lineSeparator()
                        + "restore=stop service, unzip tracker.zip into backend/data, restore app.env and keys, start service"
                        + System.lineSeparator());
        restrict(target.resolve("MANIFEST.txt"), false);

        lastBackupAt = Instant.now();
        log.info("数据备份完成：{}", target);
        prune(root);
        return target;
    }

    public Instant getLastBackupAt() {
        return lastBackupAt;
    }

    private void prune(Path root) throws IOException {
        Instant cutoff = Instant.now().minusSeconds(Math.max(1, properties.getRetentionDays()) * 86400L);
        try (Stream<Path> entries = Files.list(root)) {
            entries.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("data-"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            deleteRecursively(path);
                        } catch (IOException ex) {
                            log.warn("清理旧备份失败：{}", path, ex);
                        }
                    });
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> entries = Files.walk(path)) {
            for (Path entry : entries.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private void copyIfExists(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source)) {
            return;
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        restrict(target, false);
    }

    private void restrict(Path path, boolean directory) {
        try {
            Set<PosixFilePermission> permissions = directory
                    ? EnumSet.of(PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)
                    : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows development environments do not expose POSIX permissions.
        }
    }
}
