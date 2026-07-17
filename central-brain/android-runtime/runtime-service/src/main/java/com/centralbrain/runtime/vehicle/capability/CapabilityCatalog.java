package com.centralbrain.runtime.vehicle.capability;

import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed Stage 2 capability catalog. It contains metadata only and owns no adapters. */
public final class CapabilityCatalog {
    private static final Set<String> SEAT_AREAS = Set.of(
            "row1.driver",
            "row1.passenger",
            "row2.left",
            "row2.right");
    private static final CapabilityCatalog STAGE2_DEFAULTS = new CapabilityCatalog(List.of(
            capability(
                    VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE,
                    SEAT_AREAS,
                    "celsius",
                    VehicleCapability.TargetRange.decimal(16.0, 30.0, 0.5),
                    VehicleCapability.RiskClass.LOW,
                    VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                    Set.of()),
            capability(
                    VehicleCapability.CapabilityId.HVAC_POWER,
                    Set.of("cabin"),
                    "",
                    VehicleCapability.TargetRange.booleanValue(),
                    VehicleCapability.RiskClass.LOW,
                    VehicleSignalPath.HVAC_ACTIVE,
                    Set.of()),
            capability(
                    VehicleCapability.CapabilityId.HVAC_FAN_LEVEL,
                    Set.of("cabin", "row1.driver", "row1.passenger"),
                    "level",
                    VehicleCapability.TargetRange.integer(0, 7, 1),
                    VehicleCapability.RiskClass.LOW,
                    VehicleSignalPath.HVAC_FAN_LEVEL,
                    Set.of()),
            capability(
                    VehicleCapability.CapabilityId.SEAT_HEATING_LEVEL,
                    SEAT_AREAS,
                    "level",
                    VehicleCapability.TargetRange.integer(0, 3, 1),
                    VehicleCapability.RiskClass.MEDIUM,
                    VehicleSignalPath.SEAT_HEATING_LEVEL,
                    Set.of(VehicleSignalPath.SEAT_OCCUPIED)),
            capability(
                    VehicleCapability.CapabilityId.SEAT_VENTILATION_LEVEL,
                    SEAT_AREAS,
                    "level",
                    VehicleCapability.TargetRange.integer(0, 3, 1),
                    VehicleCapability.RiskClass.MEDIUM,
                    VehicleSignalPath.SEAT_VENTILATION_LEVEL,
                    Set.of(VehicleSignalPath.SEAT_OCCUPIED)),
            capability(
                    VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE,
                    SEAT_AREAS,
                    "degree",
                    VehicleCapability.TargetRange.decimal(0.0, 60.0, 1.0),
                    VehicleCapability.RiskClass.HIGH,
                    VehicleSignalPath.SEAT_RECLINE_ANGLE,
                    Set.of(
                            VehicleSignalPath.VEHICLE_SPEED,
                            VehicleSignalPath.CURRENT_GEAR,
                            VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                            VehicleSignalPath.SEAT_OCCUPIED,
                            VehicleSignalPath.SEAT_BELTED)),
            capabilityWithoutVehicleReadback(
                    VehicleCapability.CapabilityId.MEDIA_PLAYBACK,
                    Set.of("cabin"),
                    "",
                    VehicleCapability.TargetRange.text(
                            16,
                            Set.of("PLAY", "PAUSE", "STOP")),
                    VehicleCapability.RiskClass.LOW),
            capabilityWithoutVehicleReadback(
                    VehicleCapability.CapabilityId.NAVIGATION_POI,
                    Set.of("cabin"),
                    "",
                    VehicleCapability.TargetRange.text(128, Set.of()),
                    VehicleCapability.RiskClass.MEDIUM)));

    private final Map<VehicleCapability.CapabilityId, VehicleCapability> byId;
    private final List<VehicleCapability> ordered;

    CapabilityCatalog(List<VehicleCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_CAPABILITY: catalog must not be empty");
        }
        EnumMap<VehicleCapability.CapabilityId, VehicleCapability> values =
                new EnumMap<>(VehicleCapability.CapabilityId.class);
        List<VehicleCapability> order = new ArrayList<>();
        for (VehicleCapability capability : capabilities) {
            if (capability == null || values.put(capability.getId(), capability) != null) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: duplicate or null capability");
            }
            order.add(capability);
        }
        this.byId = Collections.unmodifiableMap(values);
        this.ordered = Collections.unmodifiableList(order);
    }

    public static CapabilityCatalog stage2Defaults() {
        return STAGE2_DEFAULTS;
    }

    public int size() {
        return ordered.size();
    }

    public List<VehicleCapability> all() {
        return ordered;
    }

    public VehicleCapability require(VehicleCapability.CapabilityId id) {
        VehicleCapability capability = byId.get(id);
        if (capability == null) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_CAPABILITY: capability is not cataloged");
        }
        return capability;
    }

    public int productionAuthorizedCount() {
        int count = 0;
        for (VehicleCapability capability : ordered) {
            if (capability.getAvailability().canUseProduction()) {
                count++;
            }
        }
        return count;
    }

    private static VehicleCapability capability(
            VehicleCapability.CapabilityId id,
            Set<String> areas,
            String unit,
            VehicleCapability.TargetRange targetRange,
            VehicleCapability.RiskClass riskClass,
            VehicleSignalPath reportedSignalPath,
            Set<VehicleSignalPath> requiredFreshSignals) {
        return new VehicleCapability(
                id,
                1,
                new LinkedHashSet<>(areas),
                CapabilityAvailability.softwareContract(true, true, true),
                unit,
                targetRange,
                riskClass,
                reportedSignalPath,
                new LinkedHashSet<>(requiredFreshSignals));
    }

    private static VehicleCapability capabilityWithoutVehicleReadback(
            VehicleCapability.CapabilityId id,
            Set<String> areas,
            String unit,
            VehicleCapability.TargetRange targetRange,
            VehicleCapability.RiskClass riskClass) {
        return new VehicleCapability(
                id,
                1,
                new LinkedHashSet<>(areas),
                CapabilityAvailability.softwareContract(false, true, true),
                unit,
                targetRange,
                riskClass,
                null,
                Set.of());
    }
}
