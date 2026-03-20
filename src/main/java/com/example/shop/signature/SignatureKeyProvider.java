package com.example.shop.signature;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

@Component
public class SignatureKeyProvider {

    private final SignatureProperties properties;
    private final ResourceLoader resourceLoader;

    private volatile PrivateKey cachedPrivateKey;
    private volatile X509Certificate cachedCertificate;

    public SignatureKeyProvider(SignatureProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    public PrivateKey getSigningKey() {
        ensureLoaded();
        return cachedPrivateKey;
    }

    public X509Certificate getCertificate() {
        ensureLoaded();
        return cachedCertificate;
    }

    private synchronized void ensureLoaded() {
        if (cachedPrivateKey != null && cachedCertificate != null) {
            return;
        }

        try {
            KeyStore keyStore = KeyStore.getInstance(properties.getKeyStoreType());
            Resource resource = resourceLoader.getResource(properties.getKeyStorePath());

            try (InputStream inputStream = resource.getInputStream()) {
                keyStore.load(inputStream, properties.getKeyStorePassword().toCharArray());
            }

            String effectiveKeyPassword = properties.getKeyPassword();
            if (effectiveKeyPassword == null || effectiveKeyPassword.isBlank()) {
                effectiveKeyPassword = properties.getKeyStorePassword();
            }

            PrivateKey privateKey =
                    (PrivateKey) keyStore.getKey(properties.getKeyAlias(), effectiveKeyPassword.toCharArray());

            Certificate certificate = keyStore.getCertificate(properties.getKeyAlias());

            if (privateKey == null) {
                throw new IllegalStateException("Private key not found for alias: " + properties.getKeyAlias());
            }

            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new IllegalStateException("Certificate not found or invalid for alias: " + properties.getKeyAlias());
            }

            this.cachedPrivateKey = privateKey;
            this.cachedCertificate = x509Certificate;

        } catch (Exception e) {
            throw new IllegalStateException("Failed to load signing key from keystore", e);
        }
    }
}