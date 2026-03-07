package com.example.shop.controller;

import com.example.shop.controller.dto.CreateLicenseRequest;
import com.example.shop.controller.dto.CreateLicenseResponse;
import com.example.shop.model.UserAccount;
import com.example.shop.repository.UserAccountRepository;
import com.example.shop.service.LicenseService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/licenses")
@RequiredArgsConstructor
public class AdminLicenseController {

    private final LicenseService licenseService;
    private final UserAccountRepository userAccountRepository;

    @PostMapping
    public ResponseEntity<?> createLicense(@Valid @RequestBody CreateLicenseRequest request,
                                           Authentication authentication) {
        try {
            String username = authentication.getName();

            UserAccount admin = userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> new EntityNotFoundException("Admin not found"));

            CreateLicenseResponse response = licenseService.createLicense(request, admin.getId());
            return ResponseEntity.status(201).body(response);

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(
                    java.util.Map.of("error", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    java.util.Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }
}