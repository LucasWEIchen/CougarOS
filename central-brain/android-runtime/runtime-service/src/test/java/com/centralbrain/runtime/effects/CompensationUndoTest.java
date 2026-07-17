package com.centralbrain.runtime.effects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

import org.junit.Test;

import java.util.List;
import java.util.Set;

public final class CompensationUndoTest {
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

    private final CapabilityCatalog catalog = CapabilityCatalog.stage2Defaults();

    @Test
    public void plannerUsesAbsoluteBeforeTargetsInReverseDependencyOrder() {
        Fixture fixture = fixture();

        CompensationPlanner.Plan plan = planner().plan(
                fixture.batch, fixture.states, NOW);

        assertEquals(2, plan.getWaves().size());
        assertEquals(FAN_EFFECT,
                plan.getWaves().get(0).getSteps().get(0).getSourceIntent().effectId);
        assertEquals(POWER_EFFECT,
                plan.getWaves().get(1).getSteps().get(0).getSourceIntent().effectId);
        assertEquals(2L,
                plan.getWaves().get(0).getSteps().get(0)
                        .getCompensationIntent().integerValue);
        assertFalse(plan.getWaves().get(1).getSteps().get(0)
                .getCompensationIntent().booleanValue);
        assertNotEquals(fixture.power.targetValueDigest,
                plan.getWaves().get(1).getSteps().get(0)
                        .getCompensationIntent().targetValueDigest);
        assertEquals(COMP_SESSION, plan.getCompensationSessionId());
        assertEquals(COMP_PLAN, plan.getCompensationPlanId());
    }

    @Test
    public void irreversibleOrUnapprovedTargetsNeverAdvertiseUndo() {
        Fixture fixture = fixture();
        fixture.power.reversible = false;
        fixture.power.compensationDigest = "";
        EffectBatch irreversibleBatch = batch(fixture.power, fixture.fan);

        assertThrows(IllegalArgumentException.class, () -> planner().plan(
                irreversibleBatch, fixture.states, NOW));

        CompensationPlanner powerOnly = new CompensationPlanner(
                catalog,
                List.of(new CompensationPlanner.ReversibleTarget(
                        "vehicle.hvac.power", "cabin")));
        assertThrows(IllegalArgumentException.class, () -> powerOnly.plan(
                fixture.batch, fixture.states, NOW));
    }

    @Test
    public void plannerRejectsSnapshotDriftAndRelativeTargets() {
        Fixture fixture = fixture();
        CompensationPlanner.SourceState wrongDigest =
                CompensationPlanner.SourceState.verified(
                        POWER_EFFECT,
                        fixture.powerVerified,
                        fixture.powerBefore,
                        digest("wrong.before"),
                        fixture.powerCompensation);
        assertThrows(IllegalArgumentException.class, () -> planner().plan(
                fixture.batch,
                List.of(wrongDigest, fixture.states.get(1)),
                NOW));

        EffectIntent relative = copyIntent(fixture.powerCompensation);
        relative.booleanValue = true;
        relative.targetValueDigest = EffectVerifier.expectedTargetDigest(relative, List.of());
        CompensationPlanner.SourceState relativeState =
                CompensationPlanner.SourceState.verified(
                        POWER_EFFECT,
                        fixture.powerVerified,
                        fixture.powerBefore,
                        fixture.powerBefore.getSnapshotDigest(),
                        relative);
        assertThrows(IllegalArgumentException.class, () -> planner().plan(
                fixture.batch,
                List.of(relativeState, fixture.states.get(1)),
                NOW));
    }

    @Test
    public void sourceVerifiedObservationRemainsTerminalAndUnchanged() {
        Fixture fixture = fixture();
        String originalDigest = fixture.powerVerified.observationDigest;

        CompensationPlanner.Plan plan = planner().plan(
                fixture.batch, fixture.states, NOW);

        assertEquals(EffectContract.STATE_VERIFIED, fixture.powerVerified.state);
        assertTrue(fixture.powerVerified.terminal);
        assertEquals(originalDigest, fixture.powerVerified.observationDigest);
        assertNotEquals(POWER_EFFECT,
                plan.getOrderedSteps().get(1).getCompensationIntent().effectId);
        assertFalse(plan.getOrderedSteps().get(1).getCompensationIntent().reversible);
    }

    @Test
    public void undoHandlesAreDigestBoundAndDeadlineCapped() {
        CompensationPlanner.Plan plan = plan();
        UndoService service = new UndoService();
        List<UndoHandle> handles = service.issueHandles(
                plan,
                List.of(
                        "bace6130-4dd2-4489-829e-d8c3f6766d62",
                        "3facda7e-5113-41f0-9199-c6e8503ad171"),
                NOW,
                EffectContract.MAX_UNDO_TTL_MS);

        assertEquals(2, handles.size());
        assertEquals(plan.getCompensationDeadlineEpochMs(),
                handles.get(0).expiresAtEpochMs);
        assertEquals(EffectContract.UNDO_AVAILABLE, handles.get(0).state);
        assertEquals(UndoService.digestHandle(handles.get(0)),
                handles.get(0).handleDigest);

        UndoHandle tampered = copyHandle(handles.get(0));
        tampered.capabilityId = "vehicle.seat.recline";
        assertThrows(IllegalArgumentException.class, () -> service.requestUndo(
                TASK_ID,
                "undo:task:1",
                plan,
                List.of(tampered, handles.get(1)),
                governance(true, true, true, true, true, NEW_CONTEXT, 2L),
                Profile.DEBUG_SIMULATION,
                NOW + 1L));
    }

    @Test
    public void admittedUndoCreatesNewGovernedTaskAndIdempotentReplay() {
        CompensationPlanner.Plan plan = plan();
        UndoService service = new UndoService();
        List<UndoHandle> handles = handles(service, plan);
        UndoService.GovernanceSnapshot governance = governance(
                true, true, true, true, true, NEW_CONTEXT, 2L);

        UndoService.Admission admitted = service.requestUndo(
                TASK_ID,
                "undo:task:1",
                plan,
                handles,
                governance,
                Profile.DEBUG_SIMULATION,
                NOW + 1L);
        UndoService.Admission replay = service.requestUndo(
                TASK_ID,
                "undo:task:1",
                plan,
                handles,
                governance,
                Profile.DEBUG_SIMULATION,
                NOW + 2L);

        assertEquals(UndoService.Decision.ADMITTED, admitted.getDecision());
        assertFalse(admitted.isIdempotentReplay());
        assertTrue(replay.isIdempotentReplay());
        assertEquals(admitted.getTask().getTaskDigest(), replay.getTask().getTaskDigest());
        assertEquals(COMP_SESSION, admitted.getTask().getCompensationSessionId());
        assertEquals(COMP_PLAN, admitted.getTask().getCompensationPlanId());
        assertEquals(2, admitted.getRequestedHandles().size());
        assertEquals(EffectContract.UNDO_REQUESTED,
                admitted.getRequestedHandles().get(0).state);
        assertEquals(1, service.processRecordCount());
    }

    @Test
    public void governanceSafetyContextAndExpiryFailClosed() {
        CompensationPlanner.Plan plan = plan();
        UndoService service = new UndoService();
        List<UndoHandle> handles = handles(service, plan);

        assertRejected("SAFETY_UNSAFE", service.requestUndo(
                TASK_ID, "undo:unsafe", plan, handles,
                governance(true, true, true, true, false, NEW_CONTEXT, 2L),
                Profile.DEBUG_SIMULATION, NOW + 1L));
        assertRejected("POLICY_DENIED", service.requestUndo(
                TASK_ID, "undo:policy", plan, handles,
                governance(true, true, false, true, true, NEW_CONTEXT, 2L),
                Profile.DEBUG_SIMULATION, NOW + 1L));
        assertRejected("CONTEXT_BINDING_CHANGED", service.requestUndo(
                TASK_ID, "undo:context", plan, handles,
                governance(true, true, true, true, true, digest("changed"), 2L),
                Profile.DEBUG_SIMULATION, NOW + 1L));
        assertRejected("UNDO_HANDLE_EXPIRED", service.requestUndo(
                TASK_ID, "undo:expired", plan, handles,
                governance(true, true, true, true, true, NEW_CONTEXT, 2L),
                Profile.DEBUG_SIMULATION, handles.get(0).expiresAtEpochMs));
        assertEquals(0, service.processRecordCount());
    }

    @Test
    public void productionProfileFailsClosedWithoutTaskOrDispatch() {
        CompensationPlanner.Plan plan = plan();
        UndoService service = new UndoService();
        List<UndoHandle> handles = handles(service, plan);

        UndoService.Admission result = service.requestUndo(
                TASK_ID,
                "undo:production",
                plan,
                handles,
                governance(true, true, true, true, true, NEW_CONTEXT, 2L),
                Profile.PRODUCTION,
                NOW + 1L);

        assertRejected("PRODUCTION_COMPENSATION_UNAVAILABLE", result);
        assertThrows(IllegalStateException.class, result::getTask);
        assertEquals(0, service.processRecordCount());
    }

    private CompensationPlanner planner() {
        return new CompensationPlanner(
                catalog,
                List.of(
                        new CompensationPlanner.ReversibleTarget(
                                "vehicle.hvac.power", "cabin"),
                        new CompensationPlanner.ReversibleTarget(
                                "vehicle.hvac.fan_level", "cabin")));
    }

    private CompensationPlanner.Plan plan() {
        Fixture fixture = fixture();
        return planner().plan(fixture.batch, fixture.states, NOW);
    }

    private List<UndoHandle> handles(
            UndoService service,
            CompensationPlanner.Plan plan) {
        return service.issueHandles(
                plan,
                List.of(
                        "bace6130-4dd2-4489-829e-d8c3f6766d62",
                        "3facda7e-5113-41f0-9199-c6e8503ad171"),
                NOW,
                20_000L);
    }

    private UndoService.GovernanceSnapshot governance(
            boolean authorityTrusted,
            boolean contextFresh,
            boolean policyAuthorized,
            boolean safetyTrusted,
            boolean safetySafe,
            String contextDigest,
            long contextVersion) {
        return new UndoService.GovernanceSnapshot(
                PRINCIPAL,
                contextDigest,
                contextVersion,
                POLICY,
                SAFETY,
                Set.of("vehicle.hvac.power", "vehicle.hvac.fan_level"),
                authorityTrusted,
                contextFresh,
                policyAuthorized,
                safetyTrusted,
                safetySafe);
    }

    private Fixture fixture() {
        EffectIntent power = sourceBoolean(
                POWER_EFFECT, "power", "vehicle.hvac.power", true);
        EffectIntent fan = sourceInteger(
                FAN_EFFECT, "fan", "vehicle.hvac.fan_level", 5L);
        EffectBatch batch = batch(power, fan);
        EffectObservation powerVerified = verified(power, "power");
        EffectObservation fanVerified = verified(fan, "fan");
        CompensationPlanner.BeforeSnapshot powerBefore = beforeBoolean(false, 1L);
        CompensationPlanner.BeforeSnapshot fanBefore = beforeInteger(2L, 2L);
        EffectIntent powerCompensation = compensationBoolean(
                power, COMP_POWER_EFFECT, "undo.power", false);
        EffectIntent fanCompensation = compensationInteger(
                fan, COMP_FAN_EFFECT, "undo.fan", 2L);
        List<CompensationPlanner.SourceState> states = List.of(
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
                        fanCompensation));
        return new Fixture(
                batch,
                power,
                fan,
                powerVerified,
                fanVerified,
                powerBefore,
                fanBefore,
                powerCompensation,
                fanCompensation,
                states);
    }

    private static EffectBatch batch(EffectIntent power, EffectIntent fan) {
        return EffectBatch.create(
                SOURCE_BATCH,
                List.of(
                        new EffectBatch.Entry(power, "hvac:cabin_power", List.of()),
                        new EffectBatch.Entry(fan, "hvac:cabin_fan", List.of(POWER_EFFECT))),
                NOW);
    }

    private static EffectIntent sourceBoolean(
            String effectId,
            String nodeId,
            String capabilityId,
            boolean target) {
        EffectIntent intent = sourceBase(effectId, nodeId, capabilityId);
        intent.valueKind = EffectContract.VALUE_BOOLEAN;
        intent.booleanValue = target;
        intent.unit = "";
        bindSourceTarget(intent);
        return intent;
    }

    private static EffectIntent sourceInteger(
            String effectId,
            String nodeId,
            String capabilityId,
            long target) {
        EffectIntent intent = sourceBase(effectId, nodeId, capabilityId);
        intent.valueKind = EffectContract.VALUE_INTEGER;
        intent.integerValue = target;
        intent.unit = "level";
        bindSourceTarget(intent);
        return intent;
    }

    private static EffectIntent sourceBase(
            String effectId,
            String nodeId,
            String capabilityId) {
        EffectIntent intent = new EffectIntent();
        intent.schemaVersion = EffectContract.SCHEMA_VERSION;
        intent.effectId = effectId;
        intent.sessionId = SOURCE_SESSION;
        intent.planId = SOURCE_PLAN;
        intent.nodeId = nodeId;
        intent.actionId = SOURCE_ACTION;
        intent.capabilityId = capabilityId;
        intent.targetArea = "vehicle.cabin";
        intent.textValue = "";
        intent.idempotencyKey = "source:" + effectId;
        intent.planDigest = SOURCE_PLAN_DIGEST;
        intent.contextDigest = CONTEXT;
        intent.contextVersion = 1L;
        intent.riskClass = EffectContract.RISK_LOW;
        intent.required = true;
        intent.verificationPolicy = EffectContract.VERIFY_REPORTED_EQUALS;
        intent.verificationTolerance = 0.0;
        intent.reversible = true;
        intent.createdAtEpochMs = NOW - 10_000L;
        intent.deadlineEpochMs = NOW + 120_000L;
        return intent;
    }

    private static void bindSourceTarget(EffectIntent intent) {
        intent.targetValueDigest = EffectVerifier.expectedTargetDigest(intent, List.of());
        intent.compensationDigest =
                CompensationPlanner.expectedCompensationDescriptorDigest(intent);
    }

    private static EffectIntent compensationBoolean(
            EffectIntent source,
            String effectId,
            String nodeId,
            boolean target) {
        EffectIntent intent = compensationBase(source, effectId, nodeId);
        intent.valueKind = EffectContract.VALUE_BOOLEAN;
        intent.booleanValue = target;
        intent.unit = "";
        intent.targetValueDigest = EffectVerifier.expectedTargetDigest(intent, List.of());
        return intent;
    }

    private static EffectIntent compensationInteger(
            EffectIntent source,
            String effectId,
            String nodeId,
            long target) {
        EffectIntent intent = compensationBase(source, effectId, nodeId);
        intent.valueKind = EffectContract.VALUE_INTEGER;
        intent.integerValue = target;
        intent.unit = "level";
        intent.targetValueDigest = EffectVerifier.expectedTargetDigest(intent, List.of());
        return intent;
    }

    private static EffectIntent compensationBase(
            EffectIntent source,
            String effectId,
            String nodeId) {
        EffectIntent intent = new EffectIntent();
        intent.schemaVersion = EffectContract.SCHEMA_VERSION;
        intent.effectId = effectId;
        intent.sessionId = COMP_SESSION;
        intent.planId = COMP_PLAN;
        intent.nodeId = nodeId;
        intent.actionId = COMP_ACTION;
        intent.capabilityId = source.capabilityId;
        intent.targetArea = source.targetArea;
        intent.textValue = "";
        intent.idempotencyKey = CompensationPlanner.expectedCompensationIdempotencyKey(
                COMP_PLAN, source.effectId);
        intent.planDigest = COMP_PLAN_DIGEST;
        intent.contextDigest = NEW_CONTEXT;
        intent.contextVersion = 2L;
        intent.riskClass = source.riskClass;
        intent.required = source.required;
        intent.verificationPolicy = EffectContract.VERIFY_REPORTED_EQUALS;
        intent.verificationTolerance = 0.0;
        intent.reversible = false;
        intent.compensationDigest = "";
        intent.createdAtEpochMs = NOW - 100L;
        intent.deadlineEpochMs = NOW + 30_000L;
        return intent;
    }

    private static EffectObservation verified(EffectIntent intent, String suffix) {
        EffectObservation observation = new EffectObservation();
        observation.schemaVersion = EffectContract.SCHEMA_VERSION;
        observation.observationId = suffix.equals("power")
                ? "c7df6bbd-7419-468f-a669-c53e670d9fd7"
                : "258f5855-20f1-470b-90a1-a980059c0b41";
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

    private static CompensationPlanner.BeforeSnapshot beforeBoolean(
            boolean value,
            long revision) {
        return new CompensationPlanner.BeforeSnapshot(
                SignalValue.ofBoolean(
                        VehicleSignalPath.HVAC_ACTIVE,
                        value,
                        "",
                        "cabin",
                        new SignalTimestamp(NOW - 5_000L, ELAPSED),
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        revision),
                CONTEXT,
                1L,
                NOW - 5_000L,
                ELAPSED,
                false);
    }

    private static CompensationPlanner.BeforeSnapshot beforeInteger(
            long value,
            long revision) {
        return new CompensationPlanner.BeforeSnapshot(
                SignalValue.ofInteger(
                        VehicleSignalPath.HVAC_FAN_LEVEL,
                        value,
                        "level",
                        "cabin",
                        new SignalTimestamp(NOW - 5_000L, ELAPSED),
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        revision),
                CONTEXT,
                1L,
                NOW - 5_000L,
                ELAPSED,
                false);
    }

    private static EffectIntent copyIntent(EffectIntent source) {
        return EffectBatch.copyIntent(source);
    }

    private static UndoHandle copyHandle(UndoHandle source) {
        UndoHandle copy = new UndoHandle();
        copy.schemaVersion = source.schemaVersion;
        copy.undoId = source.undoId;
        copy.sessionId = source.sessionId;
        copy.effectId = source.effectId;
        copy.sourceObservationId = source.sourceObservationId;
        copy.capabilityId = source.capabilityId;
        copy.planDigest = source.planDigest;
        copy.verifiedObservationDigest = source.verifiedObservationDigest;
        copy.compensationDigest = source.compensationDigest;
        copy.handleDigest = source.handleDigest;
        copy.issuedContextVersion = source.issuedContextVersion;
        copy.state = source.state;
        copy.reasonCode = source.reasonCode;
        copy.createdAtEpochMs = source.createdAtEpochMs;
        copy.expiresAtEpochMs = source.expiresAtEpochMs;
        return copy;
    }

    private static void assertRejected(
            String reason,
            UndoService.Admission admission) {
        assertEquals(UndoService.Decision.REJECTED, admission.getDecision());
        assertEquals(reason, admission.getReasonCode());
    }

    private static String digest(String value) {
        return EffectBatch.digest("test.compensation.fixture", value);
    }

    private static final class Fixture {
        private final EffectBatch batch;
        private final EffectIntent power;
        private final EffectIntent fan;
        private final EffectObservation powerVerified;
        private final EffectObservation fanVerified;
        private final CompensationPlanner.BeforeSnapshot powerBefore;
        private final CompensationPlanner.BeforeSnapshot fanBefore;
        private final EffectIntent powerCompensation;
        private final EffectIntent fanCompensation;
        private final List<CompensationPlanner.SourceState> states;

        private Fixture(
                EffectBatch batch,
                EffectIntent power,
                EffectIntent fan,
                EffectObservation powerVerified,
                EffectObservation fanVerified,
                CompensationPlanner.BeforeSnapshot powerBefore,
                CompensationPlanner.BeforeSnapshot fanBefore,
                EffectIntent powerCompensation,
                EffectIntent fanCompensation,
                List<CompensationPlanner.SourceState> states) {
            this.batch = batch;
            this.power = power;
            this.fan = fan;
            this.powerVerified = powerVerified;
            this.fanVerified = fanVerified;
            this.powerBefore = powerBefore;
            this.fanBefore = fanBefore;
            this.powerCompensation = powerCompensation;
            this.fanCompensation = fanCompensation;
            this.states = states;
        }
    }
}
