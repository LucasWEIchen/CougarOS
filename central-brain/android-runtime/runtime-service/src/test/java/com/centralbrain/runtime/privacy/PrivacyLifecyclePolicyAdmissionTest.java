package com.centralbrain.runtime.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class PrivacyLifecyclePolicyAdmissionTest {
    private static final String AUTH = digest('a');
    private static final String CONSENT = digest('b');

    @Test
    public void currentDraftBindsAllSurfacesAndFailsClosedWithoutOwnerInput() {
        PrivacyLifecyclePolicyAdmission.PolicyProfile draft =
                PrivacyLifecyclePolicyAdmission.currentDraft();
        assertEquals(12, draft.getSurfacePolicies().size());
        assertEquals(
                PrivacyDataInventoryContract.inventoryDigest(),
                draft.getInventoryDigest());
        assertTrue(draft.getBodyDigest().matches("[0-9a-f]{64}"));
        List<String> unresolved = new ArrayList<>();
        for (PrivacyLifecyclePolicyAdmission.SurfacePolicy policy
                : draft.getSurfacePolicies()) {
            if (policy.getState()
                    == PrivacyLifecyclePolicyAdmission.RuleState.OWNER_INPUT_REQUIRED) {
                unresolved.add(policy.getSurfaceId());
                assertNull(policy.getRetentionCeilingSeconds());
                assertEquals(
                        policy.getSurfaceId().equals("durable.effect_recovery")
                                ? PrivacyLifecyclePolicyAdmission.HoldGuard
                                        .ACTIVE_EFFECT_AND_COMPENSATION
                                : PrivacyLifecyclePolicyAdmission.HoldGuard.LEGAL_AND_SAFETY,
                        policy.getHoldGuard());
            } else {
                assertEquals(
                        PrivacyLifecyclePolicyAdmission.RuleState.INVENTORY_BOUND,
                        policy.getState());
                assertNull(policy.getRetentionCeilingSeconds());
                assertEquals(
                        PrivacyLifecyclePolicyAdmission.HoldGuard.NONE,
                        policy.getHoldGuard());
            }
        }
        assertEquals(List.of("durable.effect_recovery", "durable.audit"), unresolved);

        PrivacyLifecyclePolicyAdmission.AdmissionDecision decision =
                PrivacyLifecyclePolicyAdmission.evaluate(draft, List.of());
        assertFalse(decision.isAdmitted());
        assertTrue(decision.getCodes().contains(
                PrivacyLifecyclePolicyAdmission.AdmissionCode.SURFACE_POLICY_UNRESOLVED));
        assertTrue(decision.getCodes().contains(
                PrivacyLifecyclePolicyAdmission.AdmissionCode.RETENTION_CEILING_INVALID));
        assertTrue(decision.getCodes().contains(
                PrivacyLifecyclePolicyAdmission.AdmissionCode.OWNER_APPROVAL_MISSING));
    }

    @Test
    public void syntheticApprovedFixtureRequiresAllOwnersAndExactDigestBinding() {
        PrivacyLifecyclePolicyAdmission.PolicyProfile profile = completeSyntheticProfile();
        List<PrivacyLifecyclePolicyAdmission.ApprovalEvidence> approvals =
                approvalsFor(profile);
        assertTrue(PrivacyLifecyclePolicyAdmission.evaluate(profile, approvals).isAdmitted());

        assertFalse(PrivacyLifecyclePolicyAdmission.evaluate(
                profile, approvals.subList(0, 2)).isAdmitted());
        List<PrivacyLifecyclePolicyAdmission.ApprovalEvidence> mismatched =
                new ArrayList<>(approvals);
        mismatched.set(0, PrivacyLifecyclePolicyAdmission.approvalEvidence(
                PrivacyLifecyclePolicyAdmission.OwnerRole.PRIVACY,
                digest('c'),
                profile.getInventoryDigest(),
                digest('d')));
        assertTrue(PrivacyLifecyclePolicyAdmission.evaluate(profile, mismatched)
                .getCodes().contains(
                        PrivacyLifecyclePolicyAdmission.AdmissionCode
                                .OWNER_APPROVAL_MISMATCH));

        List<PrivacyLifecyclePolicyAdmission.ApprovalEvidence> duplicatedReference =
                new ArrayList<>();
        for (PrivacyLifecyclePolicyAdmission.OwnerRole role
                : PrivacyLifecyclePolicyAdmission.OwnerRole.values()) {
            duplicatedReference.add(PrivacyLifecyclePolicyAdmission.approvalEvidence(
                    role,
                    profile.getBodyDigest(),
                    profile.getInventoryDigest(),
                    digest('e')));
        }
        assertTrue(PrivacyLifecyclePolicyAdmission.evaluate(profile, duplicatedReference)
                .getCodes().contains(
                        PrivacyLifecyclePolicyAdmission.AdmissionCode
                                .OWNER_APPROVAL_DUPLICATED));
    }

    @Test
    public void activeEffectAndCompensationBlockDeletionBeforeRepositoryMutation() {
        PrivacyLifecyclePolicyAdmission.PolicyProfile profile = completeSyntheticProfile();
        PrivacyLifecyclePolicyAdmission.OperationDecision decision =
                PrivacyLifecyclePolicyAdmission.evaluateOperation(
                        profile,
                        approvalsFor(profile),
                        request(
                                "durable.effect_recovery",
                                PrivacyLifecyclePolicyAdmission.OperationType.DELETE,
                                AUTH,
                                null,
                                new PrivacyLifecyclePolicyAdmission.LifecycleStateSnapshot(
                                        1, 1, 0, 0)));
        assertFalse(decision.isAdmitted());
        assertTrue(decision.getCodes().contains(
                PrivacyLifecyclePolicyAdmission.OperationCode.ACTIVE_EFFECT_BLOCKED));
        assertTrue(decision.getCodes().contains(
                PrivacyLifecyclePolicyAdmission.OperationCode
                        .PENDING_COMPENSATION_BLOCKED));
        assertFalse(decision.dataWasMutated());
    }

    @Test
    public void legalAndSafetyHoldBlockAuditDeletion() {
        PrivacyLifecyclePolicyAdmission.PolicyProfile profile = completeSyntheticProfile();
        PrivacyLifecyclePolicyAdmission.OperationDecision decision =
                PrivacyLifecyclePolicyAdmission.evaluateOperation(
                        profile,
                        approvalsFor(profile),
                        request(
                                "durable.audit",
                                PrivacyLifecyclePolicyAdmission.OperationType.DELETE,
                                AUTH,
                                null,
                                new PrivacyLifecyclePolicyAdmission.LifecycleStateSnapshot(
                                        0, 0, 1, 1)));
        assertEquals(List.of(
                PrivacyLifecyclePolicyAdmission.OperationCode.LEGAL_HOLD_BLOCKED,
                PrivacyLifecyclePolicyAdmission.OperationCode.SAFETY_HOLD_BLOCKED),
                decision.getCodes());
        assertFalse(decision.dataWasMutated());
    }

    @Test
    public void onlyProfileExportWithAuthorizationAndConsentCanPassPreflight() {
        PrivacyLifecyclePolicyAdmission.PolicyProfile profile = completeSyntheticProfile();
        List<PrivacyLifecyclePolicyAdmission.ApprovalEvidence> approvals =
                approvalsFor(profile);

        assertTrue(PrivacyLifecyclePolicyAdmission.evaluateOperation(
                profile,
                approvals,
                request(
                        "memory.profile",
                        PrivacyLifecyclePolicyAdmission.OperationType.EXPORT,
                        AUTH,
                        CONSENT,
                        emptyState())).isAdmitted());
        assertTrue(PrivacyLifecyclePolicyAdmission.evaluateOperation(
                profile,
                approvals,
                request(
                        "memory.profile",
                        PrivacyLifecyclePolicyAdmission.OperationType.EXPORT,
                        AUTH,
                        null,
                        emptyState())).getCodes().contains(
                                PrivacyLifecyclePolicyAdmission.OperationCode.CONSENT_MISSING));
        assertTrue(PrivacyLifecyclePolicyAdmission.evaluateOperation(
                profile,
                approvals,
                request(
                        "durable.audit",
                        PrivacyLifecyclePolicyAdmission.OperationType.EXPORT,
                        AUTH,
                        CONSENT,
                        emptyState())).getCodes().contains(
                                PrivacyLifecyclePolicyAdmission.OperationCode
                                        .OPERATION_NOT_AUTHORIZED));
    }

    @Test
    public void admittedPreflightNeverMutatesExportsOrGrantsRuntimeAuthority() {
        PrivacyLifecyclePolicyAdmission.PolicyProfile profile = completeSyntheticProfile();
        List<PrivacyLifecyclePolicyAdmission.ApprovalEvidence> approvals =
                approvalsFor(profile);
        PrivacyLifecyclePolicyAdmission.AdmissionDecision admission =
                PrivacyLifecyclePolicyAdmission.evaluate(profile, approvals);
        PrivacyLifecyclePolicyAdmission.OperationDecision operation =
                PrivacyLifecyclePolicyAdmission.evaluateOperation(
                        profile,
                        approvals,
                        request(
                                "durable.audit",
                                PrivacyLifecyclePolicyAdmission.OperationType.DELETE,
                                AUTH,
                                null,
                                emptyState()));
        assertTrue(admission.isAdmitted());
        assertTrue(operation.isAdmitted());
        assertFalse(admission.grantsRepositoryMutationAuthority());
        assertFalse(admission.grantsRuntimeAuthority());
        assertFalse(operation.dataWasMutated());
        assertFalse(operation.dataWasExported());
    }

    @Test
    public void repositoryClaimsRemainUnapprovedUnwiredAndUnverified() {
        assertFalse(PrivacyLifecyclePolicyAdmission.isCurrentPolicyAdmitted());
        assertFalse(PrivacyLifecyclePolicyAdmission.isOwnerPolicyApproved());
        assertFalse(PrivacyLifecyclePolicyAdmission.isRepositoryMutationWired());
        assertFalse(PrivacyLifecyclePolicyAdmission.isRuntimeLifecycleWiringComplete());
        assertFalse(PrivacyLifecyclePolicyAdmission.isAndroid13Arm64Verified());
        assertFalse(PrivacyLifecyclePolicyAdmission.isHardwareAccessed());
        assertFalse(PrivacyLifecyclePolicyAdmission.isProductionReady());
        assertFalse(PrivacyLifecyclePolicyAdmission.isTargetHardwareValidated());
    }

    private static PrivacyLifecyclePolicyAdmission.PolicyProfile completeSyntheticProfile() {
        List<PrivacyLifecyclePolicyAdmission.SurfacePolicy> policies = new ArrayList<>();
        for (PrivacyLifecyclePolicyAdmission.SurfacePolicy policy
                : PrivacyLifecyclePolicyAdmission.currentDraft().getSurfacePolicies()) {
            if (policy.getSurfaceId().equals("durable.effect_recovery")) {
                policies.add(PrivacyLifecyclePolicyAdmission.surfacePolicy(
                        policy.getSurfaceId(),
                        PrivacyLifecyclePolicyAdmission.RuleState.OWNER_APPROVED,
                        3600L,
                        PrivacyLifecyclePolicyAdmission.HoldGuard
                                .ACTIVE_EFFECT_AND_COMPENSATION));
            } else if (policy.getSurfaceId().equals("durable.audit")) {
                policies.add(PrivacyLifecyclePolicyAdmission.surfacePolicy(
                        policy.getSurfaceId(),
                        PrivacyLifecyclePolicyAdmission.RuleState.OWNER_APPROVED,
                        86400L,
                        PrivacyLifecyclePolicyAdmission.HoldGuard.LEGAL_AND_SAFETY));
            } else {
                policies.add(policy);
            }
        }
        return PrivacyLifecyclePolicyAdmission.policyProfile(
                "synthetic-contract-fixture",
                "1.0.0-test",
                PrivacyDataInventoryContract.inventoryDigest(),
                policies);
    }

    private static List<PrivacyLifecyclePolicyAdmission.ApprovalEvidence> approvalsFor(
            PrivacyLifecyclePolicyAdmission.PolicyProfile profile) {
        List<PrivacyLifecyclePolicyAdmission.ApprovalEvidence> approvals = new ArrayList<>();
        char reference = '1';
        for (PrivacyLifecyclePolicyAdmission.OwnerRole role
                : PrivacyLifecyclePolicyAdmission.OwnerRole.values()) {
            approvals.add(PrivacyLifecyclePolicyAdmission.approvalEvidence(
                    role,
                    profile.getBodyDigest(),
                    profile.getInventoryDigest(),
                    digest(reference++)));
        }
        return approvals;
    }

    private static PrivacyLifecyclePolicyAdmission.OperationRequest request(
            String surfaceId,
            PrivacyLifecyclePolicyAdmission.OperationType operationType,
            String authorizationDigest,
            String consentReceiptDigest,
            PrivacyLifecyclePolicyAdmission.LifecycleStateSnapshot state) {
        return new PrivacyLifecyclePolicyAdmission.OperationRequest(
                surfaceId,
                operationType,
                authorizationDigest,
                consentReceiptDigest,
                state);
    }

    private static PrivacyLifecyclePolicyAdmission.LifecycleStateSnapshot emptyState() {
        return new PrivacyLifecyclePolicyAdmission.LifecycleStateSnapshot(0, 0, 0, 0);
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }
}
