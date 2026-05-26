package com.attendance.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "attendance")
public record AuthProperties(Jwt jwt, Cors cors, Cookie cookie, Audit audit) {

    public record Jwt(String secret, long accessTtlSeconds, long refreshTtlSeconds, String issuer) {
    }

    public record Cors(String allowedOrigins) {
    }

    public record Cookie(String refreshName, boolean secure) {
    }

    public record Audit(boolean enabled) {
    }
}
