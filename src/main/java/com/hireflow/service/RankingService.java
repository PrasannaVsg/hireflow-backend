package com.hireflow.service;

import com.hireflow.ai.AnthropicClient;
import com.hireflow.ai.PromptBuilder;
import com.hireflow.ai.dto.RankingResult;
import com.hireflow.domain.Candidate;
import com.hireflow.domain.JobRequisition;
import com.hireflow.domain.Ranking;
import com.hireflow.domain.enums.AiOperation;
import com.hireflow.exception.AiProviderException;
import com.hireflow.exception.ResourceNotFoundException;
import com.hireflow.repository.CandidateRepository;
import com.hireflow.repository.CandidateRepository.SemanticMatch;
import com.hireflow.repository.JobRequisitionRepository;
import com.hireflow.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final BigDecimal VECTOR_WEIGHT = new BigDecimal("0.30");
    private static final BigDecimal LLM_WEIGHT    = new BigDecimal("0.70");

    private final JobRequisitionRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final RankingRepository rankingRepository;
    private final SemanticSearchService semanticSearchService;
    private final AnthropicClient anthropicClient;
    private final PromptBuilder promptBuilder;
    private final AuditService auditService;

    @Transactional
    public List<Ranking> rankCandidatesForJob(UUID jobId, int shortlistSize) {
        UUID orgId = SecurityUtils.currentOrgId();
        UUID actorId = SecurityUtils.currentUserId();

        JobRequisition job = jobRepository.findByIdAndOrganisationId(jobId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRequisition", jobId));

        Boolean hasEmbedding = jobRepository.hasEmbedding(jobId);
        if (hasEmbedding == null || !hasEmbedding) {
            throw new AiProviderException("Job has no embedding; (re)index the job before ranking.");
        }

        List<SemanticMatch> shortlist = semanticSearchService
                .searchByVector(jobId, orgId, shortlistSize);

        List<Ranking> results = new ArrayList<>(shortlist.size());
        for (SemanticMatch match : shortlist) {
            Candidate candidate = candidateRepository
                    .findByIdAndOrganisationId(match.getCandidateId(), orgId)
                    .orElse(null);
            if (candidate == null || candidate.getResumeText() == null) {
                continue;
            }
            Ranking ranking = scoreOne(job, candidate, match.getSimilarity(), orgId, actorId);
            // Upsert: update existing row or insert new one
            rankingRepository.findByJobIdAndCandidateId(jobId, candidate.getId())
                    .ifPresentOrElse(existing -> {
                        existing.setScore(ranking.getScore());
                        existing.setVectorSimilarity(ranking.getVectorSimilarity());
                        existing.setLlmScore(ranking.getLlmScore());
                        existing.setRationale(ranking.getRationale());
                        existing.setSkillBreakdown(ranking.getSkillBreakdown());
                        existing.setModel(ranking.getModel());
                        existing.setClaudeError(ranking.getClaudeError());
                        results.add(rankingRepository.save(existing));
                    }, () -> results.add(rankingRepository.save(ranking)));
        }

        results.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        log.info("Ranked {} candidates for job {}", results.size(), jobId);
        return results;
    }

    @Transactional(readOnly = true)
    public Page<Ranking> listRankings(UUID jobId, Pageable pageable) {
        return rankingRepository.findByJobIdOrderByScoreDesc(jobId, pageable);
    }

    private Ranking scoreOne(JobRequisition job, Candidate candidate,
                             double similarity, UUID orgId, UUID actorId) {
        long start = System.currentTimeMillis();
        BigDecimal vectorSimilarity = BigDecimal.valueOf(similarity).setScale(8, RoundingMode.HALF_UP);
        try {
            String systemPrompt = promptBuilder.buildRankingSystemPrompt();
            String userPrompt = promptBuilder.buildRankingUserPrompt(job, candidate);

            RankingResult parsed = anthropicClient.completeForJson(
                    systemPrompt, userPrompt, RankingResult.class);

            BigDecimal vectorComponent = BigDecimal.valueOf(similarity * 100)
                    .multiply(VECTOR_WEIGHT);
            BigDecimal llmComponent = BigDecimal.valueOf(parsed.fitScore())
                    .multiply(LLM_WEIGHT);
            BigDecimal blended = vectorComponent.add(llmComponent)
                    .setScale(4, RoundingMode.HALF_UP);

            auditService.recordSuccess(orgId, actorId, AiOperation.RANKING,
                    anthropicClient.modelId(), parsed.usageInputTokens(), parsed.usageOutputTokens(),
                    System.currentTimeMillis() - start, candidate.getId(), userPrompt);

            return Ranking.builder()
                    .job(job)
                    .candidate(candidate)
                    .score(blended)
                    .vectorSimilarity(vectorSimilarity)
                    .llmScore(parsed.fitScore())
                    .rationale(parsed.rationale())
                    .skillBreakdown(parsed.skillBreakdownJson())
                    .model(anthropicClient.modelId())
                    .build();

        } catch (AiProviderException e) {
            auditService.recordFailure(orgId, actorId, AiOperation.RANKING,
                    anthropicClient.modelId(), System.currentTimeMillis() - start,
                    candidate.getId(), e.getMessage());
            log.error("Claude ranking failed for candidate {} [{}]: {}", candidate.getFullName(), candidate.getId(), e.getMessage());
            return Ranking.builder()
                    .job(job)
                    .candidate(candidate)
                    .score(BigDecimal.valueOf(similarity * 100).multiply(VECTOR_WEIGHT).setScale(4, RoundingMode.HALF_UP))
                    .vectorSimilarity(vectorSimilarity)
                    .llmScore(null)
                    .rationale(null)
                    .skillBreakdown(null)
                    .model(anthropicClient.modelId())
                    .claudeError(e.getMessage())
                    .build();
        }
    }
}
