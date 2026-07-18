package com.centralbrain.runtime.privacy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Fail-closed P9-W04b admission for owner-supplied privacy lifecycle policy. */
public final class PrivacyLifecyclePolicyAdmission {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-privacy-policy-admission-v1";
    public static final String CURRENT_POLICY_ID = "cougaros-privacy-draft";
    public static final String CURRENT_POLICY_VERSION = "0.1.0-draft";
    public static final int REQUIRED_SURFACE_COUNT = 12;
    public static final int REQUIRED_OWNER_APPROVAL_COUNT = 3;

    private static final String DIGEST_PATTERN = "[0-9a-f]{64}";
    private static final List<String> REQUIRED_SURFACE_IDS = buildRequiredSurfaceIds();
    private static final PolicyProfile CURRENT_DRAFT = buildCurrentDraft();

    private PrivacyLifecyclePolicyAdmission() {
    }

    public enum RuleState {
        INVENTORY_BOUND,
        OWNER_INPUT_REQUIRED,
        OWNER_APPROVED
    }

    public enum HoldGuard {
        NONE,
        ACTIVE_EFFECT_AND_COMPENSATION,
        LEGAL_AND_SAFETY
    }

    public enum OwnerRole {
        PRIVACY,
        FUNCTIONAL_SAFETY,
        COMPLIANCE
    }

    public enum AdmissionCode {
        ADMITTED,
        INVENTORY_DIGEST_MISMATCH,
        SURFACE_SET_MISMATCH,
        SURFACE_POLICY_UNRESOLVED,
        RETENTION_CEILING_INVALID,
        HOLD_GUARD_MISMATCH,
        OWNER_APPROVAL_MISSING,
        OWNER_APPROVAL_DUPLICATED,
        OWNER_APPROVAL_MISMATCH
    }

    public enum OperationType {
        DELETE,
        ERASE,
        EXPORT
    }

    public enum OperationCode {
        ADMITTED,
        POLICY_NOT_ADMITTED,
        SURFACE_UNKNOWN,
        OPERATION_NOT_AUTHORIZED,
        AUTHORIZATION_MISSING,
        CONSENT_MISSING,
        ACTIVE_EFFECT_BLOCKED,
        PENDING_COMPENSATION_BLOCKED,
        LEGAL_HOLD_BLOCKED,
        SAFETY_HOLD_BLOCKED
    }

    public static final class SurfacePolicy {
        private final String surfaceId;
        private final RuleState state;
        private final Long retentionCeilingSeconds;
        private final HoldGuard holdGuard;

        private SurfacePolicy(
                String surfaceId,
                RuleState state,
                Long retentionCeilingSeconds,
                HoldGuard holdGuard) {
            this.surfaceId = requireSurfaceId(surfaceId);
            this.state = Objects.requireNonNull(state, "state");
            this.retentionCeilingSeconds = retentionCeilingSeconds;
            this.holdGuard = Objects.requireNonNull(holdGuard, "holdGuard");
        }

        public String getSurfaceId() {
            return surfaceId;
        }

        public RuleState getState() {
            return state;
        }

        public Long getRetentionCeilingSeconds() {
            return retentionCeilingSeconds;
        }

        public HoldGuard getHoldGuard() {
            return holdGuard;
        }

        private String canonicalForm() {
            return surfaceId + '|' + state.name() + '|'
                    + (retentionCeilingSeconds == null ? "UNSET" : retentionCeilingSeconds)
                    + '|' + holdGuard.name();
        }
    }

    public static final class PolicyProfile {
        private final String policyId;
        private final String policyVersion;
        private final String inventoryDigest;
        private final List<SurfacePolicy> surfacePolicies;
        private final String bodyDigest;

        private PolicyProfile(
                String policyId,
                String policyVersion,
                String inventoryDigest,
                List<SurfacePolicy> surfacePolicies) {
            this.policyId = requirePolicyId(policyId);
            this.policyVersion = requirePolicyVersion(policyVersion);
            this.inventoryDigest = requireDigest(inventoryDigest, "inventoryDigest");
            Objects.requireNonNull(surfacePolicies, "surfacePolicies");
            this.surfacePolicies = Collections.unmodifiableList(
                    new ArrayList<>(surfacePolicies));
            for (SurfacePolicy policy : this.surfacePolicies) {
                Objects.requireNonNull(policy, "surfacePolicy");
            }
            this.bodyDigest = sha256(canonicalBody());
        }

        public String getPolicyId() {
            return policyId;
        }

        public String getPolicyVersion() {
            return policyVersion;
        }

        public String getInventoryDigest() {
            return inventoryDigest;
        }

        public List<SurfacePolicy> getSurfacePolicies() {
            return surfacePolicies;
        }

        public String getBodyDigest() {
            return bodyDigest;
        }

        private String canonicalBody() {
            StringBuilder value = new StringBuilder()
                    .append(SCHEMA_VERSION).append('|').append(PROFILE_ID)
                    .append('|').append(policyId).append('|').append(policyVersion)
                    .append('|').append(inventoryDigest);
            for (SurfacePolicy policy : surfacePolicies) {
                value.append('|').append(policy.canonicalForm());
            }
            return value.toString();
        }
    }

    public static final class ApprovalEvidence {
        private final OwnerRole ownerRole;
        private final String policyBodyDigest;
        private final String inventoryDigest;
        private final String approvalReferenceDigest;

        private ApprovalEvidence(
                OwnerRole ownerRole,
                String policyBodyDigest,
                String inventoryDigest,
                String approvalReferenceDigest) {
            this.ownerRole = Objects.requireNonNull(ownerRole, "ownerRole");
            this.policyBodyDigest = requireDigest(policyBodyDigest, "policyBodyDigest");
            this.inventoryDigest = requireDigest(inventoryDigest, "inventoryDigest");
            this.approvalReferenceDigest = requireDigest(
                    approvalReferenceDigest, "approvalReferenceDigest");
        }

        public OwnerRole getOwnerRole() {
            return ownerRole;
        }

        public String getPolicyBodyDigest() {
            return policyBodyDigest;
        }

        public String getInventoryDigest() {
            return inventoryDigest;
        }

        public String getApprovalReferenceDigest() {
            return approvalReferenceDigest;
        }
    }

    public static final class AdmissionDecision {
        private final List<AdmissionCode> codes;
        private final String policyBodyDigest;

        private AdmissionDecision(List<AdmissionCode> codes, String policyBodyDigest) {
            this.codes = Collections.unmodifiableList(new ArrayList<>(codes));
            this.policyBodyDigest = policyBodyDigest;
        }

        public boolean isAdmitted() {
            return codes.equals(List.of(AdmissionCode.ADMITTED));
        }

        public List<AdmissionCode> getCodes() {
            return codes;
        }

        public String getPolicyBodyDigest() {
            return policyBodyDigest;
        }

        public boolean grantsRepositoryMutationAuthority() {
            return false;
        }

        public boolean grantsRuntimeAuthority() {
            return false;
        }
    }

    public static final class LifecycleStateSnapshot {
        private final int activeEffectCount;
        private final int pendingCompensationCount;
        private final int legalHoldCount;
        private final int safetyHoldCount;

        public LifecycleStateSnapshot(
                int activeEffectCount,
                int pendingCompensationCount,
                int legalHoldCount,
                int safetyHoldCount) {
            if (activeEffectCount < 0 || pendingCompensationCount < 0
                    || legalHoldCount < 0 || safetyHoldCount < 0) {
                throw new IllegalArgumentException("lifecycle count is negative");
            }
            this.activeEffectCount = activeEffectCount;
            this.pendingCompensationCount = pendingCompensationCount;
            this.legalHoldCount = legalHoldCount;
            this.safetyHoldCount = safetyHoldCount;
        }
    }

    public static final class OperationRequest {
        private final String surfaceId;
        private final OperationType operationType;
        private final String authorizationDigest;
        private final String consentReceiptDigest;
        private final LifecycleStateSnapshot stateSnapshot;

        public OperationRequest(
                String surfaceId,
                OperationType operationType,
                String authorizationDigest,
                String consentReceiptDigest,
                LifecycleStateSnapshot stateSnapshot) {
            this.surfaceId = requireSurfaceId(surfaceId);
            this.operationType = Objects.requireNonNull(operationType, "operationType");
            this.authorizationDigest = optionalDigest(
                    authorizationDigest, "authorizationDigest");
            this.consentReceiptDigest = optionalDigest(
                    consentReceiptDigest, "consentReceiptDigest");
            this.stateSnapshot = Objects.requireNonNull(stateSnapshot, "stateSnapshot");
        }
    }

    public static final class OperationDecision {
        private final List<OperationCode> codes;
        private final String surfaceId;

        private OperationDecision(List<OperationCode> codes, String surfaceId) {
            this.codes = Collections.unmodifiableList(new ArrayList<>(codes));
            this.surfaceId = surfaceId;
        }

        public boolean isAdmitted() {
            return codes.equals(List.of(OperationCode.ADMITTED));
        }

        public List<OperationCode> getCodes() {
            return codes;
        }

        public String getSurfaceId() {
            return surfaceId;
        }

        public boolean dataWasMutated() {
            return false;
        }

        public boolean dataWasExported() {
            return false;
        }
    }

    public static PolicyProfile currentDraft() {
        return CURRENT_DRAFT;
    }

    public static SurfacePolicy surfacePolicy(
            String surfaceId,
            RuleState state,
            Long retentionCeilingSeconds,
            HoldGuard holdGuard) {
        return new SurfacePolicy(surfaceId, state, retentionCeilingSeconds, holdGuard);
    }

    public static PolicyProfile policyProfile(
            String policyId,
            String policyVersion,
            String inventoryDigest,
            List<SurfacePolicy> surfacePolicies) {
        return new PolicyProfile(
                policyId, policyVersion, inventoryDigest, surfacePolicies);
    }

    public static ApprovalEvidence approvalEvidence(
            OwnerRole ownerRole,
            String policyBodyDigest,
            String inventoryDigest,
            String approvalReferenceDigest) {
        return new ApprovalEvidence(
                ownerRole,
                policyBodyDigest,
                inventoryDigest,
                approvalReferenceDigest);
    }

    public static AdmissionDecision evaluate(
            PolicyProfile profile,
            List<ApprovalEvidence> approvals) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(approvals, "approvals");
        List<AdmissionCode> codes = new ArrayList<>();

        if (!PrivacyDataInventoryContract.inventoryDigest().equals(profile.inventoryDigest)) {
            addOnce(codes, AdmissionCode.INVENTORY_DIGEST_MISMATCH);
        }
        if (!hasExactSurfaceSet(profile.surfacePolicies)) {
            addOnce(codes, AdmissionCode.SURFACE_SET_MISMATCH);
        } else {
            validateSurfacePolicies(profile.surfacePolicies, codes);
        }
        validateApprovals(profile, approvals, codes);

        if (codes.isEmpty()) {
            codes.add(AdmissionCode.ADMITTED);
        }
        return new AdmissionDecision(codes, profile.bodyDigest);
    }

    public static OperationDecision evaluateOperation(
            PolicyProfile profile,
            List<ApprovalEvidence> approvals,
            OperationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!evaluate(profile, approvals).isAdmitted()) {
            return operationDecision(request.surfaceId, OperationCode.POLICY_NOT_ADMITTED);
        }
        PrivacyDataInventoryContract.DataSurface surface = findSurface(request.surfaceId);
        if (surface == null) {
            return operationDecision(request.surfaceId, OperationCode.SURFACE_UNKNOWN);
        }

        List<OperationCode> codes = new ArrayList<>();
        if (!isOperationSupported(surface, request.operationType)) {
            addOnce(codes, OperationCode.OPERATION_NOT_AUTHORIZED);
        }
        if (request.authorizationDigest == null) {
            addOnce(codes, OperationCode.AUTHORIZATION_MISSING);
        }
        if (request.operationType == OperationType.EXPORT
                && request.consentReceiptDigest == null) {
            addOnce(codes, OperationCode.CONSENT_MISSING);
        }
        applyHoldGuards(request, codes);
        if (codes.isEmpty()) {
            codes.add(OperationCode.ADMITTED);
        }
        return new OperationDecision(codes, request.surfaceId);
    }

    public static boolean isCurrentPolicyAdmitted() {
        return false;
    }

    public static boolean isOwnerPolicyApproved() {
        return false;
    }

    public static boolean isRepositoryMutationWired() {
        return false;
    }

    public static boolean isRuntimeLifecycleWiringComplete() {
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

    private static PolicyProfile buildCurrentDraft() {
        List<SurfacePolicy> policies = new ArrayList<>();
        for (String surfaceId : REQUIRED_SURFACE_IDS) {
            if (surfaceId.equals("durable.effect_recovery")) {
                policies.add(surfacePolicy(
                        surfaceId,
                        RuleState.OWNER_INPUT_REQUIRED,
                        null,
                        HoldGuard.ACTIVE_EFFECT_AND_COMPENSATION));
            } else if (surfaceId.equals("durable.audit")) {
                policies.add(surfacePolicy(
                        surfaceId,
                        RuleState.OWNER_INPUT_REQUIRED,
                        null,
                        HoldGuard.LEGAL_AND_SAFETY));
            } else {
                policies.add(surfacePolicy(
                        surfaceId, RuleState.INVENTORY_BOUND, null, HoldGuard.NONE));
            }
        }
        return policyProfile(
                CURRENT_POLICY_ID,
                CURRENT_POLICY_VERSION,
                PrivacyDataInventoryContract.inventoryDigest(),
                policies);
    }

    private static List<String> buildRequiredSurfaceIds() {
        List<String> ids = new ArrayList<>();
        for (PrivacyDataInventoryContract.DataSurface surface
                : PrivacyDataInventoryContract.surfaces()) {
            ids.add(surface.getSurfaceId());
        }
        return Collections.unmodifiableList(ids);
    }

    private static boolean hasExactSurfaceSet(List<SurfacePolicy> policies) {
        if (policies.size() != REQUIRED_SURFACE_COUNT) {
            return false;
        }
        for (int index = 0; index < policies.size(); index++) {
            if (!REQUIRED_SURFACE_IDS.get(index).equals(policies.get(index).surfaceId)) {
                return false;
            }
        }
        return true;
    }

    private static void validateSurfacePolicies(
            List<SurfacePolicy> policies,
            List<AdmissionCode> codes) {
        for (SurfacePolicy policy : policies) {
            boolean gap = policy.surfaceId.equals("durable.effect_recovery")
                    || policy.surfaceId.equals("durable.audit");
            if (gap) {
                if (policy.state != RuleState.OWNER_APPROVED) {
                    addOnce(codes, AdmissionCode.SURFACE_POLICY_UNRESOLVED);
                }
                if (policy.retentionCeilingSeconds == null
                        || policy.retentionCeilingSeconds <= 0) {
                    addOnce(codes, AdmissionCode.RETENTION_CEILING_INVALID);
                }
                HoldGuard expected = policy.surfaceId.equals("durable.effect_recovery")
                        ? HoldGuard.ACTIVE_EFFECT_AND_COMPENSATION
                        : HoldGuard.LEGAL_AND_SAFETY;
                if (policy.holdGuard != expected) {
                    addOnce(codes, AdmissionCode.HOLD_GUARD_MISMATCH);
                }
            } else if (policy.state != RuleState.INVENTORY_BOUND
                    || policy.retentionCeilingSeconds != null
                    || policy.holdGuard != HoldGuard.NONE) {
                addOnce(codes, AdmissionCode.SURFACE_SET_MISMATCH);
            }
        }
    }

    private static void validateApprovals(
            PolicyProfile profile,
            List<ApprovalEvidence> approvals,
            List<AdmissionCode> codes) {
        EnumSet<OwnerRole> roles = EnumSet.noneOf(OwnerRole.class);
        List<String> approvalReferences = new ArrayList<>();
        for (ApprovalEvidence approval : approvals) {
            if (approval == null) {
                addOnce(codes, AdmissionCode.OWNER_APPROVAL_MISMATCH);
                continue;
            }
            if (!roles.add(approval.ownerRole)) {
                addOnce(codes, AdmissionCode.OWNER_APPROVAL_DUPLICATED);
            }
            if (approvalReferences.contains(approval.approvalReferenceDigest)) {
                addOnce(codes, AdmissionCode.OWNER_APPROVAL_DUPLICATED);
            } else {
                approvalReferences.add(approval.approvalReferenceDigest);
            }
            if (!profile.bodyDigest.equals(approval.policyBodyDigest)
                    || !profile.inventoryDigest.equals(approval.inventoryDigest)) {
                addOnce(codes, AdmissionCode.OWNER_APPROVAL_MISMATCH);
            }
        }
        if (roles.size() != REQUIRED_OWNER_APPROVAL_COUNT
                || !roles.containsAll(EnumSet.allOf(OwnerRole.class))) {
            addOnce(codes, AdmissionCode.OWNER_APPROVAL_MISSING);
        }
    }

    private static boolean isOperationSupported(
            PrivacyDataInventoryContract.DataSurface surface,
            OperationType operationType) {
        if (operationType == OperationType.EXPORT) {
            return surface.getExportMode()
                    == PrivacyDataInventoryContract.ExportMode.AUTHORIZED_BOUNDED;
        }
        if (surface.getSurfaceId().equals("durable.effect_recovery")
                || surface.getSurfaceId().equals("durable.audit")) {
            return operationType == OperationType.DELETE;
        }
        if (operationType == OperationType.DELETE) {
            return surface.getDeletionMode()
                    == PrivacyDataInventoryContract.DeletionMode.AUTHORIZED_DELETE;
        }
        return surface.getDeletionMode()
                == PrivacyDataInventoryContract.DeletionMode.AUTHORIZED_ERASE;
    }

    private static void applyHoldGuards(
            OperationRequest request,
            List<OperationCode> codes) {
        if (request.operationType == OperationType.EXPORT) {
            return;
        }
        if (request.surfaceId.equals("durable.effect_recovery")) {
            if (request.stateSnapshot.activeEffectCount > 0) {
                addOnce(codes, OperationCode.ACTIVE_EFFECT_BLOCKED);
            }
            if (request.stateSnapshot.pendingCompensationCount > 0) {
                addOnce(codes, OperationCode.PENDING_COMPENSATION_BLOCKED);
            }
        }
        if (request.surfaceId.equals("durable.audit")) {
            if (request.stateSnapshot.legalHoldCount > 0) {
                addOnce(codes, OperationCode.LEGAL_HOLD_BLOCKED);
            }
            if (request.stateSnapshot.safetyHoldCount > 0) {
                addOnce(codes, OperationCode.SAFETY_HOLD_BLOCKED);
            }
        }
    }

    private static PrivacyDataInventoryContract.DataSurface findSurface(String surfaceId) {
        for (PrivacyDataInventoryContract.DataSurface surface
                : PrivacyDataInventoryContract.surfaces()) {
            if (surface.getSurfaceId().equals(surfaceId)) {
                return surface;
            }
        }
        return null;
    }

    private static OperationDecision operationDecision(
            String surfaceId, OperationCode code) {
        return new OperationDecision(List.of(code), surfaceId);
    }

    private static <T> void addOnce(List<T> values, T value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static String requireSurfaceId(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{2,31}[.][a-z][a-z0-9_]{2,31}")) {
            throw new IllegalArgumentException("surfaceId is invalid");
        }
        return value;
    }

    private static String requirePolicyId(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{2,63}")) {
            throw new IllegalArgumentException("policyId is invalid");
        }
        return value;
    }

    private static String requirePolicyVersion(String value) {
        if (value == null || !value.matches("[0-9]+[.][0-9]+[.][0-9]+(?:-[a-z0-9-]+)?")) {
            throw new IllegalArgumentException("policyVersion is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !value.matches(DIGEST_PATTERN)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String optionalDigest(String value, String name) {
        return value == null ? null : requireDigest(value, name);
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                result.append(Character.forDigit((current >>> 4) & 0xf, 16));
                result.append(Character.forDigit(current & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
