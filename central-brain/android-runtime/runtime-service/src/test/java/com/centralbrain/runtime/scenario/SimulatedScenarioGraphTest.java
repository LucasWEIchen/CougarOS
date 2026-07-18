package com.centralbrain.runtime.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.context.ContextFieldPolicy;
import com.centralbrain.runtime.context.ContextSnapshot;
import com.centralbrain.runtime.context.ContextSnapshot.SeatZone;
import com.centralbrain.runtime.context.ContextSnapshotBuilder;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.MotionState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SafetyState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SourceAssurance;
import com.centralbrain.runtime.graph.AgentGraphRuntime;
import com.centralbrain.runtime.graph.GraphRunState;
import com.centralbrain.runtime.scenario.ScenarioManifest.Source;
import com.centralbrain.runtime.scenario.ScenarioManifest.Zone;
import com.centralbrain.runtime.scenario.ScenarioPlanCompiler.CompileRequest;
import com.centralbrain.runtime.scenario.ScenarioResolver.CapabilityProfile;
import com.centralbrain.runtime.scenario.ScenarioResolver.CapabilitySnapshot;
import com.centralbrain.runtime.scenario.ScenarioResolver.Request;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.VehicleDigitalTwinStore;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SimulatedScenarioGraphTest {
    private static final Path ASSETS = Paths.get(
            "src/main/assets/scenarios").toAbsolutePath().normalize();
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;
    private static final long NOW_MS = 1_760_000_000_000L;
    private static final String PLAN_ID = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";

    @Test
    public void coldPlanAutomaticallyProjectsContextAndPolicyThenWaitsForEffect()
            throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioGraph graph = graph();

        SimulatedScenarioGraph.Snapshot snapshot = graph.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        assertEquals(GraphRunState.WAITING, snapshot.getGraphState());
        assertEquals(2, snapshot.getAutomaticProjectionCount());
        assertEquals(0, snapshot.getSuppliedOutcomeCount());
        assertEquals("set_hvac_power", snapshot.getPendingNode().getNodeId());
        assertEquals(
                SimulatedScenarioGraph.PendingStage.EFFECT,
                snapshot.getPendingNode().getStage());
        assertEquals("vehicle.hvac.power", snapshot.getPendingNode().getCapabilityId());
        assertTrue(snapshot.getPendingNode().isRequired());
        assertTrue(snapshot.isPlanPublished());
        assertTrue(snapshot.isGraphProgressEnabled());
        assertFalse(snapshot.isEffectDispatchEnabled());
        assertFalse(snapshot.isReadbackAccessed());
    }

    @Test
    public void explicitSuppliedOutcomesCompleteColdGraphWithoutDispatchingHardware()
            throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioGraph graph = graph();
        SimulatedScenarioGraph.Snapshot snapshot = graph.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        int supplied = 0;
        while (!snapshot.getGraphState().isTerminal()) {
            assertNotNull(snapshot.getPendingNode());
            snapshot = graph.supplyPendingOutcome(
                    snapshot.getRunId(),
                    AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);
            supplied++;
        }

        assertEquals(GraphRunState.COMPLETED, snapshot.getGraphState());
        assertEquals(6, supplied);
        assertEquals(6, snapshot.getSuppliedOutcomeCount());
        assertEquals(3, snapshot.getAutomaticProjectionCount());
        assertNull(snapshot.getPendingNode());
        assertFalse(snapshot.isEffectDispatchEnabled());
        assertFalse(snapshot.isHardwareAccessed());
        assertFalse(snapshot.isProductionReady());
    }

    @Test
    public void parkedFatigueStopsAtApprovalBeforeSeatOrOtherEffects() throws Exception {
        Fixture fixture = fixture("scene.fatigue.assist.v1", false);
        SimulatedScenarioGraph.Snapshot snapshot = graph().start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        assertEquals("request_seat_approval", snapshot.getPendingNode().getNodeId());
        assertEquals(
                SimulatedScenarioGraph.PendingStage.APPROVAL,
                snapshot.getPendingNode().getStage());
        assertFalse(snapshot.isApprovalResponseAuthorityAvailable());
        assertEquals(2, snapshot.getAutomaticProjectionCount());
    }

    @Test
    public void movingFatiguePlanExcludesApprovalAndReclineBranch() throws Exception {
        Fixture fixture = fixture("scene.fatigue.assist.v1", true);
        SimulatedScenarioGraph.Snapshot snapshot = graph().start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        assertEquals("set_hvac_power", snapshot.getPendingNode().getNodeId());
        assertEquals(
                SimulatedScenarioGraph.PendingStage.EFFECT,
                snapshot.getPendingNode().getStage());
        assertFalse("vehicle.seat.recline".equals(
                snapshot.getPendingNode().getCapabilityId()));
    }

    @Test
    public void requiredEffectFailureFailsGraphClosed() throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioGraph graph = graph();
        SimulatedScenarioGraph.Snapshot pending = graph.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        SimulatedScenarioGraph.Snapshot failed = graph.supplyPendingOutcome(
                pending.getRunId(), AgentGraphRuntime.NodeExecutionOutcome.FAILED);

        assertEquals(GraphRunState.FAILED, failed.getGraphState());
        assertNull(failed.getPendingNode());
        assertEquals(1, failed.getSuppliedOutcomeCount());
        assertFalse(failed.isEffectDispatchEnabled());
    }

    @Test
    public void missingPendingNodeAndUnknownRunAreRejected() throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioGraph graph = graph();
        SimulatedScenarioGraph.Snapshot pending = graph.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);
        SimulatedScenarioGraph.Snapshot cancelled = graph.cancel(pending.getRunId());

        assertEquals(GraphRunState.CANCELLED, cancelled.getGraphState());
        assertViolation(() -> graph.supplyPendingOutcome(
                pending.getRunId(), AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED));
        assertViolation(() -> graph.get("unknown"));
    }

    @Test
    public void projectionDigestIsDeterministicAndBindsProgress() throws Exception {
        Fixture firstFixture = fixture("scene.comfort.cold.v1", false);
        Fixture secondFixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioGraph first = graph();
        SimulatedScenarioGraph second = graph();
        SimulatedScenarioGraph.Snapshot firstPending = first.start(
                request(), firstFixture.resolution, firstFixture.context,
                firstFixture.capabilities);
        SimulatedScenarioGraph.Snapshot secondPending = second.start(
                request(), secondFixture.resolution, secondFixture.context,
                secondFixture.capabilities);

        assertEquals(firstPending.getProjectionDigest(), secondPending.getProjectionDigest());
        assertTrue(firstPending.getProjectionDigest().matches("[0-9a-f]{64}"));
        SimulatedScenarioGraph.Snapshot progressed = first.supplyPendingOutcome(
                firstPending.getRunId(), AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);
        assertFalse(firstPending.getProjectionDigest().equals(progressed.getProjectionDigest()));
    }

    @Test
    public void debugCompositionClaimsRemainSimulationOnlyAndUnwired() throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioGraph graph = graph();
        SimulatedScenarioGraph.Snapshot snapshot = graph.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        assertTrue(snapshot.isSimulationOnly());
        assertFalse(graph.isProductionRegistered());
        assertFalse(snapshot.isAndroidRuntimeWired());
        assertFalse(snapshot.isApprovalResponseAuthorityAvailable());
        assertFalse(snapshot.isEffectDispatchEnabled());
        assertFalse(snapshot.isReadbackAccessed());
        assertFalse(snapshot.isHardwareAccessed());
        assertFalse(snapshot.isProductionReady());
        assertFalse(snapshot.isTargetHardwareValidated());
    }

    private static SimulatedScenarioGraph graph() {
        return new SimulatedScenarioGraph(new AgentGraphRuntime.Clock() {
            @Override
            public long epochTimeMs() {
                return NOW_MS;
            }

            @Override
            public long elapsedRealtimeMs() {
                return 1_000L;
            }
        });
    }

    private static Fixture fixture(String scenarioId, boolean moving) throws Exception {
        ContextFieldPolicy policy = "scene.comfort.cold.v1".equals(scenarioId)
                ? ContextFieldPolicy.seatComfort() : ContextFieldPolicy.seatRecline();
        ContextSnapshot context = context(policy, moving);
        CapabilitySnapshot capabilities = CapabilitySnapshot.capture(
                CapabilityCatalog.stage2Defaults(),
                CapabilityProfile.SOFTWARE_SIMULATION,
                1,
                Set.of());
        ScenarioResolution resolution = new DeterministicScenarioResolver().resolve(
                new Request(scenarioId, "", Source.HMI_BUTTON, Zone.ROW1_DRIVER),
                catalog(),
                context,
                capabilities);
        return new Fixture(context, capabilities, resolution);
    }

    private static CompileRequest request() {
        return new CompileRequest(
                PLAN_ID, SESSION_ID, 1, NOW_MS, NOW_MS + 120_000L);
    }

    private static ScenarioCatalog catalog() throws Exception {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (String name : List.of(
                "scene.comfort.cold.v1.json",
                "scene.fatigue.assist.v1.json",
                "scene.rest.nap.v1.json")) {
            assets.put(name, Files.readAllBytes(ASSETS.resolve(name)));
        }
        return ScenarioCatalog.load(assets);
    }

    private static ContextSnapshot context(ContextFieldPolicy policy, boolean moving) {
        long now = moving ? 2_000L : 1_000L;
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        store.updateReported(decimal(
                VehicleSignalPath.VEHICLE_SPEED,
                moving ? 35 : 0,
                "km/h",
                "global",
                now), now);
        store.updateReported(text(
                VehicleSignalPath.CURRENT_GEAR,
                moving ? "D" : "P",
                "global",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED, !moving, "global", now), now);
        store.updateReported(bool(
                VehicleSignalPath.SEAT_OCCUPIED, true, "row1.driver", now), now);
        store.updateReported(bool(
                VehicleSignalPath.SEAT_BELTED, true, "row1.driver", now), now);
        store.updateReported(decimal(
                VehicleSignalPath.SEAT_RECLINE_ANGLE,
                15,
                "degree",
                "row1.driver",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.HVAC_ACTIVE, true, "cabin", now), now);
        store.updateReported(decimal(
                VehicleSignalPath.CABIN_TEMPERATURE,
                22,
                "celsius",
                "cabin",
                now), now);

        Set<VehicleSignalPath> paths = new LinkedHashSet<>();
        for (ContextFieldPolicy.FieldRequirement requirement : policy.getRequirements()) {
            paths.add(requirement.getPath());
        }
        DigitalTwinSnapshot twin = store.snapshot(paths, now);
        SafetyVehicleStateSnapshot runtime = new SafetyVehicleStateSnapshot(
                "simulated-scenario-graph-test",
                1,
                now,
                SafetyState.NORMAL,
                moving ? MotionState.MOVING : MotionState.PARKED,
                true,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);
        return new ContextSnapshotBuilder().build(
                twin, runtime, policy, SeatZone.ROW1_DRIVER, false);
    }

    private static SignalValue decimal(
            VehicleSignalPath path,
            double value,
            String unit,
            String area,
            long now) {
        return SignalValue.ofDecimal(
                path,
                value,
                unit,
                area,
                timestamp(now),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalValue text(
            VehicleSignalPath path, String value, String area, long now) {
        return SignalValue.ofText(
                path,
                value,
                "",
                area,
                timestamp(now),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalValue bool(
            VehicleSignalPath path, boolean value, String area, long now) {
        return SignalValue.ofBoolean(
                path,
                value,
                "",
                area,
                timestamp(now),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalTimestamp timestamp(long now) {
        return new SignalTimestamp(SOURCE_EPOCH_MS + now, now);
    }

    private static void assertViolation(Runnable operation) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, operation::run);
        assertTrue(exception.getMessage().startsWith("CB_SIM_SCENARIO_GRAPH:"));
    }

    private static final class Fixture {
        private final ContextSnapshot context;
        private final CapabilitySnapshot capabilities;
        private final ScenarioResolution resolution;

        private Fixture(
                ContextSnapshot context,
                CapabilitySnapshot capabilities,
                ScenarioResolution resolution) {
            this.context = context;
            this.capabilities = capabilities;
            this.resolution = resolution;
        }
    }
}
