package com.example.shop.config;

import com.example.shop.service.MinioStorageProperties;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioStorageProperties minioStorageProperties) {
        return MinioClient.builder()
                .endpoint(minioStorageProperties.getEndpoint())
                .credentials(minioStorageProperties.getAccessKey(), minioStorageProperties.getSecretKey())
                .build();
    }
}
