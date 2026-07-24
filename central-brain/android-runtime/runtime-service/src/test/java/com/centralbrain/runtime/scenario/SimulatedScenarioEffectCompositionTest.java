package com.centralbrain.runtime.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.graph.AgentGraphRuntime;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.DrivingProfile;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.ScenarioKind;
import com.centralbrain.runtime.simulation.FaultInjectionProfile;
import com.centralbrain.runtime.simulation.SimulationClock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class SimulatedScenarioEffectCompositionTest {
    private static final Path ASSETS = Paths.get(
            "src/main/assets/scenarios").toAbsolutePath().normalize();
    private static final long EPOCH_MS = 1_760_000_000_000L;
    private static final long ELAPSED_MS = 10_000L;

    @Test
    public void coldScenarioAutomaticallyDispatchesAndMatchesReadback() throws Exception {
        Fixture fixture = fixture();

        SimulatedScenarioEffectComposition.Snapshot snapshot = fixture.composition.start(
                fixture.inputs.create(ScenarioKind.COLD, DrivingProfile.PARKED),
                ScenarioKind.COLD,
                DrivingProfile.PARKED);

        assertEquals(SimulatedScenarioRuntime.SessionState.COMPLETED,
                snapshot.getSessionState());
        assertNull(snapshot.getPendingNode());
        assertEquals(3, snapshot.getEffectDispatchCount());
        assertEquals(3, snapshot.getReadbackAttemptCount());
        assertEquals(3, snapshot.getReadbackMatchCount());
        assertEquals(0, snapshot.getApprovalInputCount());
        assertEquals(0, snapshot.getFailureCount());
        assertTrue(snapshot.isSimulatedEffectDispatchEnabled());
        assertTrue(snapshot.isReadbackAccessed());
        assertFalse(snapshot.isHardwareAccessed());
    }

    @Test
    public void parkedFatigueWaitsForApprovalThenRunsAllSimulatedEffects()
            throws Exception {
        Fixture fixture = fixture();
        SimulatedScenarioEffectComposition.Snapshot pending = fixture.composition.start(
                fixture.inputs.create(ScenarioKind.FATIGUE, DrivingProfile.PARKED),
                ScenarioKind.FATIGUE,
                DrivingProfile.PARKED);

        assertEquals(SimulatedScenarioRuntime.SessionState.WAITING_APPROVAL,
                pending.getSessionState());
        assertEquals(SimulatedScenarioGraph.PendingStage.APPROVAL,
                pending.getPendingNode().getStage());
        assertEquals(0, pending.getEffectDispatchCount());
        assertFalse(pending.isApprovalAuthorityAvailable());

        SimulatedScenarioEffectComposition.Snapshot completed =
                fixture.composition.supplyApprovalOutcome(
                        pending.getRunId(),
                        AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);

        assertEquals(SimulatedScenarioRuntime.SessionState.COMPLETED,
                completed.getSessionState());
        assertEquals(5, completed.getEffectDispatchCount());
        assertEquals(3, completed.getReadbackAttemptCount());
        assertEquals(3, completed.getReadbackMatchCount());
        assertEquals(1, completed.getApprovalInputCount());
        assertEquals(0, completed.getFailureCount());
        assertFalse(completed.isApprovalAuthorityAvailable());
    }

    @Test
    public void movingFatigueKeepsApprovalAndReclineBranchPruned() throws Exception {
        Fixture fixture = fixture();

        SimulatedScenarioEffectComposition.Snapshot snapshot = fixture.composition.start(
                fixture.inputs.create(ScenarioKind.FATIGUE, DrivingProfile.MOVING),
                ScenarioKind.FATIGUE,
                DrivingProfile.MOVING);

        assertEquals(SimulatedScenarioRuntime.SessionState.COMPLETED,
                snapshot.getSessionState());
        assertEquals(4, snapshot.getEffectDispatchCount());
        assertEquals(2, snapshot.getReadbackAttemptCount());
        assertEquals(2, snapshot.getReadbackMatchCount());
        assertEquals(0, snapshot.getApprovalInputCount());
        assertEquals(0, snapshot.getFailureCount());
    }

    @Test
    public void skippedApprovalCompletesPartialWithoutSeatRecline() throws Exception {
        Fixture fixture = fixture();
        SimulatedScenarioEffectComposition.Snapshot pending = fixture.composition.start(
                fixture.inputs.create(ScenarioKind.FATIGUE, DrivingProfile.PARKED),
                ScenarioKind.FATIGUE,
                DrivingProfile.PARKED);

        SimulatedScenarioEffectComposition.Snapshot completed =
                fixture.composition.supplyApprovalOutcome(
                        pending.getRunId(),
                        AgentGraphRuntime.NodeExecutionOutcome.SKIPPED);

        assertEquals(SimulatedScenarioRuntime.SessionState.PARTIAL,
                completed.getSessionState());
        assertEquals(4, completed.getEffectDispatchCount());
        assertEquals(2, completed.getReadbackMatchCount());
        assertEquals(1, completed.getApprovalInputCount());
        assertEquals(0, completed.getFailureCount());
    }

    @Test
    public void cabinShoppingRunsSixToolsAcrossThreeIndependentConfirmations()
            throws Exception {
        Fixture fixture = fixture();
        SimulatedScenarioEffectComposition.Snapshot assistance =
                fixture.composition.start(
                        fixture.inputs.create(
                                ScenarioKind.CABIN_MULTIMODAL,
                                DrivingProfile.PARKED),
                        ScenarioKind.CABIN_MULTIMODAL,
                        DrivingProfile.PARKED);

        assertEquals("request_shopping_consent",
                assistance.getPendingNode().getNodeId());
        assertEquals(0, assistance.getToolInvocationCount());

        SimulatedScenarioEffectComposition.Snapshot purchase =
                fixture.composition.supplyApprovalOutcome(
                        assistance.getRunId(),
                        AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);
        assertEquals("request_purchase_confirmation",
                purchase.getPendingNode().getNodeId());
        assertEquals(4, purchase.getToolInvocationCount());
        assertEquals("preview_purchase_route", purchase.getLastToolNodeId());
        assertEquals("ROUTE_PREVIEW_READY", purchase.getLastToolStatusCode());

        SimulatedScenarioEffectComposition.Snapshot navigation =
                fixture.composition.supplyApprovalOutcome(
                        assistance.getRunId(),
                        AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);
        assertEquals("request_navigation_confirmation",
                navigation.getPendingNode().getNodeId());
        assertEquals(5, navigation.getToolInvocationCount());
        assertEquals("commit_order", navigation.getLastToolNodeId());
        assertEquals("ORDER_NOT_DISPATCHED", navigation.getLastToolStatusCode());

        SimulatedScenarioEffectComposition.Snapshot completed =
                fixture.composition.supplyApprovalOutcome(
                        assistance.getRunId(),
                        AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);
        assertEquals(SimulatedScenarioRuntime.SessionState.COMPLETED,
                completed.getSessionState());
        assertNull(completed.getPendingNode());
        assertEquals(6, completed.getToolInvocationCount());
        assertEquals(3, completed.getApprovalInputCount());
        assertEquals("start_purchase_navigation", completed.getLastToolNodeId());
        assertEquals("NAVIGATION_SIMULATED", completed.getLastToolStatusCode());
        assertEquals(0, completed.getEffectDispatchCount());
        assertEquals(0, completed.getReadbackAttemptCount());
        assertEquals(0, completed.getFailureCount());
        assertFalse(completed.isHardwareAccessed());
    }

    @Test
    public void requiredEffectFailureFailsGraphClosed() throws Exception {
        Fixture fixture = fixture();
        fixture.composition.setFaultForContractTest(
                "vehicle.hvac.power", FaultInjectionProfile.terminalFailure());

        SimulatedScenarioEffectComposition.Snapshot snapshot = fixture.composition.start(
                fixture.inputs.create(ScenarioKind.COLD, DrivingProfile.PARKED),
                ScenarioKind.COLD,
                DrivingProfile.PARKED);

        assertEquals(SimulatedScenarioRuntime.SessionState.FAILED,
                snapshot.getSessionState());
        assertEquals(1, snapshot.getEffectDispatchCount());
        assertEquals(0, snapshot.getReadbackAttemptCount());
        assertEquals(1, snapshot.getFailureCount());
    }

    @Test
    public void mismatchedReadbackFailsRequiredVerification() throws Exception {
        Fixture fixture = fixture();
        fixture.composition.setFaultForContractTest(
                "vehicle.hvac.power", FaultInjectionProfile.readbackMismatch());

        SimulatedScenarioEffectComposition.Snapshot snapshot = fixture.composition.start(
                fixture.inputs.create(ScenarioKind.COLD, DrivingProfile.PARKED),
                ScenarioKind.COLD,
                DrivingProfile.PARKED);

        assertEquals(SimulatedScenarioRuntime.SessionState.FAILED,
                snapshot.getSessionState());
        assertEquals(3, snapshot.getEffectDispatchCount());
        assertEquals(1, snapshot.getReadbackAttemptCount());
        assertEquals(0, snapshot.getReadbackMatchCount());
        assertEquals(1, snapshot.getFailureCount());
    }

    @Test
    public void binderProjectionCarriesCountsAndNoProductionAuthority() throws Exception {
        Fixture fixture = fixture();
        SimulatedScenarioEffectComposition.Snapshot source = fixture.composition.start(
                fixture.inputs.create(ScenarioKind.COLD, DrivingProfile.PARKED),
                ScenarioKind.COLD,
                DrivingProfile.PARKED);

        SimulatedScenarioBinderSnapshot snapshot =
                SimulatedScenarioBinderSnapshot.from(source);

        assertEquals(2, snapshot.schemaVersion);
        assertEquals(3, snapshot.simulatedEffectDispatchCount);
        assertEquals(3, snapshot.simulatedReadbackAttemptCount);
        assertEquals(3, snapshot.simulatedReadbackMatchCount);
        assertEquals(0, snapshot.simulatedApprovalInputCount);
        assertEquals(0, snapshot.simulatedFailureCount);
        assertTrue(snapshot.effectDispatchEnabled);
        assertTrue(snapshot.readbackAccessed);
        assertFalse(snapshot.approvalAuthorityAvailable);
        assertFalse(snapshot.hardwareAccessed);
        assertFalse(snapshot.productionReady);
        assertFalse(snapshot.targetHardwareValidated);
    }

    @Test
    public void onlyApprovalPendingAcceptsExternalOutcome() throws Exception {
        Fixture fixture = fixture();
        SimulatedScenarioEffectComposition.Snapshot completed = fixture.composition.start(
                fixture.inputs.create(ScenarioKind.COLD, DrivingProfile.PARKED),
                ScenarioKind.COLD,
                DrivingProfile.PARKED);

        assertThrows(IllegalArgumentException.class, () ->
                fixture.composition.supplyApprovalOutcome(
                        completed.getRunId(),
                        AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED));
        assertThrows(IllegalArgumentException.class, () ->
                fixture.composition.get("00000000-0000-4000-8000-000000000999"));
    }

    private static Fixture fixture() throws Exception {
        MutableClock graphClock = new MutableClock();
        AtomicInteger uuid = new AtomicInteger(1);
        SimulatedScenarioInputFactory inputs = new SimulatedScenarioInputFactory(
                catalog(),
                graphClock::epochTimeMs,
                graphClock::elapsedRealtimeMs,
                () -> String.format(
                        "00000000-0000-4000-8000-%012d", uuid.getAndIncrement()));
        return new Fixture(
                inputs,
                new SimulatedScenarioEffectComposition(
                        graphClock, new SimulationClock(ELAPSED_MS)));
    }

    private static ScenarioCatalog catalog() throws Exception {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (String name : new String[] {
                "scene.cabin.multimodal.assist.v1.json",
                "scene.comfort.cold.v1.json",
                "scene.fatigue.assist.v1.json",
                "scene.rest.nap.v1.json"
        }) {
            assets.put(name, Files.readAllBytes(ASSETS.resolve(name)));
        }
        return ScenarioCatalog.load(assets);
    }

    private static final class Fixture {
        private final SimulatedScenarioInputFactory inputs;
        private final SimulatedScenarioEffectComposition composition;

        private Fixture(
                SimulatedScenarioInputFactory inputs,
                SimulatedScenarioEffectComposition composition) {
            this.inputs = inputs;
            this.composition = composition;
        }
    }

    private static final class MutableClock implements AgentGraphRuntime.Clock {
        @Override
        public long epochTimeMs() {
            return EPOCH_MS;
        }

        @Override
        public long elapsedRealtimeMs() {
            return ELAPSED_MS;
        }
    }
}
