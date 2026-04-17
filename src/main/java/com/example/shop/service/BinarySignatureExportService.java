package com.example.shop.service;

import com.example.shop.model.MalwareSignature;
import com.example.shop.model.SignatureStatus;
import com.example.shop.repository.MalwareSignatureRepository;
import com.example.shop.signature.SigningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BinarySignatureExportService {

    private static final int FORMAT_VERSION = 1;
    private static final int SHA_256_LENGTH = 32;
    private static final long SINCE_NOT_SET = -1L;

    private static final int EXPORT_TYPE_FULL = 1;
    private static final int EXPORT_TYPE_INCREMENT = 2;
    private static final int EXPORT_TYPE_BY_IDS = 3;
    private static final int STATUS_CODE_ACTUAL = 1;
    private static final int STATUS_CODE_DELETED = 2;

    private final MalwareSignatureRepository signatureRepository;
    private final SigningService signingService;
    private final BinaryExportProperties binaryExportProperties;

    public BinaryPackage exportFull() {
        List<MalwareSignature> signatures =
                signatureRepository.findAllByStatusOrderByUpdatedAtDesc(SignatureStatus.ACTUAL);
        return buildBinaryPackage(signatures, EXPORT_TYPE_FULL, null);
    }

    public BinaryPackage exportIncrement(OffsetDateTime since) {
        if (since == null) {
            throw new IllegalArgumentException("since is required");
        }

        List<MalwareSignature> signatures =
                signatureRepository.findAllByUpdatedAtAfterOrderByUpdatedAtAsc(since);
        return buildBinaryPackage(signatures, EXPORT_TYPE_INCREMENT, since);
    }

    public BinaryPackage exportByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return buildBinaryPackage(List.of(), EXPORT_TYPE_BY_IDS, null);
        }

        List<MalwareSignature> found = signatureRepository.findAllByIdIn(ids);
        Map<UUID, MalwareSignature> byId = new HashMap<>();
        for (MalwareSignature signature : found) {
            byId.put(signature.getId(), signature);
        }

        Set<UUID> visited = new HashSet<>();
        List<MalwareSignature> ordered = new ArrayList<>();
        for (UUID id : ids) {
            if (id == null || !visited.add(id)) {
                continue;
            }
            MalwareSignature signature = byId.get(id);
            if (signature != null) {
                ordered.add(signature);
            }
        }

        return buildBinaryPackage(ordered, EXPORT_TYPE_BY_IDS, null);
    }

    private BinaryPackage buildBinaryPackage(List<MalwareSignature> signatures, int exportType, OffsetDateTime since) {
        List<DataEntryDescriptor> dataEntries = buildDataEntries(signatures);
        byte[] dataBytes = serializeData(dataEntries);
        byte[] dataSha256 = sha256(dataBytes);

        byte[] unsignedManifest = serializeUnsignedManifest(signatures, dataEntries, exportType, since, dataSha256);
        byte[] manifestSignature = signingService.signBytes(unsignedManifest);
        byte[] manifestBytes = appendManifestSignature(unsignedManifest, manifestSignature);

        return new BinaryPackage(manifestBytes, dataBytes);
    }

    private List<DataEntryDescriptor> buildDataEntries(List<MalwareSignature> signatures) {
        List<DataEntryDescriptor> entries = new ArrayList<>(signatures.size());
        long offset = 0L;

        for (MalwareSignature signature : signatures) {
            byte[] payload = serializeDataRecord(signature);
            entries.add(new DataEntryDescriptor(offset, payload.length, payload));
            offset += payload.length;
        }

        return entries;
    }

    private byte[] serializeData(List<DataEntryDescriptor> dataEntries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(output);

            writeMagic(out, binaryExportProperties.getDataMagic(), "dataMagic");
            writeU16(out, FORMAT_VERSION);
            writeU32(out, dataEntries.size());

            for (DataEntryDescriptor entry : dataEntries) {
                out.write(entry.payload());
            }

            out.flush();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize data.bin", e);
        }
    }

    private byte[] serializeDataRecord(MalwareSignature signature) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(output);

            writeUtf8(out, require(signature.getThreatName(), "threatName"));
            writeBytes(out, decodeHex(require(signature.getFirstBytesHex(), "firstBytesHex"), "firstBytesHex"));
            writeBytes(out, decodeHex(require(signature.getRemainderHashHex(), "remainderHashHex"), "remainderHashHex"));
            writeI64(out, require(signature.getRemainderLength(), "remainderLength"));
            writeUtf8(out, require(signature.getFileType(), "fileType"));
            writeI64(out, require(signature.getOffsetStart(), "offsetStart"));
            writeI64(out, require(signature.getOffsetEnd(), "offsetEnd"));

            out.flush();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize data record", e);
        }
    }

    private byte[] serializeUnsignedManifest(
            List<MalwareSignature> signatures,
            List<DataEntryDescriptor> dataEntries,
            int exportType,
            OffsetDateTime since,
            byte[] dataSha256
    ) {
        if (signatures.size() != dataEntries.size()) {
            throw new IllegalStateException("Signature count and data entry count mismatch");
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(output);

            writeMagic(out, binaryExportProperties.getManifestMagic(), "manifestMagic");
            writeU16(out, FORMAT_VERSION);
            writeU8(out, exportType);
            writeI64(out, System.currentTimeMillis());
            writeI64(out, since != null ? since.toInstant().toEpochMilli() : SINCE_NOT_SET);
            writeU32(out, signatures.size());

            if (dataSha256.length != SHA_256_LENGTH) {
                throw new IllegalStateException("dataSha256 must contain 32 bytes");
            }
            out.write(dataSha256);

            for (int i = 0; i < signatures.size(); i++) {
                MalwareSignature signature = signatures.get(i);
                DataEntryDescriptor dataEntry = dataEntries.get(i);

                writeUuid(out, require(signature.getId(), "id"));
                writeU8(out, toStatusCode(require(signature.getStatus(), "status")));
                writeI64(out, require(signature.getUpdatedAt(), "updatedAt").toInstant().toEpochMilli());
                writeU64(out, dataEntry.offset());
                writeU32(out, dataEntry.length());

                byte[] recordSignature = decodeRecordSignature(signature.getDigitalSignatureBase64());
                writeU32(out, recordSignature.length);
                out.write(recordSignature);
            }

            out.flush();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize manifest.bin", e);
        }
    }

    private byte[] appendManifestSignature(byte[] unsignedManifest, byte[] manifestSignature) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(output);

            out.write(unsignedManifest);
            writeU32(out, manifestSignature.length);
            out.write(manifestSignature);

            out.flush();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to append manifest signature", e);
        }
    }

    private byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256", e);
        }
    }

    private int toStatusCode(SignatureStatus status) {
        return switch (status) {
            case ACTUAL -> STATUS_CODE_ACTUAL;
            case DELETED -> STATUS_CODE_DELETED;
        };
    }

    private byte[] decodeRecordSignature(String signatureBase64) {
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            throw new IllegalStateException("digitalSignatureBase64 is missing");
        }
        try {
            return Base64.getDecoder().decode(signatureBase64);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid digitalSignatureBase64", e);
        }
    }

    private byte[] decodeHex(String hex, String fieldName) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalStateException(fieldName + " must contain an even number of hex chars");
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalStateException(fieldName + " contains non-hex chars");
            }
            bytes[i / 2] = (byte) ((hi << 4) + lo);
        }
        return bytes;
    }

    private byte[] magicBytes(String magic, String fieldName) {
        if (magic == null || magic.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }

        byte[] bytes = magic.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length == 0) {
            throw new IllegalStateException("Magic must not be empty");
        }
        if (!new String(bytes, StandardCharsets.US_ASCII).equals(magic)) {
            throw new IllegalStateException(fieldName + " must contain only ASCII chars");
        }
        return bytes;
    }

    private void writeMagic(DataOutputStream out, String magic, String fieldName) throws Exception {
        byte[] bytes = magicBytes(magic, fieldName);
        out.write(bytes);
    }

    private void writeU8(DataOutputStream out, int value) throws Exception {
        out.writeByte(value & 0xFF);
    }

    private void writeU16(DataOutputStream out, int value) throws Exception {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("u16 out of range: " + value);
        }
        out.writeShort(value);
    }

    private void writeU32(DataOutputStream out, long value) throws Exception {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("u32 out of range: " + value);
        }
        out.writeInt((int) value);
    }

    private void writeU64(DataOutputStream out, long value) throws Exception {
        if (value < 0) {
            throw new IllegalArgumentException("u64 out of range: " + value);
        }
        out.writeLong(value);
    }

    private void writeI64(DataOutputStream out, long value) throws Exception {
        out.writeLong(value);
    }

    private void writeUuid(DataOutputStream out, UUID uuid) throws Exception {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private void writeUtf8(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeU32(out, bytes.length);
        out.write(bytes);
    }

    private void writeBytes(DataOutputStream out, byte[] bytes) throws Exception {
        writeU32(out, bytes.length);
        out.write(bytes);
    }

    private <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalStateException("Signature field is null: " + field);
        }
        return value;
    }

    private record DataEntryDescriptor(long offset, int length, byte[] payload) {
    }

    public record BinaryPackage(byte[] manifestBytes, byte[] dataBytes) {
    }
}
