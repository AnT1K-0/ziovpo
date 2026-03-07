package com.example.shop.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record RenewLicenseRequest(
        @NotBlank String activationKey
) {
}