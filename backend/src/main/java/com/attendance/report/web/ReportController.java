package com.attendance.report.web;

import com.attendance.platform.security.AppPrincipal;
import com.attendance.report.domain.ReportJob;
import com.attendance.report.service.ReportGenerationService;
import com.attendance.report.service.ReportParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
@PreAuthorize("hasAuthority('report.run')")
public class ReportController {

    private final ReportGenerationService service;

    public ReportController(ReportGenerationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Kick off an asynchronous report generation job")
    public ResponseEntity<ReportDtos.ReportJobResponse> run(
            @Valid @RequestBody ReportDtos.RunReportRequest request,
            @AuthenticationPrincipal AppPrincipal principal) {
        UUID requestedBy = principal == null ? null : principal.userId();
        ReportParameters params = request.parameters() == null
                ? new ReportParameters(null, null, null, null, null, null, null, null)
                : request.parameters();
        ReportJob job = service.submit(request.reportType(), params, requestedBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ReportDtos.ReportJobResponse.from(job));
    }

    @GetMapping
    @Operation(summary = "List the current user's recent report jobs")
    public List<ReportDtos.ReportJobResponse> list(@AuthenticationPrincipal AppPrincipal principal) {
        UUID requestedBy = principal == null ? null : principal.userId();
        return service.recent(requestedBy).stream().map(ReportDtos.ReportJobResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get the status (and download URL when done) of a report job")
    public ReportDtos.ReportJobResponse get(@PathVariable UUID id) {
        return ReportDtos.ReportJobResponse.from(service.get(id));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download the generated report file")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        Resource resource = service.resolveDownload(id);
        ReportGenerationService.MediaTypeAndExtension meta = service.exportMeta();
        String filename = "report-" + id + "." + meta.extension();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(meta.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(resource);
    }
}
