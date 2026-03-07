package com.example.shop.controller.dto;

import java.time.OffsetDateTime;

public record LicenseTicketResponse(
        Long licenseId,
        String code,
        String productName,
        String licenseType,
        Long userId,
        Long ownerId,
        boolean blocked,
        Integer deviceCount,
        OffsetDateTime firstActivationDate,
        OffsetDateTime endingDate,
        String description
) {
}