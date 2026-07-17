package com.centralbrain.runtime.vehicle.schema;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

public final class VehicleSignalSchemaProbeActivity extends Activity {
    private static final String TAG = "CbVehicleSignal";
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;
    private static final long RECEIVED_ELAPSED_MS = 50_000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            SignalTimestamp timestamp = new SignalTimestamp(
                    SOURCE_EPOCH_MS,
                    RECEIVED_ELAPSED_MS);
            SignalValue speed = SignalValue.ofDecimal(
                    VehicleSignalPath.fromCanonicalPath("Vehicle.Speed"),
                    42.5,
                    "km/h",
                    "global",
                    timestamp,
                    SignalQuality.VALID,
                    SignalSource.SIMULATED,
                    1);
            speed.validateFreshness(RECEIVED_ELAPSED_MS + 500);
            SignalValue occupied = SignalValue.ofBoolean(
                    VehicleSignalPath.SEAT_OCCUPIED,
                    true,
                    "",
                    "row1.driver",
                    timestamp,
                    SignalQuality.VALID,
                    SignalSource.SIMULATED,
                    2);
            occupied.validateFreshness(RECEIVED_ELAPSED_MS + 1_000);
            SignalValue fan = SignalValue.ofInteger(
                    VehicleSignalPath.HVAC_FAN_LEVEL,
                    3,
                    "level",
                    "cabin",
                    timestamp,
                    SignalQuality.STALE,
                    SignalSource.SIMULATED,
                    3);
            fan.validateFreshness(RECEIVED_ELAPSED_MS + 2_001);
            SignalValue gear = SignalValue.ofText(
                    VehicleSignalPath.CURRENT_GEAR,
                    "PARK",
                    "",
                    "global",
                    timestamp,
                    SignalQuality.VALID,
                    SignalSource.DERIVED,
                    4);
            gear.validateFreshness(RECEIVED_ELAPSED_MS + 20);

            boolean pathAllowlistVerified = VehicleSignalPath.values().length == 12
                    && throwsIllegalArgument(() -> VehicleSignalPath.fromCanonicalPath(
                            "Vehicle.Private.VendorProperty"));
            boolean typedScalarVerified = speed.getDecimalValue() == 42.5
                    && occupied.getBooleanValue()
                    && fan.getIntegerValue() == 3
                    && "PARK".equals(gear.getTextValue());
            boolean unitAreaVerified = throwsIllegalArgument(() -> SignalValue.ofDecimal(
                    VehicleSignalPath.VEHICLE_SPEED,
                    1,
                    "m/s",
                    "global",
                    timestamp,
                    SignalQuality.VALID,
                    SignalSource.SIMULATED,
                    5))
                    && throwsIllegalArgument(() -> SignalValue.ofBoolean(
                            VehicleSignalPath.SEAT_OCCUPIED,
                            true,
                            "",
                            "trunk",
                            timestamp,
                            SignalQuality.VALID,
                            SignalSource.SIMULATED,
                            6));
            SignalValue incorrectlyValid = SignalValue.ofDecimal(
                    VehicleSignalPath.VEHICLE_SPEED,
                    0,
                    "km/h",
                    "global",
                    timestamp,
                    SignalQuality.VALID,
                    SignalSource.SIMULATED,
                    7);
            SignalValue unavailable = SignalValue.withoutValue(
                    VehicleSignalPath.CABIN_TEMPERATURE,
                    "celsius",
                    "cabin",
                    timestamp,
                    SignalQuality.UNAVAILABLE,
                    SignalSource.SIMULATED,
                    8);
            boolean freshnessQualityVerified = throwsIllegalArgument(
                    () -> incorrectlyValid.validateFreshness(RECEIVED_ELAPSED_MS + 501))
                    && !unavailable.hasValue()
                    && !unavailable.getQuality().isUsableForDecision();
            boolean schemaVerified = pathAllowlistVerified
                    && typedScalarVerified
                    && unitAreaVerified
                    && freshnessQualityVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");

            Log.i(TAG, "nonce=" + nonce
                    + " vehicle_signal_schema_probe_complete=true"
                    + " vehicle_signal_schema_verified=" + schemaVerified
                    + " vehicle_signal_path_allowlist_verified=" + pathAllowlistVerified
                    + " vehicle_signal_typed_scalar_verified=" + typedScalarVerified
                    + " vehicle_signal_unit_area_verified=" + unitAreaVerified
                    + " vehicle_signal_freshness_quality_verified="
                    + freshnessQualityVerified
                    + " vehicle_signal_schema_android13_arm64_verified="
                    + android13Arm64Verified
                    + " vehicle_signal_provider_wired=false"
                    + " vehicle_property_mapping_configured=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " vehicle_signal_schema_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " vehicle_signal_provider_wired=false"
                    + " vehicle_property_mapping_configured=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}
