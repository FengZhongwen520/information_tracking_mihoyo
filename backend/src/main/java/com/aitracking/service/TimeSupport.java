package com.aitracking.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public final class TimeSupport {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private TimeSupport() {
    }

    public static OffsetDateTime fromUnix(Long seconds) {
        if (seconds == null || seconds <= 0) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZONE);
    }

    public static OffsetDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            // fall through to local formats
        }
        try {
            return LocalDateTime.parse(text).atZone(ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            // fall through to date only
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
