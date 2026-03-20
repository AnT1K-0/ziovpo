package com.example.shop.signature;

import com.example.shop.controller.dto.Ticket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SigningServiceTest {

    @Autowired
    private SigningService signingService;

    @Autowired
    private JsonCanonicalizationService canonicalizationService;

    @Autowired
    private SignatureKeyProvider keyProvider;

    @Test
    void shouldSignAndVerifyTicket() throws Exception {
        Ticket ticket = new Ticket(
                OffsetDateTime.parse("2026-03-11T10:00:00+03:00"),
                300L,
                OffsetDateTime.parse("2026-03-11T10:00:00+03:00"),
                OffsetDateTime.parse("2027-03-11T10:00:00+03:00"),
                1L,
                3L,
                false
        );

        String signatureBase64 = signingService.sign(ticket);
        byte[] canonicalBytes = canonicalizationService.canonicalize(ticket);
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);

        X509Certificate certificate = keyProvider.getCertificate();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(canonicalBytes);

        assertTrue(verifier.verify(signatureBytes));
    }
}