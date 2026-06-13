package com.hireflow.async;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JobStatusRegistry {

    private static final String KEY_PREFIX = "resume-job:";
    private static final Duration TTL = Duration.ofHours(6);

    private final StringRedisTemplate redis;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public enum State { QUEUED, RUNNING, COMPLETED, FAILED }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobStatus(
            String jobId,
            State state,
            int total,
            int processed,
            int succeeded,
            int failed,
            String lastError,
            Instant startedAt,
            Instant updatedAt) { }

    public String create(int total) {
        String jobId = UUID.randomUUID().toString();
        JobStatus status = new JobStatus(jobId, State.QUEUED, total, 0, 0, 0,
                null, Instant.now(), Instant.now());
        save(status);
        return jobId;
    }

    public JobStatus get(String jobId) {
        String json = redis.opsForValue().get(KEY_PREFIX + jobId);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, JobStatus.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void update(JobStatus status) {
        save(new JobStatus(status.jobId(), status.state(), status.total(), status.processed(),
                status.succeeded(), status.failed(), status.lastError(),
                status.startedAt(), Instant.now()));
    }

    private void save(JobStatus status) {
        try {
            redis.opsForValue().set(KEY_PREFIX + status.jobId(),
                    objectMapper.writeValueAsString(status), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Could not persist job status", e);
        }
    }
}
