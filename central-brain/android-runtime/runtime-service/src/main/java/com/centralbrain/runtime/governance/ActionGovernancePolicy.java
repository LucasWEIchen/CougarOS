package com.centralbrain.runtime.governance;

import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.MotionState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SafetyState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Catalog-based action risk and Safety/Vehicle State policy. */
public final class ActionGovernancePolicy {
    public static final String ACTION_VEHICLE_STATE_READ = "vehicle.state.read";
    public static final String ACTION_CABIN_TEMPERATURE_SET = "cabin.temperature.set";
    public static final String ACTION_DRIVER_VIDEO_PLAY = "driver.display.video.play";
    public static final String ACTION_DIAGNOSTIC_WRITE = "vehicle.diagnostics.write";
    public static final String ACTION_OTA_INSTALL = "system.ota.install";

    public enum RiskClass {
        READ_ONLY,
        COMFORT_CONTROL,
        DRIVER_DISTRACTION,
        DIAGNOSTIC_WRITE,
        OTA,
        UNKNOWN
    }

    public enum Outcome {
        ALLOW_POLICY_ONLY,
        APPROVAL_REQUIRED,
        DENY
    }

    public enum Reason {
        READ_ALLOWED,
        COMFORT_ALLOWED,
        HIGH_RISK_APPROVAL_REQUIRED,
        UNKNOWN_ACTION,
        STATE_UNAVAILABLE,
        SAFETY_STATE_DENIED,
        VEHICLE_MOTION_UNKNOWN,
        VEHICLE_MOVING_DENIED,
        DRIVER_UNAVAILABLE
    }

    private static final Map<String, RiskClass> RISK_BY_ACTION = createCatalog();

    public Decision evaluate(String actionId, SafetyVehicleStateSnapshot state) {
        RiskClass riskClass = classify(actionId);
        if (riskClass == RiskClass.UNKNOWN) {
            return Decision.denied(riskClass, Reason.UNKNOWN_ACTION, state);
        }
        if (state == null) {
            return Decision.denied(riskClass, Reason.STATE_UNAVAILABLE, null);
        }
        if (riskClass == RiskClass.READ_ONLY) {
            return Decision.allowed(riskClass, Reason.READ_ALLOWED, state);
        }
        if (state.getSafetyState() == SafetyState.UNKNOWN
                || state.getSafetyState() == SafetyState.EMERGENCY) {
            return Decision.denied(riskClass, Reason.SAFETY_STATE_DENIED, state);
        }
        if (state.getMotionState() == MotionState.UNKNOWN) {
            return Decision.denied(riskClass, Reason.VEHICLE_MOTION_UNKNOWN, state);
        }
        if (riskClass == RiskClass.COMFORT_CONTROL) {
            return Decision.allowed(riskClass, Reason.COMFORT_ALLOWED, state);
        }
        if (state.getMotionState() == MotionState.MOVING) {
            return Decision.denied(riskClass, Reason.VEHICLE_MOVING_DENIED, state);
        }
        if (!state.isDriverAvailable()) {
            return Decision.denied(riskClass, Reason.DRIVER_UNAVAILABLE, state);
        }
        return Decision.approvalRequired(riskClass, state);
    }

    public RiskClass classify(String actionId) {
        if (actionId == null) {
            return RiskClass.UNKNOWN;
        }
        return RISK_BY_ACTION.getOrDefault(actionId, RiskClass.UNKNOWN);
    }

    public Map<String, RiskClass> getCatalog() {
        return RISK_BY_ACTION;
    }

    public static boolean isHighRisk(RiskClass riskClass) {
        return riskClass == RiskClass.DRIVER_DISTRACTION
                || riskClass == RiskClass.DIAGNOSTIC_WRITE
                || riskClass == RiskClass.OTA;
    }

    private static Map<String, RiskClass> createCatalog() {
        Map<String, RiskClass> catalog = new LinkedHashMap<>();
        catalog.put(ACTION_VEHICLE_STATE_READ, RiskClass.READ_ONLY);
        catalog.put(ACTION_CABIN_TEMPERATURE_SET, RiskClass.COMFORT_CONTROL);
        catalog.put(ACTION_DRIVER_VIDEO_PLAY, RiskClass.DRIVER_DISTRACTION);
        catalog.put(ACTION_DIAGNOSTIC_WRITE, RiskClass.DIAGNOSTIC_WRITE);
        catalog.put(ACTION_OTA_INSTALL, RiskClass.OTA);
        return Collections.unmodifiableMap(catalog);
    }

    public static final class Decision {
        private final RiskClass riskClass;
        private final Outcome outcome;
        private final Reason reason;
        private final SafetyVehicleStateSnapshot state;

        private Decision(
                RiskClass riskClass,
                Outcome outcome,
                Reason reason,
                SafetyVehicleStateSnapshot state) {
            this.riskClass = Objects.requireNonNull(riskClass, "riskClass");
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            this.reason = Objects.requireNonNull(reason, "reason");
            this.state = state;
        }

        static Decision allowed(
                RiskClass riskClass,
                Reason reason,
                SafetyVehicleStateSnapshot state) {
            return new Decision(riskClass, Outcome.ALLOW_POLICY_ONLY, reason, state);
        }

        static Decision approvalRequired(
                RiskClass riskClass,
                SafetyVehicleStateSnapshot state) {
            return new Decision(
                    riskClass,
                    Outcome.APPROVAL_REQUIRED,
                    Reason.HIGH_RISK_APPROVAL_REQUIRED,
                    state);
        }

        static Decision denied(
                RiskClass riskClass,
                Reason reason,
                SafetyVehicleStateSnapshot state) {
            return new Decision(riskClass, Outcome.DENY, reason, state);
        }

        public RiskClass getRiskClass() {
            return riskClass;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public Reason getReason() {
            return reason;
        }

        public SafetyVehicleStateSnapshot getState() {
            return state;
        }

        public boolean isDispatchAllowed() {
            return false;
        }
    }
}
