package com.hireflow.service;

import com.hireflow.domain.Candidate;
import com.hireflow.domain.enums.PipelineStage;
import com.hireflow.exception.ResourceNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private static final Map<PipelineStage, Set<PipelineStage>> TRANSITIONS = new EnumMap<>(PipelineStage.class);
    static {
        TRANSITIONS.put(PipelineStage.SOURCED,    Set.of(PipelineStage.SCREENING, PipelineStage.REJECTED));
        TRANSITIONS.put(PipelineStage.SCREENING,  Set.of(PipelineStage.INTERVIEW, PipelineStage.REJECTED, PipelineStage.SOURCED));
        TRANSITIONS.put(PipelineStage.INTERVIEW,  Set.of(PipelineStage.OFFER, PipelineStage.REJECTED, PipelineStage.SCREENING));
        TRANSITIONS.put(PipelineStage.OFFER,      Set.of(PipelineStage.HIRED, PipelineStage.REJECTED, PipelineStage.INTERVIEW));
        TRANSITIONS.put(PipelineStage.HIRED,      Set.of());
        TRANSITIONS.put(PipelineStage.REJECTED,   Set.of(PipelineStage.SOURCED));
    }

    private final CandidateRepository candidateRepository;

    @Transactional
    public Candidate moveStage(UUID candidateId, PipelineStage target) {
        UUID orgId = SecurityUtils.currentOrgId();
        Candidate candidate = candidateRepository.findByIdAndOrganisationId(candidateId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId));

        PipelineStage current = candidate.getPipelineStage();
        if (current == target) {
            return candidate;
        }
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new ValidationException(
                    "Illegal pipeline transition: " + current + " -> " + target);
        }
        candidate.setPipelineStage(target);
        log.info("Candidate {} moved {} -> {}", candidateId, current, target);
        return candidate;
    }
}
