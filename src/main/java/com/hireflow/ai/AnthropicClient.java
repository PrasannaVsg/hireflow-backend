package com.hireflow.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.ai.dto.AnthropicResponse;
import com.hireflow.exception.AiProviderException;
import com.hireflow.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AnthropicClient {

    private final com.anthropic.client.AnthropicClient sdkClient;
    private final ObjectMapper objectMapper;
    private final Bucket anthropicBucket;
    private final String model;
    private final long maxTokens;

    public AnthropicClient(ObjectMapper objectMapper,
                           @Qualifier("anthropicBucket") Bucket anthropicBucket,
                           @Value("${hireflow.anthropic.api-key}") String apiKey,
                           @Value("${hireflow.anthropic.model:claude-sonnet-4-6}") String model,
                           @Value("${hireflow.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
                           @Value("${hireflow.anthropic.version:2023-06-01}") String anthropicVersion,
                           @Value("${hireflow.anthropic.max-tokens:1024}") int maxTokens,
                           @Value("${hireflow.anthropic.max-retries:3}") int maxRetries,
                           @Value("${hireflow.anthropic.connect-timeout-ms:5000}") long connectTimeoutMs) {
        this.objectMapper = objectMapper;
        this.anthropicBucket = anthropicBucket;
        this.model = model;
        this.maxTokens = maxTokens;
        this.sdkClient = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .maxRetries(maxRetries)
                .build();
    }

    public String modelId() { return model; }

    public <T> T completeForJson(String system, String user, Class<T> type) {
        AnthropicResponse response = send(system, user, 0.0);
        String raw = extractJson(response.text());
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isObject() && response.usage() != null) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                        .put("_input_tokens", response.usage().inputTokens())
                        .put("_output_tokens", response.usage().outputTokens());
            }
            return objectMapper.treeToValue(node, type);
        } catch (Exception e) {
            log.warn("Failed to parse Claude JSON response: {}", raw);
            throw new AiProviderException("Malformed AI response: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("deprecation")
    public AnthropicResponse send(String system, String user, double temperature) {
        if (!anthropicBucket.tryConsume(1)) {
            throw new RateLimitExceededException("Anthropic request budget exhausted; try again shortly");
        }

        try {
            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .model(Model.of(model))
                    .maxTokens(maxTokens)
                    .system(system)
                    .addUserMessage(user);

            if (temperature > 0.0) {
                paramsBuilder.temperature(temperature);
            }

            Message message = sdkClient.messages().create(paramsBuilder.build());
            return toResponse(message);

        } catch (com.anthropic.errors.AnthropicServiceException e) {
            throw new AiProviderException("Anthropic API error " + e.statusCode() + ": " + e.getMessage(), e);
        } catch (AiProviderException | RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Anthropic call failed: " + e.getMessage(), e);
        }
    }

    private AnthropicResponse toResponse(Message message) {
        // Use fully qualified SDK ContentBlock to avoid collision with our DTO's inner record
        List<AnthropicResponse.ContentBlock> blocks = new ArrayList<>();
        for (com.anthropic.models.messages.ContentBlock block : message.content()) {
            if (block.isText()) {
                blocks.add(new AnthropicResponse.ContentBlock("text", block.asText().text()));
            }
        }

        AnthropicResponse.Usage usage = new AnthropicResponse.Usage(
                (int) message.usage().inputTokens(),
                (int) message.usage().outputTokens());

        String stopReason = message.stopReason()
                .map(sr -> sr.toString().toLowerCase())
                .orElse(null);

        return new AnthropicResponse(
                message.id(),
                message.model().toString(),
                message._role().toString(),
                stopReason,
                blocks,
                usage);
    }

    private String extractJson(String text) {
        if (text == null) throw new AiProviderException("Empty AI response");
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replaceAll("(?s)```(json)?", "").trim();
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new AiProviderException("No JSON object in AI response");
        }
        return t.substring(start, end + 1);
    }
}
