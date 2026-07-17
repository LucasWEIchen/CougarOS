package com.centralbrain.runtime.vehicle.capability;

import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable capability metadata. This type does not discover or invoke a vehicle adapter. */
public final class VehicleCapability {
    public enum CapabilityId {
        HVAC_TARGET_TEMPERATURE("vehicle.hvac.target_temperature"),
        HVAC_POWER("vehicle.hvac.power"),
        HVAC_FAN_LEVEL("vehicle.hvac.fan_level"),
        SEAT_HEATING_LEVEL("vehicle.seat.heating"),
        SEAT_VENTILATION_LEVEL("vehicle.seat.ventilation"),
        SEAT_RECLINE_ANGLE("vehicle.seat.recline"),
        MEDIA_PLAYBACK("media.playback"),
        NAVIGATION_POI("navigation.poi");

        private final String canonicalId;

        CapabilityId(String canonicalId) {
            this.canonicalId = canonicalId;
        }

        public String getCanonicalId() {
            return canonicalId;
        }
    }

    public enum RiskClass {
        LOW,
        MEDIUM,
        HIGH
    }

    public static final class TargetRange {
        private final SignalValue.ScalarType scalarType;
        private final double minimumInclusive;
        private final double maximumInclusive;
        private final double step;
        private final int maximumTextChars;
        private final Set<String> allowedTextValues;

        public static TargetRange booleanValue() {
            return new TargetRange(
                    SignalValue.ScalarType.BOOLEAN,
                    0,
                    1,
                    1,
                    0,
                    Set.of());
        }

        public static TargetRange integer(long minimumInclusive, long maximumInclusive, long step) {
            if (step < 1) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: integer step must be positive");
            }
            return numeric(
                    SignalValue.ScalarType.INTEGER,
                    minimumInclusive,
                    maximumInclusive,
                    step);
        }

        public static TargetRange decimal(
                double minimumInclusive,
                double maximumInclusive,
                double step) {
            return numeric(
                    SignalValue.ScalarType.DECIMAL,
                    minimumInclusive,
                    maximumInclusive,
                    step);
        }

        public static TargetRange text(int maximumTextChars, Set<String> allowedValues) {
            if (maximumTextChars < 1 || maximumTextChars > 256) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: text bound is invalid");
            }
            Objects.requireNonNull(allowedValues, "allowedValues");
            Set<String> copy = new LinkedHashSet<>();
            for (String value : allowedValues) {
                validateText(value, maximumTextChars);
                if (!copy.add(value)) {
                    throw new IllegalArgumentException(
                            "CB_VEHICLE_CAPABILITY: duplicate allowed text value");
                }
            }
            return new TargetRange(
                    SignalValue.ScalarType.TEXT,
                    0,
                    0,
                    0,
                    maximumTextChars,
                    copy);
        }

        private static TargetRange numeric(
                SignalValue.ScalarType scalarType,
                double minimumInclusive,
                double maximumInclusive,
                double step) {
            if (!Double.isFinite(minimumInclusive)
                    || !Double.isFinite(maximumInclusive)
                    || !Double.isFinite(step)
                    || minimumInclusive > maximumInclusive
                    || step <= 0) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: numeric range is invalid");
            }
            return new TargetRange(
                    scalarType,
                    minimumInclusive,
                    maximumInclusive,
                    step,
                    0,
                    Set.of());
        }

        private TargetRange(
                SignalValue.ScalarType scalarType,
                double minimumInclusive,
                double maximumInclusive,
                double step,
                int maximumTextChars,
                Set<String> allowedTextValues) {
            this.scalarType = scalarType;
            this.minimumInclusive = minimumInclusive;
            this.maximumInclusive = maximumInclusive;
            this.step = step;
            this.maximumTextChars = maximumTextChars;
            this.allowedTextValues = Collections.unmodifiableSet(
                    new LinkedHashSet<>(allowedTextValues));
        }

        public SignalValue.ScalarType getScalarType() {
            return scalarType;
        }

        public double getMinimumInclusive() {
            requireNumeric();
            return minimumInclusive;
        }

        public double getMaximumInclusive() {
            requireNumeric();
            return maximumInclusive;
        }

        public double getStep() {
            requireNumeric();
            return step;
        }

        public int getMaximumTextChars() {
            if (scalarType != SignalValue.ScalarType.TEXT) {
                throw new IllegalStateException(
                        "CB_VEHICLE_CAPABILITY: target is not text");
            }
            return maximumTextChars;
        }

        public Set<String> getAllowedTextValues() {
            if (scalarType != SignalValue.ScalarType.TEXT) {
                throw new IllegalStateException(
                        "CB_VEHICLE_CAPABILITY: target is not text");
            }
            return allowedTextValues;
        }

        public void validateBoolean(boolean ignored) {
            requireType(SignalValue.ScalarType.BOOLEAN);
        }

        public void validateInteger(long value) {
            requireType(SignalValue.ScalarType.INTEGER);
            validateNumeric(value);
        }

        public void validateDecimal(double value) {
            requireType(SignalValue.ScalarType.DECIMAL);
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: decimal target is not finite");
            }
            validateNumeric(value);
        }

        public void validateText(String value) {
            requireType(SignalValue.ScalarType.TEXT);
            validateText(value, maximumTextChars);
            if (!allowedTextValues.isEmpty() && !allowedTextValues.contains(value)) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: text target is not allowlisted");
            }
        }

        private void validateNumeric(double value) {
            if (value < minimumInclusive || value > maximumInclusive) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: target is outside the supported range");
            }
            double stepCount = (value - minimumInclusive) / step;
            if (Math.abs(stepCount - Math.rint(stepCount)) > 1.0e-9) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: target does not align to the supported step");
            }
        }

        private void requireNumeric() {
            if (scalarType != SignalValue.ScalarType.INTEGER
                    && scalarType != SignalValue.ScalarType.DECIMAL) {
                throw new IllegalStateException(
                        "CB_VEHICLE_CAPABILITY: target is not numeric");
            }
        }

        private void requireType(SignalValue.ScalarType expected) {
            if (scalarType != expected) {
                throw new IllegalStateException(
                        "CB_VEHICLE_CAPABILITY: target accessor type mismatch");
            }
        }

        private static void validateText(String value, int maximumTextChars) {
            if (value == null || value.isEmpty() || value.length() > maximumTextChars) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: text target is invalid");
            }
            for (int index = 0; index < value.length(); index++) {
                if (Character.isISOControl(value.charAt(index))) {
                    throw new IllegalArgumentException(
                            "CB_VEHICLE_CAPABILITY: text target contains a control character");
                }
            }
        }
    }

    private final CapabilityId id;
    private final int version;
    private final Set<String> areas;
    private final CapabilityAvailability availability;
    private final String unit;
    private final TargetRange targetRange;
    private final RiskClass riskClass;
    private final VehicleSignalPath reportedSignalPath;
    private final Set<VehicleSignalPath> requiredFreshSignals;

    VehicleCapability(
            CapabilityId id,
            int version,
            Set<String> areas,
            CapabilityAvailability availability,
            String unit,
            TargetRange targetRange,
            RiskClass riskClass,
            VehicleSignalPath reportedSignalPath,
            Set<VehicleSignalPath> requiredFreshSignals) {
        this.id = Objects.requireNonNull(id, "id");
        if (version < 1) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_CAPABILITY: version must be positive");
        }
        this.version = version;
        this.areas = immutableNonEmptyAreas(areas);
        this.availability = Objects.requireNonNull(availability, "availability");
        this.unit = validateUnit(unit);
        this.targetRange = Objects.requireNonNull(targetRange, "targetRange");
        this.riskClass = Objects.requireNonNull(riskClass, "riskClass");
        this.reportedSignalPath = reportedSignalPath;
        this.requiredFreshSignals = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(
                        requiredFreshSignals,
                        "requiredFreshSignals")));
        if (reportedSignalPath != null) {
            if (reportedSignalPath.getScalarType() != targetRange.getScalarType()
                    || !reportedSignalPath.getUnit().equals(unit)) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: reported signal does not match target type/unit");
            }
            if (!reportedSignalPath.getAreas().containsAll(this.areas)) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: capability area is not supported by reported signal");
            }
        }
    }

    public CapabilityId getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public Set<String> getAreas() {
        return areas;
    }

    public CapabilityAvailability getAvailability() {
        return availability;
    }

    public String getUnit() {
        return unit;
    }

    public TargetRange getTargetRange() {
        return targetRange;
    }

    public RiskClass getRiskClass() {
        return riskClass;
    }

    public Optional<VehicleSignalPath> getReportedSignalPath() {
        return Optional.ofNullable(reportedSignalPath);
    }

    public Set<VehicleSignalPath> getRequiredFreshSignals() {
        return requiredFreshSignals;
    }

    private static Set<String> immutableNonEmptyAreas(Set<String> areas) {
        Objects.requireNonNull(areas, "areas");
        if (areas.isEmpty()) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_CAPABILITY: at least one area is required");
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String area : areas) {
            if (area == null || area.isEmpty() || area.length() > 32) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_CAPABILITY: area is invalid");
            }
            copy.add(area);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static String validateUnit(String unit) {
        Objects.requireNonNull(unit, "unit");
        if (unit.length() > 16) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_CAPABILITY: unit is too long");
        }
        return unit;
    }
}
