package com.example.shop.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RoleTestController {

    @GetMapping("/api/user/me")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of(
                "message", "Access granted for USER or ADMIN",
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities()
        );
    }

    @GetMapping("/api/admin/panel")
    public Map<String, Object> admin(Authentication authentication) {
        return Map.of(
                "message", "Access granted for ADMIN only",
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities()
        );
    }
}