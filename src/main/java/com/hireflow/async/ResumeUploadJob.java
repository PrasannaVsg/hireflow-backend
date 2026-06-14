package com.hireflow.async;

import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.hireflow.async.JobStatusRegistry.JobStatus;
import com.hireflow.async.JobStatusRegistry.State;
import com.hireflow.config.AsyncConfig;
import com.hireflow.domain.Candidate;
import com.hireflow.domain.Organisation;
import com.hireflow.domain.enums.CandidateSource;
import com.hireflow.domain.enums.CandidateStatus;
import com.hireflow.repository.CandidateRepository;
import com.hireflow.repository.JobRequisitionRepository;
import com.hireflow.repository.OrganisationRepository;
import com.hireflow.service.EmbeddingService;
import com.hireflow.service.ResumeParserService;
import com.hireflow.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeUploadJob {

    private final ResumeParserService resumeParserService;
    private final EmbeddingService embeddingService;
    private final StorageService storageService;
    private final CandidateRepository candidateRepository;
    private final OrganisationRepository organisationRepository;
    private final JobRequisitionRepository jobRequisitionRepository;
    private final JobStatusRegistry statusRegistry;

    public record ResumeItem(String objectKey, String contentType, String filename) { }

    @Async(AsyncConfig.RESUME_EXECUTOR)
    public void process(String jobId, UUID orgId, UUID jobReqId, CandidateSource source,
                        List<ResumeItem> items) {
        JobStatus status = statusRegistry.get(jobId);
        statusRegistry.update(withState(status, State.RUNNING));

        int processed = 0, succeeded = 0, failed = 0;
        String lastError = null;

        for (ResumeItem item : items) {
            try {
                ingestOne(orgId, jobReqId, source, item);
                succeeded++;
            } catch (RuntimeException e) {
                failed++;
                lastError = item.filename() + ": " + e.getMessage();
                log.warn("Resume ingest failed [{}]: {}", item.filename(), e.getMessage());
            } finally {
                processed++;
                statusRegistry.update(new JobStatus(jobId, State.RUNNING, items.size(),
                        processed, succeeded, failed, lastError,
                        status.startedAt(), null));
            }
        }

        State finalState = failed == items.size() && !items.isEmpty() ? State.FAILED : State.COMPLETED;
        statusRegistry.update(new JobStatus(jobId, finalState, items.size(),
                processed, succeeded, failed, lastError, status.startedAt(), null));
        log.info("Resume batch {} done: {} ok, {} failed", jobId, succeeded, failed);
    }

    protected void ingestOne(UUID orgId, UUID jobReqId, CandidateSource source, ResumeItem item) {
        Organisation org = organisationRepository.getReferenceById(orgId);

        String text;
        try (var stream = storageService.openStream(item.objectKey())) {
            text = resumeParserService.parse(stream, item.contentType(), item.filename());
        } catch (Exception e) {
            throw new IllegalStateException("parse failed: " + e.getMessage(), e);
        }

        Candidate candidate = Candidate.builder()
                .organisation(org)
                .job(jobReqId != null ? jobRequisitionRepository.getReferenceById(jobReqId) : null)
                .fullName(deriveName(item.filename()))
                .resumeObjectKey(item.objectKey())
                .resumeText(text)
                .source(source)
                .status(CandidateStatus.PARSED)
                .build();

        candidate = candidateRepository.save(candidate);

        float[] embedding = embeddingService.embed(text, orgId, null, candidate.getId());
        candidateRepository.updateEmbedding(candidate.getId(),
                embeddingService.toVectorLiteral(embedding));
    }

    private String deriveName(String filename) {
        String base = filename.replaceAll("\\.[^.]+$", "").replaceAll("[_-]+", " ").trim();
        return base.isBlank() ? "Unknown Candidate" : base;
    }

    private JobStatus withState(JobStatus s, State state) {
        return new JobStatus(s.jobId(), state, s.total(), s.processed(),
                s.succeeded(), s.failed(), s.lastError(), s.startedAt(), null);
    }
}
