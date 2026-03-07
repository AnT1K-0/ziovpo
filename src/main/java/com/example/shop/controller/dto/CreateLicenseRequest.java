package com.example.shop.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLicenseRequest(
        @NotNull Long productId,
        @NotNull Long typeId,
        @NotNull Long ownerId,
        @NotNull @Min(1) Integer deviceCount,
        @Size(max = 500) String description
) {
}