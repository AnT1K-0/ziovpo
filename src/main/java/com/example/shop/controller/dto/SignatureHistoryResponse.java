package com.example.shop.controller.dto;

import com.example.shop.model.SignatureStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SignatureHistoryResponse(
        Long historyId,
        UUID signatureId,
        OffsetDateTime versionCreatedAt,
        String threatName,
        String firstBytesHex,
        String remainderHashHex,
        Long remainderLength,
        String fileType,
        Long offsetStart,
        Long offsetEnd,
        OffsetDateTime updatedAt,
        SignatureStatus status,
        String digitalSignatureBase64
) {
}