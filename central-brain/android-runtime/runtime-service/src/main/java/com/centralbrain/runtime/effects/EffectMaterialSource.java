package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.persistence.DurableEffectRepository;

import java.util.Arrays;
import java.util.Objects;

/** Supplies canonical effect material without changing the Room digest-only schema. */
public interface EffectMaterialSource {
    Descriptor descriptor();

    Material resolve(DurableEffectRepository.Snapshot claim);

    enum Availability {
        EMPTY,
        AVAILABLE
    }

    enum Assurance {
        UNTRUSTED,
        TEST_ONLY,
        PRODUCTION
    }

    final class Descriptor {
        private final String sourceId;
        private final Availability availability;
        private final Assurance assurance;
        private final boolean durableAcrossProcessRestart;
        private final boolean encryptedAtRest;
        private final boolean integrityBoundToEffect;
        private final boolean deleteSupported;
        private final long retentionMs;

        public Descriptor(
                String sourceId,
                Availability availability,
                Assurance assurance,
                boolean durableAcrossProcessRestart,
                boolean encryptedAtRest,
                boolean integrityBoundToEffect,
                boolean deleteSupported,
                long retentionMs) {
            if (sourceId == null
                    || sourceId.trim().isEmpty()
                    || sourceId.length() > 128) {
                throw new IllegalArgumentException("sourceId is invalid");
            }
            if (retentionMs < 0) {
                throw new IllegalArgumentException("retentionMs must be non-negative");
            }
            this.sourceId = sourceId;
            this.availability = Objects.requireNonNull(availability, "availability");
            this.assurance = Objects.requireNonNull(assurance, "assurance");
            this.durableAcrossProcessRestart = durableAcrossProcessRestart;
            this.encryptedAtRest = encryptedAtRest;
            this.integrityBoundToEffect = integrityBoundToEffect;
            this.deleteSupported = deleteSupported;
            this.retentionMs = retentionMs;
        }

        public String getSourceId() {
            return sourceId;
        }

        public Availability getAvailability() {
            return availability;
        }

        public Assurance getAssurance() {
            return assurance;
        }

        public boolean isDurableAcrossProcessRestart() {
            return durableAcrossProcessRestart;
        }

        public boolean isEncryptedAtRest() {
            return encryptedAtRest;
        }

        public boolean isIntegrityBoundToEffect() {
            return integrityBoundToEffect;
        }

        public boolean isDeleteSupported() {
            return deleteSupported;
        }

        public long getRetentionMs() {
            return retentionMs;
        }
    }

    final class Material {
        private final String effectId;
        private final String sourceRevisionDigest;
        private final byte[] canonicalPayload;
        private final byte[] canonicalEnvelope;

        public Material(
                String effectId,
                String sourceRevisionDigest,
                byte[] canonicalPayload,
                byte[] canonicalEnvelope) {
            if (effectId == null || effectId.trim().isEmpty() || effectId.length() > 256) {
                throw new IllegalArgumentException("effectId is invalid");
            }
            if (sourceRevisionDigest == null
                    || !sourceRevisionDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "sourceRevisionDigest must be a lowercase SHA-256 digest");
            }
            this.effectId = effectId;
            this.sourceRevisionDigest = sourceRevisionDigest;
            this.canonicalPayload = copyMaterial(
                    canonicalPayload,
                    EffectAdapter.MAX_PAYLOAD_BYTES,
                    "canonicalPayload");
            this.canonicalEnvelope = copyMaterial(
                    canonicalEnvelope,
                    EffectAdapter.MAX_ENVELOPE_BYTES,
                    "canonicalEnvelope");
        }

        public String getEffectId() {
            return effectId;
        }

        public String getSourceRevisionDigest() {
            return sourceRevisionDigest;
        }

        public byte[] getCanonicalPayload() {
            return Arrays.copyOf(canonicalPayload, canonicalPayload.length);
        }

        public byte[] getCanonicalEnvelope() {
            return Arrays.copyOf(canonicalEnvelope, canonicalEnvelope.length);
        }

        private static byte[] copyMaterial(byte[] value, int maxBytes, String name) {
            if (value == null || value.length == 0 || value.length > maxBytes) {
                throw new IllegalArgumentException(name + " size is invalid");
            }
            return Arrays.copyOf(value, value.length);
        }
    }

    final class MaterialUnavailableException extends RuntimeException {
        public MaterialUnavailableException(String message) {
            super(message);
        }
    }
}
