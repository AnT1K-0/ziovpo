package com.example.shop.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateSignatureRequest(
        @NotBlank String threatName,
        @NotBlank String firstBytesHex,
        @NotBlank String remainderHashHex,
        @Min(0) Long remainderLength,
        @NotBlank String fileType,
        @Min(0) Long offsetStart,
        @Min(0) Long offsetEnd
) {
}