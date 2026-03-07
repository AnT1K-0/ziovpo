package com.example.shop.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateLicenseRequest(
        @NotBlank String activationKey,
        @NotBlank @Size(max = 255) String deviceName,
        @NotBlank @Size(max = 255) String deviceMac
) {
}