package com.attendance.timecard.web;

import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.service.TimeCardEditService;
import com.attendance.timecard.service.TimeCardReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timecards")
@Tag(name = "Time cards")
public class TimeCardController {

    private final TimeCardReadService readService;
    private final TimeCardEditService editService;

    public TimeCardController(TimeCardReadService readService, TimeCardEditService editService) {
        this.readService = readService;
        this.editService = editService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('timecard.read')")
    @Operation(summary = "List daily time cards filtered by employee/status/date window.")
    public List<TimeCardDtos.TimeCardResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) DailyTimeCardStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return readService.list(employeeId, status, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('timecard.read')")
    @Operation(summary = "Detail view of a time card: punches, breakdown, exceptions, edit history.")
    public TimeCardDtos.TimeCardDetailResponse get(@PathVariable UUID id) {
        return readService.getDetail(id);
    }

    @PostMapping("/{id}/edits")
    @PreAuthorize("hasAuthority('timecard.edit')")
    @Operation(summary = "Apply a manual edit (add/edit/delete punch, change status, set note); recomputes synchronously.")
    public TimeCardDtos.TimeCardDetailResponse edit(@PathVariable UUID id,
                                                    @Valid @RequestBody TimeCardDtos.EditRequest body) {
        return editService.applyEdit(id, body);
    }
}
