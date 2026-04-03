package com.example.shop.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SignatureAuditResponse(
        Long auditId,
        UUID signatureId,
        String changedBy,
        OffsetDateTime changedAt,
        String fieldsChanged,
        String description
) {
}