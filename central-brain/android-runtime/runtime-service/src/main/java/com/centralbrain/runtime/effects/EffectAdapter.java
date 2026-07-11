package com.centralbrain.runtime.effects;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/** Contract for an idempotent destination adapter. No production implementation is wired. */
public interface EffectAdapter {
    int MAX_PAYLOAD_BYTES = 65_536;
    int MAX_ENVELOPE_BYTES = 131_072;

    Descriptor descriptor();

    ApplyResult apply(Invocation invocation);

    StatusResult queryStatus(String idempotencyToken);

    enum IdempotencyMode {
        NONE,
        TOKEN_DEDUPLICATED
    }

    enum StatusConsistency {
        NONE,
        EVENTUAL,
        LINEARIZABLE
    }

    enum ApplyState {
        APPLIED,
        RETRYABLE_FAILURE,
        TERMINAL_FAILURE,
        UNKNOWN
    }

    enum DeliveryState {
        NOT_APPLIED,
        APPLIED,
        REJECTED,
        UNKNOWN
    }

    final class Descriptor {
        private final String adapterId;
        private final String destination;
        private final IdempotencyMode idempotencyMode;
        private final boolean duplicateApplyReturnsOriginal;
        private final boolean appliedStatusReturnsOriginalEvidence;
        private final StatusConsistency statusConsistency;
        private final long maxOperationTimeMs;

        public Descriptor(
                String adapterId,
                String destination,
                IdempotencyMode idempotencyMode,
                boolean duplicateApplyReturnsOriginal,
                boolean appliedStatusReturnsOriginalEvidence,
                StatusConsistency statusConsistency,
                long maxOperationTimeMs) {
            this.adapterId = requireMetadata(adapterId, "adapterId");
            this.destination = requireMetadata(destination, "destination");
            this.idempotencyMode = Objects.requireNonNull(
                    idempotencyMode,
                    "idempotencyMode");
            this.duplicateApplyReturnsOriginal = duplicateApplyReturnsOriginal;
            this.appliedStatusReturnsOriginalEvidence = appliedStatusReturnsOriginalEvidence;
            this.statusConsistency = Objects.requireNonNull(
                    statusConsistency,
                    "statusConsistency");
            if (maxOperationTimeMs < 1 || maxOperationTimeMs > 60_000) {
                throw new IllegalArgumentException(
                        "maxOperationTimeMs must be in range 1..60000");
            }
            this.maxOperationTimeMs = maxOperationTimeMs;
        }

        public String getAdapterId() {
            return adapterId;
        }

        public String getDestination() {
            return destination;
        }

        public IdempotencyMode getIdempotencyMode() {
            return idempotencyMode;
        }

        public boolean isDuplicateApplyReturnsOriginal() {
            return duplicateApplyReturnsOriginal;
        }

        public boolean isAppliedStatusReturnsOriginalEvidence() {
            return appliedStatusReturnsOriginalEvidence;
        }

        public StatusConsistency getStatusConsistency() {
            return statusConsistency;
        }

        public long getMaxOperationTimeMs() {
            return maxOperationTimeMs;
        }
    }

    final class Invocation {
        private final String effectId;
        private final String outboxId;
        private final String idempotencyToken;
        private final String destination;
        private final String actionId;
        private final String payloadDigest;
        private final String envelopeDigest;
        private final int attempt;
        private final int maxAttempts;
        private final byte[] canonicalPayload;
        private final byte[] canonicalEnvelope;

        public Invocation(
                String effectId,
                String outboxId,
                String idempotencyToken,
                String destination,
                String actionId,
                String payloadDigest,
                String envelopeDigest,
                int attempt,
                int maxAttempts,
                byte[] canonicalPayload,
                byte[] canonicalEnvelope) {
            this.effectId = requireMetadata(effectId, "effectId");
            this.outboxId = requireMetadata(outboxId, "outboxId");
            this.idempotencyToken = requireDigest(
                    idempotencyToken,
                    "idempotencyToken");
            this.destination = requireMetadata(destination, "destination");
            this.actionId = requireMetadata(actionId, "actionId");
            this.payloadDigest = requireDigest(payloadDigest, "payloadDigest");
            this.envelopeDigest = requireDigest(envelopeDigest, "envelopeDigest");
            if (attempt < 1 || maxAttempts < 1 || attempt > maxAttempts) {
                throw new IllegalArgumentException("attempt range is invalid");
            }
            this.attempt = attempt;
            this.maxAttempts = maxAttempts;
            this.canonicalPayload = requireMaterial(
                    canonicalPayload,
                    MAX_PAYLOAD_BYTES,
                    "canonicalPayload");
            this.canonicalEnvelope = requireMaterial(
                    canonicalEnvelope,
                    MAX_ENVELOPE_BYTES,
                    "canonicalEnvelope");
            if (!payloadDigest.equals(sha256(this.canonicalPayload))) {
                throw new IllegalArgumentException(
                        "canonicalPayload does not match payloadDigest");
            }
            if (!envelopeDigest.equals(sha256(this.canonicalEnvelope))) {
                throw new IllegalArgumentException(
                        "canonicalEnvelope does not match envelopeDigest");
            }
        }

        public String getEffectId() {
            return effectId;
        }

        public String getOutboxId() {
            return outboxId;
        }

        public String getIdempotencyToken() {
            return idempotencyToken;
        }

        public String getDestination() {
            return destination;
        }

        public String getActionId() {
            return actionId;
        }

        public String getPayloadDigest() {
            return payloadDigest;
        }

        public String getEnvelopeDigest() {
            return envelopeDigest;
        }

        public int getAttempt() {
            return attempt;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public byte[] getCanonicalPayload() {
            return Arrays.copyOf(canonicalPayload, canonicalPayload.length);
        }

        public byte[] getCanonicalEnvelope() {
            return Arrays.copyOf(canonicalEnvelope, canonicalEnvelope.length);
        }
    }

    final class ApplyResult {
        private final String idempotencyToken;
        private final ApplyState state;
        private final String evidenceDigest;

        public ApplyResult(
                String idempotencyToken,
                ApplyState state,
                String evidenceDigest) {
            this.idempotencyToken = requireDigest(
                    idempotencyToken,
                    "idempotencyToken");
            this.state = Objects.requireNonNull(state, "state");
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
        }

        public String getIdempotencyToken() {
            return idempotencyToken;
        }

        public ApplyState getState() {
            return state;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }
    }

    final class StatusResult {
        private final String idempotencyToken;
        private final DeliveryState state;
        private final String evidenceDigest;

        public StatusResult(
                String idempotencyToken,
                DeliveryState state,
                String evidenceDigest) {
            this.idempotencyToken = requireDigest(
                    idempotencyToken,
                    "idempotencyToken");
            this.state = Objects.requireNonNull(state, "state");
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
        }

        public String getIdempotencyToken() {
            return idempotencyToken;
        }

        public DeliveryState getState() {
            return state;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }
    }

    final class AdapterUnavailableException extends RuntimeException {
        public AdapterUnavailableException(String message) {
            super(message);
        }
    }

    private static String requireMetadata(String value, String name) {
        if (value == null || value.trim().isEmpty() || value.length() > 256) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    private static byte[] requireMaterial(byte[] value, int maxBytes, String name) {
        if (value == null || value.length == 0 || value.length > maxBytes) {
            throw new IllegalArgumentException(name + " size is invalid");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            char[] output = new char[bytes.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int index = 0; index < bytes.length; index++) {
                int current = bytes[index] & 0xff;
                output[index * 2] = digits[current >>> 4];
                output[index * 2 + 1] = digits[current & 0x0f];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
