package com.hireflow.service;

import com.hireflow.domain.Candidate;
import com.hireflow.domain.JobRequisition;
import com.hireflow.domain.enums.PipelineStage;
import com.hireflow.exception.ResourceNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    // Allowed forward/backward transitions per stage.
    // Recruiters can skip rounds (e.g. SOURCED → CLIENT_SHORTLIST for single-round).
    private static final Map<PipelineStage, Set<PipelineStage>> TRANSITIONS = Map.ofEntries(
        Map.entry(PipelineStage.SOURCED,          EnumSet.of(
            PipelineStage.SCREENING, PipelineStage.L1_SHORTLIST,
            PipelineStage.L2_SHORTLIST, PipelineStage.CLIENT_SHORTLIST, PipelineStage.L1_REJECT)),
        Map.entry(PipelineStage.SCREENING,         EnumSet.of(
            PipelineStage.L1_SHORTLIST, PipelineStage.L1_REJECT,
            PipelineStage.L2_SHORTLIST, PipelineStage.CLIENT_SHORTLIST, PipelineStage.SOURCED)),
        Map.entry(PipelineStage.L1_SHORTLIST,      EnumSet.of(
            PipelineStage.L2_SHORTLIST, PipelineStage.L1_REJECT,
            PipelineStage.CLIENT_SHORTLIST, PipelineStage.SCREENING)),
        Map.entry(PipelineStage.L1_REJECT,         EnumSet.of(
            PipelineStage.SCREENING, PipelineStage.L1_SHORTLIST)),
        Map.entry(PipelineStage.L2_SHORTLIST,      EnumSet.of(
            PipelineStage.CLIENT_SHORTLIST, PipelineStage.L2_REJECT, PipelineStage.L1_SHORTLIST)),
        Map.entry(PipelineStage.L2_REJECT,         EnumSet.of(
            PipelineStage.SCREENING, PipelineStage.L2_SHORTLIST)),
        Map.entry(PipelineStage.CLIENT_SHORTLIST,  EnumSet.of(
            PipelineStage.WAITING_FEEDBACK, PipelineStage.CLIENT_REJECTED, PipelineStage.L2_SHORTLIST)),
        Map.entry(PipelineStage.CLIENT_REJECTED,   EnumSet.of(
            PipelineStage.SCREENING, PipelineStage.CLIENT_SHORTLIST)),
        Map.entry(PipelineStage.WAITING_FEEDBACK,  EnumSet.of(
            PipelineStage.FINAL_SELECT, PipelineStage.CLIENT_REJECTED, PipelineStage.CLIENT_SHORTLIST)),
        Map.entry(PipelineStage.FINAL_SELECT,      EnumSet.of(
            PipelineStage.OFFER_RELEASED, PipelineStage.CLIENT_REJECTED)),
        Map.entry(PipelineStage.OFFER_RELEASED,    EnumSet.of(
            PipelineStage.HIRED, PipelineStage.FINAL_SELECT)),
        Map.entry(PipelineStage.HIRED,             EnumSet.noneOf(PipelineStage.class))
    );

    // Stages that should trigger a candidate-facing email
    private static final Set<PipelineStage> EMAIL_STAGES = EnumSet.of(
        PipelineStage.L1_SHORTLIST, PipelineStage.L1_REJECT,
        PipelineStage.L2_SHORTLIST, PipelineStage.L2_REJECT,
        PipelineStage.CLIENT_SHORTLIST, PipelineStage.CLIENT_REJECTED,
        PipelineStage.FINAL_SELECT, PipelineStage.OFFER_RELEASED
    );

    private final CandidateRepository candidateRepository;
    private final EmailService emailService;
    private final UserAuditService userAuditService;

    @Transactional
    public Candidate moveStage(UUID candidateId, PipelineStage target,
                               BigDecimal offerAmount, String rejectionReason) {
        UUID orgId = SecurityUtils.currentOrgId();
        Candidate candidate = candidateRepository.findByIdAndOrganisationId(candidateId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId));

        PipelineStage current = candidate.getPipelineStage();
        if (current == target) return candidate;

        if (!TRANSITIONS.getOrDefault(current, EnumSet.noneOf(PipelineStage.class)).contains(target)) {
            throw new ValidationException("Illegal pipeline transition: " + current + " → " + target);
        }

        candidate.setPipelineStage(target);

        if (offerAmount != null && target == PipelineStage.FINAL_SELECT) {
            candidate.setOfferAmount(offerAmount);
        }
        if (rejectionReason != null && !rejectionReason.isBlank() && isRejectionStage(target)) {
            candidate.setRejectionReason(rejectionReason);
        }

        log.info("Candidate {} moved {} → {}", candidateId, current, target);
        userAuditService.log("CANDIDATE_STAGE_CHANGED", "CANDIDATE", candidateId,
                candidate.getFullName(), current + " → " + target);

        sendStageEmail(candidate, target);
        return candidate;
    }

    private void sendStageEmail(Candidate candidate, PipelineStage newStage) {
        if (!EMAIL_STAGES.contains(newStage)) return;
        if (candidate.getEmail() == null || candidate.getEmail().isBlank()) return;

        JobRequisition job = candidate.getJob();
        if (job == null) return;
        if (!job.isAutoEmailOnStageChange()) return;

        String subject = buildSubject(newStage, job.getTitle());
        String body    = buildBody(candidate, job, newStage);

        try {
            emailService.send(candidate.getEmail(), subject, body);
            log.info("Stage email sent to {} for stage {}", candidate.getEmail(), newStage);
        } catch (Exception e) {
            log.warn("Stage email failed for candidate {}: {}", candidate.getId(), e.getMessage());
        }
    }

    private String buildSubject(PipelineStage stage, String jobTitle) {
        return switch (stage) {
            case L1_SHORTLIST      -> "Interview Invitation – " + jobTitle;
            case L1_REJECT         -> "Application Update – " + jobTitle;
            case L2_SHORTLIST      -> "Next Round Interview – " + jobTitle;
            case L2_REJECT         -> "Application Update – " + jobTitle;
            case CLIENT_SHORTLIST  -> "Client Interview – " + jobTitle;
            case CLIENT_REJECTED   -> "Application Update – " + jobTitle;
            case FINAL_SELECT      -> "Congratulations! You've been selected – " + jobTitle;
            case OFFER_RELEASED    -> "Offer Letter – " + jobTitle;
            default                -> "Update on your application – " + jobTitle;
        };
    }

    private String buildBody(Candidate candidate, JobRequisition job, PipelineStage stage) {
        // Use the job's custom mail template if set, otherwise use a default
        if (job.getMailTemplate() != null && !job.getMailTemplate().isBlank()) {
            return job.getMailTemplate()
                    .replace("[[candidateName]]", candidate.getFullName())
                    .replace("[[jobTitle]]", job.getTitle())
                    .replace("[[stage]]", stageName(stage));
        }
        return defaultBody(candidate.getFullName(), job.getTitle(), stage,
                candidate.getRejectionReason(), candidate.getOfferAmount());
    }

    private String defaultBody(String name, String jobTitle, PipelineStage stage,
                                String rejectionReason, BigDecimal offerAmount) {
        String greeting = "Dear " + name + ",\n\n";
        String closing  = "\n\nBest regards,\nThe Hiring Team";
        return switch (stage) {
            case L1_SHORTLIST     -> greeting + "We are pleased to inform you that you have been shortlisted for the first round of interviews for the position of " + jobTitle + ". Our team will reach out shortly with the interview details." + closing;
            case L2_SHORTLIST     -> greeting + "Congratulations! You have successfully cleared the first round and are invited for the second round of interviews for " + jobTitle + ". Details will follow shortly." + closing;
            case CLIENT_SHORTLIST -> greeting + "We are excited to inform you that you have been selected to meet with our client for the " + jobTitle + " role. Please expect a call from our team with scheduling details." + closing;
            case FINAL_SELECT     -> greeting + "We are delighted to inform you that you have been selected for the position of " + jobTitle + ". We will be in touch with the next steps shortly." + (offerAmount != null ? "\n\nOffered CTC: ₹" + offerAmount.toPlainString() : "") + closing;
            case OFFER_RELEASED   -> greeting + "Please find attached your offer letter for the position of " + jobTitle + ". Kindly review and revert at the earliest." + (offerAmount != null ? "\n\nOffered CTC: ₹" + offerAmount.toPlainString() : "") + closing;
            case L1_REJECT, L2_REJECT, CLIENT_REJECTED ->
                greeting + "Thank you for your interest in the " + jobTitle + " position and for the time you invested in our interview process. After careful consideration, we regret to inform you that we will not be moving forward with your application at this time." + (rejectionReason != null && !rejectionReason.isBlank() ? "\n\nFeedback: " + rejectionReason : "") + "\n\nWe encourage you to apply for future opportunities." + closing;
            default -> greeting + "We have an update regarding your application for " + jobTitle + ". Please expect further communication from our team." + closing;
        };
    }

    private String stageName(PipelineStage stage) {
        return stage.name().replace("_", " ");
    }

    private boolean isRejectionStage(PipelineStage stage) {
        return stage == PipelineStage.L1_REJECT || stage == PipelineStage.L2_REJECT
                || stage == PipelineStage.CLIENT_REJECTED;
    }
}
