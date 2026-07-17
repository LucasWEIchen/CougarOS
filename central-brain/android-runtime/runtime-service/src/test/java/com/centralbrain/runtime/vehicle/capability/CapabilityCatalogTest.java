package com.centralbrain.runtime.vehicle.capability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.List;
import java.util.Set;

import org.junit.Test;

public final class CapabilityCatalogTest {
    @Test
    public void definesEightImmutableStage2Capabilities() {
        CapabilityCatalog catalog = CapabilityCatalog.stage2Defaults();
        assertEquals(8, catalog.size());
        assertEquals(VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE,
                catalog.all().get(0).getId());
        assertEquals(VehicleCapability.CapabilityId.NAVIGATION_POI,
                catalog.all().get(7).getId());
        assertThrows(UnsupportedOperationException.class, () -> catalog.all().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> catalog.require(VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE)
                        .getAreas().clear());
    }

    @Test
    public void allProductionCapabilitiesFailClosed() {
        CapabilityCatalog catalog = CapabilityCatalog.stage2Defaults();
        assertEquals(0, catalog.productionAuthorizedCount());
        for (VehicleCapability capability : catalog.all()) {
            CapabilityAvailability availability = capability.getAvailability();
            assertTrue(availability.isWritable());
            assertTrue(availability.isSimulatable());
            assertFalse(availability.isProductionAvailable());
            assertFalse(availability.isProductionAuthorized());
            assertFalse(availability.canUseProduction());
        }
        assertThrows(IllegalArgumentException.class, () -> new CapabilityAvailability(
                true, true, true, false, true));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityAvailability(
                false, false, true, false, false));
    }

    @Test
    public void validatesHvacAndSeatTargetRanges() {
        CapabilityCatalog catalog = CapabilityCatalog.stage2Defaults();
        VehicleCapability temperature = catalog.require(
                VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE);
        assertEquals(SignalValue.ScalarType.DECIMAL,
                temperature.getTargetRange().getScalarType());
        assertEquals(16.0, temperature.getTargetRange().getMinimumInclusive(), 0.0);
        assertEquals(30.0, temperature.getTargetRange().getMaximumInclusive(), 0.0);
        temperature.getTargetRange().validateDecimal(22.5);
        assertThrows(
                IllegalArgumentException.class,
                () -> temperature.getTargetRange().validateDecimal(30.5));
        assertThrows(
                IllegalArgumentException.class,
                () -> temperature.getTargetRange().validateDecimal(22.25));

        VehicleCapability fan = catalog.require(
                VehicleCapability.CapabilityId.HVAC_FAN_LEVEL);
        fan.getTargetRange().validateInteger(7);
        assertThrows(
                IllegalArgumentException.class,
                () -> fan.getTargetRange().validateInteger(8));

        VehicleCapability heating = catalog.require(
                VehicleCapability.CapabilityId.SEAT_HEATING_LEVEL);
        heating.getTargetRange().validateInteger(3);
        assertThrows(
                IllegalArgumentException.class,
                () -> heating.getTargetRange().validateInteger(4));

        VehicleCapability power = catalog.require(
                VehicleCapability.CapabilityId.HVAC_POWER);
        power.getTargetRange().validateBoolean(true);
        assertThrows(
                IllegalStateException.class,
                () -> power.getTargetRange().validateInteger(1));
    }

    @Test
    public void validatesMediaAndNavigationTextTargets() {
        CapabilityCatalog catalog = CapabilityCatalog.stage2Defaults();
        VehicleCapability media = catalog.require(
                VehicleCapability.CapabilityId.MEDIA_PLAYBACK);
        media.getTargetRange().validateText("PLAY");
        assertFalse(media.getAvailability().isReadable());
        assertThrows(
                IllegalArgumentException.class,
                () -> media.getTargetRange().validateText("NEXT"));

        VehicleCapability navigation = catalog.require(
                VehicleCapability.CapabilityId.NAVIGATION_POI);
        navigation.getTargetRange().validateText("Central Station");
        assertThrows(
                IllegalArgumentException.class,
                () -> navigation.getTargetRange().validateText("bad\npoi"));
        assertThrows(
                IllegalArgumentException.class,
                () -> navigation.getTargetRange().validateText("x".repeat(129)));
    }

    @Test
    public void bindsReadbackPathsAndFreshSignalDependencies() {
        CapabilityCatalog catalog = CapabilityCatalog.stage2Defaults();
        VehicleCapability recline = catalog.require(
                VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE);
        assertEquals(VehicleCapability.RiskClass.HIGH, recline.getRiskClass());
        assertEquals(VehicleSignalPath.SEAT_RECLINE_ANGLE,
                recline.getReportedSignalPath().orElseThrow());
        assertEquals(Set.of(
                VehicleSignalPath.VEHICLE_SPEED,
                VehicleSignalPath.CURRENT_GEAR,
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                VehicleSignalPath.SEAT_OCCUPIED,
                VehicleSignalPath.SEAT_BELTED), recline.getRequiredFreshSignals());

        VehicleCapability media = catalog.require(
                VehicleCapability.CapabilityId.MEDIA_PLAYBACK);
        assertTrue(media.getReportedSignalPath().isEmpty());
        assertTrue(media.getRequiredFreshSignals().isEmpty());
    }

    @Test
    public void rejectsDuplicateCatalogAndMismatchedReadbackContract() {
        VehicleCapability capability = CapabilityCatalog.stage2Defaults().require(
                VehicleCapability.CapabilityId.HVAC_POWER);
        assertThrows(
                IllegalArgumentException.class,
                () -> new CapabilityCatalog(List.of(capability, capability)));
        assertThrows(IllegalArgumentException.class, () -> new VehicleCapability(
                VehicleCapability.CapabilityId.HVAC_POWER,
                1,
                Set.of("global"),
                CapabilityAvailability.softwareContract(true, true, true),
                "km/h",
                VehicleCapability.TargetRange.decimal(0, 10, 1),
                VehicleCapability.RiskClass.LOW,
                VehicleSignalPath.HVAC_ACTIVE,
                Set.of()));
    }
}
