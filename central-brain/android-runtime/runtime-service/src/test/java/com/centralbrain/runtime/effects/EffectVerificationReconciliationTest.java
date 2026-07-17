package com.centralbrain.runtime.effects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class EffectVerificationReconciliationTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final long ELAPSED = 10_000L;
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private static final String PLAN_ID = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
    private static final String ACTION_ID = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
    private static final String EFFECT_ID = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
    private static final String SOURCE_ID = "adapter.debug.hvac";
    private static final String DESTINATION = "debug.vehicle.effect";

    private final EffectVerifier verifier = new EffectVerifier(CapabilityCatalog.stage2Defaults());

    @Test
    public void verifierEmitsAppliedThenVerifiedAsSeparateTransitions() {
        EffectIntent intent = temperatureIntent(EffectContract.VERIFY_REPORTED_EQUALS, 0.0);
        SignalValue reported = temperature(21.5, 1L);
        VerificationField field = VerificationField.primary(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver",
                intent,
                reported);
        bindTarget(intent, List.of(field));
        EffectObservation delivered = observation(
                intent, EffectContract.STATE_DELIVERED, EffectContract.SOURCE_SIMULATION, SOURCE_ID);

        EffectVerifier.Result result = verifier.verify(
                intent,
                delivered,
                readback(field, digest('8')),
                Profile.DEBUG_SIMULATION,
                NOW + 1_000L);

        assertEquals(EffectVerifier.Decision.VERIFIED, result.getDecision());
        assertEquals(2, result.getTransitions().size());
        assertEquals(EffectContract.STATE_APPLIED, result.getTransitions().get(0).state);
        assertEquals(EffectContract.STATE_VERIFIED, result.getTransitions().get(1).state);
        assertTrue(result.getLatestObservation().terminal);
        assertNotEquals(
                result.getTransitions().get(0).observationDigest,
                result.getTransitions().get(1).observationDigest);
    }

    @Test
    public void callbackOnlyIsRestrictedToLowRiskCapabilitiesWithoutReadback() {
        EffectIntent media = mediaIntent();
        bindTarget(media, List.of());
        EffectObservation delivered = observation(
                media, EffectContract.STATE_DELIVERED, EffectContract.SOURCE_SIMULATION, SOURCE_ID);
        EffectVerifier.Result verified = verifier.verify(
                media,
                delivered,
                VerificationEvidence.callbackApplied(
                        EffectContract.SOURCE_SIMULATION,
                        SOURCE_ID,
                        false,
                        NOW + 1_000L,
                        digest('7')),
                Profile.DEBUG_SIMULATION,
                NOW + 1_000L);
        assertEquals(EffectVerifier.Decision.VERIFIED, verified.getDecision());

        EffectIntent hvac = temperatureIntent(EffectContract.VERIFY_CALLBACK_ONLY, 0.0);
        bindTarget(hvac, List.of());
        EffectObservation hvacDelivered = observation(
                hvac, EffectContract.STATE_DELIVERED, EffectContract.SOURCE_SIMULATION, SOURCE_ID);
        assertThrows(
                IllegalArgumentException.class,
                () -> verifier.verify(
                        hvac,
                        hvacDelivered,
                        VerificationEvidence.callbackApplied(
                                EffectContract.SOURCE_SIMULATION,
                                SOURCE_ID,
                                false,
                                NOW + 1_000L,
                                digest('7')),
                        Profile.DEBUG_SIMULATION,
                        NOW + 1_000L));
    }

    @Test
    public void toleranceMatchVerifiesWhileMismatchStaysApplied() {
        EffectIntent intent = temperatureIntent(
                EffectContract.VERIFY_REPORTED_TOLERANCE, 0.5);
        VerificationField within = VerificationField.primary(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver",
                intent,
                temperature(21.9, 1L));
        bindTarget(intent, List.of(within));
        EffectObservation delivered = observation(
                intent, EffectContract.STATE_DELIVERED, EffectContract.SOURCE_SIMULATION, SOURCE_ID);
        assertEquals(
                EffectVerifier.Decision.VERIFIED,
                verifier.verify(
                        intent,
                        delivered,
                        readback(within, digest('6')),
                        Profile.DEBUG_SIMULATION,
                        NOW + 1_000L).getDecision());

        VerificationField outside = VerificationField.primary(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver",
                intent,
                temperature(22.1, 2L));
        EffectVerifier.Result mismatch = verifier.verify(
                intent,
                delivered,
                readback(outside, digest('5')),
                Profile.DEBUG_SIMULATION,
                NOW + 1_000L);
        assertEquals(EffectVerifier.Decision.PENDING, mismatch.getDecision());
        assertEquals("VERIFICATION_MISMATCH", mismatch.getReasonCode());
        assertEquals(EffectContract.STATE_APPLIED, mismatch.getLatestObservation().state);
        assertFalse(mismatch.getLatestObservation().terminal);
        SignalValue wrongPath = SignalValue.ofInteger(
                VehicleSignalPath.HVAC_FAN_LEVEL,
                3L,
                "level",
                "row1.driver",
                new SignalTimestamp(NOW, ELAPSED),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                3L);
        assertThrows(
                IllegalArgumentException.class,
                () -> VerificationField.primary(
                        VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                        "row1.driver",
                        intent,
                        wrongPath));
    }

    @Test
    public void stateTransitionAndCompositeUseBoundedTypedFields() {
        EffectIntent transitionIntent = temperatureIntent(
                EffectContract.VERIFY_STATE_TRANSITION, 0.0);
        VerificationField transition = new VerificationField(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver",
                TypedValue.fromIntent(transitionIntent),
                TypedValue.ofDecimal(20.0, "celsius"),
                TypedValue.ofDecimal(21.5, "celsius"),
                0.0,
                true);
        bindTarget(transitionIntent, List.of(transition));
        assertEquals(
                EffectVerifier.Decision.VERIFIED,
                verifier.verify(
                        transitionIntent,
                        observation(
                                transitionIntent,
                                EffectContract.STATE_DELIVERED,
                                EffectContract.SOURCE_SIMULATION,
                                SOURCE_ID),
                        readback(transition, digest('4')),
                        Profile.DEBUG_SIMULATION,
                        NOW + 1_000L).getDecision());

        EffectIntent compositeIntent = temperatureIntent(EffectContract.VERIFY_COMPOSITE, 0.0);
        VerificationField primary = new VerificationField(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver",
                TypedValue.fromIntent(compositeIntent),
                null,
                TypedValue.ofDecimal(21.5, "celsius"),
                0.0,
                true);
        VerificationField fan = new VerificationField(
                VehicleSignalPath.HVAC_FAN_LEVEL,
                "row1.driver",
                TypedValue.ofInteger(3L, "level"),
                null,
                TypedValue.ofInteger(3L, "level"),
                0.0,
                false);
        List<VerificationField> fields = List.of(primary, fan);
        bindTarget(compositeIntent, fields);
        EffectVerifier.Result composite = verifier.verify(
                compositeIntent,
                observation(
                        compositeIntent,
                        EffectContract.STATE_DELIVERED,
                        EffectContract.SOURCE_SIMULATION,
                        SOURCE_ID),
                VerificationEvidence.readback(
                        EffectContract.SOURCE_SIMULATION,
                        SOURCE_ID,
                        false,
                        NOW + 1_000L,
                        digest('3'),
                        fields),
                Profile.DEBUG_SIMULATION,
                NOW + 1_000L);
        assertEquals(EffectVerifier.Decision.VERIFIED, composite.getDecision());
    }

    @Test
    public void deadlineAndProductionTrustFailClosed() {
        EffectIntent intent = temperatureIntent(EffectContract.VERIFY_REPORTED_EQUALS, 0.0);
        VerificationField field = VerificationField.primary(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver",
                intent,
                temperature(21.5, 1L));
        bindTarget(intent, List.of(field));
        EffectObservation delivered = observation(
                intent, EffectContract.STATE_DELIVERED, EffectContract.SOURCE_SIMULATION, SOURCE_ID);
        EffectVerifier.Result expired = verifier.verify(
                intent,
                delivered,
                VerificationEvidence.unavailable(
                        EffectContract.SOURCE_SIMULATION,
                        SOURCE_ID,
                        false,
                        intent.deadlineEpochMs,
                        digest('2')),
                Profile.DEBUG_SIMULATION,
                intent.deadlineEpochMs);
        assertEquals(EffectVerifier.Decision.FAILED_TERMINAL, expired.getDecision());
        assertEquals("VERIFICATION_DEADLINE_EXCEEDED", expired.getReasonCode());

        EffectObservation adapterDelivered = observation(
                intent, EffectContract.STATE_DELIVERED, EffectContract.SOURCE_ADAPTER,
                "adapter.production.hvac");
        EffectVerifier.Result untrusted = verifier.verify(
                intent,
                adapterDelivered,
                VerificationEvidence.readback(
                        EffectContract.SOURCE_ADAPTER,
                        "adapter.production.hvac",
                        false,
                        NOW + 1_000L,
                        digest('1'),
                        List.of(field)),
                Profile.PRODUCTION,
                NOW + 1_000L);
        assertEquals(EffectVerifier.Decision.PENDING, untrusted.getDecision());
        assertEquals("UNTRUSTED_READBACK", untrusted.getReasonCode());
    }

    @Test
    public void unknownStatusSchedulesReconcileWithoutRedispatch() {
        EffectIntent intent = temperatureIntent(
                EffectContract.VERIFY_REPORTED_TOLERANCE, 0.5);
        bindTarget(intent, List.of());
        FakeAdapter adapter = new FakeAdapter(EffectAdapter.DeliveryState.UNKNOWN);
        DigitalTwinEffectReconciler reconciler = reconciler(intent, adapter);
        EffectObservation delivered = observation(
                intent, EffectContract.STATE_DELIVERED, EffectContract.SOURCE_SIMULATION, SOURCE_ID);

        DigitalTwinEffectReconciler.Result result = reconciler.reconcile(
                intent,
                delivered,
                Profile.DEBUG_SIMULATION,
                null,
                NOW + 1_000L,
                1);

        assertEquals(
                DigitalTwinEffectReconciler.Decision.RECONCILE_SCHEDULED,
                result.getDecision());
        assertTrue(result.wasAdapterQueried());
        assertEquals(1, adapter.queryCalls.get());
        assertEquals(0, adapter.applyCalls.get());
        assertEquals(NOW + 1_250L, result.getNextReconcileAtEpochMs());
        assertEquals(EffectContract.STATE_UNKNOWN, result.getLatestObservation().state);

        FakeAdapter invalid = new FakeAdapter(EffectAdapter.DeliveryState.UNKNOWN);
        invalid.wrongToken = true;
        DigitalTwinEffectReconciler.Result invalidResult = reconciler(intent, invalid).reconcile(
                intent,
                delivered,
                Profile.DEBUG_SIMULATION,
                null,
                NOW + 1_000L,
                1);
        assertEquals(
                DigitalTwinEffectReconciler.Decision.FAILED_TERMINAL,
                invalidResult.getDecision());
        assertEquals("ADAPTER_STATUS_CONTRACT_INVALID", invalidResult.getReasonCode());
    }

    @Test
    public void matchedTwinVerifiesAndVerifiedReplaySkipsAdapterQuery() {
        EffectIntent intent = temperatureIntent(
                EffectContract.VERIFY_REPORTED_TOLERANCE, 0.5);
        bindTarget(intent, List.of());
        FakeAdapter adapter = new FakeAdapter(EffectAdapter.DeliveryState.APPLIED);
        DigitalTwinEffectReconciler reconciler = reconciler(intent, adapter);
        DigitalTwinSnapshot snapshot = snapshot(21.7);
        EffectObservation delivered = observation(
                intent, EffectContract.STATE_DELIVERED, EffectContract.SOURCE_SIMULATION, SOURCE_ID);

        DigitalTwinEffectReconciler.Result verified = reconciler.reconcile(
                intent,
                delivered,
                Profile.DEBUG_SIMULATION,
                snapshot,
                NOW + 1_000L,
                1);
        assertEquals(DigitalTwinEffectReconciler.Decision.VERIFIED, verified.getDecision());
        assertEquals(2, verified.getTransitions().size());
        assertEquals(1, adapter.queryCalls.get());

        EffectObservation mutable = verified.getLatestObservation();
        DigitalTwinEffectReconciler.Result replay = reconciler.reconcile(
                intent,
                mutable,
                Profile.DEBUG_SIMULATION,
                snapshot,
                NOW + 2_000L,
                2);
        assertEquals(
                DigitalTwinEffectReconciler.Decision.ALREADY_VERIFIED,
                replay.getDecision());
        assertFalse(replay.wasAdapterQueried());
        assertEquals(1, adapter.queryCalls.get());
        assertEquals(0, adapter.applyCalls.get());
    }

    @Test
    public void mismatchedTwinRemainsAppliedAndSchedulesAnotherReadback() {
        EffectIntent intent = temperatureIntent(
                EffectContract.VERIFY_REPORTED_TOLERANCE, 0.5);
        bindTarget(intent, List.of());
        FakeAdapter adapter = new FakeAdapter(EffectAdapter.DeliveryState.APPLIED);
        DigitalTwinEffectReconciler.Result result = reconciler(intent, adapter).reconcile(
                intent,
                observation(
                        intent,
                        EffectContract.STATE_DELIVERED,
                        EffectContract.SOURCE_SIMULATION,
                        SOURCE_ID),
                Profile.DEBUG_SIMULATION,
                snapshot(23.0),
                NOW + 1_000L,
                2);

        assertEquals(
                DigitalTwinEffectReconciler.Decision.APPLIED_PENDING_VERIFICATION,
                result.getDecision());
        assertEquals(EffectContract.STATE_APPLIED, result.getLatestObservation().state);
        assertEquals(NOW + 1_500L, result.getNextReconcileAtEpochMs());
        assertEquals(0, adapter.applyCalls.get());
    }

    @Test
    public void notAppliedAndProductionProfileNeverDispatchOrUseDebugReadback() {
        EffectIntent intent = temperatureIntent(
                EffectContract.VERIFY_REPORTED_TOLERANCE, 0.5);
        bindTarget(intent, List.of());
        FakeAdapter adapter = new FakeAdapter(EffectAdapter.DeliveryState.NOT_APPLIED);
        DigitalTwinEffectReconciler reconciler = reconciler(intent, adapter);
        EffectObservation delivered = observation(
                intent, EffectContract.STATE_DELIVERED, EffectContract.SOURCE_SIMULATION, SOURCE_ID);
        DigitalTwinEffectReconciler.Result notApplied = reconciler.reconcile(
                intent,
                delivered,
                Profile.DEBUG_SIMULATION,
                null,
                NOW + 1_000L,
                1);
        assertEquals(
                DigitalTwinEffectReconciler.Decision.CONFIRMED_NOT_APPLIED,
                notApplied.getDecision());
        assertEquals(0L, notApplied.getNextReconcileAtEpochMs());
        assertEquals(0, adapter.applyCalls.get());

        DigitalTwinEffectReconciler.Result regression = reconciler.reconcile(
                intent,
                observation(
                        intent,
                        EffectContract.STATE_APPLIED,
                        EffectContract.SOURCE_SIMULATION,
                        SOURCE_ID),
                Profile.DEBUG_SIMULATION,
                null,
                NOW + 1_000L,
                1);
        assertEquals(
                DigitalTwinEffectReconciler.Decision.FAILED_TERMINAL,
                regression.getDecision());
        assertEquals("ADAPTER_STATUS_REGRESSION", regression.getReasonCode());

        DigitalTwinEffectReconciler.Result production = reconciler.reconcile(
                intent,
                delivered,
                Profile.PRODUCTION,
                snapshot(21.5),
                NOW + 1_000L,
                1);
        assertEquals(
                DigitalTwinEffectReconciler.Decision.PRODUCTION_READBACK_UNAVAILABLE,
                production.getDecision());
        assertFalse(production.wasAdapterQueried());
        assertEquals(2, adapter.queryCalls.get());
        assertEquals(0, adapter.applyCalls.get());
    }

    private DigitalTwinEffectReconciler reconciler(
            EffectIntent intent,
            FakeAdapter adapter) {
        AdapterRegistry registry = new AdapterRegistry(List.of(
                new AdapterRegistry.Registration(
                        SOURCE_ID,
                        intent.capabilityId,
                        intent.targetArea,
                        Profile.DEBUG_SIMULATION,
                        true,
                        true,
                        false,
                        ignoredPreparation(),
                        adapter)));
        return new DigitalTwinEffectReconciler(registry, verifier);
    }

    private static AdapterRegistry.PreparationAdapter ignoredPreparation() {
        return (intent, nowEpochMs) -> PrepareResult.ready(new PreparedMaterial(
                intent.actionId,
                DESTINATION,
                "payload".getBytes(StandardCharsets.UTF_8),
                "envelope".getBytes(StandardCharsets.UTF_8),
                digest('a'),
                digest('b')));
    }

    private static DigitalTwinSnapshot snapshot(double value) {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        store.updateReported(temperature(value, 1L), ELAPSED);
        return store.snapshot(
                java.util.Set.of(VehicleSignalPath.HVAC_TARGET_TEMPERATURE),
                ELAPSED);
    }

    private static SignalValue temperature(double value, long revision) {
        return SignalValue.ofDecimal(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                value,
                "celsius",
                "row1.driver",
                new SignalTimestamp(NOW, ELAPSED),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                revision);
    }

    private static VerificationEvidence readback(
            VerificationField field,
            String evidenceDigest) {
        return VerificationEvidence.readback(
                EffectContract.SOURCE_SIMULATION,
                SOURCE_ID,
                false,
                NOW + 1_000L,
                evidenceDigest,
                List.of(field));
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

    private static EffectIntent mediaIntent() {
        EffectIntent intent = baseIntent();
        intent.capabilityId = "media.playback";
        intent.targetArea = "vehicle.cabin";
        intent.valueKind = EffectContract.VALUE_TEXT;
        intent.textValue = "PLAY";
        intent.unit = "";
        intent.riskClass = EffectContract.RISK_LOW;
        intent.verificationPolicy = EffectContract.VERIFY_CALLBACK_ONLY;
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
            List<VerificationField> expectedFields) {
        intent.targetValueDigest = EffectVerifier.expectedTargetDigest(intent, expectedFields);
        EffectContract.validateIntent(intent, NOW);
    }

    private static EffectObservation observation(
            EffectIntent intent,
            int state,
            int source,
            String sourceId) {
        EffectObservation observation = new EffectObservation();
        observation.observationId = "7ed8f09c-56fd-4ec6-94ac-68ce0e6fa9f8";
        observation.effectId = intent.effectId;
        observation.sessionId = intent.sessionId;
        observation.actionId = intent.actionId;
        observation.planDigest = intent.planDigest;
        observation.contextVersion = intent.contextVersion;
        observation.state = state;
        observation.source = source;
        observation.sourceId = sourceId;
        observation.attempt = 1;
        observation.targetValueDigest = intent.targetValueDigest;
        observation.reportedValueDigest = state == EffectContract.STATE_APPLIED
                || state == EffectContract.STATE_VERIFIED ? digest('9') : "";
        observation.evidenceDigest = digest('8');
        observation.observationDigest = digest('7');
        observation.failureCode = state == EffectContract.STATE_UNKNOWN
                ? "DELIVERY_UNKNOWN" : "";
        observation.occurredAtEpochMs = NOW;
        observation.terminal = state == EffectContract.STATE_VERIFIED;
        observation.retryable = false;
        observation.simulated = source == EffectContract.SOURCE_SIMULATION;
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
        private boolean wrongToken;

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
            return new StatusResult(
                    wrongToken ? digest('4') : idempotencyToken,
                    state,
                    digest('5'));
        }
    }
}
