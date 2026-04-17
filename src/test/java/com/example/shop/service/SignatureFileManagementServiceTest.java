package com.example.shop.service;

import com.example.shop.controller.dto.CreateSignatureRequest;
import com.example.shop.controller.dto.SignatureFilePresignedUrlResponse;
import com.example.shop.controller.dto.SignatureResponse;
import com.example.shop.model.MalwareSignature;
import com.example.shop.model.SignatureStatus;
import com.example.shop.repository.MalwareSignatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureFileManagementServiceTest {

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private MalwareSignatureService malwareSignatureService;

    @Mock
    private MalwareSignatureRepository signatureRepository;

    private SignatureFileProperties signatureFileProperties;
    private MinioStorageProperties minioStorageProperties;

    @InjectMocks
    private SignatureFileManagementService signatureFileManagementService;

    @Captor
    private ArgumentCaptor<CreateSignatureRequest> requestCaptor;

    @Captor
    private ArgumentCaptor<String> objectKeyCaptor;

    @BeforeEach
    void setUp() {
        signatureFileProperties = new SignatureFileProperties();
        signatureFileProperties.setFirstBytesLength(4);

        minioStorageProperties = new MinioStorageProperties();
        minioStorageProperties.setPresignedUrlExpirySeconds(900);

        signatureFileManagementService = new SignatureFileManagementService(
                signatureFileProperties,
                minioStorageService,
                malwareSignatureService,
                signatureRepository,
                minioStorageProperties
        );
    }

    @Test
    void shouldUploadFileAndCreateCalculatedSignature() throws Exception {
        byte[] payload = new byte[]{
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, (byte) 0x01, (byte) 0x02
        };
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evil.exe",
                "application/octet-stream",
                payload
        );

        SignatureResponse expectedResponse = new SignatureResponse(
                UUID.randomUUID(),
                "evil",
                "CAFEBABE",
                toHex(sha256(new byte[]{0x01, 0x02})),
                2L,
                "exe",
                0L,
                3L,
                OffsetDateTime.now(),
                SignatureStatus.ACTUAL,
                Base64.getEncoder().encodeToString(new byte[]{0x01, 0x02})
        );

        doNothing().when(minioStorageService).upload(anyString(), eq(file));
        when(malwareSignatureService.create(requestCaptor.capture(), eq("admin"), objectKeyCaptor.capture(), eq("evil.exe")))
                .thenReturn(expectedResponse);

        SignatureResponse result = signatureFileManagementService.uploadAndCreateSignature(file, null, "admin");

        assertEquals(expectedResponse.id(), result.id());
        CreateSignatureRequest actualRequest = requestCaptor.getValue();
        assertEquals("evil", actualRequest.threatName());
        assertEquals("CAFEBABE", actualRequest.firstBytesHex());
        assertEquals(toHex(sha256(new byte[]{0x01, 0x02})), actualRequest.remainderHashHex());
        assertEquals(2L, actualRequest.remainderLength());
        assertEquals("exe", actualRequest.fileType());
        assertEquals(0L, actualRequest.offsetStart());
        assertEquals(3L, actualRequest.offsetEnd());

        String objectKey = objectKeyCaptor.getValue();
        assertNotNull(objectKey);
        assertEquals(true, objectKey.startsWith("signatures/"));
        assertEquals(true, objectKey.endsWith("/evil.exe"));
        verify(minioStorageService).upload(objectKey, file);
    }

    @Test
    void shouldDeleteUploadedObjectWhenCreateFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.bin",
                "application/octet-stream",
                new byte[]{0x01, 0x02, 0x03, 0x04}
        );

        doNothing().when(minioStorageService).upload(anyString(), eq(file));
        when(malwareSignatureService.create(any(CreateSignatureRequest.class), eq("admin"), anyString(), eq("broken.bin")))
                .thenThrow(new IllegalStateException("db failed"));

        assertThrows(IllegalStateException.class,
                () -> signatureFileManagementService.uploadAndCreateSignature(file, "Trojan.Sample", "admin"));

        verify(minioStorageService).deleteIfExists(objectKeyCaptor.capture());
        String deletedObjectKey = objectKeyCaptor.getValue();
        assertNotNull(deletedObjectKey);
        assertEquals(true, deletedObjectKey.startsWith("signatures/"));
        assertEquals(true, deletedObjectKey.endsWith("/broken.bin"));
    }

    @Test
    void shouldReturnPresignedUrlsInRequestedOrder() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        MalwareSignature sig1 = new MalwareSignature();
        sig1.setId(id1);
        sig1.setSourceFileName("first.bin");
        sig1.setSourceFileObjectKey("signatures/a/first.bin");

        MalwareSignature sig2 = new MalwareSignature();
        sig2.setId(id2);
        sig2.setSourceFileName("second.bin");
        sig2.setSourceFileObjectKey("signatures/b/second.bin");

        MalwareSignature sig3 = new MalwareSignature();
        sig3.setId(id3);
        sig3.setSourceFileName("third.bin");
        sig3.setSourceFileObjectKey(null);

        when(signatureRepository.findAllByIdIn(List.of(id2, id1, id2, id3)))
                .thenReturn(List.of(sig1, sig2, sig3));
        when(minioStorageService.buildPresignedGetUrl("signatures/b/second.bin"))
                .thenReturn("https://minio/presigned/2");
        when(minioStorageService.buildPresignedGetUrl("signatures/a/first.bin"))
                .thenReturn("https://minio/presigned/1");

        List<SignatureFilePresignedUrlResponse> result =
                signatureFileManagementService.getPresignedUrls(List.of(id2, id1, id2, id3));

        assertEquals(2, result.size());
        assertEquals(id2, result.get(0).signatureId());
        assertEquals("https://minio/presigned/2", result.get(0).presignedUrl());
        assertEquals(900, result.get(0).expiresInSeconds());

        assertEquals(id1, result.get(1).signatureId());
        assertEquals("https://minio/presigned/1", result.get(1).presignedUrl());
        assertEquals(900, result.get(1).expiresInSeconds());
    }

    private byte[] sha256(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    private String toHex(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
