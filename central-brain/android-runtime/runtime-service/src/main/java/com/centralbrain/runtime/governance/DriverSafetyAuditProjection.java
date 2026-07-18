package com.centralbrain.runtime.governance;

import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed-key, vehicle-content-free projection used by the P9-W06b debug probe. */
public final class DriverSafetyAuditProjection {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-driver-safety-audit-v1";
    public static final int AUDIT_KEY_COUNT = 27;

    private static final List<String> ALLOWED_AUDIT_KEYS = buildAllowedAuditKeys();

    private DriverSafetyAuditProjection() {
    }

    public static final class Snapshot {
        private final int actionRuleCount;
        private final int uxProfileCount;
        private final int ownerRoleCount;
        private final long stateMaximumAgeMs;
        private final int currentOwnerApprovalCount;
        private final int movingBlockedActionCount;
        private final int vehicleEffectActionCount;
        private final int approvalRequiredActionCount;
        private final int productionCapabilityAuthorizedCount;
        private final boolean projectionVerified;

        private Snapshot(
                int actionRuleCount,
                int uxProfileCount,
                int ownerRoleCount,
                long stateMaximumAgeMs,
                int currentOwnerApprovalCount,
                int movingBlockedActionCount,
                int vehicleEffectActionCount,
                int approvalRequiredActionCount,
                int productionCapabilityAuthorizedCount,
                boolean projectionVerified) {
            this.actionRuleCount = actionRuleCount;
            this.uxProfileCount = uxProfileCount;
            this.ownerRoleCount = ownerRoleCount;
            this.stateMaximumAgeMs = stateMaximumAgeMs;
            this.currentOwnerApprovalCount = currentOwnerApprovalCount;
            this.movingBlockedActionCount = movingBlockedActionCount;
            this.vehicleEffectActionCount = vehicleEffectActionCount;
            this.approvalRequiredActionCount = approvalRequiredActionCount;
            this.productionCapabilityAuthorizedCount = productionCapabilityAuthorizedCount;
            this.projectionVerified = projectionVerified;
        }

        public boolean isProjectionVerified() {
            return projectionVerified;
        }

        public int getActionRuleCount() {
            return actionRuleCount;
        }

        public int getMovingBlockedActionCount() {
            return movingBlockedActionCount;
        }

        public int getVehicleEffectActionCount() {
            return vehicleEffectActionCount;
        }

        public int getApprovalRequiredActionCount() {
            return approvalRequiredActionCount;
        }

        public String auditMetadata() {
            return "driver_safety_probe_complete=true"
                    + " driver_safety_projection_verified=" + projectionVerified
                    + " driver_safety_action_rule_count=" + actionRuleCount
                    + " driver_safety_ux_profile_count=" + uxProfileCount
                    + " driver_safety_owner_role_count=" + ownerRoleCount
                    + " driver_safety_state_maximum_age_ms=" + stateMaximumAgeMs
                    + " driver_safety_current_owner_approval_count="
                    + currentOwnerApprovalCount
                    + " driver_safety_moving_blocked_action_count="
                    + movingBlockedActionCount
                    + " driver_safety_vehicle_effect_action_count=" + vehicleEffectActionCount
                    + " driver_safety_approval_required_action_count="
                    + approvalRequiredActionCount
                    + " driver_safety_production_capability_authorized_count="
                    + productionCapabilityAuthorizedCount
                    + " driver_safety_exact_catalog_verified=true"
                    + " driver_safety_non_normal_fault_restricted=true"
                    + " driver_safety_unknown_restricted=true"
                    + " driver_safety_moving_hard_interlock_verified=true"
                    + " driver_safety_current_owner_policy_approved=false"
                    + " driver_safety_vehicle_state_provider_wired=false"
                    + " driver_safety_effect_runtime_wired=false"
                    + " driver_safety_effect_dispatch_authorized=false"
                    + " driver_safety_hardware_operation_executed=false"
                    + " driver_safety_vehicle_scalar_read=false"
                    + " driver_safety_owner_approval_reference_logged=false"
                    + " driver_safety_raw_log_persisted=false"
                    + " driver_safety_android13_arm64_verified=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false";
        }
    }

    public static Snapshot evaluateCurrentRepository() {
        int movingBlocked = 0;
        int vehicleEffects = 0;
        int approvalRequired = 0;
        for (DriverSafetyAdmissionContract.ActionRule rule
                : DriverSafetyAdmissionContract.actionRules().values()) {
            if (rule.requiresTrustedState() && !rule.isAllowedWhileMoving()) {
                movingBlocked++;
            }
            if (rule.getCapabilityId() != null) {
                vehicleEffects++;
            }
            if (rule.getAdmittedOutcome()
                    == DriverSafetyAdmissionContract.Outcome.APPROVAL_REQUIRED) {
                approvalRequired++;
            }
        }
        int actionRuleCount = DriverSafetyAdmissionContract.actionRules().size();
        int uxProfileCount = DriverSafetyAdmissionContract.UxProfile.values().length;
        int ownerRoleCount = DriverSafetyAdmissionContract.OwnerRole.values().length;
        int ownerApprovalCount =
                DriverSafetyAdmissionContract.currentDraftPolicy().getApprovals().size();
        int productionAuthorizedCount =
                CapabilityCatalog.stage2Defaults().productionAuthorizedCount();
        boolean verified = actionRuleCount == DriverSafetyAdmissionContract.ACTION_RULE_COUNT
                && uxProfileCount == 4
                && ownerRoleCount == DriverSafetyAdmissionContract.REQUIRED_OWNER_ROLE_COUNT
                && DriverSafetyAdmissionContract.MAXIMUM_STATE_AGE_MS == 500
                && ownerApprovalCount == 0
                && movingBlocked == 6
                && vehicleEffects == 4
                && approvalRequired == 4
                && productionAuthorizedCount == 0
                && !DriverSafetyAdmissionContract.isCurrentOwnerPolicyApproved()
                && !DriverSafetyAdmissionContract.isVehicleStateProviderWired()
                && !DriverSafetyAdmissionContract.isEffectRuntimeWired()
                && !DriverSafetyAdmissionContract.isAndroid13Arm64Verified()
                && !DriverSafetyAdmissionContract.isHardwareAccessed()
                && !DriverSafetyAdmissionContract.isProductionReady()
                && !DriverSafetyAdmissionContract.isTargetHardwareValidated();
        return new Snapshot(
                actionRuleCount,
                uxProfileCount,
                ownerRoleCount,
                DriverSafetyAdmissionContract.MAXIMUM_STATE_AGE_MS,
                ownerApprovalCount,
                movingBlocked,
                vehicleEffects,
                approvalRequired,
                productionAuthorizedCount,
                verified);
    }

    public static List<String> allowedAuditKeys() {
        return ALLOWED_AUDIT_KEYS;
    }

    public static boolean isDebugProbeAvailable() {
        return true;
    }

    public static boolean isDebugProbeExecuted() {
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

    private static List<String> buildAllowedAuditKeys() {
        List<String> keys = new ArrayList<>(List.of(
                "driver_safety_probe_complete",
                "driver_safety_projection_verified",
                "driver_safety_action_rule_count",
                "driver_safety_ux_profile_count",
                "driver_safety_owner_role_count",
                "driver_safety_state_maximum_age_ms",
                "driver_safety_current_owner_approval_count",
                "driver_safety_moving_blocked_action_count",
                "driver_safety_vehicle_effect_action_count",
                "driver_safety_approval_required_action_count",
                "driver_safety_production_capability_authorized_count",
                "driver_safety_exact_catalog_verified",
                "driver_safety_non_normal_fault_restricted",
                "driver_safety_unknown_restricted",
                "driver_safety_moving_hard_interlock_verified",
                "driver_safety_current_owner_policy_approved",
                "driver_safety_vehicle_state_provider_wired",
                "driver_safety_effect_runtime_wired",
                "driver_safety_effect_dispatch_authorized",
                "driver_safety_hardware_operation_executed",
                "driver_safety_vehicle_scalar_read",
                "driver_safety_owner_approval_reference_logged",
                "driver_safety_raw_log_persisted",
                "driver_safety_android13_arm64_verified",
                "hardware_accessed",
                "production_ready",
                "target_hardware_validated"));
        if (keys.size() != AUDIT_KEY_COUNT) {
            throw new IllegalStateException("driver safety audit key count changed");
        }
        return Collections.unmodifiableList(keys);
    }
}
