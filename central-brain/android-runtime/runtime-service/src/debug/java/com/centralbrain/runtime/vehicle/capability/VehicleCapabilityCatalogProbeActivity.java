package com.centralbrain.runtime.vehicle.capability;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

public final class VehicleCapabilityCatalogProbeActivity extends Activity {
    private static final String TAG = "CbVehicleCatalog";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            CapabilityCatalog catalog = CapabilityCatalog.stage2Defaults();
            VehicleCapability temperature = catalog.require(
                    VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE);
            temperature.getTargetRange().validateDecimal(22.5);
            VehicleCapability fan = catalog.require(
                    VehicleCapability.CapabilityId.HVAC_FAN_LEVEL);
            fan.getTargetRange().validateInteger(7);
            VehicleCapability recline = catalog.require(
                    VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE);
            recline.getTargetRange().validateDecimal(45.0);
            VehicleCapability media = catalog.require(
                    VehicleCapability.CapabilityId.MEDIA_PLAYBACK);
            media.getTargetRange().validateText("PLAY");
            VehicleCapability navigation = catalog.require(
                    VehicleCapability.CapabilityId.NAVIGATION_POI);
            navigation.getTargetRange().validateText("Central Station");

            boolean catalogVerified = catalog.size() == 8
                    && catalog.all().get(0).getId()
                            == VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE
                    && catalog.all().get(7).getId()
                            == VehicleCapability.CapabilityId.NAVIGATION_POI;
            boolean targetRangesVerified = throwsIllegalArgument(
                    () -> temperature.getTargetRange().validateDecimal(30.5))
                    && throwsIllegalArgument(
                            () -> fan.getTargetRange().validateInteger(8))
                    && throwsIllegalArgument(
                            () -> media.getTargetRange().validateText("NEXT"));
            boolean productionAuthorizationFailClosedVerified =
                    catalog.productionAuthorizedCount() == 0;
            for (VehicleCapability capability : catalog.all()) {
                CapabilityAvailability availability = capability.getAvailability();
                productionAuthorizationFailClosedVerified &= availability.isSimulatable()
                        && !availability.isProductionAvailable()
                        && !availability.isProductionAuthorized()
                        && !availability.canUseProduction();
            }
            boolean signalDependencyMappingVerified = recline.getRiskClass()
                    == VehicleCapability.RiskClass.HIGH
                    && recline.getReportedSignalPath().orElseThrow()
                            == VehicleSignalPath.SEAT_RECLINE_ANGLE
                    && recline.getRequiredFreshSignals().size() == 5
                    && recline.getRequiredFreshSignals().contains(
                            VehicleSignalPath.VEHICLE_SPEED)
                    && recline.getRequiredFreshSignals().contains(
                            VehicleSignalPath.PARKING_BRAKE_ENGAGED);
            boolean allVerified = catalogVerified
                    && targetRangesVerified
                    && productionAuthorizationFailClosedVerified
                    && signalDependencyMappingVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");

            Log.i(TAG, "nonce=" + nonce
                    + " vehicle_capability_catalog_probe_complete=true"
                    + " vehicle_capability_catalog_verified=" + allVerified
                    + " vehicle_capability_count=" + catalog.size()
                    + " vehicle_capability_target_ranges_verified="
                    + targetRangesVerified
                    + " vehicle_production_authorization_fail_closed_verified="
                    + productionAuthorizationFailClosedVerified
                    + " vehicle_signal_dependency_mapping_verified="
                    + signalDependencyMappingVerified
                    + " vehicle_capability_catalog_android13_arm64_verified="
                    + android13Arm64Verified
                    + " vehicle_production_capability_authorized_count="
                    + catalog.productionAuthorizedCount()
                    + " vehicle_capability_adapter_registry_wired=false"
                    + " vehicle_property_mapping_configured=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " vehicle_capability_catalog_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " vehicle_capability_adapter_registry_wired=false"
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
