package com.example.shop.controller;

import com.example.shop.signature.SignatureKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.X509Certificate;
import java.util.Base64;

@RestController
@RequiredArgsConstructor
public class SignatureController {

    private final SignatureKeyProvider keyProvider;

    @GetMapping("/api/signature/certificate")
    public ResponseEntity<String> getCertificate() throws Exception {
        X509Certificate certificate = keyProvider.getCertificate();

        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----";

        return ResponseEntity.ok(pem);
    }
}