package com.centralbrain.runtime.vehicle.twin;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.Set;

public final class VehicleDigitalTwinStoreProbeActivity extends Activity {
    private static final String TAG = "CbVehicleTwin";
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
            VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
            DesiredStateRecord desired = desiredSpeed(42.5, 1_000, 2_000);
            long desiredRevision = store.setDesired(desired, 1_000);
            SignalValue reported = speed(42.5, 1_100, 1);
            long reportedRevision = store.updateReported(reported, 1_100);
            DigitalTwinSnapshot matched = store.snapshot(
                    Set.of(VehicleSignalPath.VEHICLE_SPEED),
                    1_200);

            boolean storeVerified = desiredRevision == 1
                    && reportedRevision == 2
                    && matched.getRevision() == 2;
            boolean separationVerified = matched.getDesiredRecords().size() == 1
                    && matched.getReportedRecords().size() == 1
                    && matched.desired(VehicleSignalPath.VEHICLE_SPEED, "global")
                            .orElseThrow().getStoreRevision() == desiredRevision
                    && matched.reported(VehicleSignalPath.VEHICLE_SPEED, "global")
                            .orElseThrow().getStoreRevision() == reportedRevision;
            boolean monotonicRevisionVerified = store.updateReported(reported, 1_200) == 2
                    && store.setDesired(desired, 1_200) == 1
                    && store.getRevision() == 2;

            DigitalTwinSnapshot stale = store.snapshot(
                    Set.of(VehicleSignalPath.VEHICLE_SPEED),
                    1_601);
            DigitalTwinSnapshot expired = store.snapshot(
                    Set.of(VehicleSignalPath.VEHICLE_SPEED),
                    2_000);
            boolean ttlQualityVerified = stale.reported(
                            VehicleSignalPath.VEHICLE_SPEED,
                            "global").orElseThrow().getEffectiveQuality() == SignalQuality.STALE
                    && expired.desired(
                            VehicleSignalPath.VEHICLE_SPEED,
                            "global").isEmpty()
                    && expired.desiredIncludingExpired(
                            VehicleSignalPath.VEHICLE_SPEED,
                            "global").isPresent();

            boolean snapshotImmutable = throwsUnsupported(
                    () -> matched.getReportedRecords().clear());
            boolean atomicSnapshotVerified = snapshotImmutable
                    && matched.getCapturedElapsedRealtimeMs() == 1_200
                    && matched.getRevision() == 2;
            boolean staleReportRejected = throwsIllegalArgument(
                    () -> store.updateReported(speed(41.0, 1_099, 2), 1_200));
            boolean reconciliationVerified = matched.reconcile(
                            VehicleSignalPath.VEHICLE_SPEED,
                            "global") == DigitalTwinSnapshot.ReconciliationState.MATCHED
                    && stale.reconcile(
                            VehicleSignalPath.VEHICLE_SPEED,
                            "global") == DigitalTwinSnapshot.ReconciliationState.REPORTED_STALE
                    && expired.reconcile(
                            VehicleSignalPath.VEHICLE_SPEED,
                            "global") == DigitalTwinSnapshot.ReconciliationState.DESIRED_EXPIRED;
            boolean allVerified = storeVerified
                    && separationVerified
                    && monotonicRevisionVerified
                    && ttlQualityVerified
                    && atomicSnapshotVerified
                    && staleReportRejected
                    && reconciliationVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");

            Log.i(TAG, "nonce=" + nonce
                    + " vehicle_digital_twin_probe_complete=true"
                    + " vehicle_digital_twin_store_verified=" + allVerified
                    + " vehicle_digital_twin_desired_reported_separation_verified="
                    + separationVerified
                    + " vehicle_digital_twin_monotonic_revision_verified="
                    + monotonicRevisionVerified
                    + " vehicle_digital_twin_ttl_quality_verified=" + ttlQualityVerified
                    + " vehicle_digital_twin_atomic_snapshot_verified="
                    + atomicSnapshotVerified
                    + " vehicle_digital_twin_stale_report_rejected=" + staleReportRejected
                    + " vehicle_digital_twin_reconciliation_verified="
                    + reconciliationVerified
                    + " vehicle_digital_twin_android13_arm64_verified="
                    + android13Arm64Verified
                    + " vehicle_digital_twin_persistence_wired=false"
                    + " vehicle_capability_adapter_registry_wired=false"
                    + " vehicle_property_mapping_configured=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " vehicle_digital_twin_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " vehicle_digital_twin_persistence_wired=false"
                    + " vehicle_capability_adapter_registry_wired=false"
                    + " vehicle_property_mapping_configured=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static DesiredStateRecord desiredSpeed(double value, long requested, long expires) {
        return DesiredStateRecord.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                value,
                "km/h",
                "global",
                requested,
                expires);
    }

    private static SignalValue speed(double value, long received, long sourceRevision) {
        return SignalValue.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                value,
                "km/h",
                "global",
                new SignalTimestamp(SOURCE_EPOCH_MS + received, received),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                sourceRevision);
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean throwsUnsupported(Runnable action) {
        try {
            action.run();
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }
}
