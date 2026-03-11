package com.example.shop.controller.dto;

import java.time.OffsetDateTime;

public record Ticket(
        OffsetDateTime serverDate,
        Long ttlSeconds,
        OffsetDateTime licenseActivationDate,
        OffsetDateTime licenseExpirationDate,
        Long userId,
        Long deviceId,
        boolean blocked
) {
}