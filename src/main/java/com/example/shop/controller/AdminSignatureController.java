package com.example.shop.controller;

import com.example.shop.controller.dto.CreateSignatureRequest;
import com.example.shop.controller.dto.UpdateSignatureRequest;
import com.example.shop.model.UserAccount;
import com.example.shop.repository.UserAccountRepository;
import com.example.shop.service.MalwareSignatureService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/signatures")
@RequiredArgsConstructor
public class AdminSignatureController {

    private final MalwareSignatureService malwareSignatureService;
    private final UserAccountRepository userAccountRepository;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateSignatureRequest request,
                                    Authentication authentication) {
        try {
            String username = authentication.getName();

            UserAccount admin = userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> new EntityNotFoundException("Admin not found"));

            return ResponseEntity.status(201)
                    .body(malwareSignatureService.create(request, admin.getUsername()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateSignatureRequest request,
                                    Authentication authentication) {
        try {
            String username = authentication.getName();

            UserAccount admin = userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> new EntityNotFoundException("Admin not found"));

            return ResponseEntity.ok(malwareSignatureService.update(id, request, admin.getUsername()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id,
                                    Authentication authentication) {
        try {
            String username = authentication.getName();

            UserAccount admin = userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> new EntityNotFoundException("Admin not found"));

            malwareSignatureService.delete(id, admin.getUsername());
            return ResponseEntity.noContent().build();

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> getHistory(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(malwareSignatureService.getHistory(id));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<?> getAudit(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(malwareSignatureService.getAudit(id));
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