package com.centralbrain.runtime.scenario;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScenarioPlanCompilerProbeActivity extends Activity {
    private static final String TAG = "CbScenarioCompiler";
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;
    private static final long COMPILED_AT_MS = 1_760_000_000_000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private void runProbe(String nonce) {
        try {
            ScenarioCatalog catalog = catalog();
            ScenarioResolver resolver = new DeterministicScenarioResolver();
            ScenarioPlanCompiler compiler = new ScenarioPlanCompiler();
            ContextSnapshot comfort = context(ContextFieldPolicy.seatComfort(), false);
            ContextSnapshot moving = context(ContextFieldPolicy.seatRecline(), true);
            CapabilitySnapshot available = capabilities(Set.of());
            CapabilitySnapshot noSeatHeat = capabilities(
                    Set.of(CapabilityId.SEAT_HEATING_LEVEL));

            ScenarioResolution coldResolution = resolve(
                    resolver, "scene.comfort.cold.v1", catalog, comfort, available);
            CompiledPlan cold = compiler.compile(
                    request(), coldResolution, comfort, available);
            CompiledPlan coldReplay = compiler.compile(
                    request(), coldResolution, comfort, available);
            ScenarioResolution degradedResolution = resolve(
                    resolver, "scene.comfort.cold.v1", catalog, comfort, noSeatHeat);
            CompiledPlan degraded = compiler.compile(
                    request(), degradedResolution, comfort, noSeatHeat);
            ScenarioResolution movingResolution = resolve(
                    resolver, "scene.fatigue.assist.v1", catalog, moving, available);
            CompiledPlan movingFatigue = compiler.compile(
                    request(), movingResolution, moving, available);

            ScenarioPlan coldPlan = cold.toScenarioPlan();
            boolean goldenVerified = coldPlan.nodes.length == 9
                    && coldPlan.dependencies.length == 10
                    && nodeIds(coldPlan).equals(List.of(
                            "capture_context",
                            "evaluate_policy",
                            "set_hvac_power",
                            "set_hvac_temperature",
                            "set_seat_heating",
                            "verify_hvac_power",
                            "verify_hvac_temperature",
                            "verify_seat_heating",
                            "render_summary"));
            boolean degradedVerified = degraded.getExcludedOptionalNodeIds().equals(
                    List.of("set_seat_heating", "verify_seat_heating"))
                    && !nodeIds(degraded.toScenarioPlan()).contains("set_seat_heating");
            ScenarioPlan movingPlan = movingFatigue.toScenarioPlan();
            boolean movingSeatAbsent = movingFatigue.getExcludedOptionalNodeIds().equals(
                    List.of(
                            "request_seat_approval",
                            "set_seat_recline",
                            "verify_seat_recline"))
                    && !capabilityIds(movingPlan).contains("vehicle.seat.recline");
            boolean digestVerified = cold.getPlanDigest().matches("[0-9a-f]{64}")
                    && cold.getPlanDigest().equals(coldReplay.getPlanDigest())
                    && !cold.isExecutable()
                    && !cold.isProductionTrusted();
            coldPlan.nodes[0].nodeId = "mutated";
            boolean immutableVerified = cold.toScenarioPlan().nodes[0].nodeId.equals(
                    "capture_context");

            ScenarioPlan cycle = cold.toScenarioPlan();
            List<NodeDependency> edges = new ArrayList<>(Arrays.asList(cycle.dependencies));
            NodeDependency backEdge = new NodeDependency();
            backEdge.prerequisiteNodeId = "render_summary";
            backEdge.dependentNodeId = "capture_context";
            backEdge.condition = PlanContract.DEPENDENCY_ON_SUCCESS;
            edges.add(backEdge);
            cycle.dependencies = edges.toArray(new NodeDependency[0]);
            boolean cycleRejected = false;
            try {
                PlanGraphValidator.validateTransport(cycle);
            } catch (IllegalArgumentException expected) {
                cycleRejected = expected.getMessage().startsWith("CB_PLAN_GRAPH:");
            }

            boolean allVerified = goldenVerified
                    && degradedVerified
                    && movingSeatAbsent
                    && digestVerified
                    && immutableVerified
                    && cycleRejected;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            Log.i(TAG, "nonce=" + nonce
                    + " scenario_plan_compiler_probe_complete=true"
                    + " scenario_plan_compiler_defined=" + allVerified
                    + " scenario_plan_golden_verified=" + goldenVerified
                    + " scenario_plan_degraded_fallback_verified=" + degradedVerified
                    + " scenario_plan_moving_seat_absent=" + movingSeatAbsent
                    + " scenario_plan_cycle_rejected=" + cycleRejected
                    + " scenario_plan_digest_verified=" + digestVerified
                    + " scenario_plan_immutable_verified=" + immutableVerified
                    + " scenario_plan_compiler_android13_arm64_verified="
                    + android13Arm64Verified
                    + " scenario_plan_compiler_runtime_wired=false"
                    + " scenario_plan_runtime_published=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_signal_provider_wired=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException | IOException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " scenario_plan_compiler_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " scenario_plan_compiler_runtime_wired=false"
                    + " scenario_plan_runtime_published=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private ScenarioCatalog catalog() throws IOException {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (String name : List.of(
                "scene.comfort.cold.v1.json",
                "scene.fatigue.assist.v1.json",
                "scene.rest.nap.v1.json")) {
            assets.put(name, readAsset("scenarios/" + name));
        }
        return ScenarioCatalog.load(assets);
    }

    private byte[] readAsset(String path) throws IOException {
        try (InputStream input = getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4_096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static ScenarioResolution resolve(
            ScenarioResolver resolver,
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
                "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0",
                "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26",
                1,
                COMPILED_AT_MS,
                COMPILED_AT_MS + 120_000);
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

    private static CapabilitySnapshot capabilities(Set<CapabilityId> unavailable) {
        return CapabilitySnapshot.capture(
                CapabilityCatalog.stage2Defaults(),
                CapabilityProfile.SOFTWARE_SIMULATION,
                1,
                unavailable);
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
                "scenario-compiler-probe",
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
