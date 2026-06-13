package com.hireflow.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Bucket4jConfig {

    @Bean
    public Bucket anthropicBucket(
            @Value("${hireflow.anthropic.rate-limit-per-min:50}") int perMinute) {
        Bandwidth limit = Bandwidth.classic(perMinute,
                Refill.intervally(perMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
