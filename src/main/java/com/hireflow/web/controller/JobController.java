package com.hireflow.web.controller;

import com.hireflow.domain.enums.JobStatus;
import com.hireflow.service.JobService;
import com.hireflow.web.dto.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    public record CreateJobRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 40) String jobCode,
            @NotBlank String description,
            @Size(max = 200) String clientName,
            List<String> locations,
            @Size(max = 60) String seniority,
            Integer expMin,
            Integer expMax,
            String requiredSkills,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            String mailTemplate,
            boolean autoProcessEnabled,
            boolean autoEmailOnStageChange,
            @Min(1) @Max(200) int shortlistSize,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal scoreThreshold,
            @Size(max = 40) String emailTone) { }

    public record JobResponse(
            UUID id, String jobCode, String title, String clientName, String description,
            List<String> locations, String seniority, Integer expMin, Integer expMax,
            String requiredSkills, BigDecimal budgetMin, BigDecimal budgetMax,
            String mailTemplate, JobStatus status,
            boolean autoProcessEnabled, boolean autoEmailOnStageChange,
            int autoShortlistSize, BigDecimal autoScoreThreshold, String autoEmailTone,
            long candidateCount, Instant createdAt, String createdByName) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(@Valid @RequestBody CreateJobRequest request) {
        return jobService.create(request);
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return jobService.get(id);
    }

    @GetMapping
    public PageResponse<JobResponse> list(@RequestParam(required = false) JobStatus status,
                                          Pageable pageable) {
        return jobService.list(status, pageable);
    }

    @PutMapping("/{id}")
    public JobResponse update(@PathVariable UUID id, @Valid @RequestBody CreateJobRequest request) {
        return jobService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public JobResponse changeStatus(@PathVariable UUID id, @RequestParam JobStatus status) {
        return jobService.changeStatus(id, status);
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<Void> reindex(@PathVariable UUID id) {
        jobService.reindexEmbedding(id);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        jobService.delete(id);
    }
}
