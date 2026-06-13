package com.hireflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.domain.enums.AiOperation;
import com.hireflow.exception.AiProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class EmbeddingService {

    private static final String VOYAGE_URL = "https://api.voyageai.com/v1/embeddings";
    private static final String MODEL      = "voyage-2";
    private static final int    DIMENSIONS = 1024;

    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient   httpClient;
    private final String       apiKey;

    public EmbeddingService(AuditService auditService,
                            ObjectMapper objectMapper,
                            @Value("${hireflow.voyage.api-key}") String apiKey) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.apiKey       = apiKey;
        this.httpClient   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public float[] embed(String text, UUID orgId, UUID actorId, UUID targetId) {
        long start = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("input", List.of(truncate(text)), "model", MODEL));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VOYAGE_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AiProviderException("Embedding API error: "
                        + response.statusCode() + " " + response.body());
            }

            JsonNode embeddingNode = objectMapper.readTree(response.body())
                    .path("data").get(0).path("embedding");

            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }

            auditService.recordSuccess(orgId, actorId, AiOperation.EMBEDDING,
                    MODEL, null, null,
                    System.currentTimeMillis() - start, targetId, text);
            return vector;

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            auditService.recordFailure(orgId, actorId, AiOperation.EMBEDDING,
                    MODEL, System.currentTimeMillis() - start, targetId, e.getMessage());
            throw new AiProviderException("Embedding failed: " + e.getMessage(), e);
        }
    }

    public String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private String truncate(String text) {
        return text.length() > 30_000 ? text.substring(0, 30_000) : text;
    }
}
