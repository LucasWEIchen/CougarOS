package com.centralbrain.runtime.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable Skill/runtime compatibility and anti-downgrade policy. */
public final class SkillVersionPolicy {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SKILLS = 128;

    private static final Pattern SKILL_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[.][a-z][a-z0-9_-]*){1,7}");

    public enum DecisionCode {
        ACCEPTED,
        UNKNOWN_SKILL,
        VERSION_BELOW_MINIMUM,
        VERSION_ABOVE_MAXIMUM,
        ARTIFACT_EPOCH_TOO_OLD,
        RUNTIME_TOO_OLD,
        RUNTIME_TOO_NEW,
        DOWNGRADE_DENIED
    }

    public static final class SemanticVersion implements Comparable<SemanticVersion> {
        private static final int MAX_COMPONENT = 1_000_000;

        private final int major;
        private final int minor;
        private final int patch;
        private final String value;

        private SemanticVersion(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.value = major + "." + minor + "." + patch;
        }

        public static SemanticVersion parse(String value) {
            if (value == null || !value.matches("(?:0|[1-9][0-9]*)[.](?:0|[1-9][0-9]*)[.](?:0|[1-9][0-9]*)")) {
                throw violation("semantic version must use canonical major.minor.patch");
            }
            String[] parts = value.split("[.]", -1);
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                int patch = Integer.parseInt(parts[2]);
                if (major > MAX_COMPONENT || minor > MAX_COMPONENT || patch > MAX_COMPONENT) {
                    throw violation("semantic version component exceeds the bound");
                }
                return new SemanticVersion(major, minor, patch);
            } catch (NumberFormatException exception) {
                throw violation("semantic version component exceeds the bound");
            }
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int comparison = Integer.compare(major, other.major);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(minor, other.minor);
            return comparison != 0 ? comparison : Integer.compare(patch, other.patch);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SemanticVersion
                    && compareTo((SemanticVersion) other) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(major, minor, patch);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static final class Entry {
        private final String skillId;
        private final SemanticVersion minimumVersion;
        private final SemanticVersion maximumVersion;
        private final long minimumArtifactEpoch;
        private final boolean rollbackAllowed;

        public Entry(
                String skillId,
                String minimumVersion,
                String maximumVersion,
                long minimumArtifactEpoch,
                boolean rollbackAllowed) {
            this.skillId = requireSkillId(skillId);
            this.minimumVersion = SemanticVersion.parse(minimumVersion);
            this.maximumVersion = SemanticVersion.parse(maximumVersion);
            if (this.minimumVersion.compareTo(this.maximumVersion) > 0) {
                throw violation("minimum Skill version exceeds maximum");
            }
            if (minimumArtifactEpoch < 1L) {
                throw violation("minimumArtifactEpoch must be positive");
            }
            this.minimumArtifactEpoch = minimumArtifactEpoch;
            this.rollbackAllowed = rollbackAllowed;
        }

        public String getSkillId() {
            return skillId;
        }

        public String getMinimumVersion() {
            return minimumVersion.toString();
        }

        public String getMaximumVersion() {
            return maximumVersion.toString();
        }

        public long getMinimumArtifactEpoch() {
            return minimumArtifactEpoch;
        }

        public boolean isRollbackAllowed() {
            return rollbackAllowed;
        }

        private String canonicalForm() {
            return skillId + ':' + minimumVersion + ':' + maximumVersion + ':'
                    + minimumArtifactEpoch + ':' + rollbackAllowed;
        }
    }

    public static final class Decision {
        private final DecisionCode code;
        private final String skillId;
        private final String candidateVersion;

        private Decision(DecisionCode code, String skillId, String candidateVersion) {
            this.code = Objects.requireNonNull(code, "code");
            this.skillId = requireSkillId(skillId);
            this.candidateVersion = SemanticVersion.parse(candidateVersion).toString();
        }

        public DecisionCode getCode() {
            return code;
        }

        public String getSkillId() {
            return skillId;
        }

        public String getCandidateVersion() {
            return candidateVersion;
        }

        public boolean isAccepted() {
            return code == DecisionCode.ACCEPTED;
        }
    }

    private final int schemaVersion;
    private final SemanticVersion currentRuntimeVersion;
    private final Map<String, Entry> entries;
    private final String policyDigest;

    public SkillVersionPolicy(
            int schemaVersion, String currentRuntimeVersion, List<Entry> entries) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw violation("unsupported version policy schemaVersion");
        }
        if (entries == null || entries.isEmpty() || entries.size() > MAX_SKILLS) {
            throw violation("Skill entries are outside 1..MAX_SKILLS");
        }
        TreeMap<String, Entry> sorted = new TreeMap<>();
        for (Entry entry : entries) {
            Entry present = Objects.requireNonNull(entry, "entry");
            if (sorted.put(present.skillId, present) != null) {
                throw violation("duplicate Skill ID");
            }
        }
        this.schemaVersion = schemaVersion;
        this.currentRuntimeVersion = SemanticVersion.parse(currentRuntimeVersion);
        this.entries = Collections.unmodifiableMap(sorted);
        this.policyDigest = SkillSignerPolicy.digest(canonicalForm());
    }

    public Decision evaluate(
            String skillId,
            String candidateVersion,
            String minimumRuntimeVersion,
            String maximumRuntimeVersion,
            long artifactEpoch,
            String highestAcceptedVersion) {
        String canonicalSkillId = requireSkillId(skillId);
        SemanticVersion candidate = SemanticVersion.parse(candidateVersion);
        SemanticVersion minimumRuntime = SemanticVersion.parse(minimumRuntimeVersion);
        SemanticVersion maximumRuntime = SemanticVersion.parse(maximumRuntimeVersion);
        if (minimumRuntime.compareTo(maximumRuntime) > 0) {
            throw violation("minimum runtime version exceeds maximum");
        }
        if (artifactEpoch < 1L) {
            throw violation("artifactEpoch must be positive");
        }
        Entry entry = entries.get(canonicalSkillId);
        if (entry == null) {
            return decision(DecisionCode.UNKNOWN_SKILL, canonicalSkillId, candidate);
        }
        if (candidate.compareTo(entry.minimumVersion) < 0) {
            return decision(DecisionCode.VERSION_BELOW_MINIMUM, canonicalSkillId, candidate);
        }
        if (candidate.compareTo(entry.maximumVersion) > 0) {
            return decision(DecisionCode.VERSION_ABOVE_MAXIMUM, canonicalSkillId, candidate);
        }
        if (artifactEpoch < entry.minimumArtifactEpoch) {
            return decision(DecisionCode.ARTIFACT_EPOCH_TOO_OLD, canonicalSkillId, candidate);
        }
        if (currentRuntimeVersion.compareTo(minimumRuntime) < 0) {
            return decision(DecisionCode.RUNTIME_TOO_OLD, canonicalSkillId, candidate);
        }
        if (currentRuntimeVersion.compareTo(maximumRuntime) > 0) {
            return decision(DecisionCode.RUNTIME_TOO_NEW, canonicalSkillId, candidate);
        }
        if (highestAcceptedVersion != null) {
            SemanticVersion highest = SemanticVersion.parse(highestAcceptedVersion);
            if (!entry.rollbackAllowed && candidate.compareTo(highest) < 0) {
                return decision(DecisionCode.DOWNGRADE_DENIED, canonicalSkillId, candidate);
            }
        }
        return decision(DecisionCode.ACCEPTED, canonicalSkillId, candidate);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getCurrentRuntimeVersion() {
        return currentRuntimeVersion.toString();
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public String getPolicyDigest() {
        return policyDigest;
    }

    public boolean isProductionRollbackAuthorityConfigured() {
        return false;
    }

    private String canonicalForm() {
        StringBuilder output = new StringBuilder("central-brain-skill-version-policy-v1")
                .append('|').append(currentRuntimeVersion);
        for (Entry entry : entries.values()) {
            output.append('|').append(entry.canonicalForm());
        }
        return output.toString();
    }

    private static Decision decision(
            DecisionCode code, String skillId, SemanticVersion candidateVersion) {
        return new Decision(code, skillId, candidateVersion.toString());
    }

    static String requireSkillId(String value) {
        if (value == null || value.length() > 128 || !SKILL_ID.matcher(value).matches()) {
            throw violation("skillId is invalid");
        }
        return value;
    }

    static IllegalArgumentException violation(String detail) {
        return new IllegalArgumentException("Skill version policy violation: " + detail);
    }
}
