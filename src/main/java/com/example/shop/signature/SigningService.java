package com.example.shop.signature;

import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

@Service
public class SigningService {

    private final JsonCanonicalizationService canonicalizationService;
    private final SignatureKeyProvider keyProvider;
    private final SignatureProperties properties;

    public SigningService(JsonCanonicalizationService canonicalizationService,
                          SignatureKeyProvider keyProvider,
                          SignatureProperties properties) {
        this.canonicalizationService = canonicalizationService;
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    public String sign(Object payload) {
        try {
            byte[] canonicalBytes = canonicalizationService.canonicalize(payload);
            byte[] signatureBytes = signBytes(canonicalBytes);
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign payload: " + e.getMessage(), e);
        }
    }

    public byte[] signBytes(byte[] payloadBytes) {
        if (payloadBytes == null) {
            throw new IllegalArgumentException("payloadBytes must not be null");
        }

        try {
            PrivateKey privateKey = keyProvider.getSigningKey();
            Signature signature = Signature.getInstance(properties.getAlgorithm());
            signature.initSign(privateKey);
            signature.update(payloadBytes);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign bytes: " + e.getMessage(), e);
        }
    }
}
