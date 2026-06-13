package com.hireflow.service;

import com.hireflow.domain.JobRequisition;
import com.hireflow.domain.Organisation;
import com.hireflow.domain.User;
import com.hireflow.domain.enums.JobStatus;
import com.hireflow.exception.ResourceNotFoundException;
import com.hireflow.repository.JobRequisitionRepository;
import com.hireflow.repository.OrganisationRepository;
import com.hireflow.repository.UserRepository;
import com.hireflow.web.controller.AutoProcessController.AutoProcessConfig;
import com.hireflow.web.controller.JobController.CreateJobRequest;
import com.hireflow.web.controller.JobController.JobResponse;
import com.hireflow.web.dto.common.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JobService {

    private final JobRequisitionRepository jobRepository;
    private final OrganisationRepository orgRepository;
    private final UserRepository userRepository;
    private final EmbeddingService embeddingService;

    public JobResponse create(CreateJobRequest request) {
        UUID orgId = SecurityUtils.currentOrgId();
        UUID userId = SecurityUtils.currentUserId();

        Organisation org = orgRepository.getReferenceById(orgId);
        User creator = userRepository.getReferenceById(userId);

        JobRequisition job = JobRequisition.builder()
                .organisation(org)
                .createdBy(creator)
                .title(request.title())
                .description(request.description())
                .location(request.location())
                .seniority(request.seniority())
                .requiredSkills(request.requiredSkills())
                .status(JobStatus.DRAFT)
                .autoProcessEnabled(request.autoProcessEnabled())
                .autoShortlistSize(request.shortlistSize() > 0 ? request.shortlistSize() : 25)
                .autoScoreThreshold(request.scoreThreshold() != null
                        ? request.scoreThreshold() : java.math.BigDecimal.valueOf(60.0))
                .autoEmailTone(request.emailTone() != null
                        && !request.emailTone().isBlank() ? request.emailTone() : "professional")
                .build();

        return toResponse(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID id) {
        UUID orgId = SecurityUtils.currentOrgId();
        return toResponse(jobRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> list(JobStatus status, Pageable pageable) {
        UUID orgId = SecurityUtils.currentOrgId();
        var page = status == null
                ? jobRepository.findByOrganisationId(orgId, pageable)
                : jobRepository.findByOrganisationIdAndStatus(orgId, status, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    public JobResponse update(UUID id, CreateJobRequest request) {
        UUID orgId = SecurityUtils.currentOrgId();
        JobRequisition job = jobRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", id));
        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setLocation(request.location());
        job.setSeniority(request.seniority());
        job.setRequiredSkills(request.requiredSkills());
        job.setAutoProcessEnabled(request.autoProcessEnabled());
        if (request.shortlistSize() > 0) job.setAutoShortlistSize(request.shortlistSize());
        if (request.scoreThreshold() != null) job.setAutoScoreThreshold(request.scoreThreshold());
        if (request.emailTone() != null && !request.emailTone().isBlank())
            job.setAutoEmailTone(request.emailTone());
        return toResponse(job);
    }

    public JobResponse changeStatus(UUID id, JobStatus status) {
        UUID orgId = SecurityUtils.currentOrgId();
        UUID userId = SecurityUtils.currentUserId();
        JobRequisition job = jobRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", id));
        job.setStatus(status);
        if (status == JobStatus.OPEN && job.getEmbedding() == null) {
            float[] embedding = embeddingService.embed(job.getDescription(), orgId, userId, id);
            jobRepository.updateEmbedding(id, embeddingService.toVectorLiteral(embedding));
            log.info("Auto-indexed embedding for job {} on status change to OPEN", id);
        }
        return toResponse(job);
    }

    public void reindexEmbedding(UUID id) {
        UUID orgId = SecurityUtils.currentOrgId();
        UUID userId = SecurityUtils.currentUserId();
        JobRequisition job = jobRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", id));
        float[] embedding = embeddingService.embed(job.getDescription(), orgId, userId, id);
        jobRepository.updateEmbedding(id, embeddingService.toVectorLiteral(embedding));
        log.info("Reindexed embedding for job {}", id);
    }

    public void delete(UUID id) {
        UUID orgId = SecurityUtils.currentOrgId();
        JobRequisition job = jobRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", id));
        jobRepository.delete(job);
    }

    public AutoProcessConfig getAutoProcessConfig(UUID jobId) {
        UUID orgId = SecurityUtils.currentOrgId();
        JobRequisition job = jobRepository.findByIdAndOrganisationId(jobId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", jobId));
        return toConfig(job);
    }

    public AutoProcessConfig updateAutoProcessConfig(UUID jobId, AutoProcessConfig config) {
        UUID orgId = SecurityUtils.currentOrgId();
        JobRequisition job = jobRepository.findByIdAndOrganisationId(jobId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", jobId));
        job.setAutoProcessEnabled(config.enabled());
        job.setAutoShortlistSize(config.shortlistSize());
        job.setAutoScoreThreshold(config.scoreThreshold());
        job.setAutoEmailTone(config.emailTone());
        log.info("Auto-process config updated for job {} — enabled={}", jobId, config.enabled());
        return toConfig(job);
    }

    private AutoProcessConfig toConfig(JobRequisition job) {
        return new AutoProcessConfig(job.isAutoProcessEnabled(), job.getAutoShortlistSize(),
                job.getAutoScoreThreshold(), job.getAutoEmailTone());
    }

    public JobResponse toResponse(JobRequisition job) {
        return new JobResponse(job.getId(), job.getTitle(), job.getDescription(),
                job.getLocation(), job.getSeniority(), job.getRequiredSkills(), job.getStatus(),
                job.isAutoProcessEnabled(), job.getAutoShortlistSize(),
                job.getAutoScoreThreshold(), job.getAutoEmailTone());
    }
}
