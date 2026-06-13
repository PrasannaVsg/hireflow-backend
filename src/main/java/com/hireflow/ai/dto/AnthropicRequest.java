package com.hireflow.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnthropicRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        Double temperature,
        List<Message> messages) {

    public record Message(String role, String content) { }
}
