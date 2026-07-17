package com.centralbrain.runtime.tools;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Strict scalar-object validator for Tool inputs and outputs. It performs no serialization. */
public final class ToolSchemaValidator {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public enum ErrorCode {
        MISSING_FIELD,
        UNKNOWN_FIELD,
        NULL_VALUE,
        TYPE_MISMATCH,
        VALUE_OUT_OF_RANGE,
        PAYLOAD_TOO_LARGE
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final ErrorCode errorCode;

        ValidationException(ErrorCode errorCode) {
            super("CB_TOOL_SCHEMA: " + errorCode.name());
            this.errorCode = errorCode;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    public Map<String, Object> validateInput(
            ToolManifest manifest, Map<?, ?> values) {
        if (manifest == null) {
            throw new NullPointerException("manifest");
        }
        return validate(manifest.getInputSchema(), values);
    }

    public Map<String, Object> validateOutput(
            ToolManifest manifest, Map<?, ?> values) {
        if (manifest == null) {
            throw new NullPointerException("manifest");
        }
        return validate(manifest.getOutputSchema(), values);
    }

    private static Map<String, Object> validate(
            ToolManifest.ObjectSchema schema, Map<?, ?> values) {
        if (values == null) {
            throw new ValidationException(ErrorCode.MISSING_FIELD);
        }
        TreeMap<String, Object> result = new TreeMap<>();
        int encodedBytes = 2;
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            Object rawName = entry.getKey();
            if (rawName == null || rawName.getClass() != String.class) {
                throw new ValidationException(ErrorCode.UNKNOWN_FIELD);
            }
            String name = (String) rawName;
            ToolManifest.FieldSchema field = schema.field(name);
            if (field == null) {
                throw new ValidationException(ErrorCode.UNKNOWN_FIELD);
            }
            Object value = entry.getValue();
            if (value == null) {
                throw new ValidationException(ErrorCode.NULL_VALUE);
            }
            validateValue(field, value);
            encodedBytes += utf8Length(name) + encodedValueBytes(value) + 6;
            if (encodedBytes > schema.getMaxEncodedBytes()) {
                throw new ValidationException(ErrorCode.PAYLOAD_TOO_LARGE);
            }
            result.put(name, value);
        }
        for (ToolManifest.FieldSchema field : schema.getFields()) {
            if (field.isRequired() && !result.containsKey(field.getName())) {
                throw new ValidationException(ErrorCode.MISSING_FIELD);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static void validateValue(ToolManifest.FieldSchema field, Object value) {
        switch (field.getType()) {
            case STRING:
                requireExactClass(value, String.class);
                if (utf8Length((String) value) > field.getMaxUtf8Bytes()) {
                    throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE);
                }
                return;
            case SHA256_DIGEST:
                requireExactClass(value, String.class);
                if (!SHA_256.matcher((String) value).matches()) {
                    throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE);
                }
                return;
            case BOOLEAN:
                requireExactClass(value, Boolean.class);
                return;
            case INTEGER:
                requireExactClass(value, Long.class);
                long integer = (Long) value;
                if (integer < field.getMinimumInteger()
                        || integer > field.getMaximumInteger()) {
                    throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE);
                }
                return;
            default:
                throw new ValidationException(ErrorCode.TYPE_MISMATCH);
        }
    }

    private static void requireExactClass(Object value, Class<?> expected) {
        if (value.getClass() != expected) {
            throw new ValidationException(ErrorCode.TYPE_MISMATCH);
        }
    }

    private static int encodedValueBytes(Object value) {
        if (value instanceof String) {
            return utf8Length((String) value) + 2;
        }
        return value.toString().getBytes(StandardCharsets.US_ASCII).length;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
