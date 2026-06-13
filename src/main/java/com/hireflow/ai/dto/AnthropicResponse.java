package com.hireflow.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicResponse(
        String id,
        String model,
        String role,
        @JsonProperty("stop_reason") String stopReason,
        List<ContentBlock> content,
        Usage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String text) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(@JsonProperty("input_tokens") int inputTokens,
                        @JsonProperty("output_tokens") int outputTokens) { }

    public String text() {
        if (content == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : content) {
            if ("text".equals(b.type()) && b.text() != null) sb.append(b.text());
        }
        return sb.toString();
    }
}
