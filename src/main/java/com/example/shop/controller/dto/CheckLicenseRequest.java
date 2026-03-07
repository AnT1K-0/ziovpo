package com.example.shop.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckLicenseRequest(
        @NotBlank @Size(max = 255) String deviceMac,
        @NotNull Long productId
) {
}