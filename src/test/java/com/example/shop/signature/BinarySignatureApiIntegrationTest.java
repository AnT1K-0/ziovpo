package com.example.shop.signature;

import com.example.shop.model.MalwareSignature;
import com.example.shop.model.UserAccount;
import com.example.shop.repository.MalwareSignatureAuditRepository;
import com.example.shop.repository.MalwareSignatureHistoryRepository;
import com.example.shop.repository.MalwareSignatureRepository;
import com.example.shop.repository.UserAccountRepository;
import com.example.shop.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BinarySignatureApiIntegrationTest {

    private static final int FORMAT_VERSION = 1;
    private static final int EXPORT_TYPE_FULL = 1;
    private static final int EXPORT_TYPE_INCREMENT = 2;
    private static final int EXPORT_TYPE_BY_IDS = 3;

    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("boundary=\"?([^\";]+)\"?");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("filename=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MalwareSignatureRepository signatureRepository;

    @Autowired
    private MalwareSignatureHistoryRepository historyRepository;

    @Autowired
    private MalwareSignatureAuditRepository auditRepository;

    @Autowired
    private SignatureKeyProvider keyProvider;

    @Value("${binary.export.manifest-magic}")
    private String manifestMagic;

    @Value("${binary.export.data-magic}")
    private String dataMagic;

    @Value("${binary.export.manifest-filename}")
    private String manifestFilename;

    @Value("${binary.export.data-filename}")
    private String dataFilename;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanup() {
        auditRepository.deleteAllInBatch();
        historyRepository.deleteAllInBatch();
        signatureRepository.deleteAllInBatch();

        Set<String> usernames = new LinkedHashSet<>(createdUsernames);
        for (String username : usernames) {
            userAccountRepository.findByUsername(username).ifPresent(userAccountRepository::delete);
        }

        userAccountRepository.flush();
        createdUsernames.clear();
    }

    @Test
    void shouldReturnMultipartFromAllBinaryEndpoints() throws Exception {
        UserAccount admin = createUser("ROLE_ADMIN");
        UserAccount user = createUser("ROLE_USER");
        String adminToken = accessToken(admin);
        String userToken = accessToken(user);

        SignatureSnapshot first = createSignature(
                adminToken,
                "Binary.First",
                "CAFEBABE",
                "AABBCCDDEEFF0011",
                16L,
                "exe",
                0L,
                64L
        );

        Thread.sleep(20);

        SignatureSnapshot second = createSignature(
                adminToken,
                "Binary.Second",
                "DEADBEEF",
                "0011223344556677",
                8L,
                "dll",
                4L,
                36L
        );

        OffsetDateTime since = OffsetDateTime.now().minusMinutes(5);

        ParsedBinaryResponse fullResponse = parseBinaryResponse(mockMvc.perform(get("/api/binary/signatures/full")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertBinaryResponse(fullResponse, EXPORT_TYPE_FULL, -1L, List.of(second, first));

        ParsedBinaryResponse incrementResponse = parseBinaryResponse(mockMvc.perform(get("/api/binary/signatures/increment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .param("since", since.toString()))
                .andExpect(status().isOk())
                .andReturn());
        assertBinaryResponse(
                incrementResponse,
                EXPORT_TYPE_INCREMENT,
                since.toInstant().toEpochMilli(),
                List.of(first, second)
        );

        ParsedBinaryResponse byIdsResponse = parseBinaryResponse(mockMvc.perform(post("/api/binary/signatures/by-ids")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(first.id(), second.id())))))
                .andExpect(status().isOk())
                .andReturn());
        assertBinaryResponse(byIdsResponse, EXPORT_TYPE_BY_IDS, -1L, List.of(first, second));
    }

    @Test
    void shouldIncludeDeletedRecordsInBinaryIncrement() throws Exception {
        UserAccount admin = createUser("ROLE_ADMIN");
        UserAccount user = createUser("ROLE_USER");
        String adminToken = accessToken(admin);
        String userToken = accessToken(user);

        SignatureSnapshot created = createSignature(
                adminToken,
                "Binary.Deleted",
                "F00DBABE",
                "8899AABBCCDDEEFF",
                12L,
                "exe",
                3L,
                24L
        );

        OffsetDateTime since = OffsetDateTime.now();
        Thread.sleep(20);

        mockMvc.perform(delete("/api/admin/signatures/" + created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        SignatureSnapshot deleted = loadSignature(created.id());

        ParsedBinaryResponse incrementResponse = parseBinaryResponse(mockMvc.perform(get("/api/binary/signatures/increment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .param("since", since.toString()))
                .andExpect(status().isOk())
                .andReturn());

        assertBinaryResponse(
                incrementResponse,
                EXPORT_TYPE_INCREMENT,
                since.toInstant().toEpochMilli(),
                List.of(deleted)
        );
    }

    @Test
    void shouldRejectInvalidIncrementDate() throws Exception {
        UserAccount user = createUser("ROLE_USER");
        String userToken = accessToken(user);

        mockMvc.perform(get("/api/binary/signatures/increment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .param("since", "invalid-date"))
                .andExpect(status().isBadRequest());
    }

    private void assertBinaryResponse(
            ParsedBinaryResponse response,
            int expectedExportType,
            long expectedSinceEpochMillis,
            List<SignatureSnapshot> expectedRecords
    ) throws Exception {
        assertTrue(response.contentType().startsWith("multipart/mixed"));
        assertEquals(2, response.partsByFilename().size());

        MultipartPart manifestPart = response.partsByFilename().get(manifestFilename);
        MultipartPart dataPart = response.partsByFilename().get(dataFilename);

        assertNotNull(manifestPart);
        assertNotNull(dataPart);
        assertPartHeaders(manifestPart, manifestFilename);
        assertPartHeaders(dataPart, dataFilename);

        ParsedManifest manifest = response.manifest();
        ParsedDataFile dataFile = response.dataFile();

        assertEquals(FORMAT_VERSION, manifest.version());
        assertEquals(FORMAT_VERSION, dataFile.version());
        assertEquals(expectedExportType, manifest.exportType());
        assertEquals(expectedSinceEpochMillis, manifest.sinceEpochMillis());
        assertTrue(manifest.generatedAtEpochMillis() > 0L);

        assertEquals(expectedRecords.size(), manifest.entries().size());
        assertEquals(expectedRecords.size(), dataFile.records().size());
        assertEquals(expectedRecords.size(), dataFile.recordCount());

        assertArrayEquals(sha256(dataPart.payload()), manifest.dataSha256());
        verifyManifestSignature(manifest);

        for (int i = 0; i < expectedRecords.size(); i++) {
            SignatureSnapshot expected = expectedRecords.get(i);
            ParsedManifestEntry manifestEntry = manifest.entries().get(i);
            ParsedDataRecord dataRecord = dataFile.records().get(i);

            assertEquals(expected.id(), manifestEntry.id());
            assertEquals(toStatusCode(expected.status()), manifestEntry.statusCode());
            assertEquals(expected.updatedAtEpochMillis(), manifestEntry.updatedAtEpochMillis());
            assertEquals(dataFile.recordOffsets().get(i).longValue(), manifestEntry.offset());
            assertEquals(dataFile.recordLengths().get(i).intValue(), manifestEntry.length());
            assertArrayEquals(expected.recordSignature(), manifestEntry.recordSignature());

            assertEquals(expected.threatName(), dataRecord.threatName());
            assertArrayEquals(expected.firstBytes(), dataRecord.firstBytes());
            assertArrayEquals(expected.remainderHash(), dataRecord.remainderHash());
            assertEquals(expected.remainderLength(), dataRecord.remainderLength());
            assertEquals(expected.fileType(), dataRecord.fileType());
            assertEquals(expected.offsetStart(), dataRecord.offsetStart());
            assertEquals(expected.offsetEnd(), dataRecord.offsetEnd());
        }
    }

    private void assertPartHeaders(MultipartPart part, String expectedFilename) {
        assertEquals("application/octet-stream", part.headers().get("content-type"));
        assertEquals("binary", part.headers().get("content-transfer-encoding"));
        assertTrue(part.headers().get("content-disposition").contains("filename=\"" + expectedFilename + "\""));
        assertEquals(Integer.toString(part.payload().length), part.headers().get("content-length"));
    }

    private ParsedBinaryResponse parseBinaryResponse(MvcResult result) throws Exception {
        String contentType = result.getResponse().getContentType();
        assertNotNull(contentType);

        String boundary = extractBoundary(contentType);
        byte[] bodyBytes = result.getResponse().getContentAsByteArray();
        String body = new String(bodyBytes, StandardCharsets.ISO_8859_1);
        String delimiter = "--" + boundary;
        String separator = "\r\n" + delimiter;

        int cursor = 0;
        Map<String, MultipartPart> partsByFilename = new LinkedHashMap<>();

        while (true) {
            assertTrue(body.startsWith(delimiter, cursor));
            int afterBoundary = cursor + delimiter.length();

            if (body.startsWith("--", afterBoundary)) {
                break;
            }

            assertTrue(body.startsWith("\r\n", afterBoundary));

            int headersStart = afterBoundary + 2;
            int headersEnd = body.indexOf("\r\n\r\n", headersStart);
            assertTrue(headersEnd >= 0);

            Map<String, String> headers = parseHeaders(body.substring(headersStart, headersEnd));
            String filename = extractFilename(headers.get("content-disposition"));

            int payloadStart = headersEnd + 4;
            int nextBoundary = body.indexOf(separator, payloadStart);
            assertTrue(nextBoundary >= 0);

            byte[] payload = body.substring(payloadStart, nextBoundary).getBytes(StandardCharsets.ISO_8859_1);
            partsByFilename.put(filename, new MultipartPart(filename, headers, payload));

            cursor = nextBoundary + 2;
        }

        MultipartPart manifestPart = partsByFilename.get(manifestFilename);
        MultipartPart dataPart = partsByFilename.get(dataFilename);

        assertNotNull(manifestPart);
        assertNotNull(dataPart);

        return new ParsedBinaryResponse(
                contentType,
                boundary,
                partsByFilename,
                parseManifest(manifestPart.payload()),
                parseData(dataPart.payload())
        );
    }

    private Map<String, String> parseHeaders(String headersBlock) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String line : headersBlock.split("\r\n")) {
            int separatorIndex = line.indexOf(':');
            assertTrue(separatorIndex > 0);
            headers.put(
                    line.substring(0, separatorIndex).trim().toLowerCase(),
                    line.substring(separatorIndex + 1).trim()
            );
        }
        return headers;
    }

    private ParsedManifest parseManifest(byte[] manifestBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(manifestBytes).order(ByteOrder.BIG_ENDIAN);

        assertMagic(buffer, manifestMagic);
        int version = Short.toUnsignedInt(buffer.getShort());
        int exportType = Byte.toUnsignedInt(buffer.get());
        long generatedAtEpochMillis = buffer.getLong();
        long sinceEpochMillis = buffer.getLong();
        int entryCount = readU32(buffer);
        byte[] dataSha256 = readBytes(buffer, 32);

        List<ParsedManifestEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            UUID id = new UUID(buffer.getLong(), buffer.getLong());
            int statusCode = Byte.toUnsignedInt(buffer.get());
            long updatedAtEpochMillis = buffer.getLong();
            long offset = readU64(buffer);
            int length = readU32(buffer);
            byte[] recordSignature = readLengthPrefixedBytes(buffer);
            entries.add(new ParsedManifestEntry(id, statusCode, updatedAtEpochMillis, offset, length, recordSignature));
        }

        int unsignedLength = buffer.position();
        byte[] unsignedBytes = Arrays.copyOf(manifestBytes, unsignedLength);
        byte[] manifestSignature = readLengthPrefixedBytes(buffer);
        assertFalse(buffer.hasRemaining());

        return new ParsedManifest(
                version,
                exportType,
                generatedAtEpochMillis,
                sinceEpochMillis,
                dataSha256,
                entries,
                unsignedBytes,
                manifestSignature
        );
    }

    private ParsedDataFile parseData(byte[] dataBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN);

        assertMagic(buffer, dataMagic);
        int version = Short.toUnsignedInt(buffer.getShort());
        int recordCount = readU32(buffer);
        int payloadStartOffset = buffer.position();

        List<Long> offsets = new ArrayList<>(recordCount);
        List<Integer> lengths = new ArrayList<>(recordCount);
        List<ParsedDataRecord> records = new ArrayList<>(recordCount);

        for (int i = 0; i < recordCount; i++) {
            int absoluteStart = buffer.position();
            int relativeStart = absoluteStart - payloadStartOffset;
            offsets.add((long) relativeStart);
            records.add(new ParsedDataRecord(
                    readUtf8(buffer),
                    readLengthPrefixedBytes(buffer),
                    readLengthPrefixedBytes(buffer),
                    buffer.getLong(),
                    readUtf8(buffer),
                    buffer.getLong(),
                    buffer.getLong()
            ));
            lengths.add(buffer.position() - absoluteStart);
        }

        assertFalse(buffer.hasRemaining());
        return new ParsedDataFile(version, recordCount, offsets, lengths, records);
    }

    private void verifyManifestSignature(ParsedManifest manifest) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyProvider.getCertificate().getPublicKey());
        verifier.update(manifest.unsignedBytes());
        assertTrue(verifier.verify(manifest.manifestSignature()));
    }

    private void assertMagic(ByteBuffer buffer, String expectedMagic) {
        byte[] magicBytes = expectedMagic.getBytes(StandardCharsets.US_ASCII);
        assertEquals(expectedMagic, new String(readBytes(buffer, magicBytes.length), StandardCharsets.US_ASCII));
    }

    private byte[] readLengthPrefixedBytes(ByteBuffer buffer) {
        return readBytes(buffer, readU32(buffer));
    }

    private String readUtf8(ByteBuffer buffer) {
        return new String(readLengthPrefixedBytes(buffer), StandardCharsets.UTF_8);
    }

    private int readU32(ByteBuffer buffer) {
        int value = buffer.getInt();
        assertTrue(value >= 0);
        return value;
    }

    private long readU64(ByteBuffer buffer) {
        long value = buffer.getLong();
        assertTrue(value >= 0L);
        return value;
    }

    private byte[] readBytes(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

    private byte[] sha256(byte[] payload) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(payload);
    }

    private String extractBoundary(String contentType) {
        Matcher matcher = BOUNDARY_PATTERN.matcher(contentType);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private String extractFilename(String contentDisposition) {
        assertNotNull(contentDisposition);
        Matcher matcher = FILENAME_PATTERN.matcher(contentDisposition);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private SignatureSnapshot createSignature(
            String adminToken,
            String threatName,
            String firstBytesHex,
            String remainderHashHex,
            Long remainderLength,
            String fileType,
            Long offsetStart,
            Long offsetEnd
    ) throws Exception {
        MvcResult createdResult = mockMvc.perform(post("/api/admin/signatures")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "threatName", threatName,
                                "firstBytesHex", firstBytesHex,
                                "remainderHashHex", remainderHashHex,
                                "remainderLength", remainderLength,
                                "fileType", fileType,
                                "offsetStart", offsetStart,
                                "offsetEnd", offsetEnd
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdBody = objectMapper.readTree(createdResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return loadSignature(UUID.fromString(createdBody.get("id").asText()));
    }

    private SignatureSnapshot loadSignature(UUID id) {
        MalwareSignature entity = signatureRepository.findById(id).orElseThrow();
        return new SignatureSnapshot(
                entity.getId(),
                entity.getThreatName(),
                decodeHex(entity.getFirstBytesHex()),
                decodeHex(entity.getRemainderHashHex()),
                entity.getRemainderLength(),
                entity.getFileType(),
                entity.getOffsetStart(),
                entity.getOffsetEnd(),
                entity.getUpdatedAt().toInstant().toEpochMilli(),
                entity.getStatus().name(),
                Base64.getDecoder().decode(entity.getDigitalSignatureBase64())
        );
    }

    private byte[] decodeHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            bytes[i / 2] = (byte) ((hi << 4) + lo);
        }
        return bytes;
    }

    private int toStatusCode(String status) {
        return switch (status) {
            case "ACTUAL" -> 1;
            case "DELETED" -> 2;
            default -> throw new IllegalArgumentException("Unsupported status: " + status);
        };
    }

    private UserAccount createUser(String role) {
        UserAccount user = new UserAccount();
        user.setUsername(role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("Passw0rd!"));
        user.setRole(role);
        user.setCreatedAt(OffsetDateTime.now());

        UserAccount saved = userAccountRepository.saveAndFlush(user);
        createdUsernames.add(saved.getUsername());
        return saved;
    }

    private String accessToken(UserAccount user) {
        return jwtTokenProvider.generateAccessToken(user, 1L);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record MultipartPart(String filename, Map<String, String> headers, byte[] payload) {
    }

    private record ParsedBinaryResponse(
            String contentType,
            String boundary,
            Map<String, MultipartPart> partsByFilename,
            ParsedManifest manifest,
            ParsedDataFile dataFile
    ) {
    }

    private record ParsedManifest(
            int version,
            int exportType,
            long generatedAtEpochMillis,
            long sinceEpochMillis,
            byte[] dataSha256,
            List<ParsedManifestEntry> entries,
            byte[] unsignedBytes,
            byte[] manifestSignature
    ) {
    }

    private record ParsedManifestEntry(
            UUID id,
            int statusCode,
            long updatedAtEpochMillis,
            long offset,
            int length,
            byte[] recordSignature
    ) {
    }

    private record ParsedDataFile(
            int version,
            int recordCount,
            List<Long> recordOffsets,
            List<Integer> recordLengths,
            List<ParsedDataRecord> records
    ) {
    }

    private record ParsedDataRecord(
            String threatName,
            byte[] firstBytes,
            byte[] remainderHash,
            long remainderLength,
            String fileType,
            long offsetStart,
            long offsetEnd
    ) {
    }

    private record SignatureSnapshot(
            UUID id,
            String threatName,
            byte[] firstBytes,
            byte[] remainderHash,
            long remainderLength,
            String fileType,
            long offsetStart,
            long offsetEnd,
            long updatedAtEpochMillis,
            String status,
            byte[] recordSignature
    ) {
    }
}
