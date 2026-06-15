package com.hireflow.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Sends LLM call events to the iManner observability platform in the background.
 * All failures are silently swallowed — this must never affect application flow.
 */
@Slf4j
@Component
public class IMannerClient {

    private static final int QUEUE_CAPACITY = 1000;
    private static final int BATCH_SIZE     = 20;
    private static final int FLUSH_INTERVAL_MS = 2000;

    private final IMannerProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final BlockingQueue<Map<String, Object>> queue;
    private final ExecutorService flusher;

    public IMannerClient(IMannerProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        if (props.isEnabled()) {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            this.flusher = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "imanner-flusher");
                t.setDaemon(true);
                return t;
            });
            flusher.submit(this::flushLoop);
            log.info("iManner observability enabled → {}", props.getApiEndpoint());
        } else {
            this.httpClient = null;
            this.flusher = null;
            log.info("iManner observability disabled (OBS_ENABLED=false)");
        }
    }

    public void record(String modelProvider, String modelName,
                       int inputTokens, int outputTokens,
                       long durationMs, boolean success) {
        if (!props.isEnabled()) return;
        try {
            if (props.getSamplingRate() < 1.0 && Math.random() > props.getSamplingRate()) return;

            Map<String, Object> event = Map.ofEntries(
                Map.entry("event_type",      "generation"),
                Map.entry("application_id",  props.getApplicationId()),
                Map.entry("application_name", props.getApplicationName()),
                Map.entry("org_id",          props.getOrgId()),
                Map.entry("project_id",      props.getProjectId()),
                Map.entry("environment",     props.getEnvironment()),
                Map.entry("model_provider",  modelProvider),
                Map.entry("model_name",      modelName),
                Map.entry("input_tokens",    inputTokens),
                Map.entry("output_tokens",   outputTokens),
                Map.entry("duration_ms",     durationMs),
                Map.entry("trace_id",        UUID.randomUUID().toString()),
                Map.entry("status",          success ? "completed" : "error")
            );
            // drop silently if queue is full rather than blocking
            queue.offer(event);
        } catch (Exception e) {
            log.debug("iManner enqueue error (ignored): {}", e.getMessage());
        }
    }

    private void flushLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(FLUSH_INTERVAL_MS);
                flush();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("iManner flush error (ignored): {}", e.getMessage());
            }
        }
    }

    private void flush() {
        if (queue.isEmpty()) return;
        try {
            List<Map<String, Object>> batch = new java.util.ArrayList<>(BATCH_SIZE);
            queue.drainTo(batch, BATCH_SIZE);
            if (batch.isEmpty()) return;

            String body = objectMapper.writeValueAsString(Map.of("events", batch));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getApiEndpoint() + "/v1/events"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                log.debug("iManner: flushed {} events", batch.size());
            } else if (status >= 500) {
                // re-queue on 5xx for one retry
                batch.forEach(e -> queue.offer(e));
                log.debug("iManner: server error {} — events re-queued", status);
            }
            // 4xx: drop silently
        } catch (Exception e) {
            log.debug("iManner: flush failed (ignored): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (!props.isEnabled() || flusher == null) return;
        try {
            flush(); // final flush on shutdown
            flusher.shutdownNow();
            flusher.awaitTermination(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("iManner: shutdown error (ignored): {}", e.getMessage());
        }
    }
}
