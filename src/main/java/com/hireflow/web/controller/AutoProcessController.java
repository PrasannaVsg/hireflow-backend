package com.hireflow.web.controller;

import com.hireflow.service.AutoProcessService;
import com.hireflow.service.AutoProcessService.AutoProcessResult;
import com.hireflow.service.JobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/auto-process")
@RequiredArgsConstructor
public class AutoProcessController {

    private final AutoProcessService autoProcessService;
    private final JobService jobService;

    public record AutoProcessConfig(
            boolean enabled,
            @Min(1) @Max(200) int shortlistSize,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal scoreThreshold,
            @Size(max = 40) String emailTone
    ) {}

    // trigger auto-process (only works if enabled=true on the job)
    @PostMapping
    public AutoProcessResult process(@PathVariable UUID jobId) {
        return autoProcessService.process(jobId);
    }

    // get current config
    @GetMapping("/config")
    public AutoProcessConfig getConfig(@PathVariable UUID jobId) {
        return jobService.getAutoProcessConfig(jobId);
    }

    // update config (enable/disable flag + thresholds)
    @PatchMapping("/config")
    public AutoProcessConfig updateConfig(@PathVariable UUID jobId,
                                          @Valid @RequestBody AutoProcessConfig config) {
        return jobService.updateAutoProcessConfig(jobId, config);
    }
}
