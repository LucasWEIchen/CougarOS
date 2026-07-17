package com.centralbrain.runtime.context;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.context.ContextSnapshot.DrivingState;
import com.centralbrain.runtime.context.ContextSnapshot.SeatZone;
import com.centralbrain.runtime.context.ContextSnapshot.SourceMode;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.MotionState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SafetyState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SourceAssurance;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.VehicleDigitalTwinStore;

import java.util.Set;

public final class ContextSnapshotBuilderProbeActivity extends Activity {
    private static final String TAG = "CbContextSnapshot";
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ContextSnapshotBuilder builder = new ContextSnapshotBuilder();
            VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
            store.updateReported(speed(0, 1_000), 1_000);
            store.updateReported(gear("P", 1_000), 1_000);
            store.updateReported(brake(true, 1_000), 1_000);
            DigitalTwinSnapshot twin = store.snapshot(generalPaths(), 1_000);
            SafetyVehicleStateSnapshot runtime = runtimeState(1_000);
            ContextSnapshot complete = builder.build(
                    twin,
                    runtime,
                    ContextFieldPolicy.general(),
                    SeatZone.ROW1_DRIVER,
                    false);
            ContextSnapshot repeated = builder.build(
                    twin,
                    runtime,
                    ContextFieldPolicy.general(),
                    SeatZone.ROW1_DRIVER,
                    false);

            VehicleDigitalTwinStore missingStore = new VehicleDigitalTwinStore();
            missingStore.updateReported(gear("P", 1_000), 1_000);
            missingStore.updateReported(brake(true, 1_000), 1_000);
            ContextSnapshot restricted = builder.build(
                    missingStore.snapshot(generalPaths(), 1_000),
                    runtime,
                    ContextFieldPolicy.general(),
                    SeatZone.ROW1_DRIVER,
                    false);

            boolean builderVerified = complete.getTwinRevision() == 3
                    && complete.getFields().size() == 5;
            boolean versionDigestVerified = complete.getSchemaVersion()
                            == ContextSnapshot.SCHEMA_VERSION
                    && complete.getContextId().matches("ctx-[0-9a-f]{24}")
                    && complete.getDigest().matches("[0-9a-f]{64}")
                    && complete.getDigest().equals(repeated.getDigest());
            boolean requiredFieldPolicyVerified = complete.getMissingRequiredFields().isEmpty()
                    && restricted.getMissingRequiredFields().size() == 1
                    && restricted.getMissingRequiredFields().get(0).getPath()
                            == VehicleSignalPath.VEHICLE_SPEED;
            boolean freshnessReportVerified = complete.isRequiredFreshnessComplete()
                    && !restricted.isRequiredFreshnessComplete()
                    && complete.getStaleFields().isEmpty()
                    && complete.getConflictFields().isEmpty();
            boolean drivingStateVerified = complete.getDrivingState() == DrivingState.PARKED
                    && !complete.hasMotionConflict();
            boolean restrictedFailClosedVerified = !complete.isRestricted()
                    && restricted.isRestricted()
                    && restricted.getDrivingState() == DrivingState.UNKNOWN;
            boolean sourceTrustVerified = complete.getSourceMode() == SourceMode.SIMULATED
                    && !complete.isProductionTrusted()
                    && complete.getNonProductionTrustedFields().size() == 3;
            boolean allVerified = builderVerified
                    && versionDigestVerified
                    && requiredFieldPolicyVerified
                    && freshnessReportVerified
                    && drivingStateVerified
                    && restrictedFailClosedVerified
                    && sourceTrustVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");

            Log.i(TAG, "nonce=" + nonce
                    + " context_snapshot_probe_complete=true"
                    + " context_snapshot_builder_verified=" + allVerified
                    + " context_snapshot_version_digest_verified="
                    + versionDigestVerified
                    + " context_snapshot_required_field_policy_verified="
                    + requiredFieldPolicyVerified
                    + " context_snapshot_freshness_report_verified="
                    + freshnessReportVerified
                    + " context_snapshot_driving_state_verified=" + drivingStateVerified
                    + " context_snapshot_restricted_fail_closed_verified="
                    + restrictedFailClosedVerified
                    + " context_snapshot_source_trust_verified=" + sourceTrustVerified
                    + " context_snapshot_android13_arm64_verified="
                    + android13Arm64Verified
                    + " context_snapshot_production_trusted=false"
                    + " context_snapshot_production_wired=false"
                    + " vehicle_signal_provider_wired=false"
                    + " vehicle_capability_adapter_registry_wired=false"
                    + " vehicle_property_mapping_configured=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " context_snapshot_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " context_snapshot_production_trusted=false"
                    + " context_snapshot_production_wired=false"
                    + " vehicle_signal_provider_wired=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static Set<VehicleSignalPath> generalPaths() {
        return Set.of(
                VehicleSignalPath.VEHICLE_SPEED,
                VehicleSignalPath.CURRENT_GEAR,
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                VehicleSignalPath.HVAC_ACTIVE,
                VehicleSignalPath.CABIN_TEMPERATURE);
    }

    private static SafetyVehicleStateSnapshot runtimeState(long captured) {
        return new SafetyVehicleStateSnapshot(
                "context-probe-state",
                1,
                captured,
                SafetyState.NORMAL,
                MotionState.PARKED,
                true,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);
    }

    private static SignalValue speed(double value, long received) {
        return SignalValue.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                value,
                "km/h",
                "global",
                timestamp(received),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalValue gear(String value, long received) {
        return SignalValue.ofText(
                VehicleSignalPath.CURRENT_GEAR,
                value,
                "",
                "global",
                timestamp(received),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalValue brake(boolean value, long received) {
        return SignalValue.ofBoolean(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                value,
                "",
                "global",
                timestamp(received),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalTimestamp timestamp(long received) {
        return new SignalTimestamp(SOURCE_EPOCH_MS + received, received);
    }
}
