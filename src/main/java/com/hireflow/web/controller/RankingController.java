package com.hireflow.web.controller;

import com.hireflow.domain.Ranking;
import com.hireflow.service.RankingService;
import com.hireflow.web.dto.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    public record RankingResponse(UUID candidateId, String candidateName, BigDecimal score,
                                  Integer llmScore, BigDecimal vectorSimilarity,
                                  String rationale, String skillBreakdown) { }

    @PostMapping("/run")
    public List<RankingResponse> run(@PathVariable UUID jobId,
                                     @RequestParam(defaultValue = "25") @Min(1) @Max(200) int shortlistSize) {
        return rankingService.rankCandidatesForJob(jobId, shortlistSize).stream()
                .map(r -> new RankingResponse(
                        r.getCandidate().getId(), r.getCandidate().getFullName(), r.getScore(),
                        r.getLlmScore(), r.getVectorSimilarity(), r.getRationale(), r.getSkillBreakdown()))
                .toList();
    }

    @GetMapping
    public PageResponse<RankingResponse> list(@PathVariable UUID jobId, Pageable pageable) {
        return PageResponse.of(rankingService.listRankings(jobId, pageable)
                .map(r -> new RankingResponse(
                        r.getCandidate().getId(), r.getCandidate().getFullName(), r.getScore(),
                        r.getLlmScore(), r.getVectorSimilarity(), r.getRationale(), r.getSkillBreakdown())));
    }
}
