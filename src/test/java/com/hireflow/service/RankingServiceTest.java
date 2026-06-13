package com.hireflow.service;

import com.hireflow.ai.AnthropicClient;
import com.hireflow.ai.PromptBuilder;
import com.hireflow.ai.dto.RankingResult;
import com.hireflow.domain.Candidate;
import com.hireflow.domain.JobRequisition;
import com.hireflow.domain.Ranking;
import com.hireflow.repository.CandidateRepository;
import com.hireflow.repository.CandidateRepository.SemanticMatch;
import com.hireflow.repository.JobRequisitionRepository;
import com.hireflow.repository.RankingRepository;
import com.hireflow.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock JobRequisitionRepository jobRepository;
    @Mock CandidateRepository candidateRepository;
    @Mock RankingRepository rankingRepository;
    @Mock SemanticSearchService semanticSearchService;
    @Mock AnthropicClient anthropicClient;
    @Mock PromptBuilder promptBuilder;
    @Mock AuditService auditService;

    @InjectMocks RankingService rankingService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void authenticate() {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getOrganisationId()).thenReturn(orgId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void rankCandidatesForJob_blendsVectorAndLlmScores_andSortsDescending() {
        JobRequisition job = new JobRequisition();
        job.setId(jobId);
        job.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
        when(jobRepository.findByIdAndOrganisationId(jobId, orgId)).thenReturn(Optional.of(job));

        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        when(semanticSearchService.searchByVector(any(), eq(orgId), eq(jobId), anyInt()))
                .thenReturn(List.of(match(c1, 0.90), match(c2, 0.80)));

        Candidate cand1 = candidate(c1, "Ada Lovelace");
        Candidate cand2 = candidate(c2, "Alan Turing");
        when(candidateRepository.findByIdAndOrganisationId(c1, orgId)).thenReturn(Optional.of(cand1));
        when(candidateRepository.findByIdAndOrganisationId(c2, orgId)).thenReturn(Optional.of(cand2));

        when(anthropicClient.modelId()).thenReturn("claude-sonnet-4-6");
        when(anthropicClient.completeForJson(any(), any(), eq(RankingResult.class)))
                .thenReturn(new RankingResult(80, "strong", null, 100, 50))
                .thenReturn(new RankingResult(40, "weak", null, 100, 50));

        when(rankingRepository.save(any(Ranking.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Ranking> result = rankingService.rankCandidatesForJob(jobId, 25);

        // c1: 0.30*90 + 0.70*80 = 27 + 56 = 83.0000
        // c2: 0.30*80 + 0.70*40 = 24 + 28 = 52.0000
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCandidate().getId()).isEqualTo(c1);
        assertThat(result.get(0).getScore()).isEqualByComparingTo(new BigDecimal("83.0000"));
        assertThat(result.get(1).getScore()).isEqualByComparingTo(new BigDecimal("52.0000"));
        verify(rankingRepository).deleteByJobId(jobId);
        verify(auditService, times(2)).recordSuccess(any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void rankCandidatesForJob_throwsWhenJobHasNoEmbedding() {
        JobRequisition job = new JobRequisition();
        job.setId(jobId);
        job.setEmbedding(null);
        when(jobRepository.findByIdAndOrganisationId(jobId, orgId)).thenReturn(Optional.of(job));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.hireflow.exception.AiProviderException.class,
                () -> rankingService.rankCandidatesForJob(jobId, 25));
        verifyNoInteractions(anthropicClient);
    }

    private SemanticMatch match(UUID id, double sim) {
        return new SemanticMatch() {
            public UUID getCandidateId() { return id; }
            public String getFullName() { return "n"; }
            public double getSimilarity() { return sim; }
        };
    }

    private Candidate candidate(UUID id, String name) {
        Candidate c = new Candidate();
        c.setId(id);
        c.setFullName(name);
        c.setResumeText("resume of " + name);
        return c;
    }
}
