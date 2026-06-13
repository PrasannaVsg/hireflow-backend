package com.hireflow.service;

import com.hireflow.exception.StorageException;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${hireflow.storage.bucket:hireflow-resumes}")
    private String bucket;

    @Value("${hireflow.storage.presign-ttl-seconds:600}")
    private int presignTtlSeconds;

    public String upload(UUID orgId, MultipartFile file) {
        String key = orgId + "/" + UUID.randomUUID() + "/" + file.getOriginalFilename();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.debug("Uploaded {} -> {}", file.getOriginalFilename(), key);
            return key;
        } catch (Exception e) {
            throw new StorageException("Upload failed for " + file.getOriginalFilename(), e);
        }
    }

    public InputStream openStream(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Cannot open stream for " + objectKey, e);
        }
    }

    public String presignDownload(String objectKey, int ttlSeconds) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry(ttlSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Presign failed for " + objectKey, e);
        }
    }
}
