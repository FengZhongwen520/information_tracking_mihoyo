package com.aitracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.backup")
public class BackupProperties {

    private boolean enabled = true;
    private String dir = "./backups/data";
    private int retentionDays = 30;
    private String cron = "0 20 3 * * *";
}
