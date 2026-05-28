package com.attendance.ingestion.web;

import com.attendance.device.web.IngestionSourcePrincipal;
import com.attendance.ingestion.service.PunchEventIngestionPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingestion")
@Tag(name = "Ingestion")
public class PunchIngestionController {

    private final PunchEventIngestionPort port;

    public PunchIngestionController(PunchEventIngestionPort port) {
        this.port = port;
    }

    @PostMapping("/punches")
    @PreAuthorize("hasAuthority('ingestion.write')")
    @Operation(summary = "Ingest a batch of punch events. Idempotent.")
    public ResponseEntity<PunchIngestionDtos.IngestionResponse> ingest(
            @Valid @RequestBody PunchIngestionDtos.PunchBatchRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Object principal) {
        UUID authenticatedSourceId = null;
        if (principal instanceof IngestionSourcePrincipal s) {
            authenticatedSourceId = s.sourceId();
        }
        PunchIngestionDtos.IngestionResponse response =
                port.ingest(authenticatedSourceId, idempotencyKey, body);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
