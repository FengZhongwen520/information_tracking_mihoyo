package com.aitracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String secret = "";
    private long tokenTtlHours = 168;
    private String allowedOrigins = "http://localhost:5173";
}
