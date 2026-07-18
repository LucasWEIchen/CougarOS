package com.centralbrain.runtime.release;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class ReleaseEvidenceEnvelopeTest {
    private static final String DIGEST_A = digest('a');
    private static final String DIGEST_B = digest('b');

    @Test
    public void exactDiagnosticCatalogIsStableAndOrdered() {
        List<ReleaseEvidenceEnvelope.DiagnosticCategory> categories =
                ReleaseEvidenceEnvelope.requiredDiagnosticCategories();

        assertEquals(ReleaseEvidenceEnvelope.DIAGNOSTIC_CATEGORY_COUNT, categories.size());
        assertEquals(
                ReleaseEvidenceEnvelope.DiagnosticCategory.RELEASE_BUNDLE,
                categories.get(0));
        assertEquals(
                ReleaseEvidenceEnvelope.DiagnosticCategory.MANUAL_SCENARIO_MATRIX,
                categories.get(categories.size() - 1));
    }

    @Test
    public void hostEnvelopeIsGithubSafeButNeverTargetEvidence() {
        ReleaseEvidenceEnvelope.Evaluation evaluation = ReleaseEvidenceEnvelope.evaluate(
                report(ReleaseEvidenceEnvelope.EvidenceMode.HOST_SYNTHETIC, null, allPass()));

        assertEquals(
                ReleaseEvidenceEnvelope.EvaluationCode.HOST_SOFTWARE_ONLY,
                evaluation.getCode());
        assertTrue(evaluation.isGithubSafe());
        assertFalse(evaluation.isTargetOwnerReviewEligible());
        assertFalse(evaluation.isProductionReady());
        assertFalse(evaluation.isTargetHardwareValidated());
    }

    @Test
    public void targetEnvelopeRequiresOwnerAndAllDiagnosticsForReviewEligibility() {
        ReleaseEvidenceEnvelope.Evaluation withoutOwner = ReleaseEvidenceEnvelope.evaluate(
                report(ReleaseEvidenceEnvelope.EvidenceMode.TARGET, null, allPass()));
        List<ReleaseEvidenceEnvelope.DiagnosticFact> withNotRun = allPass();
        withNotRun.set(
                withNotRun.size() - 1,
                notRun(ReleaseEvidenceEnvelope.DiagnosticCategory.MANUAL_SCENARIO_MATRIX));
        ReleaseEvidenceEnvelope.Evaluation incomplete = ReleaseEvidenceEnvelope.evaluate(
                report(ReleaseEvidenceEnvelope.EvidenceMode.TARGET, DIGEST_B, withNotRun));
        ReleaseEvidenceEnvelope.Evaluation eligible = ReleaseEvidenceEnvelope.evaluate(
                report(ReleaseEvidenceEnvelope.EvidenceMode.TARGET, DIGEST_B, allPass()));

        assertEquals(
                ReleaseEvidenceEnvelope.EvaluationCode.TARGET_OWNER_REVIEW_REQUIRED,
                withoutOwner.getCode());
        assertFalse(incomplete.isTargetOwnerReviewEligible());
        assertEquals(
                ReleaseEvidenceEnvelope.EvaluationCode.TARGET_OWNER_REVIEW_ELIGIBLE,
                eligible.getCode());
        assertTrue(eligible.isTargetOwnerReviewEligible());
        assertFalse(eligible.isTargetHardwareValidated());
    }

    @Test
    public void githubPolicyRejectsPrivacyIdentityOrUploadViolations() {
        for (ReleaseEvidenceEnvelope.Identity identity : List.of(
                identity(null, false, false, false),
                identity(null, true, true, false),
                identity(null, true, false, true))) {
            ReleaseEvidenceEnvelope.Evaluation evaluation = ReleaseEvidenceEnvelope.evaluate(
                    new ReleaseEvidenceEnvelope.Report(
                            ReleaseEvidenceEnvelope.EvidenceMode.TARGET,
                            identity,
                            allPass()));
            assertEquals(
                    ReleaseEvidenceEnvelope.EvaluationCode.GITHUB_POLICY_REJECTED,
                    evaluation.getCode());
            assertFalse(evaluation.isGithubSafe());
        }
    }

    @Test
    public void reportDigestIsDeterministicAndBindsDiagnosticState() {
        ReleaseEvidenceEnvelope.Report first = report(
                ReleaseEvidenceEnvelope.EvidenceMode.TARGET, DIGEST_B, allPass());
        ReleaseEvidenceEnvelope.Report second = report(
                ReleaseEvidenceEnvelope.EvidenceMode.TARGET, DIGEST_B, allPass());
        List<ReleaseEvidenceEnvelope.DiagnosticFact> failed = allPass();
        failed.set(
                5,
                fact(
                        ReleaseEvidenceEnvelope.DiagnosticCategory.RUNTIME_SERVICE,
                        ReleaseEvidenceEnvelope.DiagnosticStatus.FAIL,
                        7));
        ReleaseEvidenceEnvelope.Report changed = report(
                ReleaseEvidenceEnvelope.EvidenceMode.TARGET, DIGEST_B, failed);

        assertEquals(first.getReportDigest(), second.getReportDigest());
        assertTrue(first.getReportDigest().matches("[0-9a-f]{64}"));
        assertNotEquals(first.getReportDigest(), changed.getReportDigest());
    }

    @Test(expected = IllegalArgumentException.class)
    public void identityRejectsNonCanonicalReleaseOrSensitiveFreeform() {
        new ReleaseEvidenceEnvelope.Identity(
                "latest",
                "not-a-commit",
                DIGEST_A,
                "delivery id with spaces",
                DIGEST_A,
                "serial=raw-device",
                "evidence with spaces",
                null,
                false,
                true,
                false,
                false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void diagnosticStatusAndResultCodeMustAgree() {
        new ReleaseEvidenceEnvelope.DiagnosticFact(
                ReleaseEvidenceEnvelope.DiagnosticCategory.RELEASE_BUNDLE,
                ReleaseEvidenceEnvelope.DiagnosticStatus.PASS,
                1,
                DIGEST_A);
    }

    @Test(expected = IllegalArgumentException.class)
    public void reportRejectsMissingOrReorderedCategory() {
        List<ReleaseEvidenceEnvelope.DiagnosticFact> facts = allPass();
        facts.set(0, facts.get(1));
        report(ReleaseEvidenceEnvelope.EvidenceMode.HOST_SYNTHETIC, null, facts);
    }

    @Test
    public void repositoryClaimsRemainUnwiredUnexecutedAndUnqualified() {
        assertTrue(ReleaseEvidenceEnvelope.isContractDefined());
        assertFalse(ReleaseEvidenceEnvelope.isTargetOwnerApproved());
        assertFalse(ReleaseEvidenceEnvelope.isTargetReportAdmitted());
        assertFalse(ReleaseEvidenceEnvelope.isRuntimeDiagnosticsWired());
        assertFalse(ReleaseEvidenceEnvelope.isRetestWorkflowWired());
        assertFalse(ReleaseEvidenceEnvelope.isAutomaticUploadEnabled());
        assertFalse(ReleaseEvidenceEnvelope.isAndroid13Arm64Verified());
        assertFalse(ReleaseEvidenceEnvelope.isHardwareAccessed());
        assertFalse(ReleaseEvidenceEnvelope.isProductionReady());
        assertFalse(ReleaseEvidenceEnvelope.isTargetHardwareValidated());
    }

    private static ReleaseEvidenceEnvelope.Report report(
            ReleaseEvidenceEnvelope.EvidenceMode mode,
            String ownerDigest,
            List<ReleaseEvidenceEnvelope.DiagnosticFact> facts) {
        return new ReleaseEvidenceEnvelope.Report(
                mode,
                identity(ownerDigest, true, false, false),
                facts);
    }

    private static ReleaseEvidenceEnvelope.Identity identity(
            String ownerDigest,
            boolean privacyConfirmed,
            boolean rawIdentityIncluded,
            boolean automaticUploadEnabled) {
        return new ReleaseEvidenceEnvelope.Identity(
                "android13-hwtest-v1.2.3-rc.4",
                "0123456789abcdef0123456789abcdef01234567",
                DIGEST_A,
                "central-brain-android13-hybrid",
                DIGEST_B,
                "cockpit-a13-lab-01",
                "LAB-20260718-001",
                ownerDigest,
                true,
                privacyConfirmed,
                rawIdentityIncluded,
                automaticUploadEnabled);
    }

    private static List<ReleaseEvidenceEnvelope.DiagnosticFact> allPass() {
        List<ReleaseEvidenceEnvelope.DiagnosticFact> facts = new ArrayList<>();
        for (ReleaseEvidenceEnvelope.DiagnosticCategory category
                : ReleaseEvidenceEnvelope.requiredDiagnosticCategories()) {
            facts.add(fact(category, ReleaseEvidenceEnvelope.DiagnosticStatus.PASS, 0));
        }
        return facts;
    }

    private static ReleaseEvidenceEnvelope.DiagnosticFact fact(
            ReleaseEvidenceEnvelope.DiagnosticCategory category,
            ReleaseEvidenceEnvelope.DiagnosticStatus status,
            int resultCode) {
        return new ReleaseEvidenceEnvelope.DiagnosticFact(
                category, status, resultCode, DIGEST_A);
    }

    private static ReleaseEvidenceEnvelope.DiagnosticFact notRun(
            ReleaseEvidenceEnvelope.DiagnosticCategory category) {
        return new ReleaseEvidenceEnvelope.DiagnosticFact(
                category,
                ReleaseEvidenceEnvelope.DiagnosticStatus.NOT_RUN,
                -1,
                null);
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }
}
