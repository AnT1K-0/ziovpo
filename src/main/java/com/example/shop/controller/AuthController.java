package com.example.shop.controller;

import com.example.shop.controller.dto.AuthResponse;
import com.example.shop.controller.dto.LoginRequest;
import com.example.shop.controller.dto.RefreshRequest;
import com.example.shop.controller.dto.RegisterRequest;
import com.example.shop.service.AuthService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest servletRequest) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ip = servletRequest.getRemoteAddr();
        try {
            AuthResponse response = authService.login(req, userAgent, ip);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException | IllegalArgumentException e) {
            // Неверный логин/пароль → честный 401
            return ResponseEntity.status(401).body(
                    Map.of("error", "Invalid username or password")
            );
        } catch (Exception e) {
            // Всё остальное → 500, чтобы не превращалось магически в 403
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req,
                                     HttpServletRequest servletRequest) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ip = servletRequest.getRemoteAddr();
        try {
            AuthResponse response = authService.refresh(req, userAgent, ip);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException | EntityNotFoundException e) {
            // Неверный/просроченный refresh-токен, неактивная/не найденная сессия → 401
            return ResponseEntity.status(401).body(
                    Map.of("error", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }
}
