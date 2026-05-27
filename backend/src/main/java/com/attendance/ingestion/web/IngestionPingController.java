package com.attendance.ingestion.web;

import com.attendance.device.web.IngestionSourcePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Stub ingestion endpoint so Phase 2 can verify that API-key authentication
 * resolves correctly. The real punch ingestion endpoint lands in Phase 5.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
@Tag(name = "Ingestion")
public class IngestionPingController {

    @GetMapping("/ping")
    @PreAuthorize("hasAuthority('ingestion.write')")
    @Operation(summary = "Echoes the calling source / user so clients can verify auth wiring")
    public Map<String, Object> ping(@AuthenticationPrincipal Object principal) {
        if (principal instanceof IngestionSourcePrincipal s) {
            return Map.of(
                    "authType", "API_KEY",
                    "sourceId", s.sourceId().toString(),
                    "sourceName", s.name(),
                    "sourceType", s.type().name());
        }
        // JWT-authenticated user with ingestion.write permission
        UUID userId = null;
        String username = null;
        if (principal instanceof com.attendance.platform.security.AppPrincipal a) {
            userId = a.userId();
            username = a.username();
        }
        return Map.of(
                "authType", "JWT",
                "userId", userId == null ? "" : userId.toString(),
                "username", username == null ? "" : username);
    }
}
