package com.example.shop.controller.dto;

import java.util.UUID;

public record SignatureFilePresignedUrlResponse(
        UUID signatureId,
        String sourceFileName,
        String sourceFileObjectKey,
        String presignedUrl,
        int expiresInSeconds
) {
}
