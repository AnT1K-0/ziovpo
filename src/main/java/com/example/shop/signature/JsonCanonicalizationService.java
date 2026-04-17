package com.example.shop.signature;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsonCanonicalizationService {

    private static final BigInteger MIN_SAFE_INTEGER = BigInteger.valueOf(-9_007_199_254_740_991L);
    private static final BigInteger MAX_SAFE_INTEGER = BigInteger.valueOf(9_007_199_254_740_991L);

    private final ObjectMapper objectMapper;

    public JsonCanonicalizationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] canonicalize(Object payload) {
        String canonical = canonicalizeToString(payload);
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    private String canonicalizeToString(Object payload) {
        JsonNode node = toJsonNode(payload);
        StringBuilder canonical = new StringBuilder();
        writeCanonicalJson(node, canonical);
        return canonical.toString();
    }

    private JsonNode toJsonNode(Object payload) {
        try {
            if (payload instanceof String json) {
                return objectMapper.reader()
                        .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .readTree(json);
            }
            return objectMapper.valueToTree(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to convert payload to JSON", ex);
        }
    }

    private void writeCanonicalJson(JsonNode node, StringBuilder out) {
        if (node == null || node.isNull()) {
            out.append("null");
            return;
        }
        if (node.isObject()) {
            writeCanonicalObject((ObjectNode) node, out);
            return;
        }
        if (node.isArray()) {
            out.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                writeCanonicalJson(node.get(i), out);
            }
            out.append(']');
            return;
        }
        if (node.isTextual()) {
            writeCanonicalString(node.textValue(), out);
            return;
        }
        if (node.isBoolean()) {
            out.append(node.booleanValue());
            return;
        }
        if (node.isNumber()) {
            out.append(writeCanonicalNumber(node));
            return;
        }

        throw new IllegalStateException("Unsupported JSON node type: " + node.getNodeType());
    }

    private void writeCanonicalObject(ObjectNode node, StringBuilder out) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        fields.sort(String::compareTo);

        out.append('{');
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            String field = fields.get(i);
            writeCanonicalString(field, out);
            out.append(':');
            writeCanonicalJson(node.get(field), out);
        }
        out.append('}');
    }

    private void writeCanonicalString(String value, StringBuilder out) {
        validateNoLoneSurrogates(value);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch <= 0x1F) {
                        out.append("\\u");
                        String hex = Integer.toHexString(ch);
                        out.append("0".repeat(4 - hex.length()));
                        out.append(hex);
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    private void validateNoLoneSurrogates(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new IllegalStateException("Lone surrogate is not allowed by RFC8785");
                }
                i++;
                continue;
            }
            if (Character.isLowSurrogate(ch)) {
                throw new IllegalStateException("Lone surrogate is not allowed by RFC8785");
            }
        }
    }

    private String writeCanonicalNumber(JsonNode node) {
        validateIJsonNumber(node);

        double value = node.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Non-finite JSON numbers are not allowed by RFC8785");
        }
        if (value == 0d) {
            return "0";
        }

        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        int exponent = decimal.precision() - decimal.scale() - 1;
        if (exponent < -6 || exponent >= 21) {
            String digits = decimal.unscaledValue().abs().toString();
            String sign = decimal.signum() < 0 ? "-" : "";
            String exponentValue = exponent >= 0 ? "+" + exponent : Integer.toString(exponent);
            if (digits.length() == 1) {
                return sign + digits + "e" + exponentValue;
            }
            return sign + digits.charAt(0) + "." + digits.substring(1) + "e" + exponentValue;
        }

        String plain = decimal.toPlainString();
        if (plain.contains(".")) {
            int end = plain.length();
            while (end > 0 && plain.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && plain.charAt(end - 1) == '.') {
                end--;
            }
            return plain.substring(0, end);
        }
        return plain;
    }

    private void validateIJsonNumber(JsonNode node) {
        if (!node.isIntegralNumber()) {
            return;
        }
        BigInteger value = node.bigIntegerValue();
        if (value.compareTo(MIN_SAFE_INTEGER) < 0 || value.compareTo(MAX_SAFE_INTEGER) > 0) {
            throw new IllegalStateException("Integer is outside I-JSON IEEE-754 safe range: " + value);
        }
    }
}
