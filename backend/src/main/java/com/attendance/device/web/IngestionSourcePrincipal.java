package com.attendance.device.web;

import com.attendance.device.domain.IngestionSourceType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Authentication principal for API-key-authenticated ingestion requests.
 * Distinct from {@code AppPrincipal} so callers (and audit logs) can tell user
 * actions apart from machine-to-machine source posts.
 */
public record IngestionSourcePrincipal(UUID sourceId, String name, IngestionSourceType type) {

    /** Sources can only invoke ingestion endpoints, never anything else. */
    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ingestion.write"));
    }
}
