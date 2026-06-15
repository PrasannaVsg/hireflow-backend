package com.hireflow.service;

import com.hireflow.ai.AnthropicClient;
import com.hireflow.ai.PromptBuilder;
import com.hireflow.ai.dto.OutreachResult;
import com.hireflow.domain.Candidate;
import com.hireflow.domain.JobRequisition;
import com.hireflow.domain.OutreachDraft;
import com.hireflow.domain.User;
import com.hireflow.domain.enums.AiOperation;
import com.hireflow.domain.enums.OutreachStatus;
import com.hireflow.exception.ForbiddenException;
import com.hireflow.exception.ResourceNotFoundException;
import com.hireflow.repository.CandidateRepository;
import com.hireflow.repository.JobRequisitionRepository;
import com.hireflow.repository.OutreachDraftRepository;
import com.hireflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutreachService {

    private final CandidateRepository candidateRepository;
    private final JobRequisitionRepository jobRepository;
    private final UserRepository userRepository;
    private final OutreachDraftRepository outreachRepository;
    private final AnthropicClient anthropicClient;
    private final PromptBuilder promptBuilder;
    private final AuditService auditService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<OutreachDraft> list(org.springframework.data.domain.Pageable pageable) {
        UUID orgId = SecurityUtils.currentOrgId();
        return outreachRepository.findByCandidateOrganisationId(orgId, pageable);
    }

    @Transactional
    public OutreachDraft draftOutreach(UUID candidateId, UUID jobId, String tone) {
        UUID orgId = SecurityUtils.currentOrgId();
        UUID actorId = SecurityUtils.currentUserId();

        Candidate candidate = candidateRepository.findByIdAndOrganisationId(candidateId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId));
        JobRequisition job = jobRepository.findByIdAndOrganisationId(jobId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", jobId));
        User author = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorId));

        long start = System.currentTimeMillis();
        String systemPrompt = promptBuilder.buildOutreachSystemPrompt();
        String userPrompt = promptBuilder.buildOutreachUserPrompt(job, candidate, author.getFullName(), tone);

        OutreachResult result = anthropicClient.completeForJson(
                systemPrompt, userPrompt, OutreachResult.class);

        auditService.recordSuccess(orgId, actorId, AiOperation.OUTREACH,
                anthropicClient.modelId(), result.usageInputTokens(), result.usageOutputTokens(),
                System.currentTimeMillis() - start, candidateId, userPrompt);

        OutreachDraft draft = OutreachDraft.builder()
                .candidate(candidate)
                .job(job)
                .createdBy(author)
                .subject(result.subject())
                .body(result.body())
                .channel("EMAIL")
                .status(OutreachStatus.DRAFT)
                .model(anthropicClient.modelId())
                .build();

        return outreachRepository.save(draft);
    }

    @Transactional
    public void markSent(UUID draftId) {
        outreachRepository.findById(draftId).ifPresent(draft -> {
            draft.setStatus(OutreachStatus.SENT);
            draft.setSentAt(Instant.now());
        });
    }

    @Transactional
    public OutreachDraft updateStatus(UUID draftId, OutreachStatus status) {
        UUID actorId = SecurityUtils.currentUserId();
        OutreachDraft draft = outreachRepository.findByIdAndCreatedById(draftId, actorId)
                .orElseThrow(() -> new ForbiddenException("Cannot modify outreach draft " + draftId));
        draft.setStatus(status);

        if (status == OutreachStatus.SENT) {
            String toEmail = draft.getCandidate().getEmail();
            if (toEmail == null || toEmail.isBlank()) {
                throw new IllegalStateException("Candidate has no email address on file.");
            }
            emailService.send(toEmail, draft.getSubject(), draft.getBody());
            draft.setSentAt(Instant.now());
        }

        return draft;
    }
}
