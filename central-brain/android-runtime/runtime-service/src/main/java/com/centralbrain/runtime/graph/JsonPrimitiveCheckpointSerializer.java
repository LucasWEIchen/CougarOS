package com.centralbrain.runtime.graph;

import static com.centralbrain.runtime.graph.CheckpointSerializer.error;

import com.centralbrain.runtime.graph.CheckpointSerializer.CheckpointException;
import com.centralbrain.runtime.graph.CheckpointSerializer.ErrorCode;
import com.centralbrain.runtime.graph.CheckpointSerializer.PayloadCodec;
import com.centralbrain.runtime.graph.CheckpointSerializer.Registration;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Strict canonical JSON serializer over explicit primitive/DTO registrations. */
public final class JsonPrimitiveCheckpointSerializer implements CheckpointSerializer {
    private static final String DIGEST_DOMAIN = "central-brain.checkpoint.v1";
    private static final int MAX_JSON_DEPTH = MAX_PAYLOAD_DEPTH + 2;

    private final Map<String, Registration<?>> registrations;

    public JsonPrimitiveCheckpointSerializer(List<Registration<?>> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        if (registrations.isEmpty() || registrations.size() > 64) {
            throw error(
                    ErrorCode.INVALID_ARGUMENT,
                    "checkpoint registrations must contain 1..64 entries",
                    null);
        }
        Map<String, Registration<?>> copy = new LinkedHashMap<>();
        for (Registration<?> registration : registrations) {
            Objects.requireNonNull(registration, "registration");
            String key = key(registration.getType(), registration.getSchemaVersion());
            if (copy.put(key, registration) != null) {
                throw error(ErrorCode.INVALID_ARGUMENT, "duplicate checkpoint registration", null);
            }
        }
        this.registrations = Map.copyOf(copy);
    }

    @Override
    public <T> CheckpointEnvelope create(
            String type,
            int schemaVersion,
            String nodeId,
            String planDigest,
            String contextDigest,
            T payload,
            long createdAtEpochMs) {
        Registration<?> registration = requireRegistration(type, schemaVersion);
        CheckpointValue encoded = encodePayload(registration, payload);
        validateTree(encoded, 0, new TokenBudget());
        decodePayloadUnchecked(registration, encoded);
        String digest = digest(canonicalWithoutDigest(
                schemaVersion,
                type,
                nodeId,
                planDigest,
                contextDigest,
                encoded,
                createdAtEpochMs));
        CheckpointEnvelope envelope = new CheckpointEnvelope(
                schemaVersion,
                type,
                nodeId,
                planDigest,
                contextDigest,
                encoded,
                digest,
                createdAtEpochMs);
        serialize(envelope);
        return envelope;
    }

    @Override
    public byte[] serialize(CheckpointEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        Registration<?> registration = requireRegistration(
                envelope.getType(), envelope.getSchemaVersion());
        validateTree(envelope.getPayload(), 0, new TokenBudget());
        decodePayloadUnchecked(registration, envelope.getPayload());
        byte[] withoutDigest = canonicalWithoutDigest(
                envelope.getSchemaVersion(),
                envelope.getType(),
                envelope.getNodeId(),
                envelope.getPlanDigest(),
                envelope.getContextDigest(),
                envelope.getPayload(),
                envelope.getCreatedAtEpochMs());
        if (!MessageDigest.isEqual(
                digest(withoutDigest).getBytes(StandardCharsets.US_ASCII),
                envelope.getDigest().getBytes(StandardCharsets.US_ASCII))) {
            throw error(ErrorCode.DIGEST_MISMATCH, "checkpoint digest does not match", null);
        }
        byte[] result = canonicalWithDigest(envelope);
        requireSize(result);
        return result;
    }

    @Override
    public CheckpointEnvelope deserialize(byte[] encoded) {
        requireSize(encoded);
        CheckpointValue root = parse(encoded);
        Map<String, CheckpointValue> fields = requireEnvelopeFields(
                root,
                "schemaVersion",
                "type",
                "nodeId",
                "planDigest",
                "contextDigest",
                "payload",
                "digest",
                "createdAt");
        int schemaVersion;
        String type;
        String nodeId;
        String planDigest;
        String contextDigest;
        CheckpointValue payload;
        String suppliedDigest;
        long createdAtEpochMs;
        try {
            schemaVersion = exactInt(
                    fields.get("schemaVersion").asLong(), "schemaVersion");
            type = fields.get("type").asString();
            nodeId = fields.get("nodeId").asString();
            planDigest = fields.get("planDigest").asString();
            contextDigest = fields.get("contextDigest").asString();
            payload = fields.get("payload");
            suppliedDigest = fields.get("digest").asString();
            createdAtEpochMs = canonicalPositiveLong(
                    fields.get("createdAt").asString(), "createdAt");
        } catch (CheckpointException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw error(ErrorCode.TYPE_MISMATCH, "checkpoint envelope field type is invalid", exception);
        }
        Registration<?> registration = requireRegistration(type, schemaVersion);
        validateTree(payload, 0, new TokenBudget());
        decodePayloadUnchecked(registration, payload);
        String expectedDigest = digest(canonicalWithoutDigest(
                schemaVersion,
                type,
                nodeId,
                planDigest,
                contextDigest,
                payload,
                createdAtEpochMs));
        if (!MessageDigest.isEqual(
                expectedDigest.getBytes(StandardCharsets.US_ASCII),
                suppliedDigest.getBytes(StandardCharsets.US_ASCII))) {
            throw error(ErrorCode.DIGEST_MISMATCH, "checkpoint digest does not match", null);
        }
        CheckpointEnvelope envelope = new CheckpointEnvelope(
                schemaVersion,
                type,
                nodeId,
                planDigest,
                contextDigest,
                payload,
                suppliedDigest,
                createdAtEpochMs);
        byte[] canonical = canonicalWithDigest(envelope);
        if (!Arrays.equals(encoded, canonical)) {
            throw error(ErrorCode.NON_CANONICAL, "checkpoint JSON is not canonical", null);
        }
        return envelope;
    }

    private static Map<String, CheckpointValue> requireEnvelopeFields(
            CheckpointValue root,
            String... expectedFields) {
        Map<String, CheckpointValue> fields;
        try {
            fields = root.asMap();
        } catch (IllegalArgumentException exception) {
            throw error(ErrorCode.TYPE_MISMATCH, "checkpoint envelope must be an object", exception);
        }
        Map<String, CheckpointValue> remaining = new LinkedHashMap<>(fields);
        for (String field : expectedFields) {
            if (remaining.remove(field) == null) {
                throw error(
                        ErrorCode.TYPE_MISMATCH,
                        "required checkpoint envelope field is missing: " + field,
                        null);
            }
        }
        if (!remaining.isEmpty()) {
            throw error(
                    ErrorCode.UNKNOWN_FIELD,
                    "unknown checkpoint envelope field: "
                            + remaining.keySet().iterator().next(),
                    null);
        }
        return fields;
    }

    @Override
    public <T> T decodePayload(CheckpointEnvelope envelope, Class<T> expectedClass) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(expectedClass, "expectedClass");
        serialize(envelope);
        Registration<?> registration = requireRegistration(
                envelope.getType(), envelope.getSchemaVersion());
        if (registration.getPayloadClass() != expectedClass) {
            throw error(ErrorCode.TYPE_MISMATCH, "checkpoint payload class does not match", null);
        }
        Object result = decodePayloadUnchecked(registration, envelope.getPayload());
        return expectedClass.cast(result);
    }

    private Registration<?> requireRegistration(String type, int schemaVersion) {
        Registration<?> exact = registrations.get(key(type, schemaVersion));
        if (exact != null) {
            return exact;
        }
        boolean knownType = registrations.values().stream()
                .anyMatch(candidate -> candidate.getType().equals(type));
        throw error(
                knownType ? ErrorCode.VERSION_UNSUPPORTED : ErrorCode.TYPE_UNREGISTERED,
                knownType
                        ? "checkpoint schema version is not registered"
                        : "checkpoint type is not registered",
                null);
    }

    private static String key(String type, int schemaVersion) {
        return String.valueOf(type) + "#" + schemaVersion;
    }

    private static CheckpointValue encodePayload(
            Registration<?> registration,
            Object payload) {
        if (payload == null || payload.getClass() != registration.getPayloadClass()) {
            throw error(ErrorCode.TYPE_MISMATCH, "checkpoint payload class does not match", null);
        }
        try {
            return encodePayloadUnchecked(registration, payload);
        } catch (CheckpointException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw error(ErrorCode.PAYLOAD_REJECTED, "checkpoint payload encode failed", exception);
        }
    }

    private static Object decodePayloadUnchecked(
            Registration<?> registration,
            CheckpointValue payload) {
        try {
            Object decoded = registration.getCodec().decode(payload);
            if (decoded == null || decoded.getClass() != registration.getPayloadClass()) {
                throw error(
                        ErrorCode.TYPE_MISMATCH,
                        "checkpoint codec returned the wrong payload class",
                        null);
            }
            return decoded;
        } catch (CheckpointException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw error(ErrorCode.PAYLOAD_REJECTED, "checkpoint payload decode failed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> CheckpointValue encodePayloadUnchecked(
            Registration<T> registration,
            Object payload) {
        CheckpointValue encoded = registration.getCodec().encode((T) payload);
        if (encoded == null) {
            throw error(ErrorCode.PAYLOAD_REJECTED, "checkpoint codec returned null", null);
        }
        return encoded;
    }

    private static CheckpointValue parse(byte[] encoded) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new ByteArrayInputStream(encoded), StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            CheckpointValue value = readValue(reader, 0, new TokenBudget());
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw error(ErrorCode.MALFORMED_JSON, "trailing JSON content", null);
            }
            return value;
        } catch (CheckpointException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw error(ErrorCode.MALFORMED_JSON, "strict JSON parsing failed", exception);
        }
    }

    private static CheckpointValue readValue(
            JsonReader reader,
            int depth,
            TokenBudget budget) throws IOException {
        if (depth > MAX_JSON_DEPTH) {
            throw error(ErrorCode.DEPTH_EXCEEDED, "JSON nesting is too deep", null);
        }
        budget.consume();
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                Map<String, CheckpointValue> object = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String field = reader.nextName();
                    try {
                        CheckpointValue.requireKey(field);
                    } catch (IllegalArgumentException exception) {
                        throw error(ErrorCode.PAYLOAD_REJECTED, exception.getMessage(), exception);
                    }
                    if (object.containsKey(field)) {
                        throw error(ErrorCode.DUPLICATE_FIELD, "duplicate field " + field, null);
                    }
                    if (object.size() >= CheckpointValue.MAX_CONTAINER_ITEMS) {
                        throw error(ErrorCode.LIMIT_EXCEEDED, "object has too many fields", null);
                    }
                    object.put(field, readValue(reader, depth + 1, budget));
                }
                reader.endObject();
                return CheckpointValue.map(object);
            case BEGIN_ARRAY:
                List<CheckpointValue> array = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= CheckpointValue.MAX_CONTAINER_ITEMS) {
                        throw error(ErrorCode.LIMIT_EXCEEDED, "array has too many items", null);
                    }
                    array.add(readValue(reader, depth + 1, budget));
                }
                reader.endArray();
                return CheckpointValue.list(array);
            case STRING:
                return CheckpointValue.string(reader.nextString());
            case NUMBER:
                String number = reader.nextString();
                if (number.length() > 32) {
                    throw error(ErrorCode.LIMIT_EXCEEDED, "number is too long", null);
                }
                try {
                    BigDecimal decimal = new BigDecimal(number);
                    if (decimal.stripTrailingZeros().scale() <= 0) {
                        return CheckpointValue.integer(decimal.longValueExact());
                    }
                    return CheckpointValue.decimal(decimal);
                } catch (ArithmeticException | NumberFormatException exception) {
                    throw error(ErrorCode.LIMIT_EXCEEDED, "number is outside bounds", exception);
                }
            case BOOLEAN:
                return CheckpointValue.bool(reader.nextBoolean());
            case NULL:
                throw error(ErrorCode.TYPE_MISMATCH, "null checkpoint values are forbidden", null);
            default:
                throw error(ErrorCode.MALFORMED_JSON, "unexpected JSON token", null);
        }
    }

    private static byte[] canonicalWithoutDigest(
            int schemaVersion,
            String type,
            String nodeId,
            String planDigest,
            String contextDigest,
            CheckpointValue payload,
            long createdAtEpochMs) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        field(builder, "schemaVersion", Long.toString(schemaVersion));
        field(builder, "type", quote(type));
        field(builder, "nodeId", quote(nodeId));
        field(builder, "planDigest", quote(planDigest));
        field(builder, "contextDigest", quote(contextDigest));
        field(builder, "payload", canonicalValue(payload));
        field(builder, "createdAt", quote(Long.toString(createdAtEpochMs)));
        builder.append('}');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] canonicalWithDigest(CheckpointEnvelope envelope) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        field(builder, "schemaVersion", Integer.toString(envelope.getSchemaVersion()));
        field(builder, "type", quote(envelope.getType()));
        field(builder, "nodeId", quote(envelope.getNodeId()));
        field(builder, "planDigest", quote(envelope.getPlanDigest()));
        field(builder, "contextDigest", quote(envelope.getContextDigest()));
        field(builder, "payload", canonicalValue(envelope.getPayload()));
        field(builder, "digest", quote(envelope.getDigest()));
        field(builder, "createdAt", quote(Long.toString(envelope.getCreatedAtEpochMs())));
        builder.append('}');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String canonicalValue(CheckpointValue value) {
        switch (value.getKind()) {
            case STRING:
                return quote(value.asString());
            case BOOLEAN:
                return Boolean.toString(value.asBoolean());
            case INTEGER:
                return Long.toString(value.asLong());
            case DECIMAL:
                return value.asDecimal().stripTrailingZeros().toPlainString();
            case LIST:
                StringBuilder array = new StringBuilder("[");
                for (CheckpointValue item : value.asList()) {
                    appendSeparator(array);
                    array.append(canonicalValue(item));
                }
                return array.append(']').toString();
            case MAP:
                StringBuilder object = new StringBuilder("{");
                for (Map.Entry<String, CheckpointValue> entry : value.asMap().entrySet()) {
                    appendSeparator(object);
                    object.append(quote(entry.getKey()))
                            .append(':')
                            .append(canonicalValue(entry.getValue()));
                }
                return object.append('}').toString();
            default:
                throw error(ErrorCode.TYPE_MISMATCH, "unsupported checkpoint value", null);
        }
    }

    private static void field(StringBuilder builder, String name, String value) {
        appendSeparator(builder);
        builder.append(quote(name)).append(':').append(value);
    }

    private static void appendSeparator(StringBuilder builder) {
        char last = builder.charAt(builder.length() - 1);
        if (last != '{' && last != '[') {
            builder.append(',');
        }
    }

    private static String quote(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder builder = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
            }
        }
        return builder.append('"').toString();
    }

    private static void validateTree(
            CheckpointValue value,
            int depth,
            TokenBudget budget) {
        if (depth > MAX_PAYLOAD_DEPTH) {
            throw error(ErrorCode.DEPTH_EXCEEDED, "checkpoint payload nesting is too deep", null);
        }
        budget.consume();
        if (value.getKind() == CheckpointValue.Kind.LIST) {
            for (CheckpointValue item : value.asList()) {
                validateTree(item, depth + 1, budget);
            }
        } else if (value.getKind() == CheckpointValue.Kind.MAP) {
            for (CheckpointValue item : value.asMap().values()) {
                validateTree(item, depth + 1, budget);
            }
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_CHECKPOINT_BYTES) {
            throw error(ErrorCode.OVERSIZE, "checkpoint byte size is outside 1..65536", null);
        }
    }

    private static int exactInt(long value, String label) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw error(ErrorCode.LIMIT_EXCEEDED, label + " is outside integer bounds", null);
        }
        return (int) value;
    }

    private static long canonicalPositiveLong(String encoded, String label) {
        try {
            if (encoded == null
                    || encoded.isEmpty()
                    || (encoded.length() > 1 && encoded.charAt(0) == '0')
                    || !decimalDigitsOnly(encoded)) {
                throw error(ErrorCode.TYPE_MISMATCH, label + " is not canonical", null);
            }
            long value = Long.parseLong(encoded);
            if (value <= 0L) {
                throw error(ErrorCode.LIMIT_EXCEEDED, label + " must be positive", null);
            }
            return value;
        } catch (CheckpointException exception) {
            throw exception;
        } catch (NumberFormatException exception) {
            throw error(ErrorCode.LIMIT_EXCEEDED, label + " is outside long bounds", exception);
        }
    }

    private static boolean decimalDigitsOnly(String encoded) {
        for (int index = 0; index < encoded.length(); index++) {
            if (encoded.charAt(index) < '0' || encoded.charAt(index) > '9') {
                return false;
            }
        }
        return true;
    }

    private static String digest(byte[] canonicalWithoutDigest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DIGEST_DOMAIN.getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(canonicalWithoutDigest);
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0xf, 16));
            builder.append(Character.forDigit(value & 0xf, 16));
        }
        return builder.toString();
    }

    private static final class TokenBudget {
        private int remaining = MAX_VALUE_TOKENS;

        void consume() {
            if (--remaining < 0) {
                throw error(ErrorCode.LIMIT_EXCEEDED, "checkpoint token budget exceeded", null);
            }
        }
    }
}
