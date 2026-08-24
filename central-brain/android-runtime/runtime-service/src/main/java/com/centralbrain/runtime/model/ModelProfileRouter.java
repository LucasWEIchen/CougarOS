package com.centralbrain.runtime.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic model-profile routing inside an admitted Model Provider.
 *
 * <p>The policy router selects a Provider. This router then binds the admitted request to one
 * fixed model profile. It never invokes a model, grants Tool authority, or falls back to another
 * profile.</p>
 *
 * <p>Req IDs: S2-MDL-001/002, S2-SAF-001, S2-OBS-001/002, P4-R9.</p>
 */
public final class ModelProfileRouter {
    public static final int SCHEMA_VERSION = 1;
    public static final String SMOKING_SCENARIO_ID =
            "scene.cabin.compliance.smoking.v1";
    public static final String GENERAL_PROFILE_ID = "model.general-cockpit.9b.v1";
    public static final String TARGET_GENERAL_PROFILE_ID =
            "model.general-cockpit.2b.target.v1";
    public static final String SMOKING_PROFILE_ID = "model.cabin-smoking.2b.v1";
    public static final String GENERAL_MODEL_ID = "central-intent-general-v1";
    public static final String SMOKING_MODEL_ID = "central-vision-smoking-v1";
    public static final int GENERAL_MAX_CONTEXT_TOKENS = 8_192;
    public static final int SMOKING_MAX_CONTEXT_TOKENS = 4_096;
    public static final long MAX_HEALTH_VALIDITY_MS = 60_000L;

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,127}");
    private static final Pattern MODEL_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private ModelProfileRouter() {
    }

    public enum DecisionCode {
        SELECTED,
        REQUEST_REJECTED,
        TARGET_UNAVAILABLE
    }

    public enum RejectionReason {
        NONE,
        SMOKING_REQUIRES_VISION,
        FALLBACK_POLICY_REJECTED,
        TARGET_IDENTITY_MISMATCH,
        TARGET_HEALTH_FROM_FUTURE,
        TARGET_HEALTH_STALE,
        TARGET_NOT_READY
    }

    public enum WorkloadClass {
        GENERAL_COCKPIT,
        CABIN_SMOKING_COMPLIANCE
    }

    public enum DeploymentProfile {
        DUAL_MODEL_DEVELOPMENT,
        SINGLE_2B_TARGET_ETHERNET
    }

    /** Freshness-bounded state for one fixed model process. */
    public static final class TargetHealth {
        private final String profileId;
        private final String modelId;
        private final String servedModelName;
        private final int maximumContextTokens;
        private final boolean ready;
        private final long revision;
        private final long observedAtElapsedMs;
        private final long validUntilElapsedMs;
        private final String evidenceDigest;
        private final String snapshotDigest;

        public TargetHealth(
                String profileId,
                String modelId,
                String servedModelName,
                int maximumContextTokens,
                boolean ready,
                long revision,
                long observedAtElapsedMs,
                long validUntilElapsedMs,
                String evidenceDigest) {
            this.profileId = requireIdentifier(profileId, "profileId");
            this.modelId = requireIdentifier(modelId, "modelId");
            this.servedModelName = requireModelName(servedModelName);
            if (maximumContextTokens < 1 || maximumContextTokens > 32_768) {
                throw new IllegalArgumentException("maximumContextTokens is out of range");
            }
            if (revision < 1) {
                throw new IllegalArgumentException("revision must be positive");
            }
            if (observedAtElapsedMs < 0
                    || validUntilElapsedMs <= observedAtElapsedMs
                    || validUntilElapsedMs - observedAtElapsedMs > MAX_HEALTH_VALIDITY_MS) {
                throw new IllegalArgumentException("target health validity window is invalid");
            }
            this.maximumContextTokens = maximumContextTokens;
            this.ready = ready;
            this.revision = revision;
            this.observedAtElapsedMs = observedAtElapsedMs;
            this.validUntilElapsedMs = validUntilElapsedMs;
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
            this.snapshotDigest = sha256(canonicalForm());
        }

        public String getProfileId() { return profileId; }
        public String getModelId() { return modelId; }
        public String getServedModelName() { return servedModelName; }
        public int getMaximumContextTokens() { return maximumContextTokens; }
        public boolean isReady() { return ready; }
        public long getRevision() { return revision; }
        public long getObservedAtElapsedMs() { return observedAtElapsedMs; }
        public long getValidUntilElapsedMs() { return validUntilElapsedMs; }
        public String getEvidenceDigest() { return evidenceDigest; }
        public String getSnapshotDigest() { return snapshotDigest; }

        private String canonicalForm() {
            return SCHEMA_VERSION
                    + "|" + profileId
                    + "|" + modelId
                    + "|" + servedModelName
                    + "|" + maximumContextTokens
                    + "|" + ready
                    + "|" + revision
                    + "|" + observedAtElapsedMs
                    + "|" + validUntilElapsedMs
                    + "|" + evidenceDigest;
        }
    }

    /** Immutable route evidence. A non-selected decision carries no model target. */
    public static final class RouteDecision {
        private final DecisionCode code;
        private final RejectionReason rejectionReason;
        private final WorkloadClass workloadClass;
        private final String requestId;
        private final String profileId;
        private final String modelId;
        private final String servedModelName;
        private final int maximumContextTokens;
        private final String decisionDigest;

        private RouteDecision(
                DecisionCode code,
                RejectionReason rejectionReason,
                WorkloadClass workloadClass,
                ModelContractV2.ModelRequest request,
                TargetHealth target) {
            this.code = Objects.requireNonNull(code, "code");
            this.rejectionReason = Objects.requireNonNull(rejectionReason, "rejectionReason");
            this.workloadClass = Objects.requireNonNull(workloadClass, "workloadClass");
            this.requestId = request.getRequestId();
            this.profileId = target == null ? "none" : target.getProfileId();
            this.modelId = target == null ? "none" : target.getModelId();
            this.servedModelName = target == null ? "none" : target.getServedModelName();
            this.maximumContextTokens = target == null
                    ? 0 : target.getMaximumContextTokens();
            this.decisionDigest = sha256(
                    SCHEMA_VERSION
                            + "|" + code.name()
                            + "|" + rejectionReason.name()
                            + "|" + workloadClass.name()
                            + "|" + request.getRequestFingerprint()
                            + "|" + profileId
                            + "|" + modelId
                            + "|" + servedModelName
                            + "|" + maximumContextTokens
                            + "|" + (target == null ? "none" : target.getSnapshotDigest()));
        }

        public DecisionCode getCode() { return code; }
        public RejectionReason getRejectionReason() { return rejectionReason; }
        public WorkloadClass getWorkloadClass() { return workloadClass; }
        public String getRequestId() { return requestId; }
        public String getProfileId() { return profileId; }
        public String getModelId() { return modelId; }
        public String getServedModelName() { return servedModelName; }
        public int getMaximumContextTokens() { return maximumContextTokens; }
        public String getDecisionDigest() { return decisionDigest; }
        public boolean isFallbackSelected() { return false; }
        public boolean isModelInvoked() { return false; }
        public boolean isActionAuthorizationGranted() { return false; }
        public boolean isEffectDispatchRequested() { return false; }
    }

    public static RouteDecision decide(
            String scenarioId,
            ModelContractV2.ModelRequest request,
            TargetHealth generalTarget,
            TargetHealth smokingTarget,
            long nowElapsedMs) {
        return decide(
                scenarioId,
                request,
                generalTarget,
                smokingTarget,
                DeploymentProfile.DUAL_MODEL_DEVELOPMENT,
                nowElapsedMs);
    }

    public static RouteDecision decide(
            String scenarioId,
            ModelContractV2.ModelRequest request,
            TargetHealth generalTarget,
            TargetHealth smokingTarget,
            DeploymentProfile deploymentProfile,
            long nowElapsedMs) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(generalTarget, "generalTarget");
        Objects.requireNonNull(smokingTarget, "smokingTarget");
        Objects.requireNonNull(deploymentProfile, "deploymentProfile");
        if (nowElapsedMs < 0) {
            throw new IllegalArgumentException("nowElapsedMs must not be negative");
        }
        WorkloadClass workload = SMOKING_SCENARIO_ID.equals(
                requireIdentifier(scenarioId, "scenarioId"))
                ? WorkloadClass.CABIN_SMOKING_COMPLIANCE
                : WorkloadClass.GENERAL_COCKPIT;
        if (workload == WorkloadClass.CABIN_SMOKING_COMPLIANCE
                && request.getRequiredCapability()
                        != ModelContractV2.RequiredCapability.VISION_CLASSIFICATION) {
            return rejected(
                    DecisionCode.REQUEST_REJECTED,
                    RejectionReason.SMOKING_REQUIRES_VISION,
                    workload,
                    request);
        }
        if (request.getFallbackPolicy() != ModelContractV2.FallbackPolicy.NO_FALLBACK) {
            return rejected(
                    DecisionCode.REQUEST_REJECTED,
                    RejectionReason.FALLBACK_POLICY_REJECTED,
                    workload,
                    request);
        }
        TargetHealth target = workload == WorkloadClass.CABIN_SMOKING_COMPLIANCE
                ? smokingTarget : generalTarget;
        RejectionReason identity = validateFixedIdentity(
                workload, target, deploymentProfile);
        if (identity != RejectionReason.NONE) {
            return rejected(DecisionCode.REQUEST_REJECTED, identity, workload, request);
        }
        RejectionReason health = validateHealth(target, nowElapsedMs);
        if (health != RejectionReason.NONE) {
            return rejected(DecisionCode.TARGET_UNAVAILABLE, health, workload, request);
        }
        return new RouteDecision(
                DecisionCode.SELECTED,
                RejectionReason.NONE,
                workload,
                request,
                target);
    }

    private static RouteDecision rejected(
            DecisionCode code,
            RejectionReason reason,
            WorkloadClass workload,
            ModelContractV2.ModelRequest request) {
        return new RouteDecision(code, reason, workload, request, null);
    }

    private static RejectionReason validateFixedIdentity(
            WorkloadClass workload,
            TargetHealth target,
            DeploymentProfile deploymentProfile) {
        boolean smoking = workload == WorkloadClass.CABIN_SMOKING_COMPLIANCE;
        String expectedProfile = smoking
                ? SMOKING_PROFILE_ID
                : deploymentProfile == DeploymentProfile.SINGLE_2B_TARGET_ETHERNET
                        ? TARGET_GENERAL_PROFILE_ID : GENERAL_PROFILE_ID;
        String expectedModelId = smoking ? SMOKING_MODEL_ID : GENERAL_MODEL_ID;
        String expectedServedModel = smoking
                || deploymentProfile == DeploymentProfile.SINGLE_2B_TARGET_ETHERNET
                        ? "Qwen3.5-2B-AWQ" : "Qwen3.5-9B-AWQ";
        int expectedContext = workload == WorkloadClass.CABIN_SMOKING_COMPLIANCE
                ? SMOKING_MAX_CONTEXT_TOKENS : GENERAL_MAX_CONTEXT_TOKENS;
        if (!expectedProfile.equals(target.getProfileId())
                || !expectedModelId.equals(target.getModelId())
                || !expectedServedModel.equals(target.getServedModelName())
                || target.getMaximumContextTokens() != expectedContext) {
            return RejectionReason.TARGET_IDENTITY_MISMATCH;
        }
        return RejectionReason.NONE;
    }

    private static RejectionReason validateHealth(TargetHealth target, long nowElapsedMs) {
        if (target.getObservedAtElapsedMs() > nowElapsedMs) {
            return RejectionReason.TARGET_HEALTH_FROM_FUTURE;
        }
        if (target.getValidUntilElapsedMs() <= nowElapsedMs) {
            return RejectionReason.TARGET_HEALTH_STALE;
        }
        if (!target.isReady()) {
            return RejectionReason.TARGET_NOT_READY;
        }
        return RejectionReason.NONE;
    }

    private static String requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String requireModelName(String value) {
        if (value == null
                || !MODEL_NAME.matcher(value).matches()
                || value.contains("://")
                || value.contains("..")) {
            throw new IllegalArgumentException("servedModelName is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String field) {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = value.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(input.length).array());
            byte[] encoded = digest.digest(input);
            char[] output = new char[encoded.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int index = 0; index < encoded.length; index++) {
                int item = encoded[index] & 0xff;
                output[index * 2] = alphabet[item >>> 4];
                output[index * 2 + 1] = alphabet[item & 0xf];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
