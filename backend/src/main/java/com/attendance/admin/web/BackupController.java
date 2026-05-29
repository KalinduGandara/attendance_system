package com.attendance.admin.web;

import com.attendance.admin.domain.BackupJob;
import com.attendance.admin.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/backups")
@Tag(name = "Backups")
@PreAuthorize("hasAuthority('system.admin')")
public class BackupController {

    private final BackupService service;

    public BackupController(BackupService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List recent backup jobs (newest first)")
    public List<AdminDtos.BackupJobResponse> list() {
        return service.recent().stream().map(AdminDtos.BackupJobResponse::from).toList();
    }

    @PostMapping
    @Operation(summary = "Run a backup now (asynchronous)")
    public ResponseEntity<AdminDtos.BackupJobResponse> run() {
        BackupJob job = service.runManual();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AdminDtos.BackupJobResponse.from(job));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a backup job's status")
    public AdminDtos.BackupJobResponse get(@PathVariable UUID id) {
        return AdminDtos.BackupJobResponse.from(service.get(id));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download a completed backup file")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        Resource resource = service.resolveDownload(id);
        String filename = "backup-" + id + "." + service.fileExtension();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(resource);
    }
}
