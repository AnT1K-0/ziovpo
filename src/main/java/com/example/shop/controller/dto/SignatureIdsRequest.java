package com.example.shop.controller.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record SignatureIdsRequest(
        @NotEmpty List<UUID> ids
) {
}