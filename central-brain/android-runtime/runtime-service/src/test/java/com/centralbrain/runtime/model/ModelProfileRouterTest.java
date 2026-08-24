package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class ModelProfileRouterTest {
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Test
    public void smokingVisionRequestSelectsOnlyReady2bProfile() {
        ModelProfileRouter.RouteDecision decision = ModelProfileRouter.decide(
                ModelProfileRouter.SMOKING_SCENARIO_ID,
                request(
                        ModelContractV2.RequiredCapability.VISION_CLASSIFICATION,
                        ModelContractV2.FallbackPolicy.NO_FALLBACK),
                general(true, 900, 1_500),
                smoking(true, 900, 1_500),
                1_000);

        assertEquals(ModelProfileRouter.DecisionCode.SELECTED, decision.getCode());
        assertEquals(
                ModelProfileRouter.WorkloadClass.CABIN_SMOKING_COMPLIANCE,
                decision.getWorkloadClass());
        assertEquals(ModelProfileRouter.SMOKING_PROFILE_ID, decision.getProfileId());
        assertEquals("central-vision-smoking-v1", decision.getModelId());
        assertEquals("Qwen3.5-2B-AWQ", decision.getServedModelName());
        assertEquals(4_096, decision.getMaximumContextTokens());
        assertFalse(decision.isFallbackSelected());
        assertFalse(decision.isModelInvoked());
        assertFalse(decision.isActionAuthorizationGranted());
        assertFalse(decision.isEffectDispatchRequested());
    }

    @Test
    public void textAndNonSmokingVisionRequestsSelectGeneral9bProfile() {
        for (ModelContractV2.RequiredCapability capability : new ModelContractV2.RequiredCapability[] {
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.RequiredCapability.VISION_CLASSIFICATION,
                ModelContractV2.RequiredCapability.SUMMARIZATION}) {
            ModelProfileRouter.RouteDecision decision = ModelProfileRouter.decide(
                    "scene.aios.freeform.v1",
                    request(capability, ModelContractV2.FallbackPolicy.NO_FALLBACK),
                    general(true, 900, 1_500),
                    smoking(true, 900, 1_500),
                    1_000);

            assertEquals(ModelProfileRouter.DecisionCode.SELECTED, decision.getCode());
            assertEquals(ModelProfileRouter.GENERAL_PROFILE_ID, decision.getProfileId());
            assertEquals("central-intent-general-v1", decision.getModelId());
            assertEquals("Qwen3.5-9B-AWQ", decision.getServedModelName());
            assertEquals(8_192, decision.getMaximumContextTokens());
        }
    }

    @Test
    public void requiredTargetFailureDoesNotFallbackToOtherModel() {
        ModelProfileRouter.RouteDecision smokingUnavailable = ModelProfileRouter.decide(
                ModelProfileRouter.SMOKING_SCENARIO_ID,
                request(
                        ModelContractV2.RequiredCapability.VISION_CLASSIFICATION,
                        ModelContractV2.FallbackPolicy.NO_FALLBACK),
                general(true, 900, 1_500),
                smoking(false, 900, 1_500),
                1_000);
        ModelProfileRouter.RouteDecision generalStale = ModelProfileRouter.decide(
                "scene.fatigue.assist.v1",
                request(
                        ModelContractV2.RequiredCapability.TEXT_GENERATION,
                        ModelContractV2.FallbackPolicy.NO_FALLBACK),
                general(true, 800, 999),
                smoking(true, 900, 1_500),
                1_000);

        assertEquals(
                ModelProfileRouter.DecisionCode.TARGET_UNAVAILABLE,
                smokingUnavailable.getCode());
        assertEquals(
                ModelProfileRouter.RejectionReason.TARGET_NOT_READY,
                smokingUnavailable.getRejectionReason());
        assertEquals("none", smokingUnavailable.getModelId());
        assertFalse(smokingUnavailable.isFallbackSelected());
        assertEquals(
                ModelProfileRouter.DecisionCode.TARGET_UNAVAILABLE,
                generalStale.getCode());
        assertEquals(
                ModelProfileRouter.RejectionReason.TARGET_HEALTH_STALE,
                generalStale.getRejectionReason());
    }

    @Test
    public void targetEthernetRoutesGeneralWorkloadToExplicitShared2bProfile() {
        ModelProfileRouter.RouteDecision decision = ModelProfileRouter.decide(
                "scene.comfort.cold.v1",
                request(
                        ModelContractV2.RequiredCapability.TEXT_GENERATION,
                        ModelContractV2.FallbackPolicy.NO_FALLBACK),
                targetGeneral(true, 900, 1_500),
                smoking(true, 900, 1_500),
                ModelProfileRouter.DeploymentProfile.SINGLE_2B_TARGET_ETHERNET,
                1_000);

        assertEquals(ModelProfileRouter.DecisionCode.SELECTED, decision.getCode());
        assertEquals(ModelProfileRouter.TARGET_GENERAL_PROFILE_ID, decision.getProfileId());
        assertEquals(ModelProfileRouter.GENERAL_MODEL_ID, decision.getModelId());
        assertEquals("Qwen3.5-2B-AWQ", decision.getServedModelName());
        assertEquals(8_192, decision.getMaximumContextTokens());
    }

    @Test
    public void requestContractAndFixedProfileIdentityFailClosed() {
        ModelProfileRouter.RouteDecision wrongCapability = ModelProfileRouter.decide(
                ModelProfileRouter.SMOKING_SCENARIO_ID,
                request(
                        ModelContractV2.RequiredCapability.TEXT_GENERATION,
                        ModelContractV2.FallbackPolicy.NO_FALLBACK),
                general(true, 900, 1_500),
                smoking(true, 900, 1_500),
                1_000);
        ModelProfileRouter.RouteDecision fallback = ModelProfileRouter.decide(
                "scene.comfort.cold.v1",
                request(
                        ModelContractV2.RequiredCapability.TEXT_GENERATION,
                        ModelContractV2.FallbackPolicy.POLICY_CONTROLLED),
                general(true, 900, 1_500),
                smoking(true, 900, 1_500),
                1_000);
        ModelProfileRouter.TargetHealth drifted = new ModelProfileRouter.TargetHealth(
                ModelProfileRouter.GENERAL_PROFILE_ID,
                "central-intent-general-v1",
                "Qwen3.5-9B-AWQ",
                4_096,
                true,
                1,
                900,
                1_500,
                DIGEST_A);
        ModelProfileRouter.RouteDecision identity = ModelProfileRouter.decide(
                "scene.comfort.cold.v1",
                request(
                        ModelContractV2.RequiredCapability.TEXT_GENERATION,
                        ModelContractV2.FallbackPolicy.NO_FALLBACK),
                drifted,
                smoking(true, 900, 1_500),
                1_000);

        assertEquals(
                ModelProfileRouter.RejectionReason.SMOKING_REQUIRES_VISION,
                wrongCapability.getRejectionReason());
        assertEquals(
                ModelProfileRouter.RejectionReason.FALLBACK_POLICY_REJECTED,
                fallback.getRejectionReason());
        assertEquals(
                ModelProfileRouter.RejectionReason.TARGET_IDENTITY_MISMATCH,
                identity.getRejectionReason());
    }

    private static ModelContractV2.ModelRequest request(
            ModelContractV2.RequiredCapability capability,
            ModelContractV2.FallbackPolicy fallback) {
        return new ModelContractV2.ModelRequest(
                "request.model-profile",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(120_000),
                new ModelContractV2.TokenBudget(128, 128, 256),
                capability,
                fallback,
                DIGEST_A,
                DIGEST_B);
    }

    private static ModelProfileRouter.TargetHealth general(
            boolean ready, long observedAt, long validUntil) {
        return new ModelProfileRouter.TargetHealth(
                ModelProfileRouter.GENERAL_PROFILE_ID,
                "central-intent-general-v1",
                "Qwen3.5-9B-AWQ",
                ModelProfileRouter.GENERAL_MAX_CONTEXT_TOKENS,
                ready,
                1,
                observedAt,
                validUntil,
                DIGEST_A);
    }

    private static ModelProfileRouter.TargetHealth smoking(
            boolean ready, long observedAt, long validUntil) {
        return new ModelProfileRouter.TargetHealth(
                ModelProfileRouter.SMOKING_PROFILE_ID,
                "central-vision-smoking-v1",
                "Qwen3.5-2B-AWQ",
                ModelProfileRouter.SMOKING_MAX_CONTEXT_TOKENS,
                ready,
                1,
                observedAt,
                validUntil,
                DIGEST_B);
    }

    private static ModelProfileRouter.TargetHealth targetGeneral(
            boolean ready, long observedAt, long validUntil) {
        return new ModelProfileRouter.TargetHealth(
                ModelProfileRouter.TARGET_GENERAL_PROFILE_ID,
                ModelProfileRouter.GENERAL_MODEL_ID,
                "Qwen3.5-2B-AWQ",
                ModelProfileRouter.GENERAL_MAX_CONTEXT_TOKENS,
                ready,
                1,
                observedAt,
                validUntil,
                DIGEST_A);
    }
}
