package com.example.shop.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "signature.file")
public class SignatureFileProperties {

    @Min(1)
    private int firstBytesLength = 4;

    public int getFirstBytesLength() {
        return firstBytesLength;
    }

    public void setFirstBytesLength(int firstBytesLength) {
        this.firstBytesLength = firstBytesLength;
    }
}
