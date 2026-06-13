package com.hireflow.web.controller;

import com.hireflow.domain.enums.OutreachStatus;
import com.hireflow.service.OutreachService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outreach")
@RequiredArgsConstructor
public class OutreachController {

    private final OutreachService outreachService;

    public record DraftRequest(@NotNull UUID candidateId,
                               @NotNull UUID jobId,
                               String tone) { }

    public record OutreachResponse(UUID id, UUID candidateId, UUID jobId,
                                   String subject, String body, OutreachStatus status, Instant sentAt) { }

    public record StatusRequest(@NotNull OutreachStatus status) { }

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public OutreachResponse draft(@Valid @RequestBody DraftRequest request) {
        var d = outreachService.draftOutreach(request.candidateId(), request.jobId(), request.tone());
        return toResponse(d);
    }

    @PostMapping("/{id}/send")
    public OutreachResponse send(@PathVariable UUID id) {
        var d = outreachService.updateStatus(id, OutreachStatus.SENT);
        return toResponse(d);
    }

    @PatchMapping("/{id}/status")
    public OutreachResponse updateStatus(@PathVariable UUID id,
                                         @Valid @RequestBody StatusRequest request) {
        var d = outreachService.updateStatus(id, request.status());
        return toResponse(d);
    }

    private OutreachResponse toResponse(com.hireflow.domain.OutreachDraft d) {
        return new OutreachResponse(d.getId(), d.getCandidate().getId(), d.getJob().getId(),
                d.getSubject(), d.getBody(), d.getStatus(), d.getSentAt());
    }
}
