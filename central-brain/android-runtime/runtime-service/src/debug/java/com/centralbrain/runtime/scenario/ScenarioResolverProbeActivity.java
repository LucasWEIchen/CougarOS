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
import com.centralbrain.runtime.scenario.ScenarioResolution.Decision;
import com.centralbrain.runtime.scenario.ScenarioResolution.ReasonCode;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ScenarioResolverProbeActivity extends Activity {
    private static final String TAG = "CbScenarioResolver";
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;

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
            CapabilitySnapshot available = capabilities(Set.of());
            ContextSnapshot comfortParked = context(
                    ContextFieldPolicy.seatComfort(), false, false);
            ContextSnapshot reclineParked = context(
                    ContextFieldPolicy.seatRecline(), false, false);
            ContextSnapshot reclineMoving = context(
                    ContextFieldPolicy.seatRecline(), true, false);

            Request explicitRequest = new Request(
                    "scene.comfort.cold.v1",
                    "ignored",
                    Source.HMI_BUTTON,
                    Zone.ROW1_DRIVER);
            ScenarioResolution explicit = resolver.resolve(
                    explicitRequest, catalog, comfortParked, available);
            ScenarioResolution explicitReplay = resolver.resolve(
                    explicitRequest, catalog, comfortParked, available);
            ScenarioResolution cold = resolver.resolve(
                    text("我有点冷。"), catalog, comfortParked, available);
            ScenarioResolution fatigue = resolver.resolve(
                    text("我有些疲惫"), catalog, reclineParked, available);
            ScenarioResolution rest = resolver.resolve(
                    text("休息模式"), catalog, reclineParked, available);
            ScenarioResolution unknown = resolver.resolve(
                    text("打开天窗"), catalog, comfortParked, available);
            ScenarioResolution ambiguous = resolver.resolve(
                    text("我冷了 / 我累了"), catalog, comfortParked, available);

            ScenarioResolution requiredMissing = resolver.resolve(
                    explicitRequest,
                    catalog,
                    comfortParked,
                    capabilities(Set.of(CapabilityId.HVAC_POWER)));
            ScenarioResolution optionalMissing = resolver.resolve(
                    explicitRequest,
                    catalog,
                    comfortParked,
                    capabilities(Set.of(CapabilityId.SEAT_HEATING_LEVEL)));
            ScenarioResolution movingFatigue = resolver.resolve(
                    text("我累了"), catalog, reclineMoving, available);
            ScenarioResolution movingRest = resolver.resolve(
                    text("我想休息"), catalog, reclineMoving, available);
            ScenarioResolution production = resolver.resolve(
                    new Request(
                            "scene.comfort.cold.v1", "", Source.API, Zone.ROW1_DRIVER),
                    catalog,
                    comfortParked,
                    CapabilitySnapshot.capture(
                            CapabilityCatalog.stage2Defaults(),
                            CapabilityProfile.PRODUCTION,
                            2,
                            Set.of()));

            boolean explicitVerified = explicit.getDecision() == Decision.ACCEPTED
                    && explicit.getScenarioId().equals("scene.comfort.cold.v1")
                    && explicit.getMatchedRuleId().equals("explicit.v1");
            boolean coldVerified = cold.getDecision() == Decision.ACCEPTED
                    && cold.getScenarioId().equals("scene.comfort.cold.v1");
            boolean fatigueVerified = fatigue.getDecision() == Decision.ACCEPTED
                    && fatigue.getScenarioId().equals("scene.fatigue.assist.v1");
            boolean restVerified = rest.getDecision() == Decision.ACCEPTED
                    && rest.getScenarioId().equals("scene.rest.nap.v1");
            boolean unknownRejected = unknown.getDecision() == Decision.REJECTED
                    && unknown.getReasons().contains(ReasonCode.UNKNOWN_INTENT)
                    && unknown.getSelectedManifest().isEmpty();
            boolean ambiguousRejected = ambiguous.getDecision() == Decision.REJECTED
                    && ambiguous.getReasons().contains(ReasonCode.AMBIGUOUS_INTENT)
                    && ambiguous.getCandidateScenarioIds().size() == 2;
            boolean capabilityPolicyVerified = requiredMissing.getDecision()
                            == Decision.REJECTED
                    && optionalMissing.getDecision() == Decision.DEGRADED
                    && movingFatigue.getDecision() == Decision.DEGRADED
                    && movingFatigue.getReasons().contains(
                            ReasonCode.OPTIONAL_CAPABILITY_POLICY_BLOCKED)
                    && movingRest.getDecision() == Decision.REJECTED
                    && movingRest.getReasons().contains(
                            ReasonCode.REQUIRED_CAPABILITY_POLICY_BLOCKED);
            boolean productionFailClosed = production.getDecision() == Decision.REJECTED
                    && production.getReasons().contains(
                            ReasonCode.CONTEXT_NOT_PRODUCTION_TRUSTED)
                    && !production.isProductionTrusted();
            boolean digestVerified = explicit.getResolutionDigest().matches("[0-9a-f]{64}")
                    && explicit.getResolutionDigest().equals(
                            explicitReplay.getResolutionDigest())
                    && !explicit.isExecutable();
            boolean allVerified = explicitVerified
                    && coldVerified
                    && fatigueVerified
                    && restVerified
                    && unknownRejected
                    && ambiguousRejected
                    && capabilityPolicyVerified
                    && productionFailClosed
                    && digestVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");

            Log.i(TAG, "nonce=" + nonce
                    + " scenario_resolver_probe_complete=true"
                    + " scenario_resolver_defined=" + allVerified
                    + " scenario_resolver_explicit_verified=" + explicitVerified
                    + " scenario_resolver_cold_verified=" + coldVerified
                    + " scenario_resolver_fatigue_verified=" + fatigueVerified
                    + " scenario_resolver_rest_verified=" + restVerified
                    + " scenario_resolver_unknown_intent_rejected=" + unknownRejected
                    + " scenario_resolver_ambiguous_intent_rejected="
                    + ambiguousRejected
                    + " scenario_resolver_capability_policy_verified="
                    + capabilityPolicyVerified
                    + " scenario_resolver_production_fail_closed="
                    + productionFailClosed
                    + " scenario_resolution_digest_verified=" + digestVerified
                    + " scenario_resolver_android13_arm64_verified="
                    + android13Arm64Verified
                    + " scenario_resolver_model_invoked=false"
                    + " scenario_resolver_runtime_wired=false"
                    + " scenario_compiler_wired=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_signal_provider_wired=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException | IOException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " scenario_resolver_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " scenario_resolver_model_invoked=false"
                    + " scenario_resolver_runtime_wired=false"
                    + " scenario_compiler_wired=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private ScenarioCatalog catalog() throws IOException {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("scene.comfort.cold.v1.json", readAsset(
                "scenarios/scene.comfort.cold.v1.json"));
        assets.put("scene.fatigue.assist.v1.json", readAsset(
                "scenarios/scene.fatigue.assist.v1.json"));
        assets.put("scene.rest.nap.v1.json", readAsset(
                "scenarios/scene.rest.nap.v1.json"));
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

    private static Request text(String value) {
        return new Request("", value, Source.VOICE, Zone.ROW1_DRIVER);
    }

    private static CapabilitySnapshot capabilities(Set<CapabilityId> unavailable) {
        return CapabilitySnapshot.capture(
                CapabilityCatalog.stage2Defaults(),
                CapabilityProfile.SOFTWARE_SIMULATION,
                1,
                unavailable);
    }

    private static ContextSnapshot context(
            ContextFieldPolicy policy,
            boolean moving,
            boolean omitSpeed) {
        long now = moving ? 2_000 : 1_000;
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        if (!omitSpeed) {
            store.updateReported(decimal(
                    VehicleSignalPath.VEHICLE_SPEED,
                    moving ? 35 : 0,
                    "km/h",
                    "global",
                    now), now);
        }
        store.updateReported(textValue(
                VehicleSignalPath.CURRENT_GEAR,
                moving ? "D" : "P",
                "global",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                !moving,
                "global",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.SEAT_OCCUPIED,
                true,
                "row1.driver",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.SEAT_BELTED,
                true,
                "row1.driver",
                now), now);
        store.updateReported(decimal(
                VehicleSignalPath.SEAT_RECLINE_ANGLE,
                15,
                "degree",
                "row1.driver",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.HVAC_ACTIVE,
                true,
                "cabin",
                now), now);
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
                "scenario-resolver-probe",
                1,
                now,
                SafetyState.NORMAL,
                moving ? MotionState.MOVING : MotionState.PARKED,
                true,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);
        return new ContextSnapshotBuilder().build(
                twin,
                runtime,
                policy,
                SeatZone.ROW1_DRIVER,
                false);
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

    private static SignalValue textValue(
            VehicleSignalPath path,
            String value,
            String area,
            long now) {
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
            VehicleSignalPath path,
            boolean value,
            String area,
            long now) {
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
