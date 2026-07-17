package com.centralbrain.runtime.effects;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.effects.AdapterRegistry.Profile;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;
import com.centralbrain.sdk.effect.EffectObservation;
import com.centralbrain.sdk.effect.UndoHandle;

import java.util.List;
import java.util.Set;

public final class CompensationUndoProbeActivity extends Activity {
    private static final String TAG = "CbCompUndo";
    private static final long NOW = 1_760_000_000_000L;
    private static final long ELAPSED = 25_000L;
    private static final String SOURCE_SESSION = "21e168dc-404d-4dc8-aa4d-ab89e9b3bb35";
    private static final String SOURCE_PLAN = "857e1f4f-2532-4a0a-9d11-f4830a358af8";
    private static final String SOURCE_ACTION = "32701c03-3753-4395-a06d-a3ea753f440f";
    private static final String SOURCE_BATCH = "bb61d828-c10e-4140-9ac3-29156b7112de";
    private static final String POWER_EFFECT = "332bb0f5-7dbf-442b-a117-9427e9c3896f";
    private static final String FAN_EFFECT = "c5b156d6-2602-4eb4-beea-b6f067613d8a";
    private static final String COMP_SESSION = "c8d26060-d61c-4551-bdde-64e951453ed7";
    private static final String COMP_PLAN = "f71122d4-785c-42a9-90ed-09ef5796ff3f";
    private static final String COMP_ACTION = "26f1c30e-1131-40a2-95bb-4e04ca04345e";
    private static final String COMP_POWER_EFFECT = "2b130e6e-c49b-4647-89f8-816fa873b49f";
    private static final String COMP_FAN_EFFECT = "a2ba9970-f2d2-48ed-ac1d-532344c75d92";
    private static final String TASK_ID = "9e43d862-44cb-4300-a71e-5c51af6edfd6";
    private static final String CONTEXT = digest("context.source");
    private static final String NEW_CONTEXT = digest("context.compensation");
    private static final String SOURCE_PLAN_DIGEST = digest("plan.source");
    private static final String COMP_PLAN_DIGEST = digest("plan.compensation");
    private static final String PRINCIPAL = digest("principal.debug");
    private static final String POLICY = digest("policy.debug");
    private static final String SAFETY = digest("safety.parked");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            Fixture fixture = fixture();
            CompensationPlanner planner = planner();
            CompensationPlanner.Plan plan = planner.plan(
                    fixture.batch, fixture.states, NOW);
            require(plan.getWaves().size() == 2, "reverse wave count");
            require(plan.getWaves().get(0).getSteps().get(0)
                    .getSourceIntent().effectId.equals(FAN_EFFECT), "fan must undo first");
            require(plan.getWaves().get(1).getSteps().get(0)
                    .getSourceIntent().effectId.equals(POWER_EFFECT), "power must undo last");
            require(plan.getWaves().get(0).getSteps().get(0)
                    .getCompensationIntent().integerValue == 2L, "fan before target");
            require(!plan.getWaves().get(1).getSteps().get(0)
                    .getCompensationIntent().booleanValue, "power before target");
            require(fixture.powerVerified.state == EffectContract.STATE_VERIFIED
                    && fixture.powerVerified.terminal, "source VERIFIED must remain terminal");

            boolean irreversibleRejected = false;
            try {
                EffectIntent irreversible = EffectBatch.copyIntent(fixture.power);
                irreversible.reversible = false;
                irreversible.compensationDigest = "";
                EffectBatch invalidBatch = EffectBatch.create(
                        SOURCE_BATCH,
                        List.of(
                                new EffectBatch.Entry(
                                        irreversible, "hvac:cabin_power", List.of()),
                                new EffectBatch.Entry(
                                        fixture.fan,
                                        "hvac:cabin_fan",
                                        List.of(POWER_EFFECT))),
                        NOW);
                planner.plan(invalidBatch, fixture.states, NOW);
            } catch (IllegalArgumentException expected) {
                irreversibleRejected = true;
            }
            require(irreversibleRejected, "irreversible source must reject");

            UndoService service = new UndoService();
            List<UndoHandle> handles = service.issueHandles(
                    plan,
                    List.of(
                            "bace6130-4dd2-4489-829e-d8c3f6766d62",
                            "3facda7e-5113-41f0-9199-c6e8503ad171"),
                    NOW,
                    20_000L);
            UndoService.GovernanceSnapshot governance = governance(true);
            UndoService.Admission admitted = service.requestUndo(
                    TASK_ID,
                    "undo:probe:1",
                    plan,
                    handles,
                    governance,
                    Profile.DEBUG_SIMULATION,
                    NOW + 1L);
            UndoService.Admission replay = service.requestUndo(
                    TASK_ID,
                    "undo:probe:1",
                    plan,
                    handles,
                    governance,
                    Profile.DEBUG_SIMULATION,
                    NOW + 2L);
            require(admitted.getDecision() == UndoService.Decision.ADMITTED,
                    "Undo admission");
            require(admitted.getRequestedHandles().size() == 2
                    && admitted.getRequestedHandles().get(0).state
                            == EffectContract.UNDO_REQUESTED,
                    "requested handles");
            require(admitted.getTask().getCompensationSessionId().equals(COMP_SESSION)
                    && admitted.getTask().getCompensationPlanId().equals(COMP_PLAN),
                    "new governed task");
            require(replay.isIdempotentReplay()
                    && replay.getTask().getTaskDigest().equals(
                            admitted.getTask().getTaskDigest()),
                    "idempotent replay");

            UndoService.Admission unsafe = new UndoService().requestUndo(
                    TASK_ID,
                    "undo:probe:unsafe",
                    plan,
                    handles,
                    governance(false),
                    Profile.DEBUG_SIMULATION,
                    NOW + 1L);
            require(unsafe.getDecision() == UndoService.Decision.REJECTED
                    && unsafe.getReasonCode().equals("SAFETY_UNSAFE"),
                    "unsafe Governance must reject");
            UndoService.Admission production = new UndoService().requestUndo(
                    TASK_ID,
                    "undo:probe:production",
                    plan,
                    handles,
                    governance,
                    Profile.PRODUCTION,
                    NOW + 1L);
            require(production.getDecision() == UndoService.Decision.REJECTED
                    && production.getReasonCode().equals(
                            "PRODUCTION_COMPENSATION_UNAVAILABLE"),
                    "production must fail closed");

            boolean api33Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && "arm64-v8a".equals(Build.SUPPORTED_ABIS[0]);
            Log.i(TAG, "nonce=" + nonce + " compensation_undo_probe_complete=true");
            Log.i(TAG, "compensation_planner_defined=true");
            Log.i(TAG, "compensation_absolute_before_verified=true");
            Log.i(TAG, "compensation_reverse_dependency_verified=true");
            Log.i(TAG, "compensation_irreversible_rejected=true");
            Log.i(TAG, "undo_ttl_governance_verified=true");
            Log.i(TAG, "undo_new_governed_task_verified=true");
            Log.i(TAG, "undo_idempotent_replay_verified=true");
            Log.i(TAG, "undo_production_fail_closed=true");
            Log.i(TAG, "compensation_undo_android13_arm64_verified=" + api33Arm64);
            Log.i(TAG, "compensation_undo_runtime_wired=false");
            Log.i(TAG, "compensation_undo_persistence_wired=false");
            Log.i(TAG, "undo_binder_service_published=false");
            Log.i(TAG, "compensation_dispatch_enabled=false");
            Log.i(TAG, "production_compensation_authority_wired=false");
            Log.i(TAG, "effect_dispatch_enabled=false");
            Log.i(TAG, "hardware_accessed=false");
        } catch (RuntimeException failure) {
            Log.e(TAG, "compensation_undo_probe_failed", failure);
            throw failure;
        }
    }

    private static CompensationPlanner planner() {
        return new CompensationPlanner(
                CapabilityCatalog.stage2Defaults(),
                List.of(
                        new CompensationPlanner.ReversibleTarget(
                                "vehicle.hvac.power", "cabin"),
                        new CompensationPlanner.ReversibleTarget(
                                "vehicle.hvac.fan_level", "cabin")));
    }

    private static UndoService.GovernanceSnapshot governance(boolean safetySafe) {
        return new UndoService.GovernanceSnapshot(
                PRINCIPAL,
                NEW_CONTEXT,
                2L,
                POLICY,
                SAFETY,
                Set.of("vehicle.hvac.power", "vehicle.hvac.fan_level"),
                true,
                true,
                true,
                true,
                safetySafe);
    }

    private static Fixture fixture() {
        EffectIntent power = source(
                POWER_EFFECT,
                "power",
                "vehicle.hvac.power",
                EffectContract.VALUE_BOOLEAN,
                true,
                0L,
                "");
        EffectIntent fan = source(
                FAN_EFFECT,
                "fan",
                "vehicle.hvac.fan_level",
                EffectContract.VALUE_INTEGER,
                false,
                5L,
                "level");
        EffectBatch batch = EffectBatch.create(
                SOURCE_BATCH,
                List.of(
                        new EffectBatch.Entry(power, "hvac:cabin_power", List.of()),
                        new EffectBatch.Entry(
                                fan, "hvac:cabin_fan", List.of(POWER_EFFECT))),
                NOW);
        EffectObservation powerVerified = verified(
                power,
                "c7df6bbd-7419-468f-a669-c53e670d9fd7",
                "power");
        EffectObservation fanVerified = verified(
                fan,
                "258f5855-20f1-470b-90a1-a980059c0b41",
                "fan");
        CompensationPlanner.BeforeSnapshot powerBefore = beforePower();
        CompensationPlanner.BeforeSnapshot fanBefore = beforeFan();
        EffectIntent powerCompensation = compensation(
                power,
                COMP_POWER_EFFECT,
                "undo.power",
                EffectContract.VALUE_BOOLEAN,
                false,
                0L,
                "");
        EffectIntent fanCompensation = compensation(
                fan,
                COMP_FAN_EFFECT,
                "undo.fan",
                EffectContract.VALUE_INTEGER,
                false,
                2L,
                "level");
        return new Fixture(
                batch,
                power,
                fan,
                powerVerified,
                List.of(
                        CompensationPlanner.SourceState.verified(
                                POWER_EFFECT,
                                powerVerified,
                                powerBefore,
                                powerBefore.getSnapshotDigest(),
                                powerCompensation),
                        CompensationPlanner.SourceState.verified(
                                FAN_EFFECT,
                                fanVerified,
                                fanBefore,
                                fanBefore.getSnapshotDigest(),
                                fanCompensation)));
    }

    private static EffectIntent source(
            String effectId,
            String nodeId,
            String capabilityId,
            int valueKind,
            boolean booleanValue,
            long integerValue,
            String unit) {
        EffectIntent intent = base(
                effectId,
                SOURCE_SESSION,
                SOURCE_PLAN,
                nodeId,
                SOURCE_ACTION,
                capabilityId,
                SOURCE_PLAN_DIGEST,
                CONTEXT,
                1L,
                valueKind,
                booleanValue,
                integerValue,
                unit);
        intent.idempotencyKey = "source:" + effectId;
        intent.reversible = true;
        intent.compensationDigest =
                CompensationPlanner.expectedCompensationDescriptorDigest(intent);
        return intent;
    }

    private static EffectIntent compensation(
            EffectIntent source,
            String effectId,
            String nodeId,
            int valueKind,
            boolean booleanValue,
            long integerValue,
            String unit) {
        EffectIntent intent = base(
                effectId,
                COMP_SESSION,
                COMP_PLAN,
                nodeId,
                COMP_ACTION,
                source.capabilityId,
                COMP_PLAN_DIGEST,
                NEW_CONTEXT,
                2L,
                valueKind,
                booleanValue,
                integerValue,
                unit);
        intent.idempotencyKey = CompensationPlanner.expectedCompensationIdempotencyKey(
                COMP_PLAN, source.effectId);
        intent.reversible = false;
        intent.compensationDigest = "";
        intent.createdAtEpochMs = NOW - 100L;
        intent.deadlineEpochMs = NOW + 30_000L;
        return intent;
    }

    private static EffectIntent base(
            String effectId,
            String sessionId,
            String planId,
            String nodeId,
            String actionId,
            String capabilityId,
            String planDigest,
            String contextDigest,
            long contextVersion,
            int valueKind,
            boolean booleanValue,
            long integerValue,
            String unit) {
        EffectIntent intent = new EffectIntent();
        intent.schemaVersion = EffectContract.SCHEMA_VERSION;
        intent.effectId = effectId;
        intent.sessionId = sessionId;
        intent.planId = planId;
        intent.nodeId = nodeId;
        intent.actionId = actionId;
        intent.capabilityId = capabilityId;
        intent.targetArea = "vehicle.cabin";
        intent.valueKind = valueKind;
        intent.booleanValue = booleanValue;
        intent.integerValue = integerValue;
        intent.textValue = "";
        intent.unit = unit;
        intent.planDigest = planDigest;
        intent.contextDigest = contextDigest;
        intent.contextVersion = contextVersion;
        intent.riskClass = EffectContract.RISK_LOW;
        intent.required = true;
        intent.verificationPolicy = EffectContract.VERIFY_REPORTED_EQUALS;
        intent.verificationTolerance = 0.0;
        intent.createdAtEpochMs = NOW - 10_000L;
        intent.deadlineEpochMs = NOW + 120_000L;
        intent.targetValueDigest = EffectVerifier.expectedTargetDigest(intent, List.of());
        return intent;
    }

    private static EffectObservation verified(
            EffectIntent intent,
            String observationId,
            String suffix) {
        EffectObservation observation = new EffectObservation();
        observation.schemaVersion = EffectContract.SCHEMA_VERSION;
        observation.observationId = observationId;
        observation.effectId = intent.effectId;
        observation.sessionId = intent.sessionId;
        observation.actionId = intent.actionId;
        observation.planDigest = intent.planDigest;
        observation.contextVersion = intent.contextVersion;
        observation.state = EffectContract.STATE_VERIFIED;
        observation.source = EffectContract.SOURCE_SIMULATION;
        observation.sourceId = "adapter.debug.hvac";
        observation.attempt = 1;
        observation.targetValueDigest = intent.targetValueDigest;
        observation.reportedValueDigest = digest("reported." + suffix);
        observation.evidenceDigest = digest("evidence." + suffix);
        observation.observationDigest = digest("observation." + suffix);
        observation.failureCode = "";
        observation.occurredAtEpochMs = NOW - 1_000L;
        observation.terminal = true;
        observation.retryable = false;
        observation.simulated = true;
        EffectContract.validateObservation(observation);
        return observation;
    }

    private static CompensationPlanner.BeforeSnapshot beforePower() {
        return new CompensationPlanner.BeforeSnapshot(
                SignalValue.ofBoolean(
                        VehicleSignalPath.HVAC_ACTIVE,
                        false,
                        "",
                        "cabin",
                        new SignalTimestamp(NOW - 5_000L, ELAPSED),
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        1L),
                CONTEXT,
                1L,
                NOW - 5_000L,
                ELAPSED,
                false);
    }

    private static CompensationPlanner.BeforeSnapshot beforeFan() {
        return new CompensationPlanner.BeforeSnapshot(
                SignalValue.ofInteger(
                        VehicleSignalPath.HVAC_FAN_LEVEL,
                        2L,
                        "level",
                        "cabin",
                        new SignalTimestamp(NOW - 5_000L, ELAPSED),
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        2L),
                CONTEXT,
                1L,
                NOW - 5_000L,
                ELAPSED,
                false);
    }

    private static String digest(String value) {
        return EffectBatch.digest("probe.compensation.fixture", value);
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new IllegalStateException("CB_COMPENSATION_PROBE: " + label);
        }
    }

    private static final class Fixture {
        private final EffectBatch batch;
        private final EffectIntent power;
        private final EffectIntent fan;
        private final EffectObservation powerVerified;
        private final List<CompensationPlanner.SourceState> states;

        private Fixture(
                EffectBatch batch,
                EffectIntent power,
                EffectIntent fan,
                EffectObservation powerVerified,
                List<CompensationPlanner.SourceState> states) {
            this.batch = batch;
            this.power = power;
            this.fan = fan;
            this.powerVerified = powerVerified;
            this.states = states;
        }
    }
}
