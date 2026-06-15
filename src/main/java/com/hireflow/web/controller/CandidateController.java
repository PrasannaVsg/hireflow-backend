package com.hireflow.web.controller;

import com.hireflow.async.JobStatusRegistry.JobStatus;
import com.hireflow.domain.enums.CandidateSource;
import com.hireflow.domain.enums.PipelineStage;
import com.hireflow.service.CandidateService;
import com.hireflow.service.PipelineService;
import com.hireflow.web.dto.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;
    private final PipelineService pipelineService;

    public record CreateCandidateRequest(
            @NotBlank @Size(max = 160) String fullName,
            @Email @Size(max = 255) String email,
            @Size(max = 40) String phone,
            UUID jobId,
            CandidateSource source) { }

    public record CandidateResponse(
            UUID id, String fullName, String email, String phone,
            UUID jobId, String jobTitle, String status,
            PipelineStage pipelineStage, CandidateSource source,
            BigDecimal offerAmount, String rejectionReason,
            Instant createdAt, String createdByName) { }

    public record BatchUploadResponse(String jobId, int fileCount, String statusUrl) { }

    public record StageChangeRequest(
            @NotBlank String targetStage,
            BigDecimal offerAmount,
            String rejectionReason) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateResponse create(@Valid @RequestBody CreateCandidateRequest request) {
        return candidateService.create(request);
    }

    @GetMapping("/{id}")
    public CandidateResponse get(@PathVariable UUID id) {
        return candidateService.get(id);
    }

    @GetMapping
    public PageResponse<CandidateResponse> list(
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return candidateService.list(jobId, from, to, pageable);
    }

    @PostMapping(value = "/batch-upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BatchUploadResponse batchUpload(@RequestParam("files") List<MultipartFile> files,
                                           @RequestParam(required = false) UUID jobId,
                                           @RequestParam(required = false) CandidateSource source) {
        return candidateService.enqueueBatchUpload(files, jobId, source);
    }

    @GetMapping("/batch-upload/{jobId}/status")
    public JobStatus batchStatus(@PathVariable String jobId) {
        return candidateService.batchStatus(jobId);
    }

    @PatchMapping("/{id}/stage")
    public CandidateResponse moveStage(@PathVariable UUID id,
                                       @Valid @RequestBody StageChangeRequest request) {
        return candidateService.toResponse(
                pipelineService.moveStage(id,
                        PipelineStage.valueOf(request.targetStage()),
                        request.offerAmount(),
                        request.rejectionReason()));
    }

    @GetMapping("/{id}/resume-url")
    public ResponseEntity<String> resumeUrl(@PathVariable UUID id) {
        return ResponseEntity.ok(candidateService.presignedResumeUrl(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        candidateService.delete(id);
    }
}
