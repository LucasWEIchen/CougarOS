package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class ModelContractV2Test {
    private static final String TRACE = digest('a');
    private static final String INPUT = digest('b');
    private static final String OUTPUT = digest('c');

    @Test
    public void requestCarriesRequiredFieldsAndStableFingerprint() {
        ModelContractV2.ModelRequest first = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.FallbackPolicy.POLICY_CONTROLLED);
        ModelContractV2.ModelRequest replay = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.FallbackPolicy.POLICY_CONTROLLED);

        assertEquals(2, first.getSchemaVersion());
        assertEquals(ModelContractV2.Purpose.SCENARIO_REASONING, first.getPurpose());
        assertEquals(ModelContractV2.PrivacyClass.INTERNAL, first.getPrivacyClass());
        assertEquals(1_500L, first.getLatencyBudget().getMaximumEndToEndMs());
        assertEquals(2_048, first.getTokenBudget().getMaximumInputTokens());
        assertEquals(512, first.getTokenBudget().getMaximumOutputTokens());
        assertEquals(2_560, first.getTokenBudget().getMaximumTotalTokens());
        assertEquals(
                ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE,
                first.getRequiredCapability());
        assertEquals(ModelContractV2.FallbackPolicy.POLICY_CONTROLLED,
                first.getFallbackPolicy());
        assertEquals(TRACE, first.getTraceId());
        assertEquals(INPUT, first.getInputDigest());
        assertEquals(first.getRequestFingerprint(), replay.getRequestFingerprint());
    }

    @Test
    public void requestFingerprintChangesWhenRoutingInputChanges() {
        ModelContractV2.ModelRequest internal = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.FallbackPolicy.POLICY_CONTROLLED);
        ModelContractV2.ModelRequest sensitive = request(
                ModelContractV2.PrivacyClass.SENSITIVE,
                ModelContractV2.FallbackPolicy.SAME_PRIVACY_TIER_ONLY);

        assertNotEquals(internal.getRequestFingerprint(), sensitive.getRequestFingerprint());
    }

    @Test
    public void privacyAndFallbackCombinationFailsClosed() {
        expectInvalid(() -> request(
                ModelContractV2.PrivacyClass.SENSITIVE,
                ModelContractV2.FallbackPolicy.POLICY_CONTROLLED));
        expectInvalid(() -> request(
                ModelContractV2.PrivacyClass.RESTRICTED,
                ModelContractV2.FallbackPolicy.SAME_PRIVACY_TIER_ONLY));

        ModelContractV2.ModelRequest restricted = request(
                ModelContractV2.PrivacyClass.RESTRICTED,
                ModelContractV2.FallbackPolicy.NO_FALLBACK);
        assertEquals(ModelContractV2.FallbackPolicy.NO_FALLBACK,
                restricted.getFallbackPolicy());
    }

    @Test
    public void latencyTokenAndDigestBoundsFailClosed() {
        expectInvalid(() -> new ModelContractV2.LatencyBudget(0));
        expectInvalid(() -> new ModelContractV2.LatencyBudget(120_001));
        expectInvalid(() -> new ModelContractV2.TokenBudget(0, 1, 1));
        expectInvalid(() -> new ModelContractV2.TokenBudget(100, 20, 10));
        expectInvalid(() -> new ModelContractV2.ModelRequest(
                "request-1",
                ModelContractV2.Purpose.USER_DIALOGUE,
                ModelContractV2.PrivacyClass.PUBLIC,
                new ModelContractV2.LatencyBudget(1_000),
                new ModelContractV2.TokenBudget(100, 20, 120),
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.POLICY_CONTROLLED,
                "raw-trace",
                INPUT));
    }

    @Test
    public void resultIsBoundToRequestAndTokenBudget() {
        ModelContractV2.ModelRequest request = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.FallbackPolicy.POLICY_CONTROLLED);
        ModelContractV2.ModelResult result = ModelContractV2.ModelResult.completed(
                request,
                "deterministic.stub",
                OUTPUT,
                1_900,
                500);

        assertEquals(request.getRequestId(), result.getRequestId());
        assertEquals(request.getRequestFingerprint(), result.getRequestFingerprint());
        assertEquals(request.getTraceId(), result.getTraceId());
        assertEquals(ModelContractV2.ResultState.COMPLETED, result.getState());
        assertEquals(ModelContractV2.DetailCode.OUTPUT_ACCEPTED, result.getDetailCode());
        assertFalse(result.isActionAuthorizationGranted());
        assertFalse(result.isEffectDispatchRequested());

        expectInvalid(() -> ModelContractV2.ModelResult.completed(
                request,
                "deterministic.stub",
                OUTPUT,
                2_049,
                1));
        expectInvalid(() -> ModelContractV2.ModelResult.terminal(
                request,
                "deterministic.stub",
                ModelContractV2.ResultState.POLICY_BLOCKED,
                100,
                ModelContractV2.DetailCode.PROVIDER_TERMINAL_FAILURE));
    }

    @Test
    public void v2ContractDoesNotWireProviderRouterOrHardware() {
        ModelContractV2.ContractSnapshot snapshot = ModelContractV2.snapshot();

        assertTrue(snapshot.isContractV2Defined());
        assertFalse(snapshot.isRawContentAccepted());
        assertFalse(snapshot.isProviderRegistryWired());
        assertFalse(snapshot.isPolicyRouterWired());
        assertFalse(snapshot.isModelInvoked());
        assertFalse(snapshot.isNpuAccessed());
        assertFalse(snapshot.isHardwareAccessed());
        assertEquals("InferenceRequest", ModelProvider.InferenceRequest.class.getSimpleName());
    }

    private static ModelContractV2.ModelRequest request(
            ModelContractV2.PrivacyClass privacyClass,
            ModelContractV2.FallbackPolicy fallbackPolicy) {
        return new ModelContractV2.ModelRequest(
                "request-1",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                privacyClass,
                new ModelContractV2.LatencyBudget(1_500),
                new ModelContractV2.TokenBudget(2_048, 512, 2_560),
                ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE,
                fallbackPolicy,
                TRACE,
                INPUT);
    }

    private static String digest(char value) {
        StringBuilder builder = new StringBuilder(64);
        for (int index = 0; index < 64; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed contract rejection.
        }
    }
}
