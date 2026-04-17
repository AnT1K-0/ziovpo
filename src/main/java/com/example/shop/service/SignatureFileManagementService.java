package com.example.shop.service;

import com.example.shop.controller.dto.CreateSignatureRequest;
import com.example.shop.controller.dto.SignatureFilePresignedUrlResponse;
import com.example.shop.controller.dto.SignatureResponse;
import com.example.shop.model.MalwareSignature;
import com.example.shop.repository.MalwareSignatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SignatureFileManagementService {

    private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();
    private static final long OFFSET_START = 0L;
    private static final String DEFAULT_FILE_TYPE = "bin";
    private static final String DEFAULT_THREAT_NAME = "uploaded-file";

    private final SignatureFileProperties signatureFileProperties;
    private final MinioStorageService minioStorageService;
    private final MalwareSignatureService malwareSignatureService;
    private final MalwareSignatureRepository signatureRepository;
    private final MinioStorageProperties minioStorageProperties;

    public SignatureResponse uploadAndCreateSignature(MultipartFile file, String threatName, String changedBy) {
        validateUpload(file, changedBy);

        String safeFileName = sanitizeFileName(extractOriginalFileName(file));
        String objectKey = buildObjectKey(safeFileName);
        CreateSignatureRequest request = buildSignatureRequest(file, safeFileName, threatName);

        boolean uploaded = false;
        try {
            minioStorageService.upload(objectKey, file);
            uploaded = true;
            return malwareSignatureService.create(request, changedBy, objectKey, safeFileName);
        } catch (RuntimeException e) {
            if (uploaded) {
                minioStorageService.deleteIfExists(objectKey);
            }
            throw e;
        }
    }

    public List<SignatureFilePresignedUrlResponse> getPresignedUrls(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<MalwareSignature> found = signatureRepository.findAllByIdIn(ids);
        Map<UUID, MalwareSignature> byId = new HashMap<>();
        for (MalwareSignature signature : found) {
            byId.put(signature.getId(), signature);
        }

        Set<UUID> visited = new HashSet<>();
        List<SignatureFilePresignedUrlResponse> result = new ArrayList<>();
        for (UUID id : ids) {
            if (id == null || !visited.add(id)) {
                continue;
            }

            MalwareSignature signature = byId.get(id);
            if (signature == null || signature.getSourceFileObjectKey() == null || signature.getSourceFileObjectKey().isBlank()) {
                continue;
            }

            String url = minioStorageService.buildPresignedGetUrl(signature.getSourceFileObjectKey());
            result.add(new SignatureFilePresignedUrlResponse(
                    signature.getId(),
                    signature.getSourceFileName(),
                    signature.getSourceFileObjectKey(),
                    url,
                    minioStorageProperties.getPresignedUrlExpirySeconds()
            ));
        }

        return result;
    }

    private void validateUpload(MultipartFile file, String changedBy) {
        if (file == null) {
            throw new IllegalArgumentException("file is required");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        if (changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("changedBy is required");
        }
    }

    private CreateSignatureRequest buildSignatureRequest(MultipartFile file, String safeFileName, String threatName) {
        try {
            byte[] bytes = file.getBytes();
            int firstBytesLength = Math.min(signatureFileProperties.getFirstBytesLength(), bytes.length);
            byte[] firstBytes = new byte[firstBytesLength];
            System.arraycopy(bytes, 0, firstBytes, 0, firstBytesLength);

            int remainderLengthValue = bytes.length - firstBytesLength;
            byte[] remainder = new byte[remainderLengthValue];
            System.arraycopy(bytes, firstBytesLength, remainder, 0, remainderLengthValue);

            String firstBytesHex = toHex(firstBytes);
            String remainderHashHex = toHex(sha256(remainder));
            long remainderLength = remainderLengthValue;
            String fileType = detectFileType(safeFileName);
            long offsetEnd = firstBytesLength == 0 ? OFFSET_START : firstBytesLength - 1L;

            return new CreateSignatureRequest(
                    resolveThreatName(threatName, safeFileName),
                    firstBytesHex,
                    remainderHashHex,
                    remainderLength,
                    fileType,
                    OFFSET_START,
                    offsetEnd
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate signature from file", e);
        }
    }

    private String buildObjectKey(String safeFileName) {
        return "signatures/" + UUID.randomUUID() + "/" + safeFileName;
    }

    private String extractOriginalFileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return DEFAULT_THREAT_NAME + ".bin";
        }

        int unixSlash = original.lastIndexOf('/');
        int winSlash = original.lastIndexOf('\\');
        int separator = Math.max(unixSlash, winSlash);
        return separator >= 0 ? original.substring(separator + 1) : original;
    }

    private String sanitizeFileName(String fileName) {
        StringBuilder sanitized = new StringBuilder(fileName.length());
        for (char c : fileName.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }

        String result = sanitized.toString();
        if (result.isBlank()) {
            return DEFAULT_THREAT_NAME + ".bin";
        }
        return result;
    }

    private String resolveThreatName(String threatName, String safeFileName) {
        if (threatName != null && !threatName.isBlank()) {
            return threatName.trim();
        }

        int dot = safeFileName.lastIndexOf('.');
        if (dot > 0) {
            return safeFileName.substring(0, dot);
        }

        return Objects.requireNonNullElse(safeFileName, DEFAULT_THREAT_NAME);
    }

    private String detectFileType(String safeFileName) {
        int dot = safeFileName.lastIndexOf('.');
        if (dot >= 0 && dot < safeFileName.length() - 1) {
            return safeFileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        return DEFAULT_FILE_TYPE;
    }

    private byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private String toHex(byte[] bytes) {
        return HEX_FORMAT.formatHex(bytes);
    }
}
