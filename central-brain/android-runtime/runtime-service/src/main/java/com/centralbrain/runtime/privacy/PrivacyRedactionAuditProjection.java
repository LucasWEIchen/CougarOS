package com.centralbrain.runtime.privacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed-key, content-free projection used by the P9-W04c debug probe. */
public final class PrivacyRedactionAuditProjection {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-privacy-redaction-audit-v1";
    public static final int AUDIT_KEY_COUNT = 21;

    private static final List<String> ALLOWED_AUDIT_KEYS = buildAllowedAuditKeys();

    private PrivacyRedactionAuditProjection() {
    }

    public static final class Snapshot {
        private final String inventoryDigest;
        private final String policyBodyDigest;
        private final int surfaceCount;
        private final int unresolvedSurfaceCount;
        private final int admissionCodeCount;
        private final int operationCodeCount;
        private final boolean projectionVerified;

        private Snapshot(
                String inventoryDigest,
                String policyBodyDigest,
                int surfaceCount,
                int unresolvedSurfaceCount,
                int admissionCodeCount,
                int operationCodeCount,
                boolean projectionVerified) {
            this.inventoryDigest = inventoryDigest;
            this.policyBodyDigest = policyBodyDigest;
            this.surfaceCount = surfaceCount;
            this.unresolvedSurfaceCount = unresolvedSurfaceCount;
            this.admissionCodeCount = admissionCodeCount;
            this.operationCodeCount = operationCodeCount;
            this.projectionVerified = projectionVerified;
        }

        public boolean isProjectionVerified() {
            return projectionVerified;
        }

        public String auditMetadata() {
            return "privacy_redaction_probe_complete=true"
                    + " privacy_redacted_audit_projection_verified=" + projectionVerified
                    + " privacy_inventory_digest=" + inventoryDigest
                    + " privacy_policy_body_digest=" + policyBodyDigest
                    + " privacy_surface_count=" + surfaceCount
                    + " privacy_unresolved_surface_count=" + unresolvedSurfaceCount
                    + " privacy_admission_code_count=" + admissionCodeCount
                    + " privacy_operation_code_count=" + operationCodeCount
                    + " privacy_current_policy_admitted=false"
                    + " privacy_raw_user_text_logged=false"
                    + " privacy_raw_model_output_logged=false"
                    + " privacy_raw_vehicle_payload_logged=false"
                    + " privacy_location_logged=false"
                    + " privacy_owner_reference_logged=false"
                    + " privacy_authorization_digest_logged=false"
                    + " privacy_consent_digest_logged=false"
                    + " privacy_repository_mutation_wired=false"
                    + " privacy_runtime_lifecycle_wiring_complete=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false";
        }
    }

    public static Snapshot evaluateCurrentDraft() {
        PrivacyLifecyclePolicyAdmission.PolicyProfile draft =
                PrivacyLifecyclePolicyAdmission.currentDraft();
        PrivacyLifecyclePolicyAdmission.AdmissionDecision admission =
                PrivacyLifecyclePolicyAdmission.evaluate(draft, List.of());
        PrivacyLifecyclePolicyAdmission.OperationDecision operation =
                PrivacyLifecyclePolicyAdmission.evaluateOperation(
                        draft,
                        List.of(),
                        new PrivacyLifecyclePolicyAdmission.OperationRequest(
                                "memory.profile",
                                PrivacyLifecyclePolicyAdmission.OperationType.EXPORT,
                                null,
                                null,
                                new PrivacyLifecyclePolicyAdmission.LifecycleStateSnapshot(
                                        0, 0, 0, 0)));

        int unresolved = 0;
        for (PrivacyLifecyclePolicyAdmission.SurfacePolicy policy
                : draft.getSurfacePolicies()) {
            if (policy.getState()
                    == PrivacyLifecyclePolicyAdmission.RuleState.OWNER_INPUT_REQUIRED) {
                unresolved++;
            }
        }
        boolean verified = PrivacyDataInventoryContract.surfaces().size() == 12
                && unresolved == 2
                && PrivacyDataInventoryContract.inventoryDigest().matches("[0-9a-f]{64}")
                && draft.getBodyDigest().matches("[0-9a-f]{64}")
                && !admission.isAdmitted()
                && admission.getCodes().contains(
                        PrivacyLifecyclePolicyAdmission.AdmissionCode
                                .SURFACE_POLICY_UNRESOLVED)
                && admission.getCodes().contains(
                        PrivacyLifecyclePolicyAdmission.AdmissionCode
                                .RETENTION_CEILING_INVALID)
                && admission.getCodes().contains(
                        PrivacyLifecyclePolicyAdmission.AdmissionCode
                                .OWNER_APPROVAL_MISSING)
                && !operation.isAdmitted()
                && operation.getCodes().equals(List.of(
                        PrivacyLifecyclePolicyAdmission.OperationCode.POLICY_NOT_ADMITTED))
                && !admission.grantsRepositoryMutationAuthority()
                && !admission.grantsRuntimeAuthority()
                && !operation.dataWasMutated()
                && !operation.dataWasExported()
                && !PrivacyDataInventoryContract.isRawUserTextPersisted()
                && !PrivacyDataInventoryContract.isRawModelOutputPersisted()
                && !PrivacyDataInventoryContract.isRawVehiclePayloadPersisted()
                && !PrivacyDataInventoryContract.isLocationPersisted()
                && !PrivacyDataInventoryContract.isAuditContentLogged();

        return new Snapshot(
                PrivacyDataInventoryContract.inventoryDigest(),
                draft.getBodyDigest(),
                PrivacyDataInventoryContract.surfaces().size(),
                unresolved,
                admission.getCodes().size(),
                operation.getCodes().size(),
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

    public static boolean isRuntimeWired() {
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
                "privacy_redaction_probe_complete",
                "privacy_redacted_audit_projection_verified",
                "privacy_inventory_digest",
                "privacy_policy_body_digest",
                "privacy_surface_count",
                "privacy_unresolved_surface_count",
                "privacy_admission_code_count",
                "privacy_operation_code_count",
                "privacy_current_policy_admitted",
                "privacy_raw_user_text_logged",
                "privacy_raw_model_output_logged",
                "privacy_raw_vehicle_payload_logged",
                "privacy_location_logged",
                "privacy_owner_reference_logged",
                "privacy_authorization_digest_logged",
                "privacy_consent_digest_logged",
                "privacy_repository_mutation_wired",
                "privacy_runtime_lifecycle_wiring_complete",
                "hardware_accessed",
                "production_ready",
                "target_hardware_validated"));
        if (keys.size() != AUDIT_KEY_COUNT) {
            throw new IllegalStateException("privacy audit key count changed");
        }
        return Collections.unmodifiableList(keys);
    }
}
