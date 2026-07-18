package com.centralbrain.runtime.release;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class ProductionReleaseAdmissionTest {
    private static final String SIGNER = digest('a');
    private static final String OTHER_SIGNER = digest('b');
    private static final String EVIDENCE = digest('c');

    @Test
    public void sameSignerUpgradeWithReadableSchemaIsAdmittedWithoutMutation() {
        ProductionReleaseAdmission.Decision decision = ProductionReleaseAdmission.evaluate(
                release("installed.10", 10, 3, 1, 1, 4, 1, 4, SIGNER),
                release("candidate.11", 11, 4, 1, 1, 4, 1, 4, SIGNER),
                upgradeRequest(null));

        assertTrue(decision.isAdmitted());
        assertEquals(ProductionReleaseAdmission.DecisionCode.ADMITTED, decision.getCode());
        assertEquals(64, decision.getDecisionDigest().length());
        assertFalse(decision.mutatesDatabase());
        assertFalse(decision.installsPackages());
        assertFalse(decision.uninstallsPackages());
        assertFalse(decision.executesRollback());
    }

    @Test
    public void signerMismatchAndSignerCohortMismatchFailClosed() {
        ProductionReleaseAdmission.ReleaseSet installed =
                release("installed.10", 10, 3, 1, 1, 4, 1, 4, SIGNER);
        ProductionReleaseAdmission.ReleaseSet changedRuntimeSigner =
                release("candidate.11", 11, 4, 1, 1, 4, 1, 4, OTHER_SIGNER);
        assertCode(
                ProductionReleaseAdmission.DecisionCode.SIGNER_MISMATCH,
                installed,
                changedRuntimeSigner,
                upgradeRequest(null));

        List<ProductionReleaseAdmission.PackageSnapshot> mixed = new ArrayList<>(
                release("candidate.11", 11, 4, 1, 1, 4, 1, 4, SIGNER).getPackages());
        mixed.set(2, packageSnapshot(
                "client2-demo", "com.tuanjie.urasclient2", 1, OTHER_SIGNER, 0, 0, 0));
        ProductionReleaseAdmission.ReleaseSet mixedCandidate = new ProductionReleaseAdmission.ReleaseSet(
                "candidate.11", 11, EVIDENCE, digest('d'), mixed);
        assertCode(
                ProductionReleaseAdmission.DecisionCode.SIGNER_MISMATCH,
                installed,
                mixedCandidate,
                upgradeRequest(null));

        List<ProductionReleaseAdmission.PackageSnapshot> mixedInstalledPackages =
                new ArrayList<>(installed.getPackages());
        mixedInstalledPackages.set(2, packageSnapshot(
                "client2-demo", "com.tuanjie.urasclient2", 1, OTHER_SIGNER, 0, 0, 0));
        ProductionReleaseAdmission.ReleaseSet mixedInstalled =
                new ProductionReleaseAdmission.ReleaseSet(
                        "installed.10", 10, EVIDENCE, digest('d'), mixedInstalledPackages);
        assertCode(
                ProductionReleaseAdmission.DecisionCode.SIGNER_COHORT_MISMATCH,
                mixedInstalled,
                mixedCandidate,
                upgradeRequest(null));
    }

    @Test
    public void exactPackageSetAndOwnerEvidenceAreRequired() {
        ProductionReleaseAdmission.ReleaseSet installed =
                release("installed.10", 10, 3, 1, 1, 4, 1, 4, SIGNER);
        ProductionReleaseAdmission.ReleaseSet candidate =
                release("candidate.11", 11, 4, 1, 1, 4, 1, 4, SIGNER);
        List<ProductionReleaseAdmission.PackageSnapshot> missing =
                new ArrayList<>(candidate.getPackages());
        missing.remove(2);
        assertCode(
                ProductionReleaseAdmission.DecisionCode.PACKAGE_SET_MISMATCH,
                installed,
                new ProductionReleaseAdmission.ReleaseSet(
                        "candidate.11", 11, EVIDENCE, digest('d'), missing),
                upgradeRequest(null));
        assertCode(
                ProductionReleaseAdmission.DecisionCode.PRODUCTION_SIGNER_APPROVAL_MISSING,
                installed,
                candidate,
                new ProductionReleaseAdmission.AdmissionRequest(
                        ProductionReleaseAdmission.Mode.UPGRADE,
                        null, EVIDENCE, null, null, null, null));
        assertCode(
                ProductionReleaseAdmission.DecisionCode.RELEASE_OWNER_APPROVAL_MISSING,
                installed,
                candidate,
                new ProductionReleaseAdmission.AdmissionRequest(
                        ProductionReleaseAdmission.Mode.UPGRADE,
                        EVIDENCE, null, null, null, null, null));
    }

    @Test
    public void upgradeRequiresMonotonicVersionsAndMigrationEvidence() {
        ProductionReleaseAdmission.ReleaseSet installed =
                release("installed.10", 10, 3, 2, 1, 4, 1, 4, SIGNER);
        assertCode(
                ProductionReleaseAdmission.DecisionCode.PACKAGE_VERSION_INVALID,
                installed,
                release("candidate.11", 11, 4, 1, 1, 4, 1, 4, SIGNER),
                upgradeRequest(null));
        assertCode(
                ProductionReleaseAdmission.DecisionCode.PACKAGE_VERSION_UNCHANGED,
                installed,
                release("candidate.11", 11, 3, 2, 1, 4, 1, 4, SIGNER),
                upgradeRequest(null));
        assertCode(
                ProductionReleaseAdmission.DecisionCode.MIGRATION_EVIDENCE_MISSING,
                installed,
                release("candidate.11", 11, 4, 2, 1, 5, 1, 5, SIGNER),
                upgradeRequest(null));
        assertTrue(ProductionReleaseAdmission.evaluate(
                installed,
                release("candidate.11", 11, 4, 2, 1, 5, 1, 5, SIGNER),
                upgradeRequest(EVIDENCE)).isAdmitted());
    }

    @Test
    public void upgradeRejectsUnreadableOrDowngradedDatabaseSchema() {
        ProductionReleaseAdmission.ReleaseSet installed =
                release("installed.10", 10, 3, 1, 1, 4, 1, 4, SIGNER);
        assertCode(
                ProductionReleaseAdmission.DecisionCode.DATA_SCHEMA_UNREADABLE,
                installed,
                release("candidate.11", 11, 4, 1, 1, 5, 5, 5, SIGNER),
                upgradeRequest(EVIDENCE));
        assertCode(
                ProductionReleaseAdmission.DecisionCode.DATA_SCHEMA_DOWNGRADE,
                installed,
                release("candidate.11", 11, 4, 1, 1, 3, 1, 4, SIGNER),
                upgradeRequest(null));
    }

    @Test
    public void rollbackRequiresDecisionOwnerAndDataCompatibilityEvidence() {
        ProductionReleaseAdmission.ReleaseSet installed =
                release("installed.11", 11, 4, 2, 1, 4, 1, 4, SIGNER);
        ProductionReleaseAdmission.ReleaseSet candidate =
                release("candidate.10", 10, 3, 1, 1, 4, 1, 4, SIGNER);
        assertCode(
                ProductionReleaseAdmission.DecisionCode.ROLLBACK_OWNER_APPROVAL_MISSING,
                installed,
                candidate,
                rollbackRequest(null, EVIDENCE, EVIDENCE));
        assertCode(
                ProductionReleaseAdmission.DecisionCode.ROLLBACK_DECISION_MISSING,
                installed,
                candidate,
                rollbackRequest(EVIDENCE, null, EVIDENCE));
        assertCode(
                ProductionReleaseAdmission.DecisionCode.ROLLBACK_DATA_COMPATIBILITY_MISSING,
                installed,
                candidate,
                rollbackRequest(EVIDENCE, EVIDENCE, null));
        assertTrue(ProductionReleaseAdmission.evaluate(
                installed,
                candidate,
                rollbackRequest(EVIDENCE, EVIDENCE, EVIDENCE)).isAdmitted());
    }

    @Test
    public void rollbackRejectsTargetThatCannotReadInstalledDatabase() {
        ProductionReleaseAdmission.ReleaseSet installed =
                release("installed.11", 11, 4, 2, 1, 4, 1, 4, SIGNER);
        ProductionReleaseAdmission.ReleaseSet incompatible =
                release("candidate.10", 10, 3, 1, 1, 3, 1, 3, SIGNER);
        assertCode(
                ProductionReleaseAdmission.DecisionCode.DATA_SCHEMA_UNREADABLE,
                installed,
                incompatible,
                rollbackRequest(EVIDENCE, EVIDENCE, EVIDENCE));
    }

    @Test
    public void repositoryClaimsRemainOwnerBlockedUnwiredAndUnverified() {
        assertEquals(3, ProductionReleaseAdmission.requiredPackages().size());
        assertFalse(ProductionReleaseAdmission.isProductionSignerOwnerApproved());
        assertFalse(ProductionReleaseAdmission.isProductionReleaseCandidateAdmitted());
        assertFalse(ProductionReleaseAdmission.isInstallerWired());
        assertFalse(ProductionReleaseAdmission.isRollbackExecutorWired());
        assertFalse(ProductionReleaseAdmission.isAndroid13Arm64Verified());
        assertFalse(ProductionReleaseAdmission.isHardwareAccessed());
        assertFalse(ProductionReleaseAdmission.isProductionReady());
        assertFalse(ProductionReleaseAdmission.isTargetHardwareValidated());
    }

    private static ProductionReleaseAdmission.ReleaseSet release(
            String releaseId,
            long releaseSequence,
            long runtimeVersion,
            long demoVersion,
            long clientVersion,
            int runtimeSchema,
            int minimumReadableSchema,
            int maximumReadableSchema,
            String signer) {
        return new ProductionReleaseAdmission.ReleaseSet(
                releaseId,
                releaseSequence,
                EVIDENCE,
                digest('d'),
                List.of(
                        packageSnapshot(
                                "runtime-service", "com.centralbrain.runtime", runtimeVersion,
                                signer, runtimeSchema, minimumReadableSchema, maximumReadableSchema),
                        packageSnapshot(
                                "demo-hmi", "com.centralbrain.demo", demoVersion,
                                signer, 0, 0, 0),
                        packageSnapshot(
                                "client2-demo", "com.tuanjie.urasclient2", clientVersion,
                                signer, 0, 0, 0)));
    }

    private static ProductionReleaseAdmission.PackageSnapshot packageSnapshot(
            String packageId,
            String packageName,
            long versionCode,
            String signer,
            int dataSchemaVersion,
            int minimumReadableSchema,
            int maximumReadableSchema) {
        return new ProductionReleaseAdmission.PackageSnapshot(
                packageId,
                packageName,
                versionCode,
                signer,
                digest((char) ('e' + packageId.length() % 2)),
                dataSchemaVersion,
                minimumReadableSchema,
                maximumReadableSchema);
    }

    private static ProductionReleaseAdmission.AdmissionRequest upgradeRequest(
            String migrationEvidence) {
        return new ProductionReleaseAdmission.AdmissionRequest(
                ProductionReleaseAdmission.Mode.UPGRADE,
                EVIDENCE,
                digest('f'),
                migrationEvidence,
                null,
                null,
                null);
    }

    private static ProductionReleaseAdmission.AdmissionRequest rollbackRequest(
            String rollbackOwner,
            String rollbackDecision,
            String rollbackCompatibility) {
        return new ProductionReleaseAdmission.AdmissionRequest(
                ProductionReleaseAdmission.Mode.ROLLBACK,
                EVIDENCE,
                digest('f'),
                null,
                rollbackOwner,
                rollbackDecision,
                rollbackCompatibility);
    }

    private static void assertCode(
            ProductionReleaseAdmission.DecisionCode code,
            ProductionReleaseAdmission.ReleaseSet installed,
            ProductionReleaseAdmission.ReleaseSet candidate,
            ProductionReleaseAdmission.AdmissionRequest request) {
        ProductionReleaseAdmission.Decision decision =
                ProductionReleaseAdmission.evaluate(installed, candidate, request);
        assertFalse(decision.isAdmitted());
        assertEquals(code, decision.getCode());
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }
}
