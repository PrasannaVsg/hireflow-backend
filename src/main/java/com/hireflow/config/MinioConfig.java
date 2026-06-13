package com.hireflow.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${hireflow.storage.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${hireflow.storage.access-key:minioadmin}")
    private String accessKey;

    @Value("${hireflow.storage.secret-key:minioadmin}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
