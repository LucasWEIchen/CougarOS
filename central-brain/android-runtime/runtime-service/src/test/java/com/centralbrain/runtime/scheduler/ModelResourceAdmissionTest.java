package com.centralbrain.runtime.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.model.ModelContractV2;
import com.centralbrain.runtime.model.ModelProviderRegistry;
import com.centralbrain.runtime.model.PolicyAwareModelRouter;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class ModelResourceAdmissionTest {
    private static final long NOW = 1_000L;
    private static final String DIGEST_A = digest('a');
    private static final String DIGEST_B = digest('b');
    private static final String DIGEST_C = digest('c');
    private static final String DIGEST_D = digest('d');

    @Test
    public void foregroundVehicleWorkPrecedesQueuedBackgroundWork() {
        AtomicLong clock = new AtomicLong(NOW);
        InferenceResourceScheduler scheduler = scheduler(clock, 8, 8, 2);
        PolicyAwareModelRouter.PolicySnapshot policy = policy(
                PolicyAwareModelRouter.ThermalState.NOMINAL, 900, 2_000);
        ModelContractV2.ModelRequest background = request(
                "resource.background",
                ModelContractV2.Purpose.CONTEXT_SUMMARY,
                4_000,
                2_560);
        ModelContractV2.ModelRequest foreground = request(
                "resource.foreground",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                4_000,
                2_560);

        assertEquals(
                ModelResourceAdmission.DecisionCode.ADMITTED,
                admit(
                        background,
                        policy,
                        resource(policy, ModelResourceAdmission.CapacityState.AVAILABLE, 2),
                        context(
                                DIGEST_A,
                                ModelResourceAdmission.WorkloadClass.BACKGROUND_MAINTENANCE),
                        scheduler,
                        NOW).getCode());
        ModelResourceAdmission.AdmissionDecision foregroundDecision = admit(
                foreground,
                policy,
                resource(policy, ModelResourceAdmission.CapacityState.AVAILABLE, 2),
                context(
                        DIGEST_B,
                        ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE),
                scheduler,
                NOW);

        assertEquals(ModelResourceAdmission.DecisionCode.ADMITTED,
                foregroundDecision.getCode());
        assertEquals(InferenceResourceScheduler.EffectivePriority.HIGH,
                foregroundDecision.getEffectivePriority());
        assertEquals(
                foreground.getRequestId(),
                scheduler.claimNext().getLease().getActive().getRequestId());
    }

    @Test
    public void elevatedOrConstrainedForegroundUsesBoundedCompactBudget() {
        PolicyAwareModelRouter.PolicySnapshot elevated = policy(
                PolicyAwareModelRouter.ThermalState.ELEVATED, 900, 2_000);
        ModelContractV2.ModelRequest request = request(
                "resource.compact",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                6_000,
                2_560);
        ModelResourceAdmission.AdmissionDecision decision = admit(
                request,
                elevated,
                resource(elevated, ModelResourceAdmission.CapacityState.CONSTRAINED, 1),
                context(
                        DIGEST_A,
                        ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE),
                scheduler(new AtomicLong(NOW), 4, 4, 1),
                NOW);

        assertEquals(ModelResourceAdmission.DecisionCode.ADMITTED_DEGRADED,
                decision.getCode());
        assertEquals(ModelResourceAdmission.DegradationMode.COMPACT_FOREGROUND,
                decision.getDegradationMode());
        assertEquals(ModelResourceAdmission.COMPACT_INPUT_TOKEN_LIMIT,
                decision.getEffectiveInputTokenLimit());
        assertEquals(ModelResourceAdmission.COMPACT_OUTPUT_TOKEN_LIMIT,
                decision.getEffectiveOutputTokenLimit());
        assertEquals(
                ModelResourceAdmission.COMPACT_INPUT_TOKEN_LIMIT
                        + ModelResourceAdmission.COMPACT_OUTPUT_TOKEN_LIMIT,
                decision.getEffectiveTotalTokenLimit());
        assertEquals(ModelResourceAdmission.FOREGROUND_MAX_QUEUE_WAIT_MS,
                decision.getEffectiveMaxQueueWaitMs());
    }

    @Test
    public void hotStateOnlyAdmitsMinimalForegroundSafetyClassification() {
        AtomicLong clock = new AtomicLong(NOW);
        InferenceResourceScheduler scheduler = scheduler(clock, 4, 4, 2);
        PolicyAwareModelRouter.PolicySnapshot hot = policy(
                PolicyAwareModelRouter.ThermalState.HOT, 900, 2_000);
        ModelResourceAdmission.ResourceSnapshot available = resource(
                hot, ModelResourceAdmission.CapacityState.AVAILABLE, 1);
        ModelResourceAdmission.AdmissionContext foreground = context(
                DIGEST_A, ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE);

        ModelResourceAdmission.AdmissionDecision safety = admit(
                request(
                        "resource.safety",
                        ModelContractV2.Purpose.SAFETY_CLASSIFICATION,
                        4_000,
                        2_560),
                hot,
                available,
                foreground,
                scheduler,
                NOW);
        int queuedAfterSafety = scheduler.snapshot().getQueuedCount();
        ModelResourceAdmission.AdmissionDecision scenario = admit(
                request(
                        "resource.hot.scenario",
                        ModelContractV2.Purpose.SCENARIO_REASONING,
                        4_000,
                        2_560),
                hot,
                available,
                foreground,
                scheduler,
                NOW);

        assertEquals(ModelResourceAdmission.DecisionCode.ADMITTED_DEGRADED,
                safety.getCode());
        assertEquals(ModelResourceAdmission.DegradationMode.MINIMAL_SAFETY,
                safety.getDegradationMode());
        assertEquals(ModelResourceAdmission.MINIMAL_SAFETY_INPUT_TOKEN_LIMIT,
                safety.getEffectiveInputTokenLimit());
        assertEquals(ModelResourceAdmission.MINIMAL_SAFETY_OUTPUT_TOKEN_LIMIT,
                safety.getEffectiveOutputTokenLimit());
        assertEquals(ModelResourceAdmission.DecisionCode.THERMAL_BLOCKED,
                scenario.getCode());
        assertEquals(queuedAfterSafety, scheduler.snapshot().getQueuedCount());
        assertNull(scenario.getActiveSnapshot());
    }

    @Test
    public void unknownCriticalAndExhaustedStatesFailClosedWithoutQueueMutation() {
        assertPreSchedulerRejection(
                PolicyAwareModelRouter.ThermalState.UNKNOWN,
                ModelResourceAdmission.CapacityState.AVAILABLE,
                1,
                ModelResourceAdmission.DecisionCode.THERMAL_BLOCKED);
        assertPreSchedulerRejection(
                PolicyAwareModelRouter.ThermalState.CRITICAL,
                ModelResourceAdmission.CapacityState.AVAILABLE,
                1,
                ModelResourceAdmission.DecisionCode.THERMAL_BLOCKED);
        assertPreSchedulerRejection(
                PolicyAwareModelRouter.ThermalState.NOMINAL,
                ModelResourceAdmission.CapacityState.EXHAUSTED,
                0,
                ModelResourceAdmission.DecisionCode.RESOURCE_BLOCKED);
        assertPreSchedulerRejection(
                PolicyAwareModelRouter.ThermalState.NOMINAL,
                ModelResourceAdmission.CapacityState.UNKNOWN,
                0,
                ModelResourceAdmission.DecisionCode.RESOURCE_BLOCKED);
    }

    @Test
    public void staleFutureAndMismatchedBindingsFailBeforeScheduler() {
        AtomicLong clock = new AtomicLong(NOW);
        InferenceResourceScheduler scheduler = scheduler(clock, 8, 8, 2);
        ModelContractV2.ModelRequest request = request(
                "resource.freshness",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                4_000,
                2_560);
        ModelResourceAdmission.AdmissionContext context = context(
                DIGEST_A, ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE);
        PolicyAwareModelRouter.PolicySnapshot expires = policy(
                PolicyAwareModelRouter.ThermalState.NOMINAL, 900, 1_500);
        PolicyAwareModelRouter.RouteDecision expiresRoute = route(request, expires, NOW);
        ModelResourceAdmission.ResourceSnapshot longResource = resource(
                expires,
                ModelResourceAdmission.CapacityState.AVAILABLE,
                1,
                900,
                2_000);
        ModelResourceAdmission.AdmissionDecision stalePolicy = ModelResourceAdmission.admit(
                request,
                expiresRoute,
                expires,
                longResource,
                context,
                scheduler,
                1_500);

        PolicyAwareModelRouter.PolicySnapshot future = policy(
                PolicyAwareModelRouter.ThermalState.NOMINAL, 1_100, 2_000);
        ModelResourceAdmission.AdmissionDecision futurePolicy = ModelResourceAdmission.admit(
                request,
                route(request, future, 1_200),
                future,
                resource(
                        future,
                        ModelResourceAdmission.CapacityState.AVAILABLE,
                        1,
                        900,
                        2_000),
                context,
                scheduler,
                NOW);

        PolicyAwareModelRouter.PolicySnapshot fresh = policy(
                PolicyAwareModelRouter.ThermalState.NOMINAL, 900, 2_000);
        ModelResourceAdmission.ResourceSnapshot staleResource = resource(
                fresh,
                ModelResourceAdmission.CapacityState.AVAILABLE,
                1,
                900,
                1_001);
        ModelResourceAdmission.AdmissionDecision expiredResource = ModelResourceAdmission.admit(
                request,
                route(request, fresh, NOW),
                fresh,
                staleResource,
                context,
                scheduler,
                1_001);

        PolicyAwareModelRouter.PolicySnapshot other = new PolicyAwareModelRouter.PolicySnapshot(
                PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                PolicyAwareModelRouter.ThermalState.NOMINAL,
                100,
                10_000,
                2,
                900,
                2_000,
                DIGEST_D);
        ModelResourceAdmission.AdmissionDecision mismatch = ModelResourceAdmission.admit(
                request,
                route(request, fresh, NOW),
                other,
                resource(other, ModelResourceAdmission.CapacityState.AVAILABLE, 1),
                context,
                scheduler,
                NOW);

        assertEquals(ModelResourceAdmission.DecisionCode.POLICY_SNAPSHOT_REJECTED,
                stalePolicy.getCode());
        assertEquals(ModelResourceAdmission.SnapshotRejection.STALE,
                stalePolicy.getPolicySnapshotRejection());
        assertEquals(ModelResourceAdmission.SnapshotRejection.FROM_FUTURE,
                futurePolicy.getPolicySnapshotRejection());
        assertEquals(ModelResourceAdmission.DecisionCode.RESOURCE_SNAPSHOT_REJECTED,
                expiredResource.getCode());
        assertEquals(ModelResourceAdmission.SnapshotRejection.STALE,
                expiredResource.getResourceSnapshotRejection());
        assertEquals(ModelResourceAdmission.DecisionCode.POLICY_BINDING_MISMATCH,
                mismatch.getCode());
        assertEquals(0, scheduler.snapshot().getActiveCount());
    }

    @Test
    public void replayAndDegradedSchedulerRejectionPreserveTypedOutcomes() {
        AtomicLong clock = new AtomicLong(NOW);
        InferenceResourceScheduler scheduler = scheduler(clock, 1, 1, 1);
        PolicyAwareModelRouter.PolicySnapshot elevated = policy(
                PolicyAwareModelRouter.ThermalState.ELEVATED, 900, 2_000);
        ModelResourceAdmission.ResourceSnapshot resource = resource(
                elevated, ModelResourceAdmission.CapacityState.AVAILABLE, 1);
        ModelResourceAdmission.AdmissionContext context = context(
                DIGEST_A, ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE);
        ModelContractV2.ModelRequest first = request(
                "resource.replay",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                4_000,
                2_560);

        ModelResourceAdmission.AdmissionDecision admitted = admit(
                first, elevated, resource, context, scheduler, NOW);
        ModelResourceAdmission.AdmissionDecision replayed = admit(
                first, elevated, resource, context, scheduler, NOW);
        ModelResourceAdmission.AdmissionDecision quota = admit(
                request(
                        "resource.quota",
                        ModelContractV2.Purpose.SCENARIO_REASONING,
                        4_000,
                        2_560),
                elevated,
                resource,
                context,
                scheduler,
                NOW);

        assertEquals(ModelResourceAdmission.DecisionCode.ADMITTED_DEGRADED,
                admitted.getCode());
        assertEquals(ModelResourceAdmission.DecisionCode.REPLAYED_DEGRADED,
                replayed.getCode());
        assertEquals(ModelResourceAdmission.SchedulerAdmission.REPLAYED,
                replayed.getSchedulerAdmission());
        assertEquals(ModelResourceAdmission.DecisionCode.SCHEDULER_REJECTED,
                quota.getCode());
        assertEquals(
                ModelResourceAdmission.SchedulerAdmission.GLOBAL_QUEUE_QUOTA_EXCEEDED,
                quota.getSchedulerAdmission());
        assertEquals(ModelResourceAdmission.DegradationMode.COMPACT_FOREGROUND,
                quota.getDegradationMode());
        assertTrue(quota.isDegraded());
        assertFalse(quota.isAdmitted());
        assertNull(quota.getActiveSnapshot());
    }

    @Test
    public void workloadMismatchAndAuthorityBoundariesRemainClosed() {
        AtomicLong clock = new AtomicLong(NOW);
        InferenceResourceScheduler scheduler = scheduler(clock, 4, 4, 1);
        PolicyAwareModelRouter.PolicySnapshot nominal = policy(
                PolicyAwareModelRouter.ThermalState.NOMINAL, 900, 2_000);
        ModelContractV2.ModelRequest request = request(
                "resource.boundary",
                ModelContractV2.Purpose.USER_DIALOGUE,
                4_000,
                2_560);
        ModelResourceAdmission.AdmissionDecision decision = admit(
                request,
                nominal,
                resource(nominal, ModelResourceAdmission.CapacityState.AVAILABLE, 1),
                context(
                        DIGEST_A,
                        ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE),
                scheduler,
                NOW);

        assertEquals(ModelResourceAdmission.DecisionCode.WORKLOAD_PURPOSE_MISMATCH,
                decision.getCode());
        assertEquals(0, scheduler.snapshot().getActiveCount());
        assertFalse(decision.isProviderInvoked());
        assertFalse(decision.isModelInvoked());
        assertFalse(decision.isActionAuthorizationGranted());
        assertFalse(decision.isEffectDispatchRequested());
        assertFalse(decision.isRuntimeWired());
        assertFalse(decision.isHardwareAccessed());
        assertFalse(decision.isProductionQualified());
        assertNotEquals(DIGEST_A, decision.getDecisionDigest());
    }

    @Test
    public void decisionDigestBindsCompleteActiveSchedulerState() {
        PolicyAwareModelRouter.PolicySnapshot nominal = policy(
                PolicyAwareModelRouter.ThermalState.NOMINAL, 900, 2_000);
        ModelContractV2.ModelRequest request = request(
                "resource.digest",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                4_000,
                2_560);
        ModelResourceAdmission.ResourceSnapshot resource = resource(
                nominal, ModelResourceAdmission.CapacityState.AVAILABLE, 1);
        ModelResourceAdmission.AdmissionContext context = context(
                DIGEST_A, ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE);

        ModelResourceAdmission.AdmissionDecision first = admit(
                request,
                nominal,
                resource,
                context,
                scheduler(new AtomicLong(NOW), 4, 4, 1),
                NOW);
        ModelResourceAdmission.AdmissionDecision second = admit(
                request,
                nominal,
                resource,
                context,
                scheduler(new AtomicLong(NOW + 1), 4, 4, 1),
                NOW);

        assertEquals(ModelResourceAdmission.DecisionCode.ADMITTED, first.getCode());
        assertEquals(ModelResourceAdmission.DecisionCode.ADMITTED, second.getCode());
        assertNotEquals(
                first.getActiveSnapshot().getAdmittedAtElapsedRealtimeMs(),
                second.getActiveSnapshot().getAdmittedAtElapsedRealtimeMs());
        assertNotEquals(first.getDecisionDigest(), second.getDecisionDigest());
    }

    private static void assertPreSchedulerRejection(
            PolicyAwareModelRouter.ThermalState thermalState,
            ModelResourceAdmission.CapacityState capacityState,
            int slots,
            ModelResourceAdmission.DecisionCode expected) {
        AtomicLong clock = new AtomicLong(NOW);
        InferenceResourceScheduler scheduler = scheduler(clock, 4, 4, 1);
        PolicyAwareModelRouter.PolicySnapshot policy = policy(thermalState, 900, 2_000);
        ModelResourceAdmission.AdmissionDecision decision = admit(
                request(
                        "resource.reject." + thermalState.name().toLowerCase()
                                + "." + capacityState.name().toLowerCase(),
                        ModelContractV2.Purpose.SCENARIO_REASONING,
                        4_000,
                        2_560),
                policy,
                resource(policy, capacityState, slots),
                context(
                        DIGEST_A,
                        ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE),
                scheduler,
                NOW);
        assertEquals(expected, decision.getCode());
        assertEquals(ModelResourceAdmission.SchedulerAdmission.NOT_SUBMITTED,
                decision.getSchedulerAdmission());
        assertEquals(0, scheduler.snapshot().getActiveCount());
    }

    private static ModelResourceAdmission.AdmissionDecision admit(
            ModelContractV2.ModelRequest request,
            PolicyAwareModelRouter.PolicySnapshot policy,
            ModelResourceAdmission.ResourceSnapshot resource,
            ModelResourceAdmission.AdmissionContext context,
            InferenceResourceScheduler scheduler,
            long now) {
        return ModelResourceAdmission.admit(
                request,
                route(request, policy, now),
                policy,
                resource,
                context,
                scheduler,
                now);
    }

    private static PolicyAwareModelRouter.RouteDecision route(
            ModelContractV2.ModelRequest request,
            PolicyAwareModelRouter.PolicySnapshot policy,
            long now) {
        return PolicyAwareModelRouter.decide(
                request,
                policy,
                healthyRegistry().snapshot(now),
                now);
    }

    private static ModelProviderRegistry healthyRegistry() {
        ModelProviderRegistry registry = ModelProviderRegistry.createForContractTest();
        registry.publishHealth(
                new ModelProviderRegistry.HealthReport(
                        ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                        ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                        ModelProviderRegistry.HealthState.HEALTHY,
                        1,
                        900,
                        2_000,
                        DIGEST_C),
                NOW);
        return registry;
    }

    private static PolicyAwareModelRouter.PolicySnapshot policy(
            PolicyAwareModelRouter.ThermalState thermalState,
            long observedAt,
            long validUntil) {
        return new PolicyAwareModelRouter.PolicySnapshot(
                PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                thermalState,
                100,
                10_000,
                1,
                observedAt,
                validUntil,
                DIGEST_D);
    }

    private static ModelResourceAdmission.ResourceSnapshot resource(
            PolicyAwareModelRouter.PolicySnapshot policy,
            ModelResourceAdmission.CapacityState capacityState,
            int slots) {
        return resource(policy, capacityState, slots, 900, 2_000);
    }

    private static ModelResourceAdmission.ResourceSnapshot resource(
            PolicyAwareModelRouter.PolicySnapshot policy,
            ModelResourceAdmission.CapacityState capacityState,
            int slots,
            long observedAt,
            long validUntil) {
        return new ModelResourceAdmission.ResourceSnapshot(
                ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                capacityState,
                slots,
                1,
                observedAt,
                validUntil,
                DIGEST_C,
                policy);
    }

    private static ModelResourceAdmission.AdmissionContext context(
            String owner,
            ModelResourceAdmission.WorkloadClass workloadClass) {
        return new ModelResourceAdmission.AdmissionContext(
                owner,
                "central-intent-v0",
                workloadClass);
    }

    private static ModelContractV2.ModelRequest request(
            String requestId,
            ModelContractV2.Purpose purpose,
            long latencyMs,
            int totalTokens) {
        return new ModelContractV2.ModelRequest(
                requestId,
                purpose,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(latencyMs),
                new ModelContractV2.TokenBudget(2_048, 512, totalTokens),
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                DIGEST_A,
                DIGEST_B);
    }

    private static InferenceResourceScheduler scheduler(
            AtomicLong clock,
            int maxQueuedGlobal,
            int maxQueuedPerOwner,
            int slots) {
        AtomicInteger leases = new AtomicInteger();
        return new InferenceResourceScheduler(
                new InferenceResourceScheduler.Limits(
                        maxQueuedGlobal,
                        maxQueuedPerOwner,
                        slots,
                        slots,
                        10_000),
                clock::get,
                () -> "lease-resource-" + leases.incrementAndGet(),
                Collections.singletonList(
                        InferenceResourceScheduler.RouteTarget.forFixedAdmissionContract(
                                ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                                slots,
                                true)));
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }
}
