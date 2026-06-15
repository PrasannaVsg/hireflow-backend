package com.hireflow.service;

import com.hireflow.async.JobStatusRegistry;
import com.hireflow.async.ResumeUploadJob;
import com.hireflow.domain.Candidate;
import com.hireflow.domain.Organisation;
import com.hireflow.domain.enums.CandidateSource;
import com.hireflow.domain.enums.CandidateStatus;
import com.hireflow.exception.ResourceNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.repository.CandidateRepository;
import com.hireflow.repository.JobRequisitionRepository;
import com.hireflow.repository.OrganisationRepository;
import com.hireflow.repository.RankingRepository;
import com.hireflow.repository.UserRepository;
import com.hireflow.web.controller.CandidateController.BatchUploadResponse;
import com.hireflow.web.controller.CandidateController.CandidateResponse;
import com.hireflow.web.controller.CandidateController.CreateCandidateRequest;
import com.hireflow.web.dto.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final RankingRepository rankingRepository;
    private final OrganisationRepository orgRepository;
    private final JobRequisitionRepository jobRepository;
    private final StorageService storageService;
    private final ResumeUploadJob resumeUploadJob;
    private final JobStatusRegistry statusRegistry;
    private final UserAuditService userAuditService;
    private final UserRepository userRepository;

    @Value("${hireflow.storage.presign-ttl-seconds:600}")
    private int presignTtlSeconds;

    public CandidateResponse create(CreateCandidateRequest request) {
        UUID orgId = SecurityUtils.currentOrgId();
        Organisation org = orgRepository.getReferenceById(orgId);

        if (request.email() != null && !request.email().isBlank()) {
            candidateRepository.findByEmailAndOrganisationId(request.email(), orgId).ifPresent(existing -> {
                throw new ValidationException(
                    "A candidate with email '" + request.email() + "' already exists in your organisation.");
            });
        }

        Candidate candidate = Candidate.builder()
                .organisation(org)
                .fullName(request.fullName())
                .email(request.email())
                .phone(request.phone())
                .source(request.source())
                .status(CandidateStatus.NEW)
                .build();

        if (request.jobId() != null) {
            candidate.setJob(jobRepository.getReferenceById(request.jobId()));
        }

        Candidate saved = candidateRepository.save(candidate);
        userAuditService.log("CANDIDATE_CREATED", "CANDIDATE", saved.getId(), saved.getFullName(), null);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CandidateResponse get(UUID id) {
        UUID orgId = SecurityUtils.currentOrgId();
        return toResponse(candidateRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<CandidateResponse> list(UUID jobId, LocalDate from, LocalDate to, Pageable pageable) {
        UUID orgId = SecurityUtils.currentOrgId();
        Instant fromInstant = from != null ? from.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant toInstant   = to   != null ? to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        Specification<Candidate> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organisation").get("id"), orgId));
            predicates.add(cb.notEqual(root.get("status"), CandidateStatus.ARCHIVED));
            if (jobId != null)      predicates.add(cb.equal(root.get("job").get("id"), jobId));
            if (fromInstant != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
            if (toInstant != null)   predicates.add(cb.lessThan(root.get("createdAt"), toInstant));
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return PageResponse.of(candidateRepository.findAll(spec, pageable).map(this::toResponse));
    }

    public void delete(UUID id) {
        UUID orgId = SecurityUtils.currentOrgId();
        Candidate candidate = candidateRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", id));
        userAuditService.log("CANDIDATE_DELETED", "CANDIDATE", id, candidate.getFullName(), null);
        rankingRepository.deleteByJobIdAndCandidateId(candidate.getJob().getId(), id);
        candidateRepository.delete(candidate);
    }

    public BatchUploadResponse enqueueBatchUpload(List<MultipartFile> files, UUID jobId,
                                                   CandidateSource source) {
        UUID orgId = SecurityUtils.currentOrgId();
        if (files == null || files.isEmpty()) {
            throw new ValidationException("No files supplied");
        }
        if (files.size() > 200) {
            throw new ValidationException("Batch limit is 200 files");
        }

        List<ResumeUploadJob.ResumeItem> items = new ArrayList<>(files.size());
        for (MultipartFile f : files) {
            String key = storageService.upload(orgId, f);
            items.add(new ResumeUploadJob.ResumeItem(key, f.getContentType(), f.getOriginalFilename()));
        }

        String jobIdStr = statusRegistry.create(items.size());
        resumeUploadJob.process(jobIdStr, orgId, jobId, source, items);

        userAuditService.log("CANDIDATES_UPLOADED", "CANDIDATE", jobId,
                null, items.size() + " resumes queued for job " + jobId);
        return new BatchUploadResponse(jobIdStr, items.size(),
                "/api/v1/candidates/batch-upload/" + jobIdStr + "/status");
    }

    @Transactional(readOnly = true)
    public JobStatusRegistry.JobStatus batchStatus(String jobId) {
        JobStatusRegistry.JobStatus status = statusRegistry.get(jobId);
        if (status == null) {
            throw new ResourceNotFoundException("BatchJob", jobId);
        }
        return status;
    }

    @Transactional(readOnly = true)
    public String presignedResumeUrl(UUID id) {
        UUID orgId = SecurityUtils.currentOrgId();
        Candidate candidate = candidateRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", id));
        if (candidate.getResumeObjectKey() == null) {
            throw new ResourceNotFoundException("ResumeFile", id);
        }
        return storageService.presignDownload(candidate.getResumeObjectKey(), presignTtlSeconds);
    }

    @Transactional(readOnly = true)
    public CandidateResponse toResponse(Candidate c) {
        // Re-fetch to ensure job proxy is loaded in an active session
        Candidate fresh = candidateRepository.findById(c.getId()).orElse(c);
        String createdByName = null;
        if (fresh.getCreatedByUserId() != null) {
            createdByName = userRepository.findById(fresh.getCreatedByUserId())
                    .map(u -> u.getFullName()).orElse(null);
        }
        return new CandidateResponse(
                fresh.getId(), fresh.getFullName(), fresh.getEmail(), fresh.getPhone(),
                fresh.getJob() != null ? fresh.getJob().getId() : null,
                fresh.getJob() != null ? fresh.getJob().getTitle() : null,
                fresh.getStatus().name(), fresh.getPipelineStage(), fresh.getSource(),
                fresh.getOfferAmount(), fresh.getRejectionReason(),
                fresh.getCreatedAt(), createdByName);
    }
}
