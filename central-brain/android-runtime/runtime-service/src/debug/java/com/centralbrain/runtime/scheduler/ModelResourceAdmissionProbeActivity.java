package com.centralbrain.runtime.scheduler;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.model.ModelContractV2;
import com.centralbrain.runtime.model.ModelProviderRegistry;
import com.centralbrain.runtime.model.PolicyAwareModelRouter;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ModelResourceAdmissionProbeActivity extends Activity {
    private static final String TAG = "CbResourceProbe";
    private static final long NOW = 1_000L;
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String DIGEST_C = "c".repeat(64);
    private static final String DIGEST_D = "d".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            PolicyAwareModelRouter.PolicySnapshot nominal = policy(
                    PolicyAwareModelRouter.ThermalState.NOMINAL);
            InferenceResourceScheduler orderScheduler = scheduler(4, 2);
            ModelResourceAdmission.AdmissionDecision background = admit(
                    request("probe.background", ModelContractV2.Purpose.CONTEXT_SUMMARY),
                    nominal,
                    ModelResourceAdmission.CapacityState.AVAILABLE,
                    ModelResourceAdmission.WorkloadClass.BACKGROUND_MAINTENANCE,
                    DIGEST_A,
                    orderScheduler);
            ModelResourceAdmission.AdmissionDecision foreground = admit(
                    request("probe.foreground", ModelContractV2.Purpose.SCENARIO_REASONING),
                    nominal,
                    ModelResourceAdmission.CapacityState.AVAILABLE,
                    ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE,
                    DIGEST_B,
                    orderScheduler);
            InferenceResourceScheduler.Claim first = orderScheduler.claimNext();
            boolean foregroundPriorityVerified = background.isAdmitted()
                    && foreground.isAdmitted()
                    && foreground.getEffectivePriority()
                            == InferenceResourceScheduler.EffectivePriority.HIGH
                    && first.getLease() != null
                    && "probe.foreground".equals(
                            first.getLease().getActive().getRequestId());

            PolicyAwareModelRouter.PolicySnapshot elevated = policy(
                    PolicyAwareModelRouter.ThermalState.ELEVATED);
            ModelResourceAdmission.AdmissionDecision compact = admit(
                    request("probe.compact", ModelContractV2.Purpose.SCENARIO_REASONING),
                    elevated,
                    ModelResourceAdmission.CapacityState.CONSTRAINED,
                    ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE,
                    DIGEST_A,
                    scheduler(2, 1));
            PolicyAwareModelRouter.PolicySnapshot hot = policy(
                    PolicyAwareModelRouter.ThermalState.HOT);
            ModelResourceAdmission.AdmissionDecision minimalSafety = admit(
                    request("probe.safety", ModelContractV2.Purpose.SAFETY_CLASSIFICATION),
                    hot,
                    ModelResourceAdmission.CapacityState.AVAILABLE,
                    ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE,
                    DIGEST_A,
                    scheduler(2, 1));
            boolean thermalDegradationVerified = compact.getCode()
                            == ModelResourceAdmission.DecisionCode.ADMITTED_DEGRADED
                    && compact.getDegradationMode()
                            == ModelResourceAdmission.DegradationMode.COMPACT_FOREGROUND
                    && compact.getEffectiveInputTokenLimit()
                            == ModelResourceAdmission.COMPACT_INPUT_TOKEN_LIMIT
                    && compact.getEffectiveOutputTokenLimit()
                            == ModelResourceAdmission.COMPACT_OUTPUT_TOKEN_LIMIT
                    && minimalSafety.getCode()
                            == ModelResourceAdmission.DecisionCode.ADMITTED_DEGRADED
                    && minimalSafety.getDegradationMode()
                            == ModelResourceAdmission.DegradationMode.MINIMAL_SAFETY
                    && minimalSafety.getEffectiveInputTokenLimit()
                            == ModelResourceAdmission.MINIMAL_SAFETY_INPUT_TOKEN_LIMIT
                    && minimalSafety.getEffectiveOutputTokenLimit()
                            == ModelResourceAdmission.MINIMAL_SAFETY_OUTPUT_TOKEN_LIMIT;

            InferenceResourceScheduler blockedScheduler = scheduler(2, 1);
            ModelResourceAdmission.AdmissionDecision hotScenario = admit(
                    request("probe.hot.block", ModelContractV2.Purpose.SCENARIO_REASONING),
                    hot,
                    ModelResourceAdmission.CapacityState.AVAILABLE,
                    ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE,
                    DIGEST_A,
                    blockedScheduler);
            ModelResourceAdmission.AdmissionDecision exhausted = admit(
                    request("probe.exhausted", ModelContractV2.Purpose.SCENARIO_REASONING),
                    nominal,
                    ModelResourceAdmission.CapacityState.EXHAUSTED,
                    ModelResourceAdmission.WorkloadClass.FOREGROUND_VEHICLE,
                    DIGEST_A,
                    blockedScheduler);
            boolean thermalResourceFailClosedVerified = hotScenario.getCode()
                            == ModelResourceAdmission.DecisionCode.THERMAL_BLOCKED
                    && exhausted.getCode()
                            == ModelResourceAdmission.DecisionCode.RESOURCE_BLOCKED
                    && blockedScheduler.snapshot().getActiveCount() == 0;
            boolean boundaryVerified = !foreground.isProviderInvoked()
                    && !foreground.isModelInvoked()
                    && !foreground.isActionAuthorizationGranted()
                    && !foreground.isEffectDispatchRequested()
                    && !foreground.isRuntimeWired()
                    && !foreground.isHardwareAccessed()
                    && !foreground.isProductionQualified();
            boolean verified = foregroundPriorityVerified
                    && thermalDegradationVerified
                    && thermalResourceFailClosedVerified
                    && boundaryVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " resource_admission_probe_complete=true"
                    + " model_resource_admission_verified=" + verified
                    + " foreground_vehicle_priority_verified="
                    + foregroundPriorityVerified
                    + " thermal_degradation_verified=" + thermalDegradationVerified
                    + " thermal_resource_fail_closed_verified="
                    + thermalResourceFailClosedVerified
                    + " admission_boundary_verified=" + boundaryVerified
                    + " admitted_count=4"
                    + " blocked_count=2"
                    + " resource_admission_runtime_wired=false"
                    + " provider_invoked=false"
                    + " model_invoked=false"
                    + " action_authorization_granted=false"
                    + " effect_dispatch_requested=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " resource_admission_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " resource_admission_runtime_wired=false"
                    + " provider_invoked=false"
                    + " model_invoked=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false", exception);
        }
    }

    private static ModelResourceAdmission.AdmissionDecision admit(
            ModelContractV2.ModelRequest request,
            PolicyAwareModelRouter.PolicySnapshot policy,
            ModelResourceAdmission.CapacityState capacity,
            ModelResourceAdmission.WorkloadClass workload,
            String owner,
            InferenceResourceScheduler scheduler) {
        int slots = capacity == ModelResourceAdmission.CapacityState.AVAILABLE
                        || capacity == ModelResourceAdmission.CapacityState.CONSTRAINED
                ? 1 : 0;
        return ModelResourceAdmission.admit(
                request,
                PolicyAwareModelRouter.decide(
                        request,
                        policy,
                        registry().snapshot(NOW),
                        NOW),
                policy,
                new ModelResourceAdmission.ResourceSnapshot(
                        ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                        capacity,
                        slots,
                        1,
                        900,
                        2_000,
                        DIGEST_C,
                        policy),
                new ModelResourceAdmission.AdmissionContext(
                        owner,
                        "central-intent-v0",
                        workload),
                scheduler,
                NOW);
    }

    private static ModelProviderRegistry registry() {
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
            PolicyAwareModelRouter.ThermalState thermal) {
        return new PolicyAwareModelRouter.PolicySnapshot(
                PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                thermal,
                100,
                10_000,
                1,
                900,
                2_000,
                DIGEST_D);
    }

    private static ModelContractV2.ModelRequest request(
            String requestId,
            ModelContractV2.Purpose purpose) {
        return new ModelContractV2.ModelRequest(
                requestId,
                purpose,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(4_000),
                new ModelContractV2.TokenBudget(2_048, 512, 2_560),
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                DIGEST_A,
                DIGEST_B);
    }

    private static InferenceResourceScheduler scheduler(int maxQueued, int slots) {
        AtomicLong clock = new AtomicLong(NOW);
        AtomicInteger leases = new AtomicInteger();
        return new InferenceResourceScheduler(
                new InferenceResourceScheduler.Limits(
                        maxQueued, maxQueued, slots, slots, 10_000),
                clock::get,
                () -> "lease-resource-probe-" + leases.incrementAndGet(),
                Collections.singletonList(
                        InferenceResourceScheduler.RouteTarget.forFixedAdmissionContract(
                                ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                                slots,
                                true)));
    }
}
