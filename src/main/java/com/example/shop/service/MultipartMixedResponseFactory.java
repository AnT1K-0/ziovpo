package com.example.shop.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MultipartMixedResponseFactory {

    private static final String CONTENT_TRANSFER_ENCODING_BINARY = "binary";
    private static final String CONTENT_TRANSFER_ENCODING_HEADER = "Content-Transfer-Encoding";
    private static final String MANIFEST_PART_NAME = "manifest";
    private static final String DATA_PART_NAME = "data";

    private final BinaryExportProperties binaryExportProperties;

    public ResponseEntity<?> create(byte[] manifestBytes, byte[] dataBytes) {
        Objects.requireNonNull(manifestBytes, "manifestBytes must not be null");
        Objects.requireNonNull(dataBytes, "dataBytes must not be null");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(MANIFEST_PART_NAME, createPart(binaryExportProperties.getManifestFilename(), manifestBytes));
        body.add(DATA_PART_NAME, createPart(binaryExportProperties.getDataFilename(), dataBytes));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MediaType.MULTIPART_MIXED_VALUE))
                .body(body);
    }

    private HttpEntity<ByteArrayResource> createPart(String filename, byte[] payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.US_ASCII)
                        .build()
        );
        headers.set(CONTENT_TRANSFER_ENCODING_HEADER, CONTENT_TRANSFER_ENCODING_BINARY);
        headers.setContentLength(payload.length);

        ByteArrayResource resource = new ByteArrayResource(payload) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        return new HttpEntity<>(resource, headers);
    }
}
