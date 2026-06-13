package com.hireflow.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RankingResult(
        @JsonProperty("fit_score") int fitScore,
        String rationale,
        @JsonProperty("skill_breakdown") Object skillBreakdownRaw,
        @JsonProperty("_input_tokens") Integer usageInputTokens,
        @JsonProperty("_output_tokens") Integer usageOutputTokens) {

    public String skillBreakdownJson() {
        return com.hireflow.ai.JsonHolder.write(skillBreakdownRaw);
    }
}
