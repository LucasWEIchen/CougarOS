package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PolicyAwareModelRouterTest {
    private static final String DIGEST_A = digest('a');
    private static final String DIGEST_B = digest('b');

    @Test
    public void healthyContractProviderIsSelectedAndBoundToRequest() {
        ModelContractV2.ModelRequest request = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                1_000,
                512);
        PolicyAwareModelRouter.RouteDecision decision = decide(
                request,
                policy(PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                        PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                        PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                        PolicyAwareModelRouter.ThermalState.CRITICAL,
                        10,
                        10_000,
                        900,
                        1_500),
                healthyRegistry(),
                1_000);

        assertEquals(PolicyAwareModelRouter.DecisionCode.SELECTED, decision.getCode());
        assertEquals(ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                decision.getPrimaryProviderId());
        assertEquals(request.getRequestId(), decision.getRequestId());
        assertEquals(request.getRequestFingerprint(), decision.getRequestFingerprint());
        assertEquals(request.getTraceId(), decision.getTraceId());
        assertTrue(decision.getFallbackProviderIds().isEmpty());
        assertNotEquals(DIGEST_A, decision.getDecisionDigest());
    }

    @Test
    public void privacyAndNetworkPolicyRejectCloudBeforeExecution() {
        ModelContractV2.ModelRequest request = request(
                ModelContractV2.PrivacyClass.RESTRICTED,
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                2_000,
                512);
        PolicyAwareModelRouter.RouteDecision decision = decide(
                request,
                policy(PolicyAwareModelRouter.RouteMode.PRODUCTION,
                        PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                        PolicyAwareModelRouter.NetworkState.UNMETERED,
                        PolicyAwareModelRouter.ThermalState.NOMINAL,
                        10,
                        10_000,
                        900,
                        1_500),
                healthyRegistry(),
                1_000);
        PolicyAwareModelRouter.CandidateEvaluation cloud = find(
                decision,
                ModelProviderRegistry.CLOUD_PLACEHOLDER_ID);

        assertEquals(PolicyAwareModelRouter.DecisionCode.NO_ELIGIBLE_PROVIDER,
                decision.getCode());
        assertTrue(cloud.getRejectionReasons().contains(
                PolicyAwareModelRouter.RejectionReason.PRIVACY_BLOCKED));
        assertTrue(cloud.getRejectionReasons().contains(
                PolicyAwareModelRouter.RejectionReason.NETWORK_POLICY_BLOCKED));
        assertFalse(decision.isNetworkAccessed());
    }

    @Test
    public void thermalLatencyAndCapabilityAdmissionAreExplicit() {
        ModelContractV2.ModelRequest request = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE,
                ModelContractV2.FallbackPolicy.SAME_PRIVACY_TIER_ONLY,
                25,
                512);
        PolicyAwareModelRouter.RouteDecision decision = decide(
                request,
                policy(PolicyAwareModelRouter.RouteMode.PRODUCTION,
                        PolicyAwareModelRouter.NetworkPolicy.ALLOW_ANY,
                        PolicyAwareModelRouter.NetworkState.UNMETERED,
                        PolicyAwareModelRouter.ThermalState.HOT,
                        10,
                        10_000,
                        900,
                        1_500),
                healthyRegistry(),
                1_000);
        PolicyAwareModelRouter.CandidateEvaluation vendor = find(
                decision,
                ModelProviderRegistry.VENDOR_NPU_PLACEHOLDER_ID);
        PolicyAwareModelRouter.CandidateEvaluation deterministic = find(
                decision,
                ModelProviderRegistry.DETERMINISTIC_TEST_ID);

        assertTrue(vendor.getRejectionReasons().contains(
                PolicyAwareModelRouter.RejectionReason.THERMAL_BLOCKED));
        assertTrue(vendor.getRejectionReasons().contains(
                PolicyAwareModelRouter.RejectionReason.LATENCY_BUDGET_TOO_SMALL));
        assertTrue(deterministic.getRejectionReasons().contains(
                PolicyAwareModelRouter.RejectionReason.CAPABILITY_MISSING));
    }

    @Test
    public void quotaAndPolicyFreshnessFailClosed() {
        ModelContractV2.ModelRequest request = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                1_000,
                512);
        PolicyAwareModelRouter.RouteDecision stale = decide(
                request,
                policy(PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                        PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                        PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                        PolicyAwareModelRouter.ThermalState.NOMINAL,
                        10,
                        10_000,
                        100,
                        500),
                healthyRegistry(),
                1_000);
        PolicyAwareModelRouter.RouteDecision future = decide(
                request,
                policy(PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                        PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                        PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                        PolicyAwareModelRouter.ThermalState.NOMINAL,
                        10,
                        10_000,
                        1_100,
                        1_500),
                healthyRegistry(),
                1_000);
        PolicyAwareModelRouter.RouteDecision exhausted = decide(
                request,
                policy(PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                        PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                        PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                        PolicyAwareModelRouter.ThermalState.NOMINAL,
                        0,
                        128,
                        900,
                        1_500),
                healthyRegistry(),
                1_000);

        assertEquals(PolicyAwareModelRouter.DecisionCode.POLICY_SNAPSHOT_REJECTED,
                stale.getCode());
        assertEquals(PolicyAwareModelRouter.PolicyRejection.SNAPSHOT_STALE,
                stale.getPolicyRejection());
        assertEquals(PolicyAwareModelRouter.DecisionCode.POLICY_SNAPSHOT_REJECTED,
                future.getCode());
        assertEquals(PolicyAwareModelRouter.PolicyRejection.SNAPSHOT_FROM_FUTURE,
                future.getPolicyRejection());
        PolicyAwareModelRouter.CandidateEvaluation deterministic = find(
                exhausted,
                ModelProviderRegistry.DETERMINISTIC_TEST_ID);
        assertTrue(deterministic.getRejectionReasons().contains(
                PolicyAwareModelRouter.RejectionReason.REQUEST_QUOTA_EXHAUSTED));
        assertTrue(deterministic.getRejectionReasons().contains(
                PolicyAwareModelRouter.RejectionReason.TOKEN_QUOTA_EXCEEDED));
    }

    @Test
    public void fallbackIsBoundedByRequestPolicy() {
        ModelContractV2.ModelRequest noFallback = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                1_000,
                512);
        ModelContractV2.ModelRequest boundedFallback = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.SAME_PRIVACY_TIER_ONLY,
                1_000,
                512);
        PolicyAwareModelRouter.PolicySnapshot policy = policy(
                PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                PolicyAwareModelRouter.ThermalState.NOMINAL,
                10,
                10_000,
                900,
                1_500);

        PolicyAwareModelRouter.RouteDecision one = decide(
                noFallback, policy, healthyRegistry(), 1_000);
        PolicyAwareModelRouter.RouteDecision two = decide(
                boundedFallback, policy, healthyRegistry(), 1_000);
        assertEquals(1, one.getMaximumSelectedProviders());
        assertEquals(PolicyAwareModelRouter.MAX_SELECTED_PROVIDERS,
                two.getMaximumSelectedProviders());
        assertTrue(one.isFallbackBounded());
        assertTrue(two.isFallbackBounded());
        assertTrue(two.getFallbackProviderIds().size()
                <= PolicyAwareModelRouter.MAX_FALLBACK_PROVIDERS);
    }

    @Test
    public void productionAndActionExecutionBoundariesRemainClosed() {
        ModelContractV2.ModelRequest request = request(
                ModelContractV2.PrivacyClass.PUBLIC,
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.POLICY_CONTROLLED,
                2_000,
                512);
        PolicyAwareModelRouter.RouteDecision decision = decide(
                request,
                policy(PolicyAwareModelRouter.RouteMode.PRODUCTION,
                        PolicyAwareModelRouter.NetworkPolicy.ALLOW_ANY,
                        PolicyAwareModelRouter.NetworkState.UNMETERED,
                        PolicyAwareModelRouter.ThermalState.NOMINAL,
                        10,
                        10_000,
                        900,
                        1_500),
                healthyRegistry(),
                1_000);

        assertEquals(PolicyAwareModelRouter.DecisionCode.NO_ELIGIBLE_PROVIDER,
                decision.getCode());
        assertFalse(decision.isActionAuthorizationGranted());
        assertFalse(decision.isEffectDispatchRequested());
        assertFalse(decision.isProviderInvoked());
        assertFalse(decision.isModelInvoked());
        assertFalse(decision.isNetworkAccessed());
        assertFalse(decision.isNpuAccessed());
        assertFalse(decision.isHardwareAccessed());
    }

    @Test
    public void targetIntegrationSelectsOpenClawWithoutGrantingProductionAuthority() {
        ModelContractV2.ModelRequest request = request(
                ModelContractV2.PrivacyClass.INTERNAL,
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                120_000,
                512);
        PolicyAwareModelRouter.RouteDecision decision = decide(
                request,
                policy(PolicyAwareModelRouter.RouteMode.TARGET_INTEGRATION,
                        PolicyAwareModelRouter.NetworkPolicy.ALLOW_ANY,
                        PolicyAwareModelRouter.NetworkState.UNMETERED,
                        PolicyAwareModelRouter.ThermalState.NOMINAL,
                        1,
                        512,
                        900,
                        1_500),
                targetOpenClawRegistry(),
                1_000);

        assertEquals(PolicyAwareModelRouter.DecisionCode.SELECTED, decision.getCode());
        assertEquals(ModelProviderRegistry.TARGET_OPENCLAW_TRANSITIONAL_ID,
                decision.getPrimaryProviderId());
        assertFalse(decision.isActionAuthorizationGranted());
        assertFalse(decision.isEffectDispatchRequested());
        assertFalse(decision.isProviderInvoked());
        assertFalse(decision.isModelInvoked());
        assertFalse(decision.isNpuAccessed());
        assertFalse(decision.isHardwareAccessed());
    }

    private static PolicyAwareModelRouter.RouteDecision decide(
            ModelContractV2.ModelRequest request,
            PolicyAwareModelRouter.PolicySnapshot policy,
            ModelProviderRegistry registry,
            long nowElapsedMs) {
        return PolicyAwareModelRouter.decide(
                request,
                policy,
                registry.snapshot(nowElapsedMs),
                nowElapsedMs);
    }

    private static ModelProviderRegistry healthyRegistry() {
        ModelProviderRegistry registry = ModelProviderRegistry.createForContractTest();
        registry.publishHealth(new ModelProviderRegistry.HealthReport(
                ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                ModelProviderRegistry.HealthState.HEALTHY,
                1,
                900,
                1_500,
                DIGEST_A), 1_000);
        return registry;
    }

    private static ModelProviderRegistry targetOpenClawRegistry() {
        ModelProviderRegistry registry = ModelProviderRegistry.createForContractTest();
        registry.publishHealth(new ModelProviderRegistry.HealthReport(
                ModelProviderRegistry.TARGET_OPENCLAW_TRANSITIONAL_ID,
                ModelProviderRegistry.HealthSource.TARGET_OPENCLAW_RUNTIME,
                ModelProviderRegistry.HealthState.HEALTHY,
                1,
                900,
                1_500,
                DIGEST_A), 1_000);
        return registry;
    }

    private static ModelContractV2.ModelRequest request(
            ModelContractV2.PrivacyClass privacyClass,
            ModelContractV2.RequiredCapability requiredCapability,
            ModelContractV2.FallbackPolicy fallbackPolicy,
            long latencyMs,
            int totalTokens) {
        return new ModelContractV2.ModelRequest(
                "request.router",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                privacyClass,
                new ModelContractV2.LatencyBudget(latencyMs),
                new ModelContractV2.TokenBudget(256, 256, totalTokens),
                requiredCapability,
                fallbackPolicy,
                DIGEST_A,
                DIGEST_B);
    }

    private static PolicyAwareModelRouter.PolicySnapshot policy(
            PolicyAwareModelRouter.RouteMode mode,
            PolicyAwareModelRouter.NetworkPolicy networkPolicy,
            PolicyAwareModelRouter.NetworkState networkState,
            PolicyAwareModelRouter.ThermalState thermalState,
            int remainingRequests,
            int remainingTokens,
            long observedAt,
            long validUntil) {
        return new PolicyAwareModelRouter.PolicySnapshot(
                mode,
                networkPolicy,
                networkState,
                thermalState,
                remainingRequests,
                remainingTokens,
                1,
                observedAt,
                validUntil,
                DIGEST_B);
    }

    private static PolicyAwareModelRouter.CandidateEvaluation find(
            PolicyAwareModelRouter.RouteDecision decision,
            String providerId) {
        for (PolicyAwareModelRouter.CandidateEvaluation candidate
                : decision.getCandidateEvaluations()) {
            if (providerId.equals(candidate.getProviderId())) {
                return candidate;
            }
        }
        throw new AssertionError("candidate not found: " + providerId);
    }

    private static String digest(char value) {
        StringBuilder builder = new StringBuilder(64);
        for (int index = 0; index < 64; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
