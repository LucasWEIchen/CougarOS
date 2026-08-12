package com.centralbrain.runtime.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.graph.AgentGraphRuntime;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.DrivingProfile;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.Input;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.ScenarioKind;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class SimulatedScenarioInputFactoryTest {
    private static final Path ASSETS = Paths.get(
            "src/main/assets/scenarios").toAbsolutePath().normalize();
    private static final long NOW_EPOCH_MS = 1_760_000_000_000L;
    private static final long NOW_ELAPSED_MS = 2_000L;
    private static final List<String> UUIDS = List.of(
            "0db2b1b0-d171-4691-b0a3-43994dfb4aa5",
            "71907824-f729-4f7a-8ccb-376e9fb01bad",
            "90f79aba-0cb4-497c-8131-e2081e823d6c",
            "75f062c3-e9a4-4bcd-969d-123d61493d6e",
            "813f5f12-1cc7-4395-8751-629b8de9c232",
            "ec328b98-cf78-431b-b847-e6c9237b750a");

    @Test
    public void coldParkedInputStartsAtEffectPending() throws Exception {
        SimulatedScenarioRuntime runtime = runtime();
        Input input = factory().create(ScenarioKind.COLD, DrivingProfile.PARKED);

        SimulatedScenarioRuntime.Snapshot snapshot = runtime.start(
                input.getRequest(),
                input.getResolution(),
                input.getContext(),
                input.getCapabilities());

        assertEquals("scene.comfort.cold.v1", snapshot.getScenarioId());
        assertEquals(SimulatedScenarioRuntime.SessionState.WAITING_EFFECT,
                snapshot.getSessionState());
        assertEquals("set_hvac_power", snapshot.getPendingNode().getNodeId());
    }

    @Test
    public void fatigueParkedInputStartsAtApprovalPending() throws Exception {
        SimulatedScenarioRuntime runtime = runtime();
        Input input = factory().create(ScenarioKind.FATIGUE, DrivingProfile.PARKED);

        SimulatedScenarioRuntime.Snapshot snapshot = runtime.start(
                input.getRequest(),
                input.getResolution(),
                input.getContext(),
                input.getCapabilities());

        assertEquals("scene.fatigue.assist.v1", snapshot.getScenarioId());
        assertEquals(SimulatedScenarioRuntime.SessionState.WAITING_APPROVAL,
                snapshot.getSessionState());
        assertEquals("request_seat_approval", snapshot.getPendingNode().getNodeId());
    }

    @Test
    public void fatigueMovingInputKeepsSeatApprovalAndReclinePruned() throws Exception {
        SimulatedScenarioRuntime runtime = runtime();
        Input input = factory().create(ScenarioKind.FATIGUE, DrivingProfile.MOVING);

        SimulatedScenarioRuntime.Snapshot snapshot = runtime.start(
                input.getRequest(),
                input.getResolution(),
                input.getContext(),
                input.getCapabilities());

        assertEquals(SimulatedScenarioRuntime.SessionState.WAITING_EFFECT,
                snapshot.getSessionState());
        assertEquals("set_hvac_power", snapshot.getPendingNode().getNodeId());
        assertNotEquals("vehicle.seat.recline",
                snapshot.getPendingNode().getCapabilityId());
    }

    @Test
    public void freeformInputPublishesAResponseOnlyPlanWithoutEffects() throws Exception {
        SimulatedScenarioRuntime runtime = runtime();
        Input input = factory().create(
                ScenarioKind.AIOS_FREEFORM, DrivingProfile.PARKED);

        SimulatedScenarioRuntime.Snapshot snapshot = runtime.start(
                input.getRequest(),
                input.getResolution(),
                input.getContext(),
                input.getCapabilities());

        assertEquals("scene.aios.freeform.v1", snapshot.getScenarioId());
        assertEquals(
                SimulatedScenarioRuntime.SessionState.COMPLETED,
                snapshot.getSessionState());
        assertEquals(null, snapshot.getPendingNode());
    }

    @Test
    public void smokingInputUsesCabinContextAndResponseOnlyPlan() throws Exception {
        SimulatedScenarioRuntime runtime = runtime();
        Input input = factory().create(
                ScenarioKind.CABIN_SMOKING, DrivingProfile.PARKED);

        SimulatedScenarioRuntime.Snapshot snapshot = runtime.start(
                input.getRequest(),
                input.getResolution(),
                input.getContext(),
                input.getCapabilities());

        assertEquals("scene.cabin.compliance.smoking.v1", snapshot.getScenarioId());
        assertEquals(com.centralbrain.runtime.context.ContextSnapshot.SeatZone.CABIN,
                input.getContext().getSeatZone());
        assertEquals(SimulatedScenarioRuntime.SessionState.COMPLETED,
                snapshot.getSessionState());
        assertEquals(null, snapshot.getPendingNode());
    }

    @Test
    public void nullOrInvalidFactoryInputsFailClosed() throws Exception {
        SimulatedScenarioInputFactory factory = factory();

        assertThrows(NullPointerException.class,
                () -> factory.create(null, DrivingProfile.PARKED));
        assertThrows(NullPointerException.class,
                () -> factory.create(ScenarioKind.COLD, null));
        assertThrows(IllegalArgumentException.class, () ->
                new SimulatedScenarioInputFactory(
                        catalog(),
                        () -> -1,
                        () -> NOW_ELAPSED_MS,
                        () -> UUIDS.get(0))
                        .create(ScenarioKind.COLD, DrivingProfile.PARKED));
    }

    @Test
    public void generatedPlanAndSessionIdentitiesAreUnique() throws Exception {
        SimulatedScenarioInputFactory factory = factory();
        SimulatedScenarioRuntime runtime = runtime();
        Input first = factory.create(ScenarioKind.COLD, DrivingProfile.PARKED);
        Input second = factory.create(ScenarioKind.FATIGUE, DrivingProfile.PARKED);

        SimulatedScenarioRuntime.Snapshot firstSnapshot = runtime.start(
                first.getRequest(), first.getResolution(), first.getContext(),
                first.getCapabilities());
        SimulatedScenarioRuntime.Snapshot secondSnapshot = runtime.start(
                second.getRequest(), second.getResolution(), second.getContext(),
                second.getCapabilities());

        assertNotEquals(firstSnapshot.getRunId(), secondSnapshot.getRunId());
        assertNotEquals(firstSnapshot.getSessionId(), secondSnapshot.getSessionId());
        assertEquals(2, runtime.size());
    }

    @Test
    public void binderSnapshotContainsOnlyMetadataAndFalseAuthorityClaims() throws Exception {
        SimulatedScenarioRuntime runtime = runtime();
        Input input = factory().create(ScenarioKind.COLD, DrivingProfile.PARKED);
        SimulatedScenarioRuntime.Snapshot source = runtime.start(
                input.getRequest(), input.getResolution(), input.getContext(),
                input.getCapabilities());

        SimulatedScenarioBinderSnapshot snapshot =
                SimulatedScenarioBinderSnapshot.from(source);

        assertEquals(1, snapshot.schemaVersion);
        assertEquals(source.getRunId(), snapshot.runId);
        assertEquals(source.getProjectionDigest(), snapshot.projectionDigest);
        assertEquals(ISimulatedScenarioRuntime.SESSION_WAITING_EFFECT,
                snapshot.sessionState);
        assertEquals(ISimulatedScenarioRuntime.PENDING_EFFECT, snapshot.pendingStage);
        assertTrue(snapshot.planDigest.matches("[0-9a-f]{64}"));
        assertFalse(snapshot.effectDispatchEnabled);
        assertFalse(snapshot.readbackAccessed);
        assertFalse(snapshot.approvalAuthorityAvailable);
        assertFalse(snapshot.hardwareAccessed);
        assertFalse(snapshot.productionReady);
        assertFalse(snapshot.targetHardwareValidated);
    }

    private static SimulatedScenarioInputFactory factory() throws Exception {
        AtomicInteger index = new AtomicInteger();
        return new SimulatedScenarioInputFactory(
                catalog(),
                () -> NOW_EPOCH_MS,
                () -> NOW_ELAPSED_MS,
                () -> UUIDS.get(index.getAndIncrement()));
    }

    private static SimulatedScenarioRuntime runtime() {
        return SimulatedScenarioRuntime.createForContractTest(
                new AgentGraphRuntime.Clock() {
                    @Override
                    public long epochTimeMs() {
                        return NOW_EPOCH_MS;
                    }

                    @Override
                    public long elapsedRealtimeMs() {
                        return NOW_ELAPSED_MS;
                    }
                },
                () -> "p4-d4c-subscription");
    }

    private static ScenarioCatalog catalog() throws Exception {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (String name : List.of(
                "scene.aios.freeform.v1.json",
                "scene.cabin.compliance.smoking.v1.json",
                "scene.comfort.cold.v1.json",
                "scene.fatigue.assist.v1.json",
                "scene.rest.nap.v1.json")) {
            assets.put(name, Files.readAllBytes(ASSETS.resolve(name)));
        }
        return ScenarioCatalog.load(assets);
    }
}
