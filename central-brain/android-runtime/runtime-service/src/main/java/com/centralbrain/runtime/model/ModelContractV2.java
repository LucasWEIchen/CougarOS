package com.centralbrain.runtime.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Digest-only ModelRequest/ModelResult v2 contract.
 * Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, DEL-001, DEL-004, DEL-005.
 */
public final class ModelContractV2 {
    public static final int SCHEMA_VERSION = 2;
    public static final long MAX_LATENCY_BUDGET_MS = 120_000L;
    public static final int MAX_INPUT_TOKENS = 32_768;
    public static final int MAX_OUTPUT_TOKENS = 8_192;
    public static final int MAX_TOTAL_TOKENS = 40_960;
    public static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private ModelContractV2() {
    }

    public enum Purpose {
        USER_DIALOGUE,
        SCENARIO_REASONING,
        CONTEXT_SUMMARY,
        SAFETY_CLASSIFICATION
    }

    public enum PrivacyClass {
        PUBLIC,
        INTERNAL,
        SENSITIVE,
        RESTRICTED
    }

    public enum RequiredCapability {
        TEXT_GENERATION,
        VISION_CLASSIFICATION,
        STRUCTURED_SCENARIO_CANDIDATE,
        SUMMARIZATION,
        CLASSIFICATION
    }

    public enum FallbackPolicy {
        NO_FALLBACK,
        SAME_PRIVACY_TIER_ONLY,
        POLICY_CONTROLLED
    }

    public enum ResultState {
        COMPLETED,
        CANCELLED,
        DEADLINE_EXCEEDED,
        CAPABILITY_UNAVAILABLE,
        POLICY_BLOCKED,
        RETRYABLE_FAILURE,
        TERMINAL_FAILURE
    }

    public enum DetailCode {
        OUTPUT_ACCEPTED,
        CALLER_CANCELLED,
        LATENCY_BUDGET_EXHAUSTED,
        REQUIRED_CAPABILITY_MISSING,
        PRIVACY_POLICY_BLOCKED,
        PROVIDER_RETRYABLE_FAILURE,
        PROVIDER_TERMINAL_FAILURE
    }

    public static final class LatencyBudget {
        private final long maximumEndToEndMs;

        public LatencyBudget(long maximumEndToEndMs) {
            if (maximumEndToEndMs < 1 || maximumEndToEndMs > MAX_LATENCY_BUDGET_MS) {
                throw new IllegalArgumentException(
                        "maximumEndToEndMs must be in range 1..120000");
            }
            this.maximumEndToEndMs = maximumEndToEndMs;
        }

        public long getMaximumEndToEndMs() {
            return maximumEndToEndMs;
        }
    }

    public static final class TokenBudget {
        private final int maximumInputTokens;
        private final int maximumOutputTokens;
        private final int maximumTotalTokens;

        public TokenBudget(
                int maximumInputTokens,
                int maximumOutputTokens,
                int maximumTotalTokens) {
            if (maximumInputTokens < 1 || maximumInputTokens > MAX_INPUT_TOKENS) {
                throw new IllegalArgumentException("maximumInputTokens is out of range");
            }
            if (maximumOutputTokens < 1 || maximumOutputTokens > MAX_OUTPUT_TOKENS) {
                throw new IllegalArgumentException("maximumOutputTokens is out of range");
            }
            if (maximumTotalTokens < Math.max(maximumInputTokens, maximumOutputTokens)
                    || maximumTotalTokens > maximumInputTokens + maximumOutputTokens
                    || maximumTotalTokens > MAX_TOTAL_TOKENS) {
                throw new IllegalArgumentException("maximumTotalTokens is inconsistent");
            }
            this.maximumInputTokens = maximumInputTokens;
            this.maximumOutputTokens = maximumOutputTokens;
            this.maximumTotalTokens = maximumTotalTokens;
        }

        public int getMaximumInputTokens() {
            return maximumInputTokens;
        }

        public int getMaximumOutputTokens() {
            return maximumOutputTokens;
        }

        public int getMaximumTotalTokens() {
            return maximumTotalTokens;
        }
    }

    public static final class ModelRequest {
        private final String requestId;
        private final Purpose purpose;
        private final PrivacyClass privacyClass;
        private final LatencyBudget latencyBudget;
        private final TokenBudget tokenBudget;
        private final RequiredCapability requiredCapability;
        private final FallbackPolicy fallbackPolicy;
        private final String traceId;
        private final String inputDigest;
        private final String requestFingerprint;

        public ModelRequest(
                String requestId,
                Purpose purpose,
                PrivacyClass privacyClass,
                LatencyBudget latencyBudget,
                TokenBudget tokenBudget,
                RequiredCapability requiredCapability,
                FallbackPolicy fallbackPolicy,
                String traceId,
                String inputDigest) {
            this.requestId = requireIdentifier(requestId, "requestId");
            this.purpose = Objects.requireNonNull(purpose, "purpose");
            this.privacyClass = Objects.requireNonNull(privacyClass, "privacyClass");
            this.latencyBudget = Objects.requireNonNull(latencyBudget, "latencyBudget");
            this.tokenBudget = Objects.requireNonNull(tokenBudget, "tokenBudget");
            this.requiredCapability = Objects.requireNonNull(
                    requiredCapability,
                    "requiredCapability");
            this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
            this.traceId = requireDigest(traceId, "traceId");
            this.inputDigest = requireDigest(inputDigest, "inputDigest");
            requireFallbackCompatibleWithPrivacy(privacyClass, fallbackPolicy);
            this.requestFingerprint = sha256(canonicalForm());
        }

        public int getSchemaVersion() {
            return SCHEMA_VERSION;
        }

        public String getRequestId() {
            return requestId;
        }

        public Purpose getPurpose() {
            return purpose;
        }

        public PrivacyClass getPrivacyClass() {
            return privacyClass;
        }

        public LatencyBudget getLatencyBudget() {
            return latencyBudget;
        }

        public TokenBudget getTokenBudget() {
            return tokenBudget;
        }

        public RequiredCapability getRequiredCapability() {
            return requiredCapability;
        }

        public FallbackPolicy getFallbackPolicy() {
            return fallbackPolicy;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getInputDigest() {
            return inputDigest;
        }

        public String getRequestFingerprint() {
            return requestFingerprint;
        }

        private String canonicalForm() {
            return SCHEMA_VERSION
                    + "|" + requestId
                    + "|" + purpose.name()
                    + "|" + privacyClass.name()
                    + "|" + latencyBudget.getMaximumEndToEndMs()
                    + "|" + tokenBudget.getMaximumInputTokens()
                    + "|" + tokenBudget.getMaximumOutputTokens()
                    + "|" + tokenBudget.getMaximumTotalTokens()
                    + "|" + requiredCapability.name()
                    + "|" + fallbackPolicy.name()
                    + "|" + traceId
                    + "|" + inputDigest;
        }
    }

    public static final class ModelResult {
        private final String requestId;
        private final String requestFingerprint;
        private final String traceId;
        private final String providerId;
        private final ResultState state;
        private final String outputDigest;
        private final int inputTokensUsed;
        private final int outputTokensUsed;
        private final DetailCode detailCode;

        private ModelResult(
                ModelRequest request,
                String providerId,
                ResultState state,
                String outputDigest,
                int inputTokensUsed,
                int outputTokensUsed,
                DetailCode detailCode) {
            ModelRequest checkedRequest = Objects.requireNonNull(request, "request");
            this.requestId = checkedRequest.getRequestId();
            this.requestFingerprint = checkedRequest.getRequestFingerprint();
            this.traceId = checkedRequest.getTraceId();
            this.providerId = requireIdentifier(providerId, "providerId");
            this.state = Objects.requireNonNull(state, "state");
            this.outputDigest = requireDigest(outputDigest, "outputDigest");
            this.detailCode = Objects.requireNonNull(detailCode, "detailCode");
            requireUsageWithinBudget(checkedRequest, inputTokensUsed, outputTokensUsed);
            requireStateConsistent(state, outputDigest, outputTokensUsed, detailCode);
            this.inputTokensUsed = inputTokensUsed;
            this.outputTokensUsed = outputTokensUsed;
        }

        public static ModelResult completed(
                ModelRequest request,
                String providerId,
                String outputDigest,
                int inputTokensUsed,
                int outputTokensUsed) {
            return new ModelResult(
                    request,
                    providerId,
                    ResultState.COMPLETED,
                    outputDigest,
                    inputTokensUsed,
                    outputTokensUsed,
                    DetailCode.OUTPUT_ACCEPTED);
        }

        public static ModelResult terminal(
                ModelRequest request,
                String providerId,
                ResultState state,
                int inputTokensUsed,
                DetailCode detailCode) {
            if (state == ResultState.COMPLETED) {
                throw new IllegalArgumentException("completed result must use completed factory");
            }
            return new ModelResult(
                    request,
                    providerId,
                    state,
                    EMPTY_SHA256,
                    inputTokensUsed,
                    0,
                    detailCode);
        }

        public int getSchemaVersion() {
            return SCHEMA_VERSION;
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

        public String getProviderId() {
            return providerId;
        }

        public ResultState getState() {
            return state;
        }

        public String getOutputDigest() {
            return outputDigest;
        }

        public int getInputTokensUsed() {
            return inputTokensUsed;
        }

        public int getOutputTokensUsed() {
            return outputTokensUsed;
        }

        public DetailCode getDetailCode() {
            return detailCode;
        }

        public boolean isActionAuthorizationGranted() {
            return false;
        }

        public boolean isEffectDispatchRequested() {
            return false;
        }
    }

    public static final class ContractSnapshot {
        public boolean isContractV2Defined() {
            return true;
        }

        public boolean isRawContentAccepted() {
            return false;
        }

        public boolean isProviderRegistryWired() {
            return false;
        }

        public boolean isPolicyRouterWired() {
            return false;
        }

        public boolean isModelInvoked() {
            return false;
        }

        public boolean isNpuAccessed() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }
    }

    public static ContractSnapshot snapshot() {
        return new ContractSnapshot();
    }

    private static void requireFallbackCompatibleWithPrivacy(
            PrivacyClass privacyClass,
            FallbackPolicy fallbackPolicy) {
        if ((privacyClass == PrivacyClass.SENSITIVE
                || privacyClass == PrivacyClass.RESTRICTED)
                && fallbackPolicy == FallbackPolicy.POLICY_CONTROLLED) {
            throw new IllegalArgumentException(
                    "sensitive and restricted requests cannot use policy-controlled fallback");
        }
        if (privacyClass == PrivacyClass.RESTRICTED
                && fallbackPolicy != FallbackPolicy.NO_FALLBACK) {
            throw new IllegalArgumentException(
                    "restricted requests cannot leave the selected provider");
        }
    }

    private static void requireUsageWithinBudget(
            ModelRequest request,
            int inputTokensUsed,
            int outputTokensUsed) {
        TokenBudget budget = request.getTokenBudget();
        if (inputTokensUsed < 0
                || outputTokensUsed < 0
                || inputTokensUsed > budget.getMaximumInputTokens()
                || outputTokensUsed > budget.getMaximumOutputTokens()
                || inputTokensUsed + outputTokensUsed > budget.getMaximumTotalTokens()) {
            throw new IllegalArgumentException("result token usage exceeds request budget");
        }
    }

    private static void requireStateConsistent(
            ResultState state,
            String outputDigest,
            int outputTokensUsed,
            DetailCode detailCode) {
        if (state == ResultState.COMPLETED) {
            if (EMPTY_SHA256.equals(outputDigest)
                    || outputTokensUsed < 1
                    || detailCode != DetailCode.OUTPUT_ACCEPTED) {
                throw new IllegalArgumentException("completed result is inconsistent");
            }
            return;
        }
        if (!EMPTY_SHA256.equals(outputDigest) || outputTokensUsed != 0) {
            throw new IllegalArgumentException("terminal failure cannot contain model output");
        }
        boolean matchingDetail =
                (state == ResultState.CANCELLED && detailCode == DetailCode.CALLER_CANCELLED)
                        || (state == ResultState.DEADLINE_EXCEEDED
                                && detailCode == DetailCode.LATENCY_BUDGET_EXHAUSTED)
                        || (state == ResultState.CAPABILITY_UNAVAILABLE
                                && detailCode == DetailCode.REQUIRED_CAPABILITY_MISSING)
                        || (state == ResultState.POLICY_BLOCKED
                                && detailCode == DetailCode.PRIVACY_POLICY_BLOCKED)
                        || (state == ResultState.RETRYABLE_FAILURE
                                && detailCode == DetailCode.PROVIDER_RETRYABLE_FAILURE)
                        || (state == ResultState.TERMINAL_FAILURE
                                && detailCode == DetailCode.PROVIDER_TERMINAL_FAILURE);
        if (!matchingDetail) {
            throw new IllegalArgumentException("result state and detail code do not agree");
        }
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
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
