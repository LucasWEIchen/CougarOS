package com.centralbrain.runtime.governance;

import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.MotionState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SafetyState;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fail-closed P9-W06a driver-distraction and vehicle-safety admission contract. */
public final class DriverSafetyAdmissionContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-driver-safety-admission-v1";
    public static final long MAXIMUM_STATE_AGE_MS = 500;
    public static final int REQUIRED_OWNER_ROLE_COUNT = 3;
    public static final int ACTION_RULE_COUNT = 12;

    public static final String ACTION_SCENE_INTENT_SUBMIT = "scene.intent.submit";
    public static final String ACTION_SESSION_CANCEL = "session.cancel";
    public static final String ACTION_UI_LONG_TEXT_DISPLAY = "ui.long_text.display";
    public static final String ACTION_UI_PARAMETER_EDIT = "ui.parameter.edit";
    public static final String ACTION_DRIVER_SEAT_HEATING_SET =
            "vehicle.seat.driver.heating.set";
    public static final String ACTION_DRIVER_SEAT_VENTILATION_SET =
            "vehicle.seat.driver.ventilation.set";
    public static final String ACTION_DRIVER_SEAT_RECLINE_SET =
            "vehicle.seat.driver.recline.set";

    private static final String DIGEST_PATTERN = "[0-9a-f]{64}";
    private static final Map<String, ActionRule> ACTION_RULES = createActionRules();
    private static final String CATALOG_DIGEST = digest(canonicalCatalog());

    private DriverSafetyAdmissionContract() {
    }

    public enum ActionClass {
        READ_ONLY_UI,
        LOW_RISK_UI,
        DRIVER_DISTRACTION_UI,
        COMFORT_EFFECT,
        SAFETY_CRITICAL_EFFECT,
        ENGINEERING_WRITE,
        OTA
    }

    public enum UxProfile {
        PARKED_FULL,
        MOVING_RESTRICTED,
        UNKNOWN_RESTRICTED,
        FAULT_RESTRICTED
    }

    public enum Outcome {
        ALLOW_UI_ONLY,
        ALLOW_POLICY_ONLY,
        APPROVAL_REQUIRED,
        DENY
    }

    public enum DecisionCode {
        ADMITTED_UI_ONLY,
        ADMITTED_POLICY_ONLY,
        APPROVAL_REQUIRED,
        UNKNOWN_ACTION,
        STATE_MISSING,
        STATE_TIME_INVALID,
        STATE_STALE,
        STATE_SOURCE_UNTRUSTED,
        SAFETY_STATE_RESTRICTED,
        MOTION_UNKNOWN,
        MOVING_HARD_INTERLOCK,
        DRIVER_UNAVAILABLE,
        OWNER_POLICY_MISSING,
        OWNER_POLICY_INVALID,
        CAPABILITY_EVIDENCE_MISSING,
        CAPABILITY_EVIDENCE_MISMATCH,
        CAPABILITY_UNAVAILABLE,
        CAPABILITY_UNAUTHORIZED,
        READBACK_UNAVAILABLE,
        ACTIVATION_EVIDENCE_MISSING
    }

    public enum OwnerRole {
        FUNCTIONAL_SAFETY,
        DRIVER_DISTRACTION_HMI,
        VEHICLE_INTEGRATION
    }

    public static final class ActionRule {
        private final String actionId;
        private final ActionClass actionClass;
        private final String capabilityId;
        private final boolean requiresTrustedState;
        private final boolean requiresOwnerPolicy;
        private final boolean allowedWhileMoving;
        private final boolean requiresDriverAvailable;
        private final boolean requiresReadback;
        private final Outcome admittedOutcome;

        private ActionRule(
                String actionId,
                ActionClass actionClass,
                String capabilityId,
                boolean requiresTrustedState,
                boolean requiresOwnerPolicy,
                boolean allowedWhileMoving,
                boolean requiresDriverAvailable,
                boolean requiresReadback,
                Outcome admittedOutcome) {
            this.actionId = actionId;
            this.actionClass = actionClass;
            this.capabilityId = capabilityId;
            this.requiresTrustedState = requiresTrustedState;
            this.requiresOwnerPolicy = requiresOwnerPolicy;
            this.allowedWhileMoving = allowedWhileMoving;
            this.requiresDriverAvailable = requiresDriverAvailable;
            this.requiresReadback = requiresReadback;
            this.admittedOutcome = admittedOutcome;
        }

        public String getActionId() {
            return actionId;
        }

        public ActionClass getActionClass() {
            return actionClass;
        }

        public String getCapabilityId() {
            return capabilityId;
        }

        public boolean requiresTrustedState() {
            return requiresTrustedState;
        }

        public boolean requiresOwnerPolicy() {
            return requiresOwnerPolicy;
        }

        public boolean isAllowedWhileMoving() {
            return allowedWhileMoving;
        }

        public boolean requiresDriverAvailable() {
            return requiresDriverAvailable;
        }

        public boolean requiresReadback() {
            return requiresReadback;
        }

        public Outcome getAdmittedOutcome() {
            return admittedOutcome;
        }

        private String canonicalForm() {
            return actionId + '|' + actionClass.name() + '|'
                    + nullToEmpty(capabilityId) + '|' + requiresTrustedState + '|'
                    + requiresOwnerPolicy + '|' + allowedWhileMoving + '|'
                    + requiresDriverAvailable + '|' + requiresReadback + '|'
                    + admittedOutcome.name();
        }
    }

    public static final class OwnerApproval {
        private final OwnerRole role;
        private final String profileId;
        private final int schemaVersion;
        private final String catalogDigest;
        private final String approvalDigest;

        public OwnerApproval(
                OwnerRole role,
                String profileId,
                int schemaVersion,
                String catalogDigest,
                String approvalDigest) {
            this.role = role;
            this.profileId = profileId;
            this.schemaVersion = schemaVersion;
            this.catalogDigest = catalogDigest;
            this.approvalDigest = approvalDigest;
        }

        public OwnerRole getRole() {
            return role;
        }

        private boolean isValid() {
            return role != null
                    && PROFILE_ID.equals(profileId)
                    && schemaVersion == SCHEMA_VERSION
                    && CATALOG_DIGEST.equals(catalogDigest)
                    && isDigest(approvalDigest);
        }

        private String canonicalForm() {
            return String.valueOf(role) + '|' + nullToEmpty(profileId) + '|'
                    + schemaVersion + '|' + nullToEmpty(catalogDigest) + '|'
                    + nullToEmpty(approvalDigest);
        }
    }

    public static final class PolicyProfile {
        private final String profileId;
        private final int schemaVersion;
        private final String catalogDigest;
        private final List<OwnerApproval> approvals;

        public PolicyProfile(
                String profileId,
                int schemaVersion,
                String catalogDigest,
                List<OwnerApproval> approvals) {
            this.profileId = profileId;
            this.schemaVersion = schemaVersion;
            this.catalogDigest = catalogDigest;
            this.approvals = approvals == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(approvals));
        }

        public List<OwnerApproval> getApprovals() {
            return approvals;
        }

        private String canonicalForm() {
            StringBuilder builder = new StringBuilder()
                    .append(nullToEmpty(profileId)).append('|')
                    .append(schemaVersion).append('|')
                    .append(nullToEmpty(catalogDigest));
            for (OwnerApproval approval : approvals) {
                builder.append('|').append(approval == null ? "null" : approval.canonicalForm());
            }
            return builder.toString();
        }
    }

    public static final class CapabilityEvidence {
        private final String capabilityId;
        private final boolean productionAvailable;
        private final boolean productionAuthorized;
        private final boolean readbackAvailable;
        private final String activationEvidenceDigest;

        public CapabilityEvidence(
                String capabilityId,
                boolean productionAvailable,
                boolean productionAuthorized,
                boolean readbackAvailable,
                String activationEvidenceDigest) {
            this.capabilityId = capabilityId;
            this.productionAvailable = productionAvailable;
            this.productionAuthorized = productionAuthorized;
            this.readbackAvailable = readbackAvailable;
            this.activationEvidenceDigest = activationEvidenceDigest;
        }

        private String canonicalForm() {
            return nullToEmpty(capabilityId) + '|' + productionAvailable + '|'
                    + productionAuthorized + '|' + readbackAvailable + '|'
                    + nullToEmpty(activationEvidenceDigest);
        }
    }

    public static final class AdmissionRequest {
        private final String actionId;
        private final SafetyVehicleStateSnapshot state;
        private final long observedAtElapsedRealtimeMs;
        private final PolicyProfile policyProfile;
        private final CapabilityEvidence capabilityEvidence;

        public AdmissionRequest(
                String actionId,
                SafetyVehicleStateSnapshot state,
                long observedAtElapsedRealtimeMs,
                PolicyProfile policyProfile,
                CapabilityEvidence capabilityEvidence) {
            this.actionId = actionId;
            this.state = state;
            this.observedAtElapsedRealtimeMs = observedAtElapsedRealtimeMs;
            this.policyProfile = policyProfile;
            this.capabilityEvidence = capabilityEvidence;
        }
    }

    public static final class Decision {
        private final String actionId;
        private final ActionClass actionClass;
        private final UxProfile uxProfile;
        private final Outcome outcome;
        private final DecisionCode code;
        private final String decisionDigest;

        private Decision(
                String actionId,
                ActionClass actionClass,
                UxProfile uxProfile,
                Outcome outcome,
                DecisionCode code,
                String decisionDigest) {
            this.actionId = actionId;
            this.actionClass = actionClass;
            this.uxProfile = uxProfile;
            this.outcome = outcome;
            this.code = code;
            this.decisionDigest = decisionDigest;
        }

        public String getActionId() {
            return actionId;
        }

        public ActionClass getActionClass() {
            return actionClass;
        }

        public UxProfile getUxProfile() {
            return uxProfile;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public DecisionCode getCode() {
            return code;
        }

        public String getDecisionDigest() {
            return decisionDigest;
        }

        public boolean isEffectDispatchAuthorized() {
            return false;
        }

        public boolean isHardwareOperationExecuted() {
            return false;
        }
    }

    public static Decision evaluate(AdmissionRequest request) {
        Objects.requireNonNull(request, "request");
        ActionRule rule = ACTION_RULES.get(request.actionId);
        UxProfile uxProfile = projectUxProfile(
                request.state,
                request.observedAtElapsedRealtimeMs);
        if (rule == null) {
            return denied(request, null, uxProfile, DecisionCode.UNKNOWN_ACTION);
        }
        if (!rule.requiresTrustedState) {
            return decision(request, rule, uxProfile, Outcome.ALLOW_UI_ONLY,
                    DecisionCode.ADMITTED_UI_ONLY);
        }
        DecisionCode stateFailure = validateState(
                request.state,
                request.observedAtElapsedRealtimeMs);
        if (stateFailure != null) {
            return denied(request, rule, uxProfile, stateFailure);
        }
        if (request.state.getMotionState() == MotionState.MOVING
                && !rule.allowedWhileMoving) {
            return denied(request, rule, uxProfile, DecisionCode.MOVING_HARD_INTERLOCK);
        }
        if (rule.requiresDriverAvailable && !request.state.isDriverAvailable()) {
            return denied(request, rule, uxProfile, DecisionCode.DRIVER_UNAVAILABLE);
        }
        if (rule.requiresOwnerPolicy) {
            DecisionCode policyFailure = validatePolicy(request.policyProfile);
            if (policyFailure != null) {
                return denied(request, rule, uxProfile, policyFailure);
            }
        }
        if (rule.capabilityId != null) {
            DecisionCode capabilityFailure = validateCapability(
                    rule,
                    request.capabilityEvidence);
            if (capabilityFailure != null) {
                return denied(request, rule, uxProfile, capabilityFailure);
            }
        }
        DecisionCode admittedCode = rule.admittedOutcome == Outcome.APPROVAL_REQUIRED
                ? DecisionCode.APPROVAL_REQUIRED
                : rule.admittedOutcome == Outcome.ALLOW_UI_ONLY
                        ? DecisionCode.ADMITTED_UI_ONLY
                        : DecisionCode.ADMITTED_POLICY_ONLY;
        return decision(request, rule, uxProfile, rule.admittedOutcome, admittedCode);
    }

    public static Map<String, ActionRule> actionRules() {
        return ACTION_RULES;
    }

    public static String catalogDigest() {
        return CATALOG_DIGEST;
    }

    public static PolicyProfile currentDraftPolicy() {
        return new PolicyProfile(PROFILE_ID, SCHEMA_VERSION, CATALOG_DIGEST, List.of());
    }

    public static UxProfile projectUxProfile(
            SafetyVehicleStateSnapshot state,
            long observedAtElapsedRealtimeMs) {
        if (state == null
                || observedAtElapsedRealtimeMs < 0
                || state.getCapturedAtElapsedRealtimeMs() > observedAtElapsedRealtimeMs
                || observedAtElapsedRealtimeMs - state.getCapturedAtElapsedRealtimeMs()
                        > MAXIMUM_STATE_AGE_MS
                || !state.isProductionTrusted()
                || state.getMotionState() == MotionState.UNKNOWN) {
            return UxProfile.UNKNOWN_RESTRICTED;
        }
        if (state.getSafetyState() != SafetyState.NORMAL) {
            return UxProfile.FAULT_RESTRICTED;
        }
        return state.getMotionState() == MotionState.MOVING
                ? UxProfile.MOVING_RESTRICTED
                : UxProfile.PARKED_FULL;
    }

    public static boolean isCurrentOwnerPolicyApproved() {
        return false;
    }

    public static boolean isVehicleStateProviderWired() {
        return false;
    }

    public static boolean isEffectRuntimeWired() {
        return false;
    }

    public static boolean isAndroid13Arm64Verified() {
        return false;
    }

    public static boolean isHardwareAccessed() {
        return false;
    }

    public static boolean isProductionReady() {
        return false;
    }

    public static boolean isTargetHardwareValidated() {
        return false;
    }

    private static DecisionCode validateState(
            SafetyVehicleStateSnapshot state,
            long observedAtElapsedRealtimeMs) {
        if (state == null) {
            return DecisionCode.STATE_MISSING;
        }
        if (observedAtElapsedRealtimeMs < 0
                || state.getCapturedAtElapsedRealtimeMs() > observedAtElapsedRealtimeMs) {
            return DecisionCode.STATE_TIME_INVALID;
        }
        if (observedAtElapsedRealtimeMs - state.getCapturedAtElapsedRealtimeMs()
                > MAXIMUM_STATE_AGE_MS) {
            return DecisionCode.STATE_STALE;
        }
        if (!state.isProductionTrusted()) {
            return DecisionCode.STATE_SOURCE_UNTRUSTED;
        }
        if (state.getSafetyState() != SafetyState.NORMAL) {
            return DecisionCode.SAFETY_STATE_RESTRICTED;
        }
        if (state.getMotionState() == MotionState.UNKNOWN) {
            return DecisionCode.MOTION_UNKNOWN;
        }
        return null;
    }

    private static DecisionCode validatePolicy(PolicyProfile policyProfile) {
        if (policyProfile == null || policyProfile.approvals.size() < REQUIRED_OWNER_ROLE_COUNT) {
            return DecisionCode.OWNER_POLICY_MISSING;
        }
        if (!PROFILE_ID.equals(policyProfile.profileId)
                || policyProfile.schemaVersion != SCHEMA_VERSION
                || !CATALOG_DIGEST.equals(policyProfile.catalogDigest)
                || policyProfile.approvals.size() != REQUIRED_OWNER_ROLE_COUNT) {
            return DecisionCode.OWNER_POLICY_INVALID;
        }
        Set<OwnerRole> roles = EnumSet.noneOf(OwnerRole.class);
        Set<String> approvalDigests = new java.util.HashSet<>();
        for (OwnerApproval approval : policyProfile.approvals) {
            if (approval == null
                    || !approval.isValid()
                    || !roles.add(approval.role)
                    || !approvalDigests.add(approval.approvalDigest)) {
                return DecisionCode.OWNER_POLICY_INVALID;
            }
        }
        return roles.equals(EnumSet.allOf(OwnerRole.class))
                ? null
                : DecisionCode.OWNER_POLICY_INVALID;
    }

    private static DecisionCode validateCapability(
            ActionRule rule,
            CapabilityEvidence evidence) {
        if (evidence == null) {
            return DecisionCode.CAPABILITY_EVIDENCE_MISSING;
        }
        if (!rule.capabilityId.equals(evidence.capabilityId)) {
            return DecisionCode.CAPABILITY_EVIDENCE_MISMATCH;
        }
        if (!evidence.productionAvailable) {
            return DecisionCode.CAPABILITY_UNAVAILABLE;
        }
        if (!evidence.productionAuthorized) {
            return DecisionCode.CAPABILITY_UNAUTHORIZED;
        }
        if (rule.requiresReadback && !evidence.readbackAvailable) {
            return DecisionCode.READBACK_UNAVAILABLE;
        }
        return isDigest(evidence.activationEvidenceDigest)
                ? null
                : DecisionCode.ACTIVATION_EVIDENCE_MISSING;
    }

    private static Decision denied(
            AdmissionRequest request,
            ActionRule rule,
            UxProfile uxProfile,
            DecisionCode code) {
        return decision(request, rule, uxProfile, Outcome.DENY, code);
    }

    private static Decision decision(
            AdmissionRequest request,
            ActionRule rule,
            UxProfile uxProfile,
            Outcome outcome,
            DecisionCode code) {
        String canonical = PROFILE_ID + '|' + SCHEMA_VERSION + '|' + CATALOG_DIGEST + '|'
                + nullToEmpty(request.actionId) + '|'
                + (rule == null ? "UNKNOWN" : rule.canonicalForm()) + '|'
                + uxProfile.name() + '|' + outcome.name() + '|' + code.name() + '|'
                + canonicalState(request.state) + '|' + request.observedAtElapsedRealtimeMs + '|'
                + (request.policyProfile == null
                        ? "null"
                        : request.policyProfile.canonicalForm()) + '|'
                + (request.capabilityEvidence == null
                        ? "null"
                        : request.capabilityEvidence.canonicalForm());
        return new Decision(
                request.actionId,
                rule == null ? null : rule.actionClass,
                uxProfile,
                outcome,
                code,
                digest(canonical));
    }

    private static String canonicalState(SafetyVehicleStateSnapshot state) {
        if (state == null) {
            return "null";
        }
        return state.getSourceId() + '|' + state.getRevision() + '|'
                + state.getCapturedAtElapsedRealtimeMs() + '|'
                + state.getSafetyState().name() + '|' + state.getMotionState().name() + '|'
                + state.isDriverAvailable() + '|' + state.getSourceAssurance().name() + '|'
                + state.isHardwareBacked();
    }

    private static Map<String, ActionRule> createActionRules() {
        LinkedHashMap<String, ActionRule> rules = new LinkedHashMap<>();
        add(rules, rule(ACTION_VEHICLE_STATE_READ(), ActionClass.READ_ONLY_UI, null,
                false, false, true, false, false, Outcome.ALLOW_UI_ONLY));
        add(rules, rule(ACTION_SCENE_INTENT_SUBMIT, ActionClass.LOW_RISK_UI, null,
                false, false, true, false, false, Outcome.ALLOW_UI_ONLY));
        add(rules, rule(ACTION_SESSION_CANCEL, ActionClass.LOW_RISK_UI, null,
                false, false, true, false, false, Outcome.ALLOW_UI_ONLY));
        add(rules, rule(ACTION_UI_LONG_TEXT_DISPLAY, ActionClass.DRIVER_DISTRACTION_UI, null,
                true, true, false, true, false, Outcome.ALLOW_UI_ONLY));
        add(rules, rule(ACTION_UI_PARAMETER_EDIT, ActionClass.DRIVER_DISTRACTION_UI, null,
                true, true, false, true, false, Outcome.ALLOW_UI_ONLY));
        add(rules, rule(ActionGovernancePolicy.ACTION_DRIVER_VIDEO_PLAY,
                ActionClass.DRIVER_DISTRACTION_UI, null,
                true, true, false, true, false, Outcome.APPROVAL_REQUIRED));
        add(rules, rule(ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                ActionClass.COMFORT_EFFECT,
                VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE.getCanonicalId(),
                true, true, true, true, true, Outcome.ALLOW_POLICY_ONLY));
        add(rules, rule(ACTION_DRIVER_SEAT_HEATING_SET, ActionClass.COMFORT_EFFECT,
                VehicleCapability.CapabilityId.SEAT_HEATING_LEVEL.getCanonicalId(),
                true, true, true, true, true, Outcome.ALLOW_POLICY_ONLY));
        add(rules, rule(ACTION_DRIVER_SEAT_VENTILATION_SET, ActionClass.COMFORT_EFFECT,
                VehicleCapability.CapabilityId.SEAT_VENTILATION_LEVEL.getCanonicalId(),
                true, true, true, true, true, Outcome.ALLOW_POLICY_ONLY));
        add(rules, rule(ACTION_DRIVER_SEAT_RECLINE_SET, ActionClass.SAFETY_CRITICAL_EFFECT,
                VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE.getCanonicalId(),
                true, true, false, true, true, Outcome.APPROVAL_REQUIRED));
        add(rules, rule(ActionGovernancePolicy.ACTION_DIAGNOSTIC_WRITE,
                ActionClass.ENGINEERING_WRITE, null,
                true, true, false, true, false, Outcome.APPROVAL_REQUIRED));
        add(rules, rule(ActionGovernancePolicy.ACTION_OTA_INSTALL, ActionClass.OTA, null,
                true, true, false, true, false, Outcome.APPROVAL_REQUIRED));
        if (rules.size() != ACTION_RULE_COUNT) {
            throw new IllegalStateException("driver safety action catalog count mismatch");
        }
        return Collections.unmodifiableMap(rules);
    }

    private static String ACTION_VEHICLE_STATE_READ() {
        return ActionGovernancePolicy.ACTION_VEHICLE_STATE_READ;
    }

    private static ActionRule rule(
            String actionId,
            ActionClass actionClass,
            String capabilityId,
            boolean requiresTrustedState,
            boolean requiresOwnerPolicy,
            boolean allowedWhileMoving,
            boolean requiresDriverAvailable,
            boolean requiresReadback,
            Outcome admittedOutcome) {
        return new ActionRule(
                actionId,
                actionClass,
                capabilityId,
                requiresTrustedState,
                requiresOwnerPolicy,
                allowedWhileMoving,
                requiresDriverAvailable,
                requiresReadback,
                admittedOutcome);
    }

    private static void add(Map<String, ActionRule> rules, ActionRule rule) {
        if (rules.put(rule.actionId, rule) != null) {
            throw new IllegalStateException("duplicate driver safety action rule");
        }
    }

    private static String canonicalCatalog() {
        StringBuilder builder = new StringBuilder()
                .append(PROFILE_ID).append('|').append(SCHEMA_VERSION);
        for (ActionRule rule : ACTION_RULES.values()) {
            builder.append('|').append(rule.canonicalForm());
        }
        return builder.toString();
    }

    private static boolean isDigest(String value) {
        return value != null && value.matches(DIGEST_PATTERN);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String digest(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
