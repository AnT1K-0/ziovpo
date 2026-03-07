package com.example.shop.controller;

import com.example.shop.controller.dto.ActivateLicenseRequest;
import com.example.shop.controller.dto.CheckLicenseRequest;
import com.example.shop.controller.dto.LicenseTicketResponse;
import com.example.shop.controller.dto.RenewLicenseRequest;
import com.example.shop.model.UserAccount;
import com.example.shop.repository.UserAccountRepository;
import com.example.shop.service.LicenseService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;
    private final UserAccountRepository userAccountRepository;

    @PostMapping("/activate")
    public ResponseEntity<?> activateLicense(@Valid @RequestBody ActivateLicenseRequest request,
                                             Authentication authentication) {
        try {
            String username = authentication.getName();

            UserAccount user = userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));

            LicenseTicketResponse response = licenseService.activateLicense(request, user.getId());
            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalAccessException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }

    @PostMapping("/renew")
    public ResponseEntity<?> renewLicense(@Valid @RequestBody RenewLicenseRequest request,
                                          Authentication authentication) {
        try {
            String username = authentication.getName();

            UserAccount user = userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));

            LicenseTicketResponse response = licenseService.renewLicense(request, user.getId());
            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalAccessException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }

    @PostMapping("/check")
    public ResponseEntity<?> checkLicense(@Valid @RequestBody CheckLicenseRequest request,
                                          Authentication authentication) {
        try {
            String username = authentication.getName();

            UserAccount user = userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));

            LicenseTicketResponse response = licenseService.checkLicense(request, user.getId());
            return ResponseEntity.ok(response);

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
}