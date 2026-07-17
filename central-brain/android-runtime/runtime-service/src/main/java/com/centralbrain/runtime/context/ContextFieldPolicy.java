package com.centralbrain.runtime.context;

import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable allowlisted field policy used to build one bounded Context snapshot. */
public final class ContextFieldPolicy {
    public static final int POLICY_VERSION = 1;
    public static final long DEFAULT_RUNTIME_STATE_MAX_AGE_MS = 1_000;

    public enum AreaScope {
        GLOBAL,
        CABIN,
        SELECTED_SEAT
    }

    public static final class FieldRequirement {
        private final VehicleSignalPath path;
        private final AreaScope areaScope;
        private final boolean required;

        private FieldRequirement(
                VehicleSignalPath path,
                AreaScope areaScope,
                boolean required) {
            this.path = Objects.requireNonNull(path, "path");
            this.areaScope = Objects.requireNonNull(areaScope, "areaScope");
            this.required = required;
            validateScope(path, areaScope);
        }

        public VehicleSignalPath getPath() {
            return path;
        }

        public AreaScope getAreaScope() {
            return areaScope;
        }

        public boolean isRequired() {
            return required;
        }

        private static void validateScope(VehicleSignalPath path, AreaScope areaScope) {
            switch (areaScope) {
                case GLOBAL:
                    if (!path.getAreas().contains("global")) {
                        throw new IllegalArgumentException(
                                "CB_CONTEXT: global scope does not match path");
                    }
                    break;
                case CABIN:
                    if (!path.getAreas().contains("cabin")) {
                        throw new IllegalArgumentException(
                                "CB_CONTEXT: cabin scope does not match path");
                    }
                    break;
                case SELECTED_SEAT:
                    if (!path.getAreas().contains("row1.driver")) {
                        throw new IllegalArgumentException(
                                "CB_CONTEXT: seat scope does not match path");
                    }
                    break;
                default:
                    throw new IllegalStateException("CB_CONTEXT: unknown area scope");
            }
        }
    }

    private final String policyId;
    private final int version;
    private final long runtimeStateMaximumAgeMs;
    private final List<FieldRequirement> requirements;

    public static ContextFieldPolicy general() {
        return create(
                "context.general.v1",
                baseRequirements());
    }

    public static ContextFieldPolicy seatComfort() {
        List<FieldRequirement> requirements = baseRequirements();
        requirements.add(required(
                VehicleSignalPath.SEAT_OCCUPIED,
                AreaScope.SELECTED_SEAT));
        requirements.add(optional(
                VehicleSignalPath.SEAT_HEATING_LEVEL,
                AreaScope.SELECTED_SEAT));
        requirements.add(optional(
                VehicleSignalPath.SEAT_VENTILATION_LEVEL,
                AreaScope.SELECTED_SEAT));
        return create("context.seat-comfort.v1", requirements);
    }

    public static ContextFieldPolicy seatRecline() {
        List<FieldRequirement> requirements = baseRequirements();
        requirements.add(required(
                VehicleSignalPath.SEAT_OCCUPIED,
                AreaScope.SELECTED_SEAT));
        requirements.add(required(
                VehicleSignalPath.SEAT_BELTED,
                AreaScope.SELECTED_SEAT));
        requirements.add(required(
                VehicleSignalPath.SEAT_RECLINE_ANGLE,
                AreaScope.SELECTED_SEAT));
        return create("context.seat-recline.v1", requirements);
    }

    private static List<FieldRequirement> baseRequirements() {
        List<FieldRequirement> requirements = new ArrayList<>();
        requirements.add(required(VehicleSignalPath.VEHICLE_SPEED, AreaScope.GLOBAL));
        requirements.add(required(VehicleSignalPath.CURRENT_GEAR, AreaScope.GLOBAL));
        requirements.add(required(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                AreaScope.GLOBAL));
        requirements.add(optional(VehicleSignalPath.HVAC_ACTIVE, AreaScope.CABIN));
        requirements.add(optional(VehicleSignalPath.CABIN_TEMPERATURE, AreaScope.CABIN));
        return requirements;
    }

    private static FieldRequirement required(
            VehicleSignalPath path,
            AreaScope areaScope) {
        return new FieldRequirement(path, areaScope, true);
    }

    private static FieldRequirement optional(
            VehicleSignalPath path,
            AreaScope areaScope) {
        return new FieldRequirement(path, areaScope, false);
    }

    private static ContextFieldPolicy create(
            String policyId,
            List<FieldRequirement> requirements) {
        return new ContextFieldPolicy(
                policyId,
                POLICY_VERSION,
                DEFAULT_RUNTIME_STATE_MAX_AGE_MS,
                requirements);
    }

    private ContextFieldPolicy(
            String policyId,
            int version,
            long runtimeStateMaximumAgeMs,
            List<FieldRequirement> requirements) {
        if (policyId == null || !policyId.matches("[a-z0-9][a-z0-9.-]{2,63}")) {
            throw new IllegalArgumentException("CB_CONTEXT: policy ID is invalid");
        }
        if (version != POLICY_VERSION || runtimeStateMaximumAgeMs < 1) {
            throw new IllegalArgumentException("CB_CONTEXT: policy metadata is invalid");
        }
        Objects.requireNonNull(requirements, "requirements");
        if (requirements.isEmpty() || requirements.size() > 16) {
            throw new IllegalArgumentException("CB_CONTEXT: field requirement count is invalid");
        }
        List<FieldRequirement> copy = new ArrayList<>(requirements.size());
        Set<String> keys = new HashSet<>();
        for (FieldRequirement requirement : requirements) {
            Objects.requireNonNull(requirement, "requirement");
            String key = requirement.path.name() + ':' + requirement.areaScope.name();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("CB_CONTEXT: duplicate field requirement");
            }
            copy.add(requirement);
        }
        this.policyId = policyId;
        this.version = version;
        this.runtimeStateMaximumAgeMs = runtimeStateMaximumAgeMs;
        this.requirements = Collections.unmodifiableList(copy);
    }

    public String getPolicyId() {
        return policyId;
    }

    public int getVersion() {
        return version;
    }

    public long getRuntimeStateMaximumAgeMs() {
        return runtimeStateMaximumAgeMs;
    }

    public List<FieldRequirement> getRequirements() {
        return requirements;
    }
}
