package com.centralbrain.runtime.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.context.ContextFieldPolicy;
import com.centralbrain.runtime.context.ContextSnapshot;
import com.centralbrain.runtime.context.ContextSnapshot.SeatZone;
import com.centralbrain.runtime.context.ContextSnapshotBuilder;
import com.centralbrain.runtime.events.BoundedEventRuntime;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class SimulatedScenarioRuntimeTest {
    private static final Path ASSETS = Paths.get(
            "src/main/assets/scenarios").toAbsolutePath().normalize();
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;
    private static final long NOW_MS = 1_760_000_000_000L;
    private static final String PLAN_ID = "d0f89314-3b27-44f1-88b4-0f1059ff27f8";
    private static final String SESSION_ID = "79ba558a-26ed-4c69-8856-8e89f8471ce7";
    private static final String OWNER =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void coldStartPublishesPlanAndEffectPendingSessionProjection() throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioRuntime runtime = runtime();

        SimulatedScenarioRuntime.Snapshot snapshot = runtime.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        assertEquals(SimulatedScenarioRuntime.SessionState.WAITING_EFFECT,
                snapshot.getSessionState());
        assertEquals(GraphRunState.WAITING, snapshot.getGraphState());
        assertEquals(2, snapshot.getAutomaticProjectionCount());
        assertEquals("set_hvac_power", snapshot.getPendingNode().getNodeId());
        assertEquals(2, snapshot.getProjectedEventCount());
        assertEquals(2, snapshot.getLastEventSequence());
        assertEquals(2, runtime.eventRuntimeSnapshot().getPublishedCount());
        assertTrue(snapshot.isPlanPublished());
    }

    @Test
    public void retainedPlanAndEffectEventsAreDeliveredThroughExistingEventRuntime()
            throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioRuntime runtime = runtime();
        runtime.start(request(), fixture.resolution, fixture.context, fixture.capabilities);
        CapturingObserver observer = new CapturingObserver();

        BoundedEventRuntime.SubscribeResult subscription = runtime.subscribe(
                BoundedEventRuntime.TrustedSubscription.fromRuntimePolicy(
                        "client-p4-d4b",
                        OWNER,
                        List.of(BoundedEventRuntime.TOPIC_TASK_STATE),
                        0,
                        8),
                observer);
        BoundedEventRuntime.DispatchResult delivered = runtime.dispatchOwned(
                subscription.getSubscription().getSubscriptionId(), OWNER, 8);

        assertEquals(BoundedEventRuntime.SubscribeOutcome.CREATED,
                subscription.getOutcome());
        assertEquals(BoundedEventRuntime.DispatchOutcome.DELIVERED,
                delivered.getOutcome());
        assertEquals(2, delivered.getDeliveredEventCount());
        assertEquals(List.of(
                SimulatedScenarioRuntime.SCHEMA_PLAN_PUBLISHED,
                SimulatedScenarioRuntime.SCHEMA_PENDING_EFFECT), observer.schemaIds);
        assertTrue(observer.payloadDigests.stream()
                .allMatch(value -> value.matches("[0-9a-f]{64}")));
    }

    @Test
    public void parkedFatigueApprovalUsesPolicyTopic() throws Exception {
        Fixture fixture = fixture("scene.fatigue.assist.v1", false);
        SimulatedScenarioRuntime runtime = runtime();
        SimulatedScenarioRuntime.Snapshot snapshot = runtime.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);
        CapturingObserver observer = new CapturingObserver();
        BoundedEventRuntime.SubscribeResult subscription = runtime.subscribe(
                BoundedEventRuntime.TrustedSubscription.fromRuntimePolicy(
                        "policy-client",
                        OWNER,
                        List.of(BoundedEventRuntime.TOPIC_POLICY_DECISION),
                        0,
                        4),
                observer);

        runtime.dispatchOwned(subscription.getSubscription().getSubscriptionId(), OWNER, 4);

        assertEquals(SimulatedScenarioRuntime.SessionState.WAITING_APPROVAL,
                snapshot.getSessionState());
        assertEquals(List.of(SimulatedScenarioRuntime.SCHEMA_PENDING_APPROVAL),
                observer.schemaIds);
        assertFalse(snapshot.isApprovalAuthorityAvailable());
    }

    @Test
    public void suppliedOutcomePublishesProgressAndMovesToReadback() throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioRuntime runtime = runtime();
        SimulatedScenarioRuntime.Snapshot progressed = runtime.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        int supplied = 0;
        while (progressed.getPendingNode().getStage()
                == SimulatedScenarioGraph.PendingStage.EFFECT) {
            progressed = runtime.supplyPendingOutcome(
                    progressed.getRunId(), AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);
            supplied++;
        }

        assertEquals(SimulatedScenarioRuntime.SessionState.WAITING_READBACK,
                progressed.getSessionState());
        assertEquals("verify_hvac_power", progressed.getPendingNode().getNodeId());
        assertEquals(3, supplied);
        assertEquals(3, progressed.getSuppliedOutcomeCount());
        assertEquals(8, progressed.getProjectedEventCount());
        assertEquals(8, runtime.eventRuntimeSnapshot().getPublishedCount());
        assertFalse(progressed.isReadbackAccessed());
    }

    @Test
    public void explicitOutcomesCompleteSessionWithoutDispatchOrHardware() throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioRuntime runtime = runtime();
        SimulatedScenarioRuntime.Snapshot snapshot = runtime.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        while (!snapshot.getGraphState().isTerminal()) {
            snapshot = runtime.supplyPendingOutcome(
                    snapshot.getRunId(), AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);
        }

        assertEquals(SimulatedScenarioRuntime.SessionState.COMPLETED,
                snapshot.getSessionState());
        assertEquals(GraphRunState.COMPLETED, snapshot.getGraphState());
        assertEquals(6, snapshot.getSuppliedOutcomeCount());
        assertEquals(14, snapshot.getProjectedEventCount());
        assertFalse(snapshot.isEffectDispatchEnabled());
        assertFalse(snapshot.isReadbackAccessed());
        assertFalse(snapshot.isHardwareAccessed());
    }

    @Test
    public void failedAndCancelledRunsProjectDistinctTerminalStates() throws Exception {
        Fixture firstFixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioRuntime failedRuntime = runtime();
        SimulatedScenarioRuntime.Snapshot failedPending = failedRuntime.start(
                request(), firstFixture.resolution, firstFixture.context,
                firstFixture.capabilities);
        SimulatedScenarioRuntime.Snapshot failed = failedRuntime.supplyPendingOutcome(
                failedPending.getRunId(), AgentGraphRuntime.NodeExecutionOutcome.FAILED);

        Fixture secondFixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioRuntime cancelledRuntime = runtime();
        SimulatedScenarioRuntime.Snapshot cancelledPending = cancelledRuntime.start(
                request(), secondFixture.resolution, secondFixture.context,
                secondFixture.capabilities);
        SimulatedScenarioRuntime.Snapshot cancelled = cancelledRuntime.cancel(
                cancelledPending.getRunId());

        assertEquals(SimulatedScenarioRuntime.SessionState.FAILED,
                failed.getSessionState());
        assertEquals(4, failed.getProjectedEventCount());
        assertEquals(SimulatedScenarioRuntime.SessionState.CANCELLED,
                cancelled.getSessionState());
        assertEquals(3, cancelled.getProjectedEventCount());
    }

    @Test
    public void projectionDigestIsDeterministicAndBindsEventProgress() throws Exception {
        Fixture firstFixture = fixture("scene.comfort.cold.v1", false);
        Fixture secondFixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioRuntime first = runtime();
        SimulatedScenarioRuntime second = runtime();

        SimulatedScenarioRuntime.Snapshot firstPending = first.start(
                request(), firstFixture.resolution, firstFixture.context,
                firstFixture.capabilities);
        SimulatedScenarioRuntime.Snapshot secondPending = second.start(
                request(), secondFixture.resolution, secondFixture.context,
                secondFixture.capabilities);
        SimulatedScenarioRuntime.Snapshot progressed = first.supplyPendingOutcome(
                firstPending.getRunId(), AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);

        assertEquals(firstPending.getProjectionDigest(), secondPending.getProjectionDigest());
        assertTrue(firstPending.getProjectionDigest().matches("[0-9a-f]{64}"));
        assertFalse(firstPending.getProjectionDigest().equals(progressed.getProjectionDigest()));
    }

    @Test
    public void debugRuntimeClaimsRemainProcessLocalAndUnpublished() throws Exception {
        Fixture fixture = fixture("scene.comfort.cold.v1", false);
        SimulatedScenarioRuntime runtime = runtime();
        SimulatedScenarioRuntime.Snapshot snapshot = runtime.start(
                request(), fixture.resolution, fixture.context, fixture.capabilities);

        assertTrue(snapshot.isDebugRuntimeWired());
        assertTrue(snapshot.isSessionProjectionEnabled());
        assertTrue(snapshot.isEventProjectionEnabled());
        assertTrue(snapshot.isProcessLocal());
        assertFalse(snapshot.isAndroidServicePublished());
        assertFalse(snapshot.isSessionEventBinderPublished());
        assertFalse(snapshot.isClient2Wired());
        assertFalse(snapshot.isEffectDispatchEnabled());
        assertFalse(snapshot.isReadbackAccessed());
        assertFalse(snapshot.isApprovalAuthorityAvailable());
        assertFalse(snapshot.isProductionRegistered());
        assertFalse(snapshot.isHardwareAccessed());
        assertFalse(snapshot.isProductionReady());
        assertFalse(snapshot.isTargetHardwareValidated());
        assertThrows(IllegalArgumentException.class, () -> runtime.get("unknown"));
    }

    private static SimulatedScenarioRuntime runtime() {
        AtomicInteger ids = new AtomicInteger();
        return SimulatedScenarioRuntime.createForContractTest(
                new AgentGraphRuntime.Clock() {
                    @Override
                    public long epochTimeMs() {
                        return NOW_MS;
                    }

                    @Override
                    public long elapsedRealtimeMs() {
                        return 2_000L;
                    }
                },
                () -> "p4-d4b-subscription-" + ids.incrementAndGet());
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
                "simulated-scenario-runtime-test",
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

    private static final class CapturingObserver implements BoundedEventRuntime.EventObserver {
        private final List<String> schemaIds = new ArrayList<>();
        private final List<String> payloadDigests = new ArrayList<>();

        @Override
        public void onOverflow(BoundedEventRuntime.OverflowSignal overflow) {
            throw new AssertionError("unexpected overflow");
        }

        @Override
        public void onEvent(BoundedEventRuntime.EventEnvelope event) {
            schemaIds.add(event.getSchemaId());
            payloadDigests.add(event.getPayloadDigest());
        }

        @Override
        public void onClosed(
                String subscriptionId, BoundedEventRuntime.CloseReason reason) {
            // No-op for projection delivery tests.
        }
    }
}
