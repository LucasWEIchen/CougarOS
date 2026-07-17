package com.centralbrain.runtime.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import com.centralbrain.runtime.scenario.ScenarioManifest.Source;
import com.centralbrain.runtime.scenario.ScenarioManifest.Zone;
import com.centralbrain.runtime.scenario.ScenarioPlanCompiler.CompileRequest;
import com.centralbrain.runtime.scenario.ScenarioPlanCompiler.CompiledPlan;
import com.centralbrain.runtime.scenario.ScenarioResolver.CapabilityProfile;
import com.centralbrain.runtime.scenario.ScenarioResolver.CapabilitySnapshot;
import com.centralbrain.runtime.scenario.ScenarioResolver.Request;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability.CapabilityId;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.VehicleDigitalTwinStore;
import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class ScenarioPlanCompilerTest {
    private static final Path ASSETS = Paths.get(
            "src/main/assets/scenarios").toAbsolutePath().normalize();
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;
    private static final long COMPILED_AT_MS = 1_760_000_000_000L;
    private static final String PLAN_ID = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";

    private final ScenarioResolver resolver = new DeterministicScenarioResolver();
    private final ScenarioPlanCompiler compiler = new ScenarioPlanCompiler();

    @Test
    public void coldGoldenPlanIsStableTypedAndNonExecutable() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot context = context(ContextFieldPolicy.seatComfort(), false);
        CapabilitySnapshot capabilities = capabilities(Set.of(), 1);
        ScenarioResolution resolution = resolve(
                "scene.comfort.cold.v1", catalog, context, capabilities);

        CompiledPlan first = compiler.compile(
                request(), resolution, context, capabilities);
        CompiledPlan second = compiler.compile(
                request(), resolution, context, capabilities);
        ScenarioPlan plan = first.toScenarioPlan();

        assertEquals(List.of(
                        "capture_context",
                        "evaluate_policy",
                        "set_hvac_power",
                        "set_hvac_temperature",
                        "set_seat_heating",
                        "verify_hvac_power",
                        "verify_hvac_temperature",
                        "verify_seat_heating",
                        "render_summary"),
                nodeIds(plan));
        assertEquals(10, plan.dependencies.length);
        assertEquals(first.getPlanDigest(), second.getPlanDigest());
        assertTrue(plan.planDigest.matches("[0-9a-f]{64}"));
        assertTrue(plan.nodes[2].policy.verificationRequired);
        assertTrue(plan.nodes[2].resourceKey.startsWith("vehicle:hvac:power:"));
        assertFalse(first.isExecutable());
        assertFalse(first.isProductionTrusted());
        PlanGraphValidator.validate(first, context);
    }

    @Test
    public void optionalCapabilityFallbackIsPrunedAndResultIsImmutable() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot context = context(ContextFieldPolicy.seatComfort(), false);
        CapabilitySnapshot capabilities = capabilities(
                Set.of(CapabilityId.SEAT_HEATING_LEVEL), 1);
        ScenarioResolution resolution = resolve(
                "scene.comfort.cold.v1", catalog, context, capabilities);

        CompiledPlan compiled = compiler.compile(
                request(), resolution, context, capabilities);
        ScenarioPlan firstView = compiled.toScenarioPlan();

        assertEquals(List.of("set_seat_heating", "verify_seat_heating"),
                compiled.getExcludedOptionalNodeIds());
        assertFalse(nodeIds(firstView).contains("set_seat_heating"));
        assertFalse(nodeIds(firstView).contains("verify_seat_heating"));
        assertEquals(7, firstView.nodes.length);
        assertThrows(UnsupportedOperationException.class,
                () -> compiled.getExcludedOptionalNodeIds().clear());
        firstView.nodes[0].nodeId = "mutated";
        firstView.planDigest = "0".repeat(64);
        ScenarioPlan secondView = compiled.toScenarioPlan();
        assertEquals("capture_context", secondView.nodes[0].nodeId);
        assertEquals(compiled.getPlanDigest(), secondView.planDigest);
    }

    @Test
    public void movingFatigueOmitsUnsafeSeatApprovalAndReclineBranch() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot context = context(ContextFieldPolicy.seatRecline(), true);
        CapabilitySnapshot capabilities = capabilities(Set.of(), 1);
        ScenarioResolution resolution = resolve(
                "scene.fatigue.assist.v1", catalog, context, capabilities);

        CompiledPlan compiled = compiler.compile(
                request(), resolution, context, capabilities);
        ScenarioPlan plan = compiled.toScenarioPlan();

        assertEquals(List.of(
                        "request_seat_approval",
                        "set_seat_recline",
                        "verify_seat_recline"),
                compiled.getExcludedOptionalNodeIds());
        assertFalse(nodeIds(plan).contains("request_seat_approval"));
        assertFalse(nodeIds(plan).contains("set_seat_recline"));
        assertFalse(capabilityIds(plan).contains("vehicle.seat.recline"));
        assertTrue(nodeIds(plan).contains("set_hvac_power"));
        assertTrue(nodeIds(plan).contains("set_hvac_fan"));
        PlanGraphValidator.validate(compiled, context);
    }

    @Test
    public void rejectedResolutionAndSnapshotDigestDriftFailClosed() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot comfort = context(ContextFieldPolicy.seatComfort(), false);
        ContextSnapshot movingComfort = context(ContextFieldPolicy.seatComfort(), true);
        CapabilitySnapshot capabilities = capabilities(Set.of(), 1);
        ScenarioResolution accepted = resolve(
                "scene.comfort.cold.v1", catalog, comfort, capabilities);
        ScenarioResolution rejected = resolver.resolve(
                new Request("", "打开天窗", Source.VOICE, Zone.ROW1_DRIVER),
                catalog,
                comfort,
                capabilities);

        assertCompileViolation(() -> compiler.compile(
                request(), rejected, comfort, capabilities));
        assertCompileViolation(() -> compiler.compile(
                request(), accepted, movingComfort, capabilities));
        assertCompileViolation(() -> compiler.compile(
                request(), accepted, comfort, capabilities(Set.of(), 2)));
    }

    @Test
    public void graphValidatorRejectsCycleAndMissingRequiredVerification() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot context = context(ContextFieldPolicy.seatComfort(), false);
        CapabilitySnapshot capabilities = capabilities(Set.of(), 1);
        CompiledPlan compiled = compiler.compile(
                request(),
                resolve("scene.comfort.cold.v1", catalog, context, capabilities),
                context,
                capabilities);

        ScenarioPlan cycle = compiled.toScenarioPlan();
        List<NodeDependency> dependencies = new ArrayList<>(
                Arrays.asList(cycle.dependencies));
        dependencies.add(dependency("render_summary", "capture_context"));
        cycle.dependencies = dependencies.toArray(new NodeDependency[0]);
        assertGraphViolation(() -> PlanGraphValidator.validateTransport(cycle));

        ScenarioPlan missingVerify = compiled.toScenarioPlan();
        missingVerify.nodes = Arrays.stream(missingVerify.nodes)
                .filter(node -> !"verify_hvac_power".equals(node.nodeId))
                .toArray(PlanNode[]::new);
        missingVerify.dependencies = Arrays.stream(missingVerify.dependencies)
                .filter(edge -> !"verify_hvac_power".equals(edge.prerequisiteNodeId)
                        && !"verify_hvac_power".equals(edge.dependentNodeId))
                .toArray(NodeDependency[]::new);
        assertGraphViolation(() -> PlanGraphValidator.validateTransport(missingVerify));
    }

    @Test
    public void highRiskEffectRequiresApprovalPredecessor() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot context = context(ContextFieldPolicy.seatRecline(), false);
        CapabilitySnapshot capabilities = capabilities(Set.of(), 1);
        CompiledPlan compiled = compiler.compile(
                request(),
                resolve("scene.fatigue.assist.v1", catalog, context, capabilities),
                context,
                capabilities);
        ScenarioPlan noApproval = compiled.toScenarioPlan();
        noApproval.nodes = Arrays.stream(noApproval.nodes)
                .filter(node -> !"request_seat_approval".equals(node.nodeId))
                .toArray(PlanNode[]::new);
        noApproval.dependencies = Arrays.stream(noApproval.dependencies)
                .filter(edge -> !"request_seat_approval".equals(edge.prerequisiteNodeId)
                        && !"request_seat_approval".equals(edge.dependentNodeId))
                .toArray(NodeDependency[]::new);

        assertGraphViolation(() -> PlanGraphValidator.validateTransport(noApproval));
    }

    @Test
    public void malformedCompileMetadataFailsClosed() {
        assertCompileViolation(() -> new CompileRequest(
                "invalid", SESSION_ID, 1, COMPILED_AT_MS, COMPILED_AT_MS + 60_000));
        assertCompileViolation(() -> new CompileRequest(
                PLAN_ID, SESSION_ID, 0, COMPILED_AT_MS, COMPILED_AT_MS + 60_000));
        assertCompileViolation(() -> new CompileRequest(
                PLAN_ID,
                SESSION_ID,
                1,
                COMPILED_AT_MS,
                COMPILED_AT_MS + PlanContract.MAX_PLAN_DEADLINE_MS + 1));
    }

    private ScenarioResolution resolve(
            String scenarioId,
            ScenarioCatalog catalog,
            ContextSnapshot context,
            CapabilitySnapshot capabilities) {
        return resolver.resolve(
                new Request(scenarioId, "", Source.HMI_BUTTON, Zone.ROW1_DRIVER),
                catalog,
                context,
                capabilities);
    }

    private static CompileRequest request() {
        return new CompileRequest(
                PLAN_ID,
                SESSION_ID,
                1,
                COMPILED_AT_MS,
                COMPILED_AT_MS + 120_000);
    }

    private static NodeDependency dependency(String prerequisite, String dependent) {
        NodeDependency dependency = new NodeDependency();
        dependency.prerequisiteNodeId = prerequisite;
        dependency.dependentNodeId = dependent;
        dependency.condition = PlanContract.DEPENDENCY_ON_SUCCESS;
        return dependency;
    }

    private static List<String> nodeIds(ScenarioPlan plan) {
        List<String> ids = new ArrayList<>();
        for (PlanNode node : plan.nodes) {
            ids.add(node.nodeId);
        }
        return ids;
    }

    private static Set<String> capabilityIds(ScenarioPlan plan) {
        Set<String> ids = new LinkedHashSet<>();
        for (PlanNode node : plan.nodes) {
            if (!node.capabilityId.isEmpty()) {
                ids.add(node.capabilityId);
            }
        }
        return ids;
    }

    private static void assertCompileViolation(Runnable operation) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, operation::run);
        assertTrue(exception.getMessage().startsWith("CB_SCENARIO_COMPILE:"));
    }

    private static void assertGraphViolation(Runnable operation) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, operation::run);
        assertTrue(exception.getMessage().startsWith("CB_PLAN_GRAPH:"));
    }

    private static CapabilitySnapshot capabilities(
            Set<CapabilityId> unavailable, long revision) {
        return CapabilitySnapshot.capture(
                CapabilityCatalog.stage2Defaults(),
                CapabilityProfile.SOFTWARE_SIMULATION,
                revision,
                unavailable);
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
        long now = moving ? 2_000 : 1_000;
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
                "scenario-compiler-test",
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
}
