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
import com.hireflow.web.controller.CandidateController.BatchUploadResponse;
import com.hireflow.web.controller.CandidateController.CandidateResponse;
import com.hireflow.web.controller.CandidateController.CreateCandidateRequest;
import com.hireflow.web.dto.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final OrganisationRepository orgRepository;
    private final JobRequisitionRepository jobRepository;
    private final StorageService storageService;
    private final ResumeUploadJob resumeUploadJob;
    private final JobStatusRegistry statusRegistry;

    @Value("${hireflow.storage.presign-ttl-seconds:600}")
    private int presignTtlSeconds;

    public CandidateResponse create(CreateCandidateRequest request) {
        UUID orgId = SecurityUtils.currentOrgId();
        Organisation org = orgRepository.getReferenceById(orgId);

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

        return toResponse(candidateRepository.save(candidate));
    }

    @Transactional(readOnly = true)
    public CandidateResponse get(UUID id) {
        UUID orgId = SecurityUtils.currentOrgId();
        return toResponse(candidateRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<CandidateResponse> list(UUID jobId, Pageable pageable) {
        UUID orgId = SecurityUtils.currentOrgId();
        var page = jobId == null
                ? candidateRepository.findByOrganisationId(orgId, pageable)
                : candidateRepository.findByJobIdAndOrganisationId(jobId, orgId, pageable);
        return PageResponse.of(page.map(this::toResponse));
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

    public CandidateResponse toResponse(Candidate c) {
        return new CandidateResponse(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(),
                c.getJob() != null ? c.getJob().getId() : null,
                c.getStatus().name(), c.getPipelineStage(), c.getSource());
    }
}
