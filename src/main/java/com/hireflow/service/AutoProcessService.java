package com.hireflow.service;

import com.hireflow.domain.JobRequisition;
import com.hireflow.domain.OutreachDraft;
import com.hireflow.domain.Ranking;
import com.hireflow.domain.enums.OutreachStatus;
import com.hireflow.domain.enums.PipelineStage;
import com.hireflow.exception.ResourceNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.repository.JobRequisitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoProcessService {

    private final RankingService rankingService;
    private final OutreachService outreachService;
    private final EmailService emailService;
    private final PipelineService pipelineService;
    private final JobRequisitionRepository jobRepository;

    public record AutoProcessResult(
            int totalRanked,
            int totalShortlisted,
            int emailsSent,
            int emailsFailed,
            List<ShortlistedCandidate> shortlisted
    ) {}

    public record ShortlistedCandidate(
            UUID candidateId,
            String candidateName,
            BigDecimal score,
            String emailStatus
    ) {}

    public AutoProcessResult process(UUID jobId) {
        UUID orgId = SecurityUtils.currentOrgId();

        JobRequisition job = jobRepository.findByIdAndOrganisationId(jobId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", jobId));

        if (!job.isAutoProcessEnabled()) {
            throw new ValidationException(
                    "Auto-process is disabled for this job. Enable it via PATCH /api/v1/jobs/" + jobId + "/auto-process/config");
        }

        int shortlistSize       = job.getAutoShortlistSize();
        BigDecimal threshold    = job.getAutoScoreThreshold();
        String emailTone        = job.getAutoEmailTone();

        log.info("Auto-process started for job {} | shortlistSize={} scoreThreshold={} tone={}",
                jobId, shortlistSize, threshold, emailTone);

        // Step 1: rank
        List<Ranking> rankings = rankingService.rankCandidatesForJob(jobId, shortlistSize);

        // Step 2: filter by score threshold
        List<Ranking> shortlisted = rankings.stream()
                .filter(r -> r.getScore().compareTo(threshold) >= 0)
                .toList();

        log.info("Ranked: {}, Shortlisted (score >= {}): {}", rankings.size(), threshold, shortlisted.size());

        // Step 3: draft + send email + move pipeline for each shortlisted candidate
        int emailsSent = 0, emailsFailed = 0;
        List<ShortlistedCandidate> results = new ArrayList<>(shortlisted.size());

        for (Ranking ranking : shortlisted) {
            UUID candidateId     = ranking.getCandidate().getId();
            String candidateName = ranking.getCandidate().getFullName();
            String emailStatus;

            try {
                OutreachDraft draft = outreachService.draftOutreach(candidateId, jobId, emailTone);

                String toEmail = ranking.getCandidate().getEmail();
                if (toEmail != null && !toEmail.isBlank()) {
                    emailService.send(toEmail, draft.getSubject(), draft.getBody());
                    outreachService.markSent(draft.getId());
                    emailsSent++;
                    emailStatus = "SENT";
                } else {
                    emailStatus = "SKIPPED_NO_EMAIL";
                }

                pipelineService.moveStage(candidateId, PipelineStage.SCREENING, null, null);

            } catch (Exception e) {
                log.warn("Auto-process failed for candidate {}: {}", candidateId, e.getMessage());
                emailsFailed++;
                emailStatus = "FAILED: " + e.getMessage();
            }

            results.add(new ShortlistedCandidate(candidateId, candidateName,
                    ranking.getScore(), emailStatus));
        }

        log.info("Auto-process done for job {} — sent: {}, failed: {}", jobId, emailsSent, emailsFailed);
        return new AutoProcessResult(rankings.size(), shortlisted.size(),
                emailsSent, emailsFailed, results);
    }
}
