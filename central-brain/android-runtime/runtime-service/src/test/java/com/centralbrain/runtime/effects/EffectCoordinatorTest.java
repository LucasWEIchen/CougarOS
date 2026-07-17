package com.centralbrain.runtime.effects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.effects.AdapterRegistry.PrepareResult;
import com.centralbrain.runtime.effects.AdapterRegistry.PreparedMaterial;
import com.centralbrain.runtime.effects.AdapterRegistry.Profile;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;
import com.centralbrain.sdk.effect.EffectObservation;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class EffectCoordinatorTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private static final String PLAN_ID = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
    private static final String ACTION_ID = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
    private static final String BATCH_ID = "692bde18-dd9e-4378-908b-2c249c77c673";
    private static final String EFFECT_A = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
    private static final String EFFECT_B = "c36ba71d-8757-4399-a30a-7234f7833c2e";
    private static final String EFFECT_C = "234f296f-94a6-4d01-a6a4-591985ca79a7";
    private static final String DESTINATION = "debug.vehicle.effect";

    @Test
    public void batchFreezesInputsAndDigestBindsDependencyStructure() {
        EffectIntent first = intent(EFFECT_A, true, "effect:a");
        EffectIntent second = intent(EFFECT_B, true, "effect:b");
        List<String> dependencies = new ArrayList<>();
        dependencies.add(EFFECT_A);
        EffectBatch batch = batch(
                entry(first, "vehicle:hvac:driver"),
                new EffectBatch.Entry(second, "vehicle:seat:driver", dependencies));
        String digest = batch.getBatchDigest();

        first.targetArea = "vehicle.cabin.changed";
        dependencies.clear();
        EffectIntent returned = batch.getEntries().get(0).getIntent();
        returned.targetArea = "vehicle.cabin.changed_again";

        assertEquals("vehicle.cabin.driver", batch.getEntries().get(0).getIntent().targetArea);
        assertEquals(Collections.singletonList(EFFECT_A),
                batch.getEntries().get(1).getDependencyEffectIds());
        assertEquals(digest, batch.getBatchDigest());

        EffectBatch independent = batch(
                entry(intent(EFFECT_A, true, "effect:a"), "vehicle:hvac:driver"),
                entry(intent(EFFECT_B, true, "effect:b"), "vehicle:seat:driver"));
        assertNotEquals(batch.getBatchDigest(), independent.getBatchDigest());
        assertThrows(
                IllegalArgumentException.class,
                () -> batch(
                        entry(intent(EFFECT_A, true, "effect:a"), "vehicle:hvac:driver"),
                        entry(intent(EFFECT_A, true, "effect:a2"), "vehicle:seat:driver")));
    }

    @Test
    public void plannerOrdersDependenciesAndSerializesResourceConflicts() {
        EffectBatch batch = batch(
                entry(intent(EFFECT_A, true, "effect:a"), "vehicle:hvac:driver"),
                entry(intent(EFFECT_B, true, "effect:b"), "vehicle:hvac:driver"),
                new EffectBatch.Entry(
                        intent(EFFECT_C, true, "effect:c"),
                        "vehicle:seat:driver",
                        Collections.singletonList(EFFECT_A)));

        EffectDependencyPlanner.Plan plan = new EffectDependencyPlanner().plan(batch);

        assertEquals(2, plan.getWaves().size());
        assertEquals(0, plan.getWaveIndex(EFFECT_A));
        assertEquals(1, plan.getWaveIndex(EFFECT_B));
        assertEquals(1, plan.getWaveIndex(EFFECT_C));
        assertEquals(2, plan.getWaves().get(1).getEffectIds().size());
    }

    @Test
    public void plannerRejectsDependencyCycles() {
        EffectBatch cyclic = batch(
                new EffectBatch.Entry(
                        intent(EFFECT_A, true, "effect:a"),
                        "vehicle:hvac:driver",
                        Collections.singletonList(EFFECT_B)),
                new EffectBatch.Entry(
                        intent(EFFECT_B, true, "effect:b"),
                        "vehicle:seat:driver",
                        Collections.singletonList(EFFECT_A)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new EffectDependencyPlanner().plan(cyclic));
        assertTrue(error.getMessage().startsWith("CB_EFFECT_DEPENDENCY:"));
    }

    @Test
    public void requiredPrepareFailureAbortsBatchBeforeAnyDispatch() {
        AtomicInteger prepareCalls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter();
        AdapterRegistry registry = registry(
                preparing(prepareCalls, EFFECT_B), adapter);
        EffectCoordinator coordinator = coordinator(registry);
        EffectBatch batch = batch(
                entry(intent(EFFECT_A, true, "effect:a"), "vehicle:hvac:driver"),
                entry(intent(EFFECT_B, true, "effect:b"), "vehicle:seat:driver"));

        EffectCoordinator.ExecutionResult result = coordinator.execute(
                batch, Profile.DEBUG_SIMULATION, NOW);

        assertEquals(2, prepareCalls.get());
        assertEquals(0, adapter.applyCount.get());
        assertEquals(EffectCoordinator.BatchStatus.PREPARE_REJECTED, result.getStatus());
        assertEquals(2, result.getItemResults().size());
        assertEquals(EffectCoordinator.Outcome.BATCH_ABORTED,
                find(result, EFFECT_A).getOutcome());
        assertEquals(EffectCoordinator.Outcome.PREPARE_REJECTED,
                find(result, EFFECT_B).getOutcome());
        assertTrue(find(result, EFFECT_B).isRequired());
        assertEquals(EffectContract.SOURCE_SIMULATION,
                find(result, EFFECT_B).getObservation().source);
    }

    @Test
    public void optionalPrepareFailureDegradesWithoutBlockingRequiredDispatch() {
        AtomicInteger prepareCalls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter();
        AdapterRegistry registry = registry(
                preparing(prepareCalls, EFFECT_B), adapter);
        EffectCoordinator coordinator = coordinator(registry);
        EffectBatch batch = batch(
                entry(intent(EFFECT_A, true, "effect:a"), "vehicle:hvac:driver"),
                entry(intent(EFFECT_B, false, "effect:b"), "vehicle:seat:driver"));

        EffectCoordinator.ExecutionResult result = coordinator.execute(
                batch, Profile.DEBUG_SIMULATION, NOW);

        assertEquals(2, prepareCalls.get());
        assertEquals(1, adapter.applyCount.get());
        assertEquals(EffectCoordinator.BatchStatus.PARTIAL, result.getStatus());
        assertEquals(EffectCoordinator.Outcome.DELIVERED, find(result, EFFECT_A).getOutcome());
        assertFalse(find(result, EFFECT_B).isRequired());
    }

    @Test
    public void registryRequiresExactProfileAndNeverFallsBackToSimulation() {
        AtomicInteger descriptorCalls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter(descriptorCalls, true);
        AdapterRegistry registry = registry(preparing(new AtomicInteger()), adapter);
        int callsAfterRegistration = descriptorCalls.get();

        registry.resolve(
                "vehicle.hvac.temperature",
                "vehicle.cabin.driver",
                Profile.DEBUG_SIMULATION);
        assertEquals(callsAfterRegistration, descriptorCalls.get());
        assertThrows(
                AdapterRegistry.AdapterUnavailableException.class,
                () -> registry.resolve(
                        "vehicle.hvac.temperature",
                        "vehicle.cabin.driver",
                        Profile.PRODUCTION));

        assertThrows(
                EffectAdapterContract.UnsafeAdapterException.class,
                () -> registry(preparing(new AtomicInteger()),
                        new FakeAdapter(new AtomicInteger(), false)));
    }

    @Test
    public void coordinatorEmitsOneTypedObservationPerMixedOutcome() {
        FakeAdapter adapter = new FakeAdapter();
        adapter.states.put(EFFECT_B, EffectAdapter.ApplyState.UNKNOWN);
        adapter.states.put(EFFECT_C, EffectAdapter.ApplyState.TERMINAL_FAILURE);
        EffectCoordinator coordinator = coordinator(registry(
                preparing(new AtomicInteger()), adapter));
        EffectBatch batch = batch(
                entry(intent(EFFECT_A, true, "effect:a"), "vehicle:hvac:driver"),
                entry(intent(EFFECT_B, true, "effect:b"), "vehicle:seat:driver"),
                entry(intent(EFFECT_C, true, "effect:c"), "vehicle:media:primary"));

        EffectCoordinator.ExecutionResult result = coordinator.execute(
                batch, Profile.DEBUG_SIMULATION, NOW);

        assertEquals(3, adapter.applyCount.get());
        assertEquals(EffectCoordinator.BatchStatus.FAILED, result.getStatus());
        assertEquals(EffectCoordinator.Outcome.UNKNOWN, find(result, EFFECT_B).getOutcome());
        assertEquals(EffectCoordinator.Outcome.TERMINAL_FAILURE,
                find(result, EFFECT_C).getOutcome());
        assertNotEquals(
                find(result, EFFECT_A).getObservation().observationId,
                find(result, EFFECT_B).getObservation().observationId);
        for (EffectCoordinator.ItemResult item : result.getItemResults()) {
            EffectContract.validateObservation(item.getObservation());
        }
    }

    @Test
    public void unknownDependencyBlocksChildAndResultRemainsDefensive() {
        FakeAdapter adapter = new FakeAdapter();
        adapter.states.put(EFFECT_A, EffectAdapter.ApplyState.UNKNOWN);
        EffectCoordinator coordinator = coordinator(registry(
                preparing(new AtomicInteger()), adapter));
        EffectBatch batch = batch(
                entry(intent(EFFECT_A, true, "effect:a"), "vehicle:hvac:driver"),
                new EffectBatch.Entry(
                        intent(EFFECT_B, true, "effect:b"),
                        "vehicle:seat:driver",
                        Collections.singletonList(EFFECT_A)));

        EffectCoordinator.ExecutionResult result = coordinator.execute(
                batch, Profile.DEBUG_SIMULATION, NOW);

        assertEquals(1, adapter.applyCount.get());
        assertEquals(EffectCoordinator.Outcome.UNKNOWN, find(result, EFFECT_A).getOutcome());
        assertEquals(EffectCoordinator.Outcome.DEPENDENCY_BLOCKED,
                find(result, EFFECT_B).getOutcome());
        EffectObservation first = find(result, EFFECT_A).getObservation();
        EffectObservation second = find(result, EFFECT_A).getObservation();
        assertNotSame(first, second);
        first.failureCode = "MUTATED";
        assertEquals("DELIVERY_UNKNOWN", second.failureCode);
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.getItemResults().clear());
    }

    @Test
    public void preparedMaterialDefensivelyCopiesTransientBytes() {
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        byte[] envelope = "envelope".getBytes(StandardCharsets.UTF_8);
        PreparedMaterial material = material(intent(EFFECT_A, true, "effect:a"), payload, envelope);
        payload[0] = 0;
        envelope[0] = 0;

        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8),
                material.getCanonicalPayload());
        assertArrayEquals("envelope".getBytes(StandardCharsets.UTF_8),
                material.getCanonicalEnvelope());
        byte[] returned = material.getCanonicalPayload();
        returned[0] = 0;
        assertEquals((byte) 'p', material.getCanonicalPayload()[0]);
    }

    private static EffectCoordinator coordinator(AdapterRegistry registry) {
        return new EffectCoordinator(registry, new EffectDependencyPlanner());
    }

    private static AdapterRegistry registry(
            AdapterRegistry.PreparationAdapter preparation,
            EffectAdapter adapter) {
        AdapterRegistry.Registration registration = new AdapterRegistry.Registration(
                "adapter.debug.vehicle",
                "vehicle.hvac.temperature",
                "vehicle.cabin.driver",
                Profile.DEBUG_SIMULATION,
                true,
                true,
                false,
                preparation,
                adapter);
        return new AdapterRegistry(Collections.singletonList(registration));
    }

    private static AdapterRegistry.PreparationAdapter preparing(
            AtomicInteger calls,
            String... rejectedEffectIds) {
        List<String> rejected = Arrays.asList(rejectedEffectIds);
        return (intent, nowEpochMs) -> {
            calls.incrementAndGet();
            if (rejected.contains(intent.effectId)) {
                return PrepareResult.rejected("PREPARE_REJECTED");
            }
            return PrepareResult.ready(material(
                    intent,
                    ("payload:" + intent.effectId).getBytes(StandardCharsets.UTF_8),
                    ("envelope:" + intent.effectId).getBytes(StandardCharsets.UTF_8)));
        };
    }

    private static PreparedMaterial material(
            EffectIntent intent,
            byte[] payload,
            byte[] envelope) {
        return new PreparedMaterial(
                intent.actionId,
                DESTINATION,
                payload,
                envelope,
                digest('b'),
                digest('c'));
    }

    private static EffectCoordinator.ItemResult find(
            EffectCoordinator.ExecutionResult result,
            String effectId) {
        for (EffectCoordinator.ItemResult item : result.getItemResults()) {
            if (item.getEffectId().equals(effectId)) {
                return item;
            }
        }
        throw new AssertionError("missing result for " + effectId);
    }

    private static EffectBatch batch(EffectBatch.Entry... entries) {
        return EffectBatch.create(BATCH_ID, Arrays.asList(entries), NOW);
    }

    private static EffectBatch.Entry entry(EffectIntent intent, String resourceKey) {
        return new EffectBatch.Entry(intent, resourceKey, Collections.emptyList());
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
        intent.createdAtEpochMs = NOW - 1_000;
        intent.deadlineEpochMs = NOW + 60_000;
        return intent;
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static final class FakeAdapter implements EffectAdapter {
        private final AtomicInteger descriptorCalls;
        private final boolean safe;
        private final AtomicInteger applyCount = new AtomicInteger();
        private final Map<String, ApplyState> states = new HashMap<>();

        private FakeAdapter() {
            this(new AtomicInteger(), true);
        }

        private FakeAdapter(AtomicInteger descriptorCalls, boolean safe) {
            this.descriptorCalls = descriptorCalls;
            this.safe = safe;
        }

        @Override
        public Descriptor descriptor() {
            descriptorCalls.incrementAndGet();
            return new Descriptor(
                    "adapter.debug.vehicle",
                    DESTINATION,
                    safe ? IdempotencyMode.TOKEN_DEDUPLICATED : IdempotencyMode.NONE,
                    true,
                    true,
                    StatusConsistency.LINEARIZABLE,
                    1_000);
        }

        @Override
        public ApplyResult apply(Invocation invocation) {
            applyCount.incrementAndGet();
            return new ApplyResult(
                    invocation.getIdempotencyToken(),
                    states.getOrDefault(invocation.getEffectId(), ApplyState.APPLIED),
                    digest('9'));
        }

        @Override
        public StatusResult queryStatus(String idempotencyToken) {
            return new StatusResult(idempotencyToken, DeliveryState.UNKNOWN, digest('8'));
        }
    }
}
