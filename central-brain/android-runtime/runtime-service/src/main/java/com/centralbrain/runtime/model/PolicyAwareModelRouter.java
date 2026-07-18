package com.centralbrain.runtime.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic model route admission. It selects metadata and never invokes a provider.
 * Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, DEL-001, DEL-004, DEL-005.
 */
public final class PolicyAwareModelRouter {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SELECTED_PROVIDERS = 2;
    public static final int MAX_FALLBACK_PROVIDERS = 1;
    public static final long MAX_POLICY_VALIDITY_MS = 60_000L;
    public static final int MAX_REMAINING_REQUESTS = 10_000;
    public static final int MAX_REMAINING_TOKENS = 1_000_000;
    public static final String NO_PROVIDER_ID = "none";

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Map<String, RouteProfile> FIXED_ROUTE_PROFILES =
            createRouteProfiles();

    private PolicyAwareModelRouter() {
    }

    public enum RouteMode {
        CONTRACT_TEST,
        DEVELOPMENT,
        PRODUCTION
    }

    public enum NetworkPolicy {
        OFFLINE_ONLY,
        UNMETERED_ONLY,
        ALLOW_ANY
    }

    public enum NetworkState {
        UNKNOWN,
        UNAVAILABLE,
        METERED,
        UNMETERED
    }

    public enum ThermalState {
        UNKNOWN,
        NOMINAL,
        ELEVATED,
        HOT,
        CRITICAL
    }

    public enum DecisionCode {
        SELECTED,
        NO_ELIGIBLE_PROVIDER,
        POLICY_SNAPSHOT_REJECTED
    }

    public enum PolicyRejection {
        NONE,
        SNAPSHOT_FROM_FUTURE,
        SNAPSHOT_STALE
    }

    public enum RejectionReason {
        MODE_UNAVAILABLE,
        HEALTH_NOT_FRESH,
        HEALTH_NOT_HEALTHY,
        CAPABILITY_MISSING,
        PRIVACY_BLOCKED,
        NETWORK_POLICY_BLOCKED,
        NETWORK_UNAVAILABLE,
        THERMAL_BLOCKED,
        LATENCY_BUDGET_TOO_SMALL,
        REQUEST_QUOTA_EXHAUSTED,
        TOKEN_QUOTA_EXCEEDED
    }

    /** Caller-owned, freshness-bounded policy input. It performs no system or hardware reads. */
    public static final class PolicySnapshot {
        private final RouteMode routeMode;
        private final NetworkPolicy networkPolicy;
        private final NetworkState networkState;
        private final ThermalState thermalState;
        private final int remainingRequests;
        private final int remainingTokens;
        private final long revision;
        private final long observedAtElapsedMs;
        private final long validUntilElapsedMs;
        private final String evidenceDigest;
        private final String snapshotDigest;

        public PolicySnapshot(
                RouteMode routeMode,
                NetworkPolicy networkPolicy,
                NetworkState networkState,
                ThermalState thermalState,
                int remainingRequests,
                int remainingTokens,
                long revision,
                long observedAtElapsedMs,
                long validUntilElapsedMs,
                String evidenceDigest) {
            this.routeMode = Objects.requireNonNull(routeMode, "routeMode");
            this.networkPolicy = Objects.requireNonNull(networkPolicy, "networkPolicy");
            this.networkState = Objects.requireNonNull(networkState, "networkState");
            this.thermalState = Objects.requireNonNull(thermalState, "thermalState");
            if (remainingRequests < 0 || remainingRequests > MAX_REMAINING_REQUESTS) {
                throw new IllegalArgumentException("remainingRequests is out of range");
            }
            if (remainingTokens < 0 || remainingTokens > MAX_REMAINING_TOKENS) {
                throw new IllegalArgumentException("remainingTokens is out of range");
            }
            if (revision < 1) {
                throw new IllegalArgumentException("revision must be positive");
            }
            if (observedAtElapsedMs < 0
                    || validUntilElapsedMs <= observedAtElapsedMs
                    || validUntilElapsedMs - observedAtElapsedMs > MAX_POLICY_VALIDITY_MS) {
                throw new IllegalArgumentException("policy validity window is invalid");
            }
            this.remainingRequests = remainingRequests;
            this.remainingTokens = remainingTokens;
            this.revision = revision;
            this.observedAtElapsedMs = observedAtElapsedMs;
            this.validUntilElapsedMs = validUntilElapsedMs;
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
            this.snapshotDigest = sha256(canonicalForm());
        }

        public RouteMode getRouteMode() {
            return routeMode;
        }

        public NetworkPolicy getNetworkPolicy() {
            return networkPolicy;
        }

        public NetworkState getNetworkState() {
            return networkState;
        }

        public ThermalState getThermalState() {
            return thermalState;
        }

        public int getRemainingRequests() {
            return remainingRequests;
        }

        public int getRemainingTokens() {
            return remainingTokens;
        }

        public long getRevision() {
            return revision;
        }

        public long getObservedAtElapsedMs() {
            return observedAtElapsedMs;
        }

        public long getValidUntilElapsedMs() {
            return validUntilElapsedMs;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }

        public String getSnapshotDigest() {
            return snapshotDigest;
        }

        private PolicyRejection rejectionAt(long nowElapsedMs) {
            if (observedAtElapsedMs > nowElapsedMs) {
                return PolicyRejection.SNAPSHOT_FROM_FUTURE;
            }
            if (validUntilElapsedMs <= nowElapsedMs) {
                return PolicyRejection.SNAPSHOT_STALE;
            }
            return PolicyRejection.NONE;
        }

        private String canonicalForm() {
            return SCHEMA_VERSION
                    + "|" + routeMode.name()
                    + "|" + networkPolicy.name()
                    + "|" + networkState.name()
                    + "|" + thermalState.name()
                    + "|" + remainingRequests
                    + "|" + remainingTokens
                    + "|" + revision
                    + "|" + observedAtElapsedMs
                    + "|" + validUntilElapsedMs
                    + "|" + evidenceDigest;
        }
    }

    /** Per-provider policy projection owned by this build. */
    private static final class RouteProfile {
        private final String providerId;
        private final long minimumLatencyMs;
        private final ModelContractV2.PrivacyClass maximumPrivacyClass;
        private final boolean thermalAdmissionRequired;

        private RouteProfile(
                String providerId,
                long minimumLatencyMs,
                ModelContractV2.PrivacyClass maximumPrivacyClass,
                boolean thermalAdmissionRequired) {
            this.providerId = providerId;
            this.minimumLatencyMs = minimumLatencyMs;
            this.maximumPrivacyClass = maximumPrivacyClass;
            this.thermalAdmissionRequired = thermalAdmissionRequired;
        }
    }

    public static final class CandidateEvaluation {
        private final ModelProviderRegistry.ProviderView provider;
        private final int preferenceRank;
        private final Set<RejectionReason> rejectionReasons;
        private final String evaluationDigest;

        private CandidateEvaluation(
                ModelProviderRegistry.ProviderView provider,
                int preferenceRank,
                Set<RejectionReason> rejectionReasons) {
            this.provider = Objects.requireNonNull(provider, "provider");
            this.preferenceRank = preferenceRank;
            this.rejectionReasons = rejectionReasons.isEmpty()
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(EnumSet.copyOf(rejectionReasons));
            this.evaluationDigest = sha256(canonicalForm());
        }

        public String getProviderId() {
            return provider.getDescriptor().getProviderId();
        }

        public int getPreferenceRank() {
            return preferenceRank;
        }

        public Set<RejectionReason> getRejectionReasons() {
            return rejectionReasons;
        }

        public boolean isEligible() {
            return rejectionReasons.isEmpty();
        }

        public String getEvaluationDigest() {
            return evaluationDigest;
        }

        private String canonicalForm() {
            StringBuilder builder = new StringBuilder();
            builder.append(getProviderId())
                    .append('|').append(provider.getDescriptor().getDescriptorDigest())
                    .append('|').append(provider.getHealthState().name())
                    .append('|').append(provider.getHealthFreshness().name())
                    .append('|').append(provider.getHealthRevision())
                    .append('|').append(preferenceRank);
            for (RejectionReason reason : rejectionReasons) {
                builder.append('|').append(reason.name());
            }
            return builder.toString();
        }
    }

    public static final class RouteDecision {
        private final DecisionCode code;
        private final PolicyRejection policyRejection;
        private final String requestId;
        private final String requestFingerprint;
        private final String traceId;
        private final String policySnapshotDigest;
        private final String registryCatalogDigest;
        private final String primaryProviderId;
        private final List<String> fallbackProviderIds;
        private final List<CandidateEvaluation> candidateEvaluations;
        private final int maximumSelectedProviders;
        private final String decisionDigest;

        private RouteDecision(
                DecisionCode code,
                PolicyRejection policyRejection,
                ModelContractV2.ModelRequest request,
                PolicySnapshot policy,
                ModelProviderRegistry.RegistrySnapshot registry,
                String primaryProviderId,
                List<String> fallbackProviderIds,
                List<CandidateEvaluation> candidateEvaluations,
                int maximumSelectedProviders) {
            this.code = Objects.requireNonNull(code, "code");
            this.policyRejection = Objects.requireNonNull(policyRejection, "policyRejection");
            this.requestId = request.getRequestId();
            this.requestFingerprint = request.getRequestFingerprint();
            this.traceId = request.getTraceId();
            this.policySnapshotDigest = policy.getSnapshotDigest();
            this.registryCatalogDigest = registry.getCatalogDigest();
            this.primaryProviderId = Objects.requireNonNull(primaryProviderId, "primaryProviderId");
            this.fallbackProviderIds = Collections.unmodifiableList(
                    new ArrayList<>(fallbackProviderIds));
            this.candidateEvaluations = Collections.unmodifiableList(
                    new ArrayList<>(candidateEvaluations));
            this.maximumSelectedProviders = maximumSelectedProviders;
            if (this.fallbackProviderIds.size() > MAX_FALLBACK_PROVIDERS
                    || 1 + this.fallbackProviderIds.size() > maximumSelectedProviders) {
                throw new IllegalArgumentException("fallback selection exceeds the fixed bound");
            }
            this.decisionDigest = sha256(canonicalForm());
        }

        public DecisionCode getCode() {
            return code;
        }

        public PolicyRejection getPolicyRejection() {
            return policyRejection;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getRequestFingerprint() {
            return requestFingerprint;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getPolicySnapshotDigest() {
            return policySnapshotDigest;
        }

        public String getRegistryCatalogDigest() {
            return registryCatalogDigest;
        }

        public String getPrimaryProviderId() {
            return primaryProviderId;
        }

        public List<String> getFallbackProviderIds() {
            return fallbackProviderIds;
        }

        public List<CandidateEvaluation> getCandidateEvaluations() {
            return candidateEvaluations;
        }

        public int getMaximumSelectedProviders() {
            return maximumSelectedProviders;
        }

        public String getDecisionDigest() {
            return decisionDigest;
        }

        public boolean isSelected() {
            return code == DecisionCode.SELECTED;
        }

        public boolean isFallbackBounded() {
            return fallbackProviderIds.size() <= MAX_FALLBACK_PROVIDERS
                    && maximumSelectedProviders <= MAX_SELECTED_PROVIDERS;
        }

        public boolean isActionAuthorizationGranted() {
            return false;
        }

        public boolean isEffectDispatchRequested() {
            return false;
        }

        public boolean isProviderInvoked() {
            return false;
        }

        public boolean isModelInvoked() {
            return false;
        }

        public boolean isNetworkAccessed() {
            return false;
        }

        public boolean isNpuAccessed() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }

        private String canonicalForm() {
            StringBuilder builder = new StringBuilder();
            builder.append(SCHEMA_VERSION)
                    .append('|').append(code.name())
                    .append('|').append(policyRejection.name())
                    .append('|').append(requestId)
                    .append('|').append(requestFingerprint)
                    .append('|').append(traceId)
                    .append('|').append(policySnapshotDigest)
                    .append('|').append(registryCatalogDigest)
                    .append('|').append(primaryProviderId)
                    .append('|').append(maximumSelectedProviders);
            for (String providerId : fallbackProviderIds) {
                builder.append("|fallback:").append(providerId);
            }
            for (CandidateEvaluation evaluation : candidateEvaluations) {
                builder.append("|candidate:").append(evaluation.getEvaluationDigest());
            }
            return builder.toString();
        }
    }

    public static RouteDecision decide(
            ModelContractV2.ModelRequest request,
            PolicySnapshot policy,
            ModelProviderRegistry.RegistrySnapshot registry,
            long nowElapsedMs) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(registry, "registry");
        if (nowElapsedMs < 0) {
            throw new IllegalArgumentException("nowElapsedMs must not be negative");
        }
        int maximumSelections = maximumSelections(request.getFallbackPolicy());
        PolicyRejection policyRejection = policy.rejectionAt(nowElapsedMs);
        if (policyRejection != PolicyRejection.NONE) {
            return new RouteDecision(
                    DecisionCode.POLICY_SNAPSHOT_REJECTED,
                    policyRejection,
                    request,
                    policy,
                    registry,
                    NO_PROVIDER_ID,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    maximumSelections);
        }

        List<CandidateEvaluation> evaluations = new ArrayList<>();
        for (ModelProviderRegistry.ProviderView provider : registry.getProviders()) {
            evaluations.add(evaluate(request, policy, provider));
        }
        evaluations.sort(Comparator
                .comparingInt(CandidateEvaluation::getPreferenceRank)
                .thenComparing(CandidateEvaluation::getProviderId));

        List<String> selected = new ArrayList<>();
        for (CandidateEvaluation evaluation : evaluations) {
            if (evaluation.isEligible() && selected.size() < maximumSelections) {
                selected.add(evaluation.getProviderId());
            }
        }
        if (selected.isEmpty()) {
            return new RouteDecision(
                    DecisionCode.NO_ELIGIBLE_PROVIDER,
                    PolicyRejection.NONE,
                    request,
                    policy,
                    registry,
                    NO_PROVIDER_ID,
                    Collections.emptyList(),
                    evaluations,
                    maximumSelections);
        }
        List<String> fallback = selected.size() == 1
                ? Collections.emptyList()
                : Collections.singletonList(selected.get(1));
        return new RouteDecision(
                DecisionCode.SELECTED,
                PolicyRejection.NONE,
                request,
                policy,
                registry,
                selected.get(0),
                fallback,
                evaluations,
                maximumSelections);
    }

    private static CandidateEvaluation evaluate(
            ModelContractV2.ModelRequest request,
            PolicySnapshot policy,
            ModelProviderRegistry.ProviderView provider) {
        ModelProviderRegistry.ProviderDescriptor descriptor = provider.getDescriptor();
        RouteProfile profile = FIXED_ROUTE_PROFILES.get(descriptor.getProviderId());
        if (profile == null) {
            throw new IllegalArgumentException("registry contains an unknown provider profile");
        }
        EnumSet<RejectionReason> reasons = EnumSet.noneOf(RejectionReason.class);
        if (!availableForMode(policy.getRouteMode(), provider)) {
            reasons.add(RejectionReason.MODE_UNAVAILABLE);
        }
        if (provider.getHealthFreshness() != ModelProviderRegistry.HealthFreshness.FRESH) {
            reasons.add(RejectionReason.HEALTH_NOT_FRESH);
        }
        if (provider.getHealthState() != ModelProviderRegistry.HealthState.HEALTHY) {
            reasons.add(RejectionReason.HEALTH_NOT_HEALTHY);
        }
        if (!descriptor.supports(request.getRequiredCapability())) {
            reasons.add(RejectionReason.CAPABILITY_MISSING);
        }
        if (!privacyAllows(profile.maximumPrivacyClass, request.getPrivacyClass())) {
            reasons.add(RejectionReason.PRIVACY_BLOCKED);
        }
        if (descriptor.isNetworkRequired()) {
            if (policy.getNetworkState() == NetworkState.UNKNOWN
                    || policy.getNetworkState() == NetworkState.UNAVAILABLE) {
                reasons.add(RejectionReason.NETWORK_UNAVAILABLE);
            }
            if (policy.getNetworkPolicy() == NetworkPolicy.OFFLINE_ONLY
                    || policy.getNetworkPolicy() == NetworkPolicy.UNMETERED_ONLY
                            && policy.getNetworkState() != NetworkState.UNMETERED) {
                reasons.add(RejectionReason.NETWORK_POLICY_BLOCKED);
            }
        }
        if (profile.thermalAdmissionRequired
                && (policy.getThermalState() == ThermalState.UNKNOWN
                        || policy.getThermalState() == ThermalState.HOT
                        || policy.getThermalState() == ThermalState.CRITICAL)) {
            reasons.add(RejectionReason.THERMAL_BLOCKED);
        }
        if (request.getLatencyBudget().getMaximumEndToEndMs()
                < profile.minimumLatencyMs) {
            reasons.add(RejectionReason.LATENCY_BUDGET_TOO_SMALL);
        }
        if (policy.getRemainingRequests() == 0) {
            reasons.add(RejectionReason.REQUEST_QUOTA_EXHAUSTED);
        }
        if (request.getTokenBudget().getMaximumTotalTokens()
                > policy.getRemainingTokens()) {
            reasons.add(RejectionReason.TOKEN_QUOTA_EXCEEDED);
        }
        return new CandidateEvaluation(
                provider,
                preferenceRank(policy.getRouteMode(), descriptor.getKind()),
                reasons);
    }

    private static boolean availableForMode(
            RouteMode routeMode,
            ModelProviderRegistry.ProviderView provider) {
        switch (routeMode) {
            case CONTRACT_TEST:
                return provider.isContractTestAvailable();
            case DEVELOPMENT:
                return provider.isDevelopmentAvailable();
            case PRODUCTION:
                return provider.isProductionReady();
            default:
                throw new IllegalStateException("unknown route mode");
        }
    }

    private static int preferenceRank(
            RouteMode routeMode,
            ModelProviderRegistry.ProviderKind kind) {
        switch (routeMode) {
            case CONTRACT_TEST:
                return kind == ModelProviderRegistry.ProviderKind.DETERMINISTIC_ANDROID_TEST
                        ? 0 : 100 + kind.ordinal();
            case DEVELOPMENT:
                if (kind == ModelProviderRegistry.ProviderKind.ANDROID_LOCAL_DEVELOPMENT) {
                    return 0;
                }
                return 100 + kind.ordinal();
            case PRODUCTION:
                if (kind == ModelProviderRegistry.ProviderKind.VENDOR_NPU) {
                    return 0;
                }
                if (kind == ModelProviderRegistry.ProviderKind.CLOUD) {
                    return 1;
                }
                return 100 + kind.ordinal();
            default:
                throw new IllegalStateException("unknown route mode");
        }
    }

    private static int maximumSelections(ModelContractV2.FallbackPolicy fallbackPolicy) {
        switch (fallbackPolicy) {
            case NO_FALLBACK:
                return 1;
            case SAME_PRIVACY_TIER_ONLY:
            case POLICY_CONTROLLED:
                return MAX_SELECTED_PROVIDERS;
            default:
                throw new IllegalStateException("unknown fallback policy");
        }
    }

    private static boolean privacyAllows(
            ModelContractV2.PrivacyClass maximumPrivacyClass,
            ModelContractV2.PrivacyClass requestPrivacyClass) {
        switch (maximumPrivacyClass) {
            case PUBLIC:
                return requestPrivacyClass == ModelContractV2.PrivacyClass.PUBLIC;
            case INTERNAL:
                return requestPrivacyClass == ModelContractV2.PrivacyClass.PUBLIC
                        || requestPrivacyClass == ModelContractV2.PrivacyClass.INTERNAL;
            case SENSITIVE:
                return requestPrivacyClass != ModelContractV2.PrivacyClass.RESTRICTED;
            case RESTRICTED:
                return true;
            default:
                throw new IllegalStateException("unknown privacy class");
        }
    }

    private static Map<String, RouteProfile> createRouteProfiles() {
        Map<String, RouteProfile> profiles = new LinkedHashMap<>();
        addProfile(profiles, new RouteProfile(
                ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                1,
                ModelContractV2.PrivacyClass.RESTRICTED,
                false));
        addProfile(profiles, new RouteProfile(
                ModelProviderRegistry.ANDROID_LOCAL_DEVELOPMENT_ID,
                100,
                ModelContractV2.PrivacyClass.RESTRICTED,
                true));
        addProfile(profiles, new RouteProfile(
                ModelProviderRegistry.VENDOR_NPU_PLACEHOLDER_ID,
                50,
                ModelContractV2.PrivacyClass.RESTRICTED,
                true));
        addProfile(profiles, new RouteProfile(
                ModelProviderRegistry.CLOUD_PLACEHOLDER_ID,
                500,
                ModelContractV2.PrivacyClass.INTERNAL,
                false));
        return Collections.unmodifiableMap(profiles);
    }

    private static void addProfile(Map<String, RouteProfile> profiles, RouteProfile profile) {
        if (profiles.put(profile.providerId, profile) != null) {
            throw new IllegalStateException("duplicate route profile");
        }
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] encoded = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte item : encoded) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
