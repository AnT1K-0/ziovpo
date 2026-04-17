package com.example.shop.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioStorageProperties minioStorageProperties;

    public void upload(String objectKey, MultipartFile file) {
        Objects.requireNonNull(objectKey, "objectKey must not be null");
        Objects.requireNonNull(file, "file must not be null");

        try {
            assertBucketExists();

            try (InputStream stream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioStorageProperties.getBucket())
                                .object(objectKey)
                                .stream(stream, file.getSize(), -1)
                                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                                .build()
                );
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload file to MinIO", e);
        }
    }

    public String buildPresignedGetUrl(String objectKey) {
        Objects.requireNonNull(objectKey, "objectKey must not be null");

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioStorageProperties.getBucket())
                            .object(objectKey)
                            .expiry(minioStorageProperties.getPresignedUrlExpirySeconds())
                            .build()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate pre-signed URL", e);
        }
    }

    public void deleteIfExists(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioStorageProperties.getBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException e) {
            // Ignore object-not-found to make compensation idempotent.
            if (!"NoSuchKey".equals(e.errorResponse().code())) {
                throw new IllegalStateException("Failed to delete object from MinIO", e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete object from MinIO", e);
        }
    }

    private void assertBucketExists() throws Exception {
        String bucket = minioStorageProperties.getBucket();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            throw new IllegalStateException("Configured MinIO bucket does not exist: " + bucket);
        }
    }
}
