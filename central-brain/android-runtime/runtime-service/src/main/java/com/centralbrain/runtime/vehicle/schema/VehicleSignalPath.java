package com.centralbrain.runtime.vehicle.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Initial VSS-style canonical allowlist. Vendor property IDs are intentionally absent. */
public enum VehicleSignalPath {
    VEHICLE_SPEED(
            "Vehicle.Speed",
            SignalValue.ScalarType.DECIMAL,
            "km/h",
            Set.of("global"),
            500),
    CURRENT_GEAR(
            "Vehicle.Powertrain.Transmission.CurrentGear",
            SignalValue.ScalarType.TEXT,
            "",
            Set.of("global"),
            1_000),
    PARKING_BRAKE_ENGAGED(
            "Vehicle.Chassis.ParkingBrake.IsEngaged",
            SignalValue.ScalarType.BOOLEAN,
            "",
            Set.of("global"),
            1_000),
    HVAC_ACTIVE(
            "Vehicle.Cabin.HVAC.IsAirConditioningActive",
            SignalValue.ScalarType.BOOLEAN,
            "",
            Set.of("cabin"),
            2_000),
    CABIN_TEMPERATURE(
            "Vehicle.Cabin.HVAC.AmbientAirTemperature",
            SignalValue.ScalarType.DECIMAL,
            "celsius",
            Set.of("cabin"),
            5_000),
    HVAC_TARGET_TEMPERATURE(
            "Vehicle.Cabin.HVAC.Station.TargetTemperature",
            SignalValue.ScalarType.DECIMAL,
            "celsius",
            Set.of("row1.driver", "row1.passenger", "row2.left", "row2.right"),
            2_000),
    HVAC_FAN_LEVEL(
            "Vehicle.Cabin.HVAC.Station.FanSpeed",
            SignalValue.ScalarType.INTEGER,
            "level",
            Set.of("cabin", "row1.driver", "row1.passenger"),
            2_000),
    SEAT_OCCUPIED(
            "Vehicle.Cabin.Seat.IsOccupied",
            SignalValue.ScalarType.BOOLEAN,
            "",
            seatAreas(),
            1_000),
    SEAT_BELTED(
            "Vehicle.Cabin.Seat.IsBelted",
            SignalValue.ScalarType.BOOLEAN,
            "",
            seatAreas(),
            1_000),
    SEAT_HEATING_LEVEL(
            "Vehicle.Cabin.Seat.Heating",
            SignalValue.ScalarType.INTEGER,
            "level",
            seatAreas(),
            2_000),
    SEAT_VENTILATION_LEVEL(
            "Vehicle.Cabin.Seat.Ventilation",
            SignalValue.ScalarType.INTEGER,
            "level",
            seatAreas(),
            2_000),
    SEAT_RECLINE_ANGLE(
            "Vehicle.Cabin.Seat.Position.Recline",
            SignalValue.ScalarType.DECIMAL,
            "degree",
            seatAreas(),
            1_000);

    private static final Map<String, VehicleSignalPath> BY_CANONICAL_PATH;

    static {
        Map<String, VehicleSignalPath> byPath = new LinkedHashMap<>();
        for (VehicleSignalPath value : values()) {
            if (byPath.put(value.canonicalPath, value) != null) {
                throw new IllegalStateException("duplicate canonical vehicle signal path");
            }
        }
        BY_CANONICAL_PATH = Collections.unmodifiableMap(byPath);
    }

    private final String canonicalPath;
    private final SignalValue.ScalarType scalarType;
    private final String unit;
    private final Set<String> areas;
    private final long maximumAgeMs;

    VehicleSignalPath(
            String canonicalPath,
            SignalValue.ScalarType scalarType,
            String unit,
            Set<String> areas,
            long maximumAgeMs) {
        this.canonicalPath = canonicalPath;
        this.scalarType = scalarType;
        this.unit = unit;
        this.areas = Set.copyOf(areas);
        this.maximumAgeMs = maximumAgeMs;
    }

    public String getCanonicalPath() {
        return canonicalPath;
    }

    public SignalValue.ScalarType getScalarType() {
        return scalarType;
    }

    public String getUnit() {
        return unit;
    }

    public Set<String> getAreas() {
        return areas;
    }

    public long getMaximumAgeMs() {
        return maximumAgeMs;
    }

    public void validateUnitAndArea(String candidateUnit, String candidateArea) {
        if (!unit.equals(candidateUnit)) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: unit does not match path");
        }
        if (!areas.contains(candidateArea)) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: area is not allowed for path");
        }
    }

    public static VehicleSignalPath fromCanonicalPath(String canonicalPath) {
        VehicleSignalPath value = BY_CANONICAL_PATH.get(canonicalPath);
        if (value == null) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: path is not allowlisted");
        }
        return value;
    }

    private static Set<String> seatAreas() {
        return Set.of("row1.driver", "row1.passenger", "row2.left", "row2.right");
    }
}
