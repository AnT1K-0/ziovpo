package com.example.shop.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String username,
        @NotBlank @Size(min = 8, max = 64) String password
) {}
