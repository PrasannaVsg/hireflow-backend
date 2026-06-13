package com.hireflow.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OutreachResult(
        String subject,
        String body,
        @JsonProperty("_input_tokens") Integer usageInputTokens,
        @JsonProperty("_output_tokens") Integer usageOutputTokens) { }
