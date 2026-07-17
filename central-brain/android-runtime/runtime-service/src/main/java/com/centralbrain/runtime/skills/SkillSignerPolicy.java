package com.centralbrain.runtime.skills;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable signer allowlist and revocation policy. It does not acquire signer evidence. */
public final class SkillSignerPolicy {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SIGNERS = 32;

    public enum SignerState {
        ACTIVE,
        RETIRED,
        REVOKED
    }

    public enum DecisionCode {
        ACCEPTED,
        UNKNOWN_SIGNER,
        SIGNER_NOT_YET_ACTIVE,
        RETIRED_SIGNER,
        REVOKED_SIGNER
    }

    public static final class Entry {
        private final String signerDigest;
        private final SignerState state;
        private final long activationEpoch;
        private final long revocationEpoch;

        public Entry(
                String signerDigest,
                SignerState state,
                long activationEpoch,
                long revocationEpoch) {
            this.signerDigest = requireDigest(signerDigest, "signerDigest");
            this.state = Objects.requireNonNull(state, "state");
            if (activationEpoch < 1L) {
                throw violation("activationEpoch must be positive");
            }
            if (state == SignerState.REVOKED) {
                if (revocationEpoch <= activationEpoch) {
                    throw violation("revocationEpoch must follow activationEpoch");
                }
            } else if (revocationEpoch != 0L) {
                throw violation("only a revoked signer may define revocationEpoch");
            }
            this.activationEpoch = activationEpoch;
            this.revocationEpoch = revocationEpoch;
        }

        public String getSignerDigest() {
            return signerDigest;
        }

        public SignerState getState() {
            return state;
        }

        public long getActivationEpoch() {
            return activationEpoch;
        }

        public long getRevocationEpoch() {
            return revocationEpoch;
        }

        private String canonicalForm() {
            return signerDigest + ':' + state.name() + ':' + activationEpoch + ':'
                    + revocationEpoch;
        }
    }

    public static final class Decision {
        private final DecisionCode code;
        private final String signerDigest;
        private final long artifactEpoch;

        private Decision(DecisionCode code, String signerDigest, long artifactEpoch) {
            this.code = Objects.requireNonNull(code, "code");
            this.signerDigest = requireDigest(signerDigest, "signerDigest");
            this.artifactEpoch = artifactEpoch;
        }

        public DecisionCode getCode() {
            return code;
        }

        public String getSignerDigest() {
            return signerDigest;
        }

        public long getArtifactEpoch() {
            return artifactEpoch;
        }

        public boolean isAccepted() {
            return code == DecisionCode.ACCEPTED;
        }
    }

    private final int schemaVersion;
    private final Map<String, Entry> entries;
    private final String policyDigest;

    public SkillSignerPolicy(int schemaVersion, List<Entry> entries) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw violation("unsupported signer policy schemaVersion");
        }
        if (entries == null || entries.isEmpty() || entries.size() > MAX_SIGNERS) {
            throw violation("signer entries are outside 1..MAX_SIGNERS");
        }
        TreeMap<String, Entry> sorted = new TreeMap<>();
        for (Entry entry : entries) {
            Entry present = Objects.requireNonNull(entry, "entry");
            if (sorted.put(present.signerDigest, present) != null) {
                throw violation("duplicate signer digest");
            }
        }
        if (sorted.values().stream().noneMatch(
                entry -> entry.state == SignerState.ACTIVE)) {
            throw violation("at least one active signer is required");
        }
        this.schemaVersion = schemaVersion;
        this.entries = Collections.unmodifiableMap(sorted);
        this.policyDigest = digest(canonicalForm());
    }

    public Decision evaluate(String signerDigest, long artifactEpoch) {
        String canonicalDigest = requireDigest(signerDigest, "signerDigest");
        if (artifactEpoch < 1L) {
            throw violation("artifactEpoch must be positive");
        }
        Entry entry = entries.get(canonicalDigest);
        if (entry == null) {
            return new Decision(DecisionCode.UNKNOWN_SIGNER, canonicalDigest, artifactEpoch);
        }
        if (artifactEpoch < entry.activationEpoch) {
            return new Decision(
                    DecisionCode.SIGNER_NOT_YET_ACTIVE, canonicalDigest, artifactEpoch);
        }
        if (entry.state == SignerState.REVOKED) {
            return new Decision(DecisionCode.REVOKED_SIGNER, canonicalDigest, artifactEpoch);
        }
        if (entry.state == SignerState.RETIRED) {
            return new Decision(DecisionCode.RETIRED_SIGNER, canonicalDigest, artifactEpoch);
        }
        return new Decision(DecisionCode.ACCEPTED, canonicalDigest, artifactEpoch);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public String getPolicyDigest() {
        return policyDigest;
    }

    public boolean isTrustedSignerEvidenceSourceConfigured() {
        return false;
    }

    public boolean isHardwareBackedAttestationRequired() {
        return false;
    }

    private String canonicalForm() {
        StringBuilder output = new StringBuilder("central-brain-skill-signer-policy-v1");
        for (Entry entry : entries.values()) {
            output.append('|').append(entry.canonicalForm());
        }
        return output.toString();
    }

    static String requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw violation(name + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = digits[value >>> 4];
            output[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(output);
    }

    static IllegalArgumentException violation(String detail) {
        return new IllegalArgumentException("Skill signer policy violation: " + detail);
    }
}
