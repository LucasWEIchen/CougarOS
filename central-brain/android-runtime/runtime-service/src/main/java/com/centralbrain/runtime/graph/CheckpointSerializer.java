package com.centralbrain.runtime.graph;

import java.util.Objects;
import java.util.regex.Pattern;

/** Registered, bounded checkpoint serialization contract for P3-W03. */
public interface CheckpointSerializer {
    int MAX_CHECKPOINT_BYTES = 64 * 1_024;
    int MAX_PAYLOAD_DEPTH = 8;
    int MAX_VALUE_TOKENS = 1_024;

    enum ErrorCode {
        INVALID_ARGUMENT,
        TYPE_UNREGISTERED,
        VERSION_UNSUPPORTED,
        TYPE_MISMATCH,
        OVERSIZE,
        MALFORMED_JSON,
        DUPLICATE_FIELD,
        UNKNOWN_FIELD,
        DEPTH_EXCEEDED,
        LIMIT_EXCEEDED,
        DIGEST_MISMATCH,
        NON_CANONICAL,
        PAYLOAD_REJECTED
    }

    final class CheckpointException extends IllegalArgumentException {
        private final ErrorCode errorCode;

        CheckpointException(ErrorCode errorCode, String message, Throwable cause) {
            super("CB_CHECKPOINT_" + errorCode.name() + ": " + message, cause);
            this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    interface PayloadCodec<T> {
        CheckpointValue encode(T value);

        T decode(CheckpointValue value);
    }

    final class Registration<T> {
        private static final Pattern TYPE =
                Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+){1,15}");

        private final String type;
        private final int schemaVersion;
        private final Class<T> payloadClass;
        private final PayloadCodec<T> codec;

        public Registration(
                String type,
                int schemaVersion,
                Class<T> payloadClass,
                PayloadCodec<T> codec) {
            if (type == null || type.length() > 96 || !TYPE.matcher(type).matches()) {
                throw error(ErrorCode.INVALID_ARGUMENT, "checkpoint type is invalid", null);
            }
            if (schemaVersion < 1 || schemaVersion > 32) {
                throw error(
                        ErrorCode.INVALID_ARGUMENT,
                        "checkpoint schema version is outside 1..32",
                        null);
            }
            this.type = type;
            this.schemaVersion = schemaVersion;
            this.payloadClass = Objects.requireNonNull(payloadClass, "payloadClass");
            this.codec = Objects.requireNonNull(codec, "codec");
        }

        public String getType() {
            return type;
        }

        public int getSchemaVersion() {
            return schemaVersion;
        }

        public Class<T> getPayloadClass() {
            return payloadClass;
        }

        public PayloadCodec<T> getCodec() {
            return codec;
        }
    }

    <T> CheckpointEnvelope create(
            String type,
            int schemaVersion,
            String nodeId,
            String planDigest,
            String contextDigest,
            T payload,
            long createdAtEpochMs);

    byte[] serialize(CheckpointEnvelope envelope);

    CheckpointEnvelope deserialize(byte[] encoded);

    <T> T decodePayload(CheckpointEnvelope envelope, Class<T> expectedClass);

    static CheckpointException error(ErrorCode code, String message, Throwable cause) {
        return new CheckpointException(code, message, cause);
    }
}
