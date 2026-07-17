package com.centralbrain.runtime.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Read-only digest evidence verifier. It never parses, installs, loads, or executes an artifact. */
public final class SkillArtifactVerifier {
    public static final int MANIFEST_SCHEMA_VERSION = 1;
    public static final int MAX_CAPABILITIES = 32;
    public static final int MAX_SKILLS = 128;

    private static final Pattern CAPABILITY_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");

    public enum Outcome {
        VERIFIED,
        REJECTED
    }

    public enum FailureCode {
        NONE,
        MANIFEST_DIGEST_MISMATCH,
        ARTIFACT_DIGEST_MISMATCH,
        SIGNER_EVIDENCE_MISMATCH,
        UNKNOWN_SIGNER,
        SIGNER_NOT_YET_ACTIVE,
        RETIRED_SIGNER,
        REVOKED_SIGNER,
        UNKNOWN_SKILL,
        VERSION_BELOW_MINIMUM,
        VERSION_ABOVE_MAXIMUM,
        ARTIFACT_EPOCH_TOO_OLD,
        RUNTIME_TOO_OLD,
        RUNTIME_TOO_NEW,
        DOWNGRADE_DENIED,
        CAPABILITY_POLICY_MISSING,
        CAPABILITY_DENIED
    }

    public static final class SkillPackageManifest {
        private final int schemaVersion;
        private final String skillId;
        private final String version;
        private final String artifactDigest;
        private final String signerDigest;
        private final String minimumRuntimeVersion;
        private final String maximumRuntimeVersion;
        private final Set<String> capabilities;
        private final String manifestDigest;

        public SkillPackageManifest(
                int schemaVersion,
                String skillId,
                String version,
                String artifactDigest,
                String signerDigest,
                String minimumRuntimeVersion,
                String maximumRuntimeVersion,
                Set<String> capabilities) {
            if (schemaVersion != MANIFEST_SCHEMA_VERSION) {
                throw violation("unsupported Skill package manifest schemaVersion");
            }
            this.schemaVersion = schemaVersion;
            this.skillId = SkillVersionPolicy.requireSkillId(skillId);
            this.version = SkillVersionPolicy.SemanticVersion.parse(version).toString();
            this.artifactDigest = SkillSignerPolicy.requireDigest(
                    artifactDigest, "artifactDigest");
            this.signerDigest = SkillSignerPolicy.requireDigest(signerDigest, "signerDigest");
            SkillVersionPolicy.SemanticVersion minimumRuntime =
                    SkillVersionPolicy.SemanticVersion.parse(minimumRuntimeVersion);
            SkillVersionPolicy.SemanticVersion maximumRuntime =
                    SkillVersionPolicy.SemanticVersion.parse(maximumRuntimeVersion);
            if (minimumRuntime.compareTo(maximumRuntime) > 0) {
                throw violation("minimum runtime version exceeds maximum");
            }
            this.minimumRuntimeVersion = minimumRuntime.toString();
            this.maximumRuntimeVersion = maximumRuntime.toString();
            this.capabilities = immutableCapabilities(capabilities, "manifest capabilities");
            this.manifestDigest = SkillSignerPolicy.digest(canonicalForm());
        }

        public int getSchemaVersion() {
            return schemaVersion;
        }

        public String getSkillId() {
            return skillId;
        }

        public String getVersion() {
            return version;
        }

        public String getArtifactDigest() {
            return artifactDigest;
        }

        public String getSignerDigest() {
            return signerDigest;
        }

        public String getMinimumRuntimeVersion() {
            return minimumRuntimeVersion;
        }

        public String getMaximumRuntimeVersion() {
            return maximumRuntimeVersion;
        }

        public Set<String> getCapabilities() {
            return capabilities;
        }

        public String getManifestDigest() {
            return manifestDigest;
        }

        private String canonicalForm() {
            StringBuilder output = new StringBuilder("central-brain-skill-package-manifest-v1")
                    .append('|').append(schemaVersion)
                    .append('|').append(skillId)
                    .append('|').append(version)
                    .append('|').append(artifactDigest)
                    .append('|').append(signerDigest)
                    .append('|').append(minimumRuntimeVersion)
                    .append('|').append(maximumRuntimeVersion);
            for (String capability : capabilities) {
                output.append('|').append(capability);
            }
            return output.toString();
        }
    }

    public static final class VerificationEvidence {
        private final SkillPackageManifest manifest;
        private final String declaredManifestDigest;
        private final String measuredArtifactDigest;
        private final String observedSignerDigest;
        private final long artifactEpoch;
        private final String highestAcceptedVersion;

        public VerificationEvidence(
                SkillPackageManifest manifest,
                String declaredManifestDigest,
                String measuredArtifactDigest,
                String observedSignerDigest,
                long artifactEpoch,
                String highestAcceptedVersion) {
            this.manifest = Objects.requireNonNull(manifest, "manifest");
            this.declaredManifestDigest = SkillSignerPolicy.requireDigest(
                    declaredManifestDigest, "declaredManifestDigest");
            this.measuredArtifactDigest = SkillSignerPolicy.requireDigest(
                    measuredArtifactDigest, "measuredArtifactDigest");
            this.observedSignerDigest = SkillSignerPolicy.requireDigest(
                    observedSignerDigest, "observedSignerDigest");
            if (artifactEpoch < 1L) {
                throw violation("artifactEpoch must be positive");
            }
            this.artifactEpoch = artifactEpoch;
            this.highestAcceptedVersion = highestAcceptedVersion == null
                    ? null
                    : SkillVersionPolicy.SemanticVersion.parse(
                            highestAcceptedVersion).toString();
        }

        public SkillPackageManifest getManifest() {
            return manifest;
        }
    }

    public static final class VerifiedPackage {
        private final String skillId;
        private final String version;
        private final String artifactDigest;
        private final String signerDigest;
        private final String manifestDigest;
        private final String policyDigest;
        private final Set<String> capabilities;

        private VerifiedPackage(SkillPackageManifest manifest, String policyDigest) {
            skillId = manifest.skillId;
            version = manifest.version;
            artifactDigest = manifest.artifactDigest;
            signerDigest = manifest.signerDigest;
            manifestDigest = manifest.manifestDigest;
            this.policyDigest = policyDigest;
            capabilities = manifest.capabilities;
        }

        public String getSkillId() {
            return skillId;
        }

        public String getVersion() {
            return version;
        }

        public String getArtifactDigest() {
            return artifactDigest;
        }

        public String getSignerDigest() {
            return signerDigest;
        }

        public String getManifestDigest() {
            return manifestDigest;
        }

        public String getPolicyDigest() {
            return policyDigest;
        }

        public Set<String> getCapabilities() {
            return capabilities;
        }

        public boolean isDynamicLoadAllowed() {
            return false;
        }

        public boolean isExecutionAllowed() {
            return false;
        }
    }

    public static final class VerificationResult {
        private final Outcome outcome;
        private final FailureCode failureCode;
        private final VerifiedPackage verifiedPackage;

        private VerificationResult(
                Outcome outcome, FailureCode failureCode, VerifiedPackage verifiedPackage) {
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
            this.verifiedPackage = verifiedPackage;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public FailureCode getFailureCode() {
            return failureCode;
        }

        public VerifiedPackage getVerifiedPackage() {
            return verifiedPackage;
        }

        public boolean isVerified() {
            return outcome == Outcome.VERIFIED;
        }

        public boolean isDynamicLoadAllowed() {
            return false;
        }

        public boolean isExecutionAllowed() {
            return false;
        }
    }

    private final SkillSignerPolicy signerPolicy;
    private final SkillVersionPolicy versionPolicy;
    private final Map<String, Set<String>> capabilityAllowlist;
    private final String policyDigest;

    public SkillArtifactVerifier(
            SkillSignerPolicy signerPolicy,
            SkillVersionPolicy versionPolicy,
            Map<String, Set<String>> capabilityAllowlist) {
        this.signerPolicy = Objects.requireNonNull(signerPolicy, "signerPolicy");
        this.versionPolicy = Objects.requireNonNull(versionPolicy, "versionPolicy");
        if (capabilityAllowlist == null
                || capabilityAllowlist.isEmpty()
                || capabilityAllowlist.size() > MAX_SKILLS) {
            throw violation("capability allowlist is outside 1..MAX_SKILLS");
        }
        TreeMap<String, Set<String>> sorted = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : capabilityAllowlist.entrySet()) {
            String skillId = SkillVersionPolicy.requireSkillId(entry.getKey());
            if (sorted.put(
                    skillId,
                    immutableCapabilities(entry.getValue(), "capability allowlist")) != null) {
                throw violation("duplicate capability allowlist Skill ID");
            }
        }
        this.capabilityAllowlist = Collections.unmodifiableMap(sorted);
        this.policyDigest = SkillSignerPolicy.digest(canonicalPolicyForm());
    }

    public VerificationResult verify(VerificationEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        SkillPackageManifest manifest = evidence.manifest;
        if (!manifest.manifestDigest.equals(evidence.declaredManifestDigest)) {
            return rejected(FailureCode.MANIFEST_DIGEST_MISMATCH);
        }
        if (!manifest.artifactDigest.equals(evidence.measuredArtifactDigest)) {
            return rejected(FailureCode.ARTIFACT_DIGEST_MISMATCH);
        }
        if (!manifest.signerDigest.equals(evidence.observedSignerDigest)) {
            return rejected(FailureCode.SIGNER_EVIDENCE_MISMATCH);
        }
        SkillSignerPolicy.Decision signerDecision = signerPolicy.evaluate(
                evidence.observedSignerDigest, evidence.artifactEpoch);
        if (!signerDecision.isAccepted()) {
            return rejected(mapSignerFailure(signerDecision.getCode()));
        }
        SkillVersionPolicy.Decision versionDecision = versionPolicy.evaluate(
                manifest.skillId,
                manifest.version,
                manifest.minimumRuntimeVersion,
                manifest.maximumRuntimeVersion,
                evidence.artifactEpoch,
                evidence.highestAcceptedVersion);
        if (!versionDecision.isAccepted()) {
            return rejected(mapVersionFailure(versionDecision.getCode()));
        }
        Set<String> allowedCapabilities = capabilityAllowlist.get(manifest.skillId);
        if (allowedCapabilities == null) {
            return rejected(FailureCode.CAPABILITY_POLICY_MISSING);
        }
        if (!allowedCapabilities.containsAll(manifest.capabilities)) {
            return rejected(FailureCode.CAPABILITY_DENIED);
        }
        return new VerificationResult(
                Outcome.VERIFIED,
                FailureCode.NONE,
                new VerifiedPackage(manifest, policyDigest));
    }

    public String getPolicyDigest() {
        return policyDigest;
    }

    public boolean isTrustedEvidenceSourceConfigured() {
        return false;
    }

    public boolean isPackageSignatureCryptographicallyVerified() {
        return false;
    }

    public boolean isDynamicLoadingEnabled() {
        return false;
    }

    public boolean isExecutionEnabled() {
        return false;
    }

    public boolean isProductionWired() {
        return false;
    }

    private String canonicalPolicyForm() {
        StringBuilder output = new StringBuilder("central-brain-skill-artifact-verifier-v1")
                .append('|').append(signerPolicy.getPolicyDigest())
                .append('|').append(versionPolicy.getPolicyDigest());
        for (Map.Entry<String, Set<String>> entry : capabilityAllowlist.entrySet()) {
            output.append('|').append(entry.getKey());
            for (String capability : entry.getValue()) {
                output.append(':').append(capability);
            }
        }
        return output.toString();
    }

    private static Set<String> immutableCapabilities(Set<String> values, String name) {
        if (values == null || values.isEmpty() || values.size() > MAX_CAPABILITIES) {
            throw violation(name + " is outside 1..MAX_CAPABILITIES");
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String value : values) {
            if (value == null
                    || value.length() > 96
                    || !CAPABILITY_ID.matcher(value).matches()) {
                throw violation(name + " contains an invalid capability ID");
            }
            if (!sorted.add(value)) {
                throw violation(name + " contains a duplicate capability ID");
            }
        }
        return Collections.unmodifiableSet(sorted);
    }

    private static VerificationResult rejected(FailureCode failureCode) {
        return new VerificationResult(Outcome.REJECTED, failureCode, null);
    }

    private static FailureCode mapSignerFailure(SkillSignerPolicy.DecisionCode code) {
        switch (code) {
            case UNKNOWN_SIGNER:
                return FailureCode.UNKNOWN_SIGNER;
            case SIGNER_NOT_YET_ACTIVE:
                return FailureCode.SIGNER_NOT_YET_ACTIVE;
            case RETIRED_SIGNER:
                return FailureCode.RETIRED_SIGNER;
            case REVOKED_SIGNER:
                return FailureCode.REVOKED_SIGNER;
            case ACCEPTED:
            default:
                throw new IllegalStateException("unexpected accepted signer decision");
        }
    }

    private static FailureCode mapVersionFailure(SkillVersionPolicy.DecisionCode code) {
        switch (code) {
            case UNKNOWN_SKILL:
                return FailureCode.UNKNOWN_SKILL;
            case VERSION_BELOW_MINIMUM:
                return FailureCode.VERSION_BELOW_MINIMUM;
            case VERSION_ABOVE_MAXIMUM:
                return FailureCode.VERSION_ABOVE_MAXIMUM;
            case ARTIFACT_EPOCH_TOO_OLD:
                return FailureCode.ARTIFACT_EPOCH_TOO_OLD;
            case RUNTIME_TOO_OLD:
                return FailureCode.RUNTIME_TOO_OLD;
            case RUNTIME_TOO_NEW:
                return FailureCode.RUNTIME_TOO_NEW;
            case DOWNGRADE_DENIED:
                return FailureCode.DOWNGRADE_DENIED;
            case ACCEPTED:
            default:
                throw new IllegalStateException("unexpected accepted version decision");
        }
    }

    private static IllegalArgumentException violation(String detail) {
        return new IllegalArgumentException("Skill artifact verifier violation: " + detail);
    }
}
