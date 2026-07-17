package com.centralbrain.runtime.tools;

import java.util.Objects;
import java.util.regex.Pattern;

/** Deterministic static-contract and health resolver. It never invokes a Tool. */
public final class ToolResolver {
    private static final Pattern QUALIFIED_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    public enum RegistrationState {
        REGISTERED,
        NOT_REGISTERED
    }

    public enum ResolutionState {
        RESOLVED,
        NOT_RESOLVED
    }

    public enum UsabilityState {
        USABLE,
        NOT_USABLE
    }

    public enum FailureCode {
        NONE,
        TOOL_NOT_REGISTERED,
        NO_COMPATIBLE_VERSION,
        CAPABILITY_MISMATCH,
        CONTRACT_DIGEST_MISMATCH,
        HEALTH_MISSING,
        HEALTH_UNKNOWN,
        HEALTH_UNHEALTHY,
        HEALTH_STALE,
        HEALTH_CLOCK_INVALID
    }

    public static final class Query {
        private final String familyId;
        private final int minimumVersion;
        private final int maximumVersion;
        private final String requiredCapabilityId;
        private final String requiredContractDigest;

        public Query(
                String familyId,
                int minimumVersion,
                int maximumVersion,
                String requiredCapabilityId,
                String requiredContractDigest) {
            this.familyId = ToolRegistry.requireFamilyId(familyId);
            if (minimumVersion < 1 || maximumVersion < minimumVersion) {
                throw new IllegalArgumentException(
                        "CB_TOOL_RESOLVER: invalid version range");
            }
            this.minimumVersion = minimumVersion;
            this.maximumVersion = maximumVersion;
            if (requiredCapabilityId == null
                    || !QUALIFIED_ID.matcher(requiredCapabilityId).matches()) {
                throw new IllegalArgumentException(
                        "CB_TOOL_RESOLVER: capabilityId is not canonical");
            }
            this.requiredCapabilityId = requiredCapabilityId;
            if (requiredContractDigest != null
                    && !DIGEST.matcher(requiredContractDigest).matches()) {
                throw new IllegalArgumentException(
                        "CB_TOOL_RESOLVER: contract digest is not canonical");
            }
            this.requiredContractDigest = requiredContractDigest;
        }

        public String getFamilyId() {
            return familyId;
        }

        public int getMinimumVersion() {
            return minimumVersion;
        }

        public int getMaximumVersion() {
            return maximumVersion;
        }

        public String getRequiredCapabilityId() {
            return requiredCapabilityId;
        }

        public String getRequiredContractDigest() {
            return requiredContractDigest;
        }
    }

    public static final class Resolution {
        private final RegistrationState registrationState;
        private final ResolutionState resolutionState;
        private final UsabilityState usabilityState;
        private final FailureCode failureCode;
        private final ToolManifest manifest;

        private Resolution(
                RegistrationState registrationState,
                ResolutionState resolutionState,
                UsabilityState usabilityState,
                FailureCode failureCode,
                ToolManifest manifest) {
            this.registrationState = registrationState;
            this.resolutionState = resolutionState;
            this.usabilityState = usabilityState;
            this.failureCode = failureCode;
            this.manifest = manifest;
        }

        public RegistrationState getRegistrationState() {
            return registrationState;
        }

        public ResolutionState getResolutionState() {
            return resolutionState;
        }

        public UsabilityState getUsabilityState() {
            return usabilityState;
        }

        public FailureCode getFailureCode() {
            return failureCode;
        }

        public ToolManifest getManifest() {
            if (manifest == null) {
                throw new IllegalStateException("CB_TOOL_RESOLVER: no resolved manifest");
            }
            return manifest;
        }

        /** USABLE means eligible for later rule solving; P5-W02 never enables execution. */
        public boolean isExecutionEnabled() {
            return false;
        }
    }

    private final ToolRegistry registry;

    public ToolResolver(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public Resolution resolve(
            Query query,
            ToolHealthSnapshot health,
            long nowElapsedRealtimeMs) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(health, "health");
        if (!registry.isRegistered(query.familyId)) {
            return unresolved(RegistrationState.NOT_REGISTERED, FailureCode.TOOL_NOT_REGISTERED);
        }
        ToolManifest selected = registry.highestCompatible(
                query.familyId, query.minimumVersion, query.maximumVersion);
        if (selected == null) {
            return unresolved(RegistrationState.REGISTERED, FailureCode.NO_COMPATIBLE_VERSION);
        }
        if (!selected.getCapabilityId().equals(query.requiredCapabilityId)) {
            return unresolved(RegistrationState.REGISTERED, FailureCode.CAPABILITY_MISMATCH);
        }
        if (query.requiredContractDigest != null
                && !selected.getContractDigest().equals(query.requiredContractDigest)) {
            return unresolved(
                    RegistrationState.REGISTERED, FailureCode.CONTRACT_DIGEST_MISMATCH);
        }
        ToolHealthSnapshot.Eligibility eligibility = health.eligibility(
                selected, nowElapsedRealtimeMs);
        if (eligibility == ToolHealthSnapshot.Eligibility.HEALTHY) {
            return new Resolution(
                    RegistrationState.REGISTERED,
                    ResolutionState.RESOLVED,
                    UsabilityState.USABLE,
                    FailureCode.NONE,
                    selected);
        }
        return new Resolution(
                RegistrationState.REGISTERED,
                ResolutionState.RESOLVED,
                UsabilityState.NOT_USABLE,
                healthFailure(eligibility),
                selected);
    }

    private static Resolution unresolved(
            RegistrationState registrationState, FailureCode failureCode) {
        return new Resolution(
                registrationState,
                ResolutionState.NOT_RESOLVED,
                UsabilityState.NOT_USABLE,
                failureCode,
                null);
    }

    private static FailureCode healthFailure(ToolHealthSnapshot.Eligibility eligibility) {
        switch (eligibility) {
            case MISSING:
                return FailureCode.HEALTH_MISSING;
            case UNKNOWN:
                return FailureCode.HEALTH_UNKNOWN;
            case UNHEALTHY:
                return FailureCode.HEALTH_UNHEALTHY;
            case STALE:
                return FailureCode.HEALTH_STALE;
            case CLOCK_INVALID:
                return FailureCode.HEALTH_CLOCK_INVALID;
            default:
                throw new IllegalStateException(
                        "CB_TOOL_RESOLVER: unexpected health eligibility");
        }
    }
}
