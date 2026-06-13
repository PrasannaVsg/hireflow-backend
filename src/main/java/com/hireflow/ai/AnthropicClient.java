package com.hireflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.ai.dto.AnthropicRequest;
import com.hireflow.ai.dto.AnthropicResponse;
import com.hireflow.exception.AiProviderException;
import com.hireflow.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class AnthropicClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Bucket anthropicBucket;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String anthropicVersion;
    private final int maxTokens;
    private final int maxRetries;

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
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.anthropicVersion = anthropicVersion;
        this.maxTokens = maxTokens;
        this.maxRetries = maxRetries;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_2)
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

    public AnthropicResponse send(String system, String user, double temperature) {
        if (!anthropicBucket.tryConsume(1)) {
            throw new RateLimitExceededException("Anthropic request budget exhausted; try again shortly");
        }

        AnthropicRequest body = new AnthropicRequest(
                model, maxTokens, system, temperature,
                List.of(new AnthropicRequest.Message("user", user)));

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new AiProviderException("Could not serialize Anthropic request", e);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("x-api-key", apiKey)
                .header("anthropic-version", anthropicVersion)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        int attempt = 0;
        long backoffMs = 500;
        AiProviderException lastError = null;

        while (attempt < maxRetries) {
            attempt++;
            try {
                HttpResponse<String> resp = httpClient.send(
                        httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = resp.statusCode();

                if (status >= 200 && status < 300) {
                    return objectMapper.readValue(resp.body(), AnthropicResponse.class);
                }
                if (status == 429 || status >= 500) {
                    long retryAfter = parseRetryAfter(resp).orElse(backoffMs);
                    log.warn("Anthropic {} (attempt {}/{}), backing off {}ms",
                            status, attempt, maxRetries, retryAfter);
                    lastError = new AiProviderException("Anthropic transient error " + status + ": " + resp.body());
                    sleep(retryAfter);
                    backoffMs = Math.min(backoffMs * 2, 8000);
                    continue;
                }
                throw new AiProviderException("Anthropic error " + status + ": " + resp.body());

            } catch (java.io.IOException e) {
                lastError = new AiProviderException("Anthropic I/O error: " + e.getMessage(), e);
                log.warn("Anthropic I/O error (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 8000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("Anthropic call interrupted", e);
            }
        }
        throw lastError != null ? lastError
                : new AiProviderException("Anthropic call failed after " + maxRetries + " attempts");
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

    private Optional<Long> parseRetryAfter(HttpResponse<String> resp) {
        return resp.headers().firstValue("retry-after")
                .map(v -> {
                    try { return Long.parseLong(v.trim()) * 1000; }
                    catch (NumberFormatException e) { return null; }
                });
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
