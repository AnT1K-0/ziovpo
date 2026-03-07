package com.example.shop.controller.dto;

import java.time.OffsetDateTime;

public record CreateLicenseResponse(
        Long id,
        String code,
        Long ownerId,
        Long productId,
        Long typeId,
        Integer deviceCount,
        boolean blocked,
        OffsetDateTime firstActivationDate,
        OffsetDateTime endingDate,
        String description
) {
}