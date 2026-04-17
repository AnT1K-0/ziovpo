package com.example.shop.controller;

import com.example.shop.controller.dto.SignatureIdsRequest;
import com.example.shop.service.BinarySignatureExportService;
import com.example.shop.service.MultipartMixedResponseFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/binary/signatures")
@RequiredArgsConstructor
public class BinarySignatureController {

    private final BinarySignatureExportService binarySignatureExportService;
    private final MultipartMixedResponseFactory multipartMixedResponseFactory;

    @GetMapping("/full")
    public ResponseEntity<?> getFull() {
        try {
            BinarySignatureExportService.BinaryPackage binaryPackage = binarySignatureExportService.exportFull();
            return multipartMixedResponseFactory.create(binaryPackage.manifestBytes(), binaryPackage.dataBytes());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }

    @GetMapping("/increment")
    public ResponseEntity<?> getIncrement(@RequestParam("since") String since) {
        try {
            OffsetDateTime parsedSince = OffsetDateTime.parse(since);
            BinarySignatureExportService.BinaryPackage binaryPackage =
                    binarySignatureExportService.exportIncrement(parsedSince);
            return multipartMixedResponseFactory.create(binaryPackage.manifestBytes(), binaryPackage.dataBytes());
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "error", "Internal error",
                            "details", e.getMessage()
                    )
            );
        }
    }

    @PostMapping("/by-ids")
    public ResponseEntity<?> getByIds(@Valid @RequestBody SignatureIdsRequest request) {
        try {
            BinarySignatureExportService.BinaryPackage binaryPackage =
                    binarySignatureExportService.exportByIds(request.ids());
            return multipartMixedResponseFactory.create(binaryPackage.manifestBytes(), binaryPackage.dataBytes());
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
