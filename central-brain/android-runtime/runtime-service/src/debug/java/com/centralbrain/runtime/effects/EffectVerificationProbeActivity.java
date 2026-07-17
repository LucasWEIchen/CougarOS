package com.centralbrain.runtime.effects;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.effects.AdapterRegistry.PrepareResult;
import com.centralbrain.runtime.effects.AdapterRegistry.PreparedMaterial;
import com.centralbrain.runtime.effects.AdapterRegistry.Profile;
import com.centralbrain.runtime.effects.EffectVerifier.TypedValue;
import com.centralbrain.runtime.effects.EffectVerifier.VerificationEvidence;
import com.centralbrain.runtime.effects.EffectVerifier.VerificationField;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.VehicleDigitalTwinStore;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;
import com.centralbrain.sdk.effect.EffectObservation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class EffectVerificationProbeActivity extends Activity {
    private static final String TAG = "CbEffectVerify";
    private static final long NOW = 1_750_000_000_000L;
    private static final long ELAPSED = 10_000L;
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private static final String PLAN_ID = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
    private static final String ACTION_ID = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
    private static final String EFFECT_ID = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
    private static final String SOURCE_ID = "adapter.debug.hvac";
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
            EffectVerifier verifier = new EffectVerifier(CapabilityCatalog.stage2Defaults());
            boolean policiesVerified = verifyCallback(verifier)
                    && verifyReported(verifier, EffectContract.VERIFY_REPORTED_EQUALS)
                    && verifyReported(verifier, EffectContract.VERIFY_REPORTED_TOLERANCE)
                    && verifyStateTransition(verifier)
                    && verifyComposite(verifier);

            EffectIntent intent = temperatureIntent(
                    EffectContract.VERIFY_REPORTED_TOLERANCE, 0.5);
            bindTarget(intent, Collections.emptyList());
            FakeAdapter appliedAdapter = new FakeAdapter(EffectAdapter.DeliveryState.APPLIED);
            DigitalTwinEffectReconciler reconciler = reconciler(intent, appliedAdapter, verifier);
            DigitalTwinEffectReconciler.Result verified = reconciler.reconcile(
                    intent,
                    delivered(intent),
                    Profile.DEBUG_SIMULATION,
                    snapshot(21.7),
                    NOW + 1_000L,
                    1);
            boolean separationVerified = verified.getDecision()
                            == DigitalTwinEffectReconciler.Decision.VERIFIED
                    && verified.getTransitions().size() == 2
                    && verified.getTransitions().get(0).state == EffectContract.STATE_APPLIED
                    && verified.getTransitions().get(1).state == EffectContract.STATE_VERIFIED;
            DigitalTwinEffectReconciler.Result replay = reconciler.reconcile(
                    intent,
                    verified.getLatestObservation(),
                    Profile.DEBUG_SIMULATION,
                    snapshot(21.7),
                    NOW + 2_000L,
                    2);
            boolean verifiedDedup = replay.getDecision()
                            == DigitalTwinEffectReconciler.Decision.ALREADY_VERIFIED
                    && !replay.wasAdapterQueried()
                    && appliedAdapter.queryCalls.get() == 1
                    && appliedAdapter.applyCalls.get() == 0;

            FakeAdapter unknownAdapter = new FakeAdapter(EffectAdapter.DeliveryState.UNKNOWN);
            DigitalTwinEffectReconciler.Result unknown = reconciler(
                    intent, unknownAdapter, verifier).reconcile(
                            intent,
                            delivered(intent),
                            Profile.DEBUG_SIMULATION,
                            null,
                            NOW + 1_000L,
                            1);
            boolean unknownReconciliation = unknown.getDecision()
                            == DigitalTwinEffectReconciler.Decision.RECONCILE_SCHEDULED
                    && unknown.getNextReconcileAtEpochMs() == NOW + 1_250L
                    && unknown.getLatestObservation().state == EffectContract.STATE_UNKNOWN
                    && unknownAdapter.queryCalls.get() == 1
                    && unknownAdapter.applyCalls.get() == 0;

            int queryBeforeProduction = appliedAdapter.queryCalls.get();
            DigitalTwinEffectReconciler.Result production = reconciler.reconcile(
                    intent,
                    delivered(intent),
                    Profile.PRODUCTION,
                    snapshot(21.5),
                    NOW + 1_000L,
                    1);
            boolean productionFailClosed = production.getDecision()
                            == DigitalTwinEffectReconciler.Decision
                                    .PRODUCTION_READBACK_UNAVAILABLE
                    && !production.wasAdapterQueried()
                    && appliedAdapter.queryCalls.get() == queryBeforeProduction;

            boolean allVerified = policiesVerified
                    && separationVerified
                    && unknownReconciliation
                    && verifiedDedup
                    && productionFailClosed;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            Log.i(TAG, "nonce=" + nonce
                    + " effect_verification_probe_complete=" + allVerified
                    + " effect_verifier_defined=true"
                    + " effect_verification_policies_verified=" + policiesVerified
                    + " effect_state_separation_verified=" + separationVerified
                    + " effect_unknown_reconciliation_verified=" + unknownReconciliation
                    + " effect_verified_redispatch_blocked=" + verifiedDedup
                    + " effect_production_readback_fail_closed=" + productionFailClosed
                    + " effect_verification_android13_arm64_verified="
                    + android13Arm64Verified
                    + " effect_verification_reconciliation_runtime_wired=false"
                    + " effect_verification_scheduler_wired=false"
                    + " effect_verification_persistence_wired=false"
                    + " effect_verification_production_readback_wired=false"
                    + " effect_verification_graph_wired=false"
                    + " production_effect_dispatch_enabled=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " effect_verification_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " effect_verification_reconciliation_runtime_wired=false"
                    + " effect_verification_scheduler_wired=false"
                    + " effect_verification_persistence_wired=false"
                    + " effect_verification_production_readback_wired=false"
                    + " production_effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static boolean verifyCallback(EffectVerifier verifier) {
        EffectIntent intent = baseIntent();
        intent.capabilityId = "media.playback";
        intent.targetArea = "vehicle.cabin";
        intent.valueKind = EffectContract.VALUE_TEXT;
        intent.textValue = "PLAY";
        intent.riskClass = EffectContract.RISK_LOW;
        intent.verificationPolicy = EffectContract.VERIFY_CALLBACK_ONLY;
        bindTarget(intent, Collections.emptyList());
        return verifier.verify(
                intent,
                delivered(intent),
                VerificationEvidence.callbackApplied(
                        EffectContract.SOURCE_SIMULATION,
                        SOURCE_ID,
                        false,
                        NOW + 1_000L,
                        digest('1')),
                Profile.DEBUG_SIMULATION,
                NOW + 1_000L).getDecision() == EffectVerifier.Decision.VERIFIED;
    }

    private static boolean verifyReported(EffectVerifier verifier, int policy) {
        EffectIntent intent = temperatureIntent(
                policy,
                policy == EffectContract.VERIFY_REPORTED_TOLERANCE ? 0.5 : 0.0);
        VerificationField field = VerificationField.primary(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver",
                intent,
                temperature(policy == EffectContract.VERIFY_REPORTED_TOLERANCE ? 21.8 : 21.5));
        bindTarget(intent, Collections.singletonList(field));
        return verifyReadback(verifier, intent, Collections.singletonList(field));
    }

    private static boolean verifyStateTransition(EffectVerifier verifier) {
        EffectIntent intent = temperatureIntent(EffectContract.VERIFY_STATE_TRANSITION, 0.0);
        VerificationField field = new VerificationField(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver",
                TypedValue.fromIntent(intent),
                TypedValue.ofDecimal(20.0, "celsius"),
                TypedValue.ofDecimal(21.5, "celsius"),
                0.0,
                true);
        bindTarget(intent, Collections.singletonList(field));
        return verifyReadback(verifier, intent, Collections.singletonList(field));
    }

    private static boolean verifyComposite(EffectVerifier verifier) {
        EffectIntent intent = temperatureIntent(EffectContract.VERIFY_COMPOSITE, 0.0);
        List<VerificationField> fields = new ArrayList<>();
        fields.add(new VerificationField(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver",
                TypedValue.fromIntent(intent),
                null,
                TypedValue.ofDecimal(21.5, "celsius"),
                0.0,
                true));
        fields.add(new VerificationField(
                VehicleSignalPath.HVAC_FAN_LEVEL,
                "row1.driver",
                TypedValue.ofInteger(3L, "level"),
                null,
                TypedValue.ofInteger(3L, "level"),
                0.0,
                false));
        bindTarget(intent, fields);
        return verifyReadback(verifier, intent, fields);
    }

    private static boolean verifyReadback(
            EffectVerifier verifier,
            EffectIntent intent,
            List<VerificationField> fields) {
        return verifier.verify(
                intent,
                delivered(intent),
                VerificationEvidence.readback(
                        EffectContract.SOURCE_SIMULATION,
                        SOURCE_ID,
                        false,
                        NOW + 1_000L,
                        digest('2'),
                        fields),
                Profile.DEBUG_SIMULATION,
                NOW + 1_000L).getDecision() == EffectVerifier.Decision.VERIFIED;
    }

    private static DigitalTwinEffectReconciler reconciler(
            EffectIntent intent,
            FakeAdapter adapter,
            EffectVerifier verifier) {
        AdapterRegistry registry = new AdapterRegistry(Collections.singletonList(
                new AdapterRegistry.Registration(
                        SOURCE_ID,
                        intent.capabilityId,
                        intent.targetArea,
                        Profile.DEBUG_SIMULATION,
                        true,
                        true,
                        false,
                        (candidate, nowEpochMs) -> PrepareResult.ready(new PreparedMaterial(
                                candidate.actionId,
                                DESTINATION,
                                "payload".getBytes(StandardCharsets.UTF_8),
                                "envelope".getBytes(StandardCharsets.UTF_8),
                                digest('a'),
                                digest('b'))),
                        adapter)));
        return new DigitalTwinEffectReconciler(registry, verifier);
    }

    private static DigitalTwinSnapshot snapshot(double value) {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        store.updateReported(temperature(value), ELAPSED);
        return store.snapshot(
                Collections.singleton(VehicleSignalPath.HVAC_TARGET_TEMPERATURE),
                ELAPSED);
    }

    private static SignalValue temperature(double value) {
        return SignalValue.ofDecimal(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                value,
                "celsius",
                "row1.driver",
                new SignalTimestamp(NOW, ELAPSED),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1L);
    }

    private static EffectIntent temperatureIntent(int policy, double tolerance) {
        EffectIntent intent = baseIntent();
        intent.capabilityId = "vehicle.hvac.target_temperature";
        intent.targetArea = "row1.driver";
        intent.valueKind = EffectContract.VALUE_DECIMAL;
        intent.decimalValue = 21.5;
        intent.unit = "celsius";
        intent.riskClass = EffectContract.RISK_LOW;
        intent.verificationPolicy = policy;
        intent.verificationTolerance = tolerance;
        return intent;
    }

    private static EffectIntent baseIntent() {
        EffectIntent intent = new EffectIntent();
        intent.effectId = EFFECT_ID;
        intent.sessionId = SESSION_ID;
        intent.planId = PLAN_ID;
        intent.nodeId = "apply-effect";
        intent.actionId = ACTION_ID;
        intent.targetValueDigest = digest('0');
        intent.idempotencyKey = "effect:verification";
        intent.planDigest = digest('e');
        intent.contextDigest = digest('f');
        intent.contextVersion = 7L;
        intent.required = true;
        intent.reversible = true;
        intent.compensationDigest = digest('c');
        intent.createdAtEpochMs = NOW - 1_000L;
        intent.deadlineEpochMs = NOW + 60_000L;
        return intent;
    }

    private static void bindTarget(
            EffectIntent intent,
            List<VerificationField> fields) {
        intent.targetValueDigest = EffectVerifier.expectedTargetDigest(intent, fields);
        EffectContract.validateIntent(intent, NOW);
    }

    private static EffectObservation delivered(EffectIntent intent) {
        EffectObservation observation = new EffectObservation();
        observation.observationId = "7ed8f09c-56fd-4ec6-94ac-68ce0e6fa9f8";
        observation.effectId = intent.effectId;
        observation.sessionId = intent.sessionId;
        observation.actionId = intent.actionId;
        observation.planDigest = intent.planDigest;
        observation.contextVersion = intent.contextVersion;
        observation.state = EffectContract.STATE_DELIVERED;
        observation.source = EffectContract.SOURCE_SIMULATION;
        observation.sourceId = SOURCE_ID;
        observation.attempt = 1;
        observation.targetValueDigest = intent.targetValueDigest;
        observation.reportedValueDigest = "";
        observation.evidenceDigest = digest('8');
        observation.observationDigest = digest('7');
        observation.failureCode = "";
        observation.occurredAtEpochMs = NOW;
        observation.terminal = false;
        observation.retryable = false;
        observation.simulated = true;
        EffectContract.validateObservation(observation);
        return observation;
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static final class FakeAdapter implements EffectAdapter {
        private final DeliveryState state;
        private final AtomicInteger applyCalls = new AtomicInteger();
        private final AtomicInteger queryCalls = new AtomicInteger();

        private FakeAdapter(DeliveryState state) {
            this.state = state;
        }

        @Override
        public Descriptor descriptor() {
            return new Descriptor(
                    SOURCE_ID,
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
                    invocation.getIdempotencyToken(), ApplyState.APPLIED, digest('6'));
        }

        @Override
        public StatusResult queryStatus(String idempotencyToken) {
            queryCalls.incrementAndGet();
            return new StatusResult(idempotencyToken, state, digest('5'));
        }
    }
}
