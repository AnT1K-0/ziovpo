package com.example.shop.controller.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {
}
