package com.centralbrain.runtime.effects;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.effects.AdapterRegistry.PrepareResult;
import com.centralbrain.runtime.effects.AdapterRegistry.PreparedMaterial;
import com.centralbrain.runtime.effects.AdapterRegistry.Profile;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class EffectCoordinatorProbeActivity extends Activity {
    private static final String TAG = "CbEffectCoordinator";
    private static final long NOW = 1_750_000_000_000L;
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private static final String PLAN_ID = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
    private static final String ACTION_ID = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
    private static final String EFFECT_A = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
    private static final String EFFECT_B = "c36ba71d-8757-4399-a30a-7234f7833c2e";
    private static final String EFFECT_C = "234f296f-94a6-4d01-a6a4-591985ca79a7";
    private static final String DESTINATION = "debug.vehicle.effect";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            EffectBatch plannedBatch = EffectBatch.create(
                    "692bde18-dd9e-4378-908b-2c249c77c673",
                    Arrays.asList(
                            entry(intent(EFFECT_A, true, "effect:a"), "vehicle:hvac:driver"),
                            entry(intent(EFFECT_B, true, "effect:b"), "vehicle:hvac:driver"),
                            new EffectBatch.Entry(
                                    intent(EFFECT_C, true, "effect:c"),
                                    "vehicle:seat:driver",
                                    Collections.singletonList(EFFECT_A))),
                    NOW);
            EffectDependencyPlanner.Plan dependencyPlan =
                    new EffectDependencyPlanner().plan(plannedBatch);
            boolean batchDefined = plannedBatch.getEntries().size() == 3
                    && plannedBatch.getBatchDigest().length() == 64;
            boolean dependencyPlanVerified = dependencyPlan.getWaveIndex(EFFECT_A)
                    < dependencyPlan.getWaveIndex(EFFECT_C);
            boolean conflictSerialized = dependencyPlan.getWaveIndex(EFFECT_A)
                    < dependencyPlan.getWaveIndex(EFFECT_B);

            AtomicInteger requiredPrepareCalls = new AtomicInteger();
            FakeAdapter requiredAdapter = new FakeAdapter();
            EffectCoordinator requiredCoordinator = coordinator(
                    requiredPrepareCalls, EFFECT_B, requiredAdapter);
            EffectCoordinator.ExecutionResult requiredResult = requiredCoordinator.execute(
                    simpleBatch(true), Profile.DEBUG_SIMULATION, NOW);
            boolean prepareAllRequiredVerified = requiredPrepareCalls.get() == 2
                    && requiredAdapter.applyCalls.get() == 0
                    && requiredResult.getStatus()
                            == EffectCoordinator.BatchStatus.PREPARE_REJECTED;

            AtomicInteger optionalPrepareCalls = new AtomicInteger();
            FakeAdapter optionalAdapter = new FakeAdapter();
            EffectCoordinator optionalCoordinator = coordinator(
                    optionalPrepareCalls, EFFECT_B, optionalAdapter);
            EffectCoordinator.ExecutionResult optionalResult = optionalCoordinator.execute(
                    simpleBatch(false), Profile.DEBUG_SIMULATION, NOW);
            boolean optionalDegradationVerified = optionalPrepareCalls.get() == 2
                    && optionalAdapter.applyCalls.get() == 1
                    && optionalResult.getStatus() == EffectCoordinator.BatchStatus.PARTIAL;

            FakeAdapter mixedAdapter = new FakeAdapter();
            mixedAdapter.states.put(EFFECT_B, EffectAdapter.ApplyState.UNKNOWN);
            EffectCoordinator.ExecutionResult mixedResult = coordinator(
                    new AtomicInteger(), "", mixedAdapter).execute(
                            simpleBatch(true), Profile.DEBUG_SIMULATION, NOW);
            boolean independentObservationVerified = mixedResult.getItemResults().size() == 2
                    && !mixedResult.getItemResults().get(0).getObservation().observationId.equals(
                            mixedResult.getItemResults().get(1).getObservation().observationId)
                    && mixedResult.getItemResults().get(1).getOutcome()
                            == EffectCoordinator.Outcome.UNKNOWN;

            AdapterRegistry isolatedRegistry = registry(
                    new AtomicInteger(), "", new FakeAdapter());
            isolatedRegistry.resolve(
                    "vehicle.hvac.temperature",
                    "vehicle.cabin.driver",
                    Profile.DEBUG_SIMULATION);
            boolean profileIsolationVerified;
            try {
                isolatedRegistry.resolve(
                        "vehicle.hvac.temperature",
                        "vehicle.cabin.driver",
                        Profile.PRODUCTION);
                profileIsolationVerified = false;
            } catch (AdapterRegistry.AdapterUnavailableException expected) {
                profileIsolationVerified = true;
            }

            boolean allVerified = batchDefined
                    && dependencyPlanVerified
                    && conflictSerialized
                    && profileIsolationVerified
                    && prepareAllRequiredVerified
                    && optionalDegradationVerified
                    && independentObservationVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            Log.i(TAG, "nonce=" + nonce
                    + " effect_coordinator_probe_complete=" + allVerified
                    + " effect_batch_defined=" + batchDefined
                    + " effect_dependency_plan_verified=" + dependencyPlanVerified
                    + " effect_resource_conflict_serialized=" + conflictSerialized
                    + " effect_adapter_registry_profile_isolation_verified="
                    + profileIsolationVerified
                    + " effect_prepare_all_required_verified=" + prepareAllRequiredVerified
                    + " effect_optional_degradation_verified=" + optionalDegradationVerified
                    + " effect_independent_observation_verified="
                    + independentObservationVerified
                    + " effect_coordinator_android13_arm64_verified="
                    + android13Arm64Verified
                    + " effect_coordinator_graph_wired=false"
                    + " effect_coordinator_persistence_wired=false"
                    + " production_effect_adapter_registered=false"
                    + " production_effect_dispatch_enabled=false"
                    + " effect_verification_reconciliation_wired=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " effect_coordinator_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " effect_coordinator_graph_wired=false"
                    + " effect_coordinator_persistence_wired=false"
                    + " production_effect_adapter_registered=false"
                    + " production_effect_dispatch_enabled=false"
                    + " effect_verification_reconciliation_wired=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static EffectBatch simpleBatch(boolean secondRequired) {
        return EffectBatch.create(
                "792bde18-dd9e-4378-908b-2c249c77c674",
                Arrays.asList(
                        entry(intent(EFFECT_A, true, "effect:a"), "vehicle:hvac:driver"),
                        entry(intent(EFFECT_B, secondRequired, "effect:b"),
                                "vehicle:seat:driver")),
                NOW);
    }

    private static EffectBatch.Entry entry(EffectIntent intent, String resourceKey) {
        return new EffectBatch.Entry(intent, resourceKey, Collections.emptyList());
    }

    private static EffectCoordinator coordinator(
            AtomicInteger prepareCalls,
            String rejectedEffectId,
            FakeAdapter adapter) {
        return new EffectCoordinator(
                registry(prepareCalls, rejectedEffectId, adapter),
                new EffectDependencyPlanner());
    }

    private static AdapterRegistry registry(
            AtomicInteger prepareCalls,
            String rejectedEffectId,
            FakeAdapter adapter) {
        AdapterRegistry.PreparationAdapter preparation = (intent, nowEpochMs) -> {
            prepareCalls.incrementAndGet();
            if (intent.effectId.equals(rejectedEffectId)) {
                return PrepareResult.rejected("PREPARE_REJECTED");
            }
            return PrepareResult.ready(new PreparedMaterial(
                    intent.actionId,
                    DESTINATION,
                    ("payload:" + intent.effectId).getBytes(StandardCharsets.UTF_8),
                    ("envelope:" + intent.effectId).getBytes(StandardCharsets.UTF_8),
                    digest('b'),
                    digest('c')));
        };
        return new AdapterRegistry(Collections.singletonList(
                new AdapterRegistry.Registration(
                        "adapter.debug.vehicle",
                        "vehicle.hvac.temperature",
                        "vehicle.cabin.driver",
                        Profile.DEBUG_SIMULATION,
                        true,
                        true,
                        false,
                        preparation,
                        adapter)));
    }

    private static EffectIntent intent(String effectId, boolean required, String key) {
        EffectIntent intent = new EffectIntent();
        intent.effectId = effectId;
        intent.sessionId = SESSION_ID;
        intent.planId = PLAN_ID;
        intent.nodeId = "apply-hvac";
        intent.actionId = ACTION_ID;
        intent.capabilityId = "vehicle.hvac.temperature";
        intent.targetArea = "vehicle.cabin.driver";
        intent.valueKind = EffectContract.VALUE_DECIMAL;
        intent.decimalValue = 21.5;
        intent.unit = "celsius";
        intent.targetValueDigest = digest('d');
        intent.idempotencyKey = key;
        intent.planDigest = digest('e');
        intent.contextDigest = digest('f');
        intent.contextVersion = 7;
        intent.riskClass = EffectContract.RISK_LOW;
        intent.required = required;
        intent.verificationPolicy = EffectContract.VERIFY_REPORTED_TOLERANCE;
        intent.verificationTolerance = 0.5;
        intent.reversible = true;
        intent.compensationDigest = digest('a');
        intent.createdAtEpochMs = NOW - 1_000L;
        intent.deadlineEpochMs = NOW + 60_000L;
        return intent;
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static final class FakeAdapter implements EffectAdapter {
        private final AtomicInteger applyCalls = new AtomicInteger();
        private final Map<String, ApplyState> states = new HashMap<>();

        @Override
        public Descriptor descriptor() {
            return new Descriptor(
                    "adapter.debug.vehicle",
                    DESTINATION,
                    IdempotencyMode.TOKEN_DEDUPLICATED,
                    true,
                    true,
                    StatusConsistency.LINEARIZABLE,
                    1_000L);
        }

        @Override
        public ApplyResult apply(Invocation invocation) {
            applyCalls.incrementAndGet();
            return new ApplyResult(
                    invocation.getIdempotencyToken(),
                    states.containsKey(invocation.getEffectId())
                            ? states.get(invocation.getEffectId())
                            : ApplyState.APPLIED,
                    digest('9'));
        }

        @Override
        public StatusResult queryStatus(String idempotencyToken) {
            return new StatusResult(idempotencyToken, DeliveryState.UNKNOWN, digest('8'));
        }
    }
}
