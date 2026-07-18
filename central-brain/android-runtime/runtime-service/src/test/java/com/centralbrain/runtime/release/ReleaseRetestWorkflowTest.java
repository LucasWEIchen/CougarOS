package com.centralbrain.runtime.release;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class ReleaseRetestWorkflowTest {
    private static final String DIGEST_A = digest('a');
    private static final String DIGEST_B = digest('b');
    private static final String DIGEST_C = digest('c');
    private static final String DIGEST_D = digest('d');
    private static final String DIGEST_E = digest('e');
    private static final String DIGEST_F = digest('f');

    @Test
    public void exactIssueStateCatalogIsStableAndOrdered() {
        List<ReleaseRetestWorkflow.IssueState> states =
                ReleaseRetestWorkflow.requiredIssueStates();

        assertEquals(ReleaseRetestWorkflow.ISSUE_STATE_COUNT, states.size());
        assertEquals(ReleaseRetestWorkflow.IssueState.TRIAGE, states.get(0));
        assertEquals(ReleaseRetestWorkflow.IssueState.VERIFIED, states.get(4));
        assertEquals("state/retest", states.get(3).getLabel());
    }

    @Test
    public void maintainerAndTesterCompleteNamedReplacementWorkflow() {
        ReleaseRetestWorkflow.IssueSnapshot snapshot = readyForRetest();
        ReleaseRetestWorkflow.ReplacementRelease replacement = replacement(4, 'b');
        ReleaseRetestWorkflow.Decision requested = ReleaseRetestWorkflow.requestRetest(
                snapshot, ReleaseRetestWorkflow.ActorRole.MAINTAINER, replacement);
        ReleaseRetestWorkflow.Decision verified = ReleaseRetestWorkflow.submitRetest(
                requested.getSnapshot(),
                ReleaseRetestWorkflow.ActorRole.TARGET_TESTER,
                targetReport(replacement, allPass(), true),
                DIGEST_E,
                DIGEST_F);

        assertEquals(ReleaseRetestWorkflow.DecisionCode.RETEST_REQUESTED, requested.getCode());
        assertEquals(ReleaseRetestWorkflow.IssueState.RETEST, requested.getSnapshot().getState());
        assertEquals(1, requested.getSnapshot().getRetestCycle());
        assertEquals(ReleaseRetestWorkflow.DecisionCode.RETEST_VERIFIED, verified.getCode());
        assertEquals(ReleaseRetestWorkflow.IssueState.VERIFIED, verified.getSnapshot().getState());
        assertTrue(verified.isTargetReportAdmitted());
        assertTrue(verified.isIssueCloseEligible());
        assertFalse(verified.isAutomaticIssueCloseAllowed());
        assertFalse(verified.isTargetHardwareValidated());
    }

    @Test
    public void actorAndTransitionMatrixFailsClosed() {
        ReleaseRetestWorkflow.IssueSnapshot triage = issue();
        ReleaseRetestWorkflow.Decision wrongActor = ReleaseRetestWorkflow.advance(
                triage, ReleaseRetestWorkflow.ActorRole.TARGET_TESTER);
        ReleaseRetestWorkflow.Decision invalid = ReleaseRetestWorkflow.requestRetest(
                triage,
                ReleaseRetestWorkflow.ActorRole.MAINTAINER,
                replacement(4, 'b'));

        assertEquals(
                ReleaseRetestWorkflow.DecisionCode.ACTOR_NOT_AUTHORIZED,
                wrongActor.getCode());
        assertEquals(ReleaseRetestWorkflow.DecisionCode.INVALID_TRANSITION, invalid.getCode());
        assertFalse(wrongActor.isAccepted());
        assertEquals(triage.getWorkflowDigest(), wrongActor.getSnapshot().getWorkflowDigest());
    }

    @Test
    public void replacementMustBeStrictlyNewerAndArtifactDistinct() {
        ReleaseRetestWorkflow.IssueSnapshot snapshot = readyForRetest();
        ReleaseRetestWorkflow.Decision sameTag = ReleaseRetestWorkflow.requestRetest(
                snapshot,
                ReleaseRetestWorkflow.ActorRole.MAINTAINER,
                replacement(3, 'b'));
        ReleaseRetestWorkflow.ReplacementRelease reusedArchive =
                new ReleaseRetestWorkflow.ReplacementRelease(
                        new ReleaseRetestWorkflow.ReleaseIdentity(
                                "android13-hwtest-v1.2.3-rc.4",
                                commit('b'),
                                DIGEST_A,
                                DIGEST_B),
                        DIGEST_D);
        ReleaseRetestWorkflow.Decision reused = ReleaseRetestWorkflow.requestRetest(
                snapshot, ReleaseRetestWorkflow.ActorRole.MAINTAINER, reusedArchive);

        assertEquals(
                ReleaseRetestWorkflow.DecisionCode.REPLACEMENT_NOT_NEWER,
                sameTag.getCode());
        assertEquals(
                ReleaseRetestWorkflow.DecisionCode.REPLACEMENT_NOT_NEWER,
                reused.getCode());
        assertFalse(sameTag.isAccepted());
        assertFalse(reused.isAccepted());
    }

    @Test
    public void verificationRequiresMatchingCompleteTargetOwnerEvidence() {
        ReleaseRetestWorkflow.ReplacementRelease replacement = replacement(4, 'b');
        ReleaseRetestWorkflow.IssueSnapshot retest = ReleaseRetestWorkflow.requestRetest(
                readyForRetest(),
                ReleaseRetestWorkflow.ActorRole.MAINTAINER,
                replacement).getSnapshot();
        List<ReleaseEvidenceEnvelope.DiagnosticFact> incomplete = allPass();
        incomplete.set(
                7,
                new ReleaseEvidenceEnvelope.DiagnosticFact(
                        ReleaseEvidenceEnvelope.DiagnosticCategory.MANUAL_SCENARIO_MATRIX,
                        ReleaseEvidenceEnvelope.DiagnosticStatus.NOT_RUN,
                        -1,
                        null));
        ReleaseRetestWorkflow.Decision host = ReleaseRetestWorkflow.submitRetest(
                retest,
                ReleaseRetestWorkflow.ActorRole.TARGET_TESTER,
                hostReport(replacement, allPass()),
                DIGEST_E,
                DIGEST_F);
        ReleaseRetestWorkflow.Decision notRun = ReleaseRetestWorkflow.submitRetest(
                retest,
                ReleaseRetestWorkflow.ActorRole.TARGET_TESTER,
                targetReport(replacement, incomplete, true),
                DIGEST_E,
                DIGEST_F);
        ReleaseRetestWorkflow.Decision noTargetOwner = ReleaseRetestWorkflow.submitRetest(
                retest,
                ReleaseRetestWorkflow.ActorRole.TARGET_TESTER,
                targetReport(replacement, allPass(), false),
                DIGEST_E,
                DIGEST_F);
        ReleaseRetestWorkflow.Decision noDiagnosticsOwner = ReleaseRetestWorkflow.submitRetest(
                retest,
                ReleaseRetestWorkflow.ActorRole.TARGET_TESTER,
                targetReport(replacement, allPass(), true),
                null,
                DIGEST_F);
        ReleaseRetestWorkflow.Decision duplicateOwnerEvidence =
                ReleaseRetestWorkflow.submitRetest(
                        retest,
                        ReleaseRetestWorkflow.ActorRole.TARGET_TESTER,
                        targetReport(replacement, allPass(), true),
                        DIGEST_D,
                        DIGEST_F);

        for (ReleaseRetestWorkflow.Decision decision
                : List.of(
                        host, notRun, noTargetOwner, noDiagnosticsOwner,
                        duplicateOwnerEvidence)) {
            assertEquals(
                    ReleaseRetestWorkflow.DecisionCode.EVIDENCE_REJECTED,
                    decision.getCode());
            assertFalse(decision.isAccepted());
            assertFalse(decision.isIssueCloseEligible());
        }
    }

    @Test
    public void failedRetestReturnsSameIssueToFixReadyAndRequiresNewRelease() {
        ReleaseRetestWorkflow.ReplacementRelease first = replacement(4, 'b');
        ReleaseRetestWorkflow.IssueSnapshot retest = ReleaseRetestWorkflow.requestRetest(
                readyForRetest(),
                ReleaseRetestWorkflow.ActorRole.MAINTAINER,
                first).getSnapshot();
        List<ReleaseEvidenceEnvelope.DiagnosticFact> failedFacts = allPass();
        failedFacts.set(
                5,
                fact(
                        ReleaseEvidenceEnvelope.DiagnosticCategory.RUNTIME_SERVICE,
                        ReleaseEvidenceEnvelope.DiagnosticStatus.FAIL,
                        7));
        ReleaseRetestWorkflow.Decision failed = ReleaseRetestWorkflow.submitRetest(
                retest,
                ReleaseRetestWorkflow.ActorRole.TARGET_TESTER,
                targetReport(first, failedFacts, true),
                DIGEST_E,
                DIGEST_F);
        ReleaseRetestWorkflow.Decision reused = ReleaseRetestWorkflow.requestRetest(
                failed.getSnapshot(),
                ReleaseRetestWorkflow.ActorRole.MAINTAINER,
                first);
        ReleaseRetestWorkflow.Decision next = ReleaseRetestWorkflow.requestRetest(
                failed.getSnapshot(),
                ReleaseRetestWorkflow.ActorRole.MAINTAINER,
                replacement(5, 'c'));

        assertEquals(
                ReleaseRetestWorkflow.DecisionCode.RETEST_FAILED_FIX_REQUIRED,
                failed.getCode());
        assertEquals(ReleaseRetestWorkflow.IssueState.FIX_READY, failed.getSnapshot().getState());
        assertFalse(failed.isTargetReportAdmitted());
        assertEquals(
                ReleaseRetestWorkflow.DecisionCode.REPLACEMENT_NOT_NEWER,
                reused.getCode());
        assertEquals(ReleaseRetestWorkflow.DecisionCode.RETEST_REQUESTED, next.getCode());
        assertEquals(2, next.getSnapshot().getRetestCycle());
    }

    @Test
    public void workflowDigestIsDeterministicAndBindsStateAndEvidence() {
        ReleaseRetestWorkflow.IssueSnapshot first = issue();
        ReleaseRetestWorkflow.IssueSnapshot second = issue();
        ReleaseRetestWorkflow.IssueSnapshot advanced = ReleaseRetestWorkflow.advance(
                first, ReleaseRetestWorkflow.ActorRole.MAINTAINER).getSnapshot();

        assertEquals(first.getWorkflowDigest(), second.getWorkflowDigest());
        assertTrue(first.getWorkflowDigest().matches("[0-9a-f]{64}"));
        assertNotEquals(first.getWorkflowDigest(), advanced.getWorkflowDigest());
    }

    @Test(expected = IllegalArgumentException.class)
    public void workflowRejectsFreeformOrMalformedIdentity() {
        ReleaseRetestWorkflow.IssueSnapshot.open(
                0,
                new ReleaseRetestWorkflow.ReleaseIdentity(
                        "latest release",
                        "not-a-commit",
                        DIGEST_A,
                        DIGEST_B));
    }

    @Test
    public void repositoryClaimsRemainUnpublishedUnmutatedAndUnqualified() {
        assertTrue(ReleaseRetestWorkflow.isStateMachineDefined());
        assertFalse(ReleaseRetestWorkflow.isReplacementReleasePublished());
        assertFalse(ReleaseRetestWorkflow.isTargetReportAdmitted());
        assertFalse(ReleaseRetestWorkflow.isGithubIssueMutationWired());
        assertFalse(ReleaseRetestWorkflow.isAutomaticIssueCloseAllowed());
        assertFalse(ReleaseRetestWorkflow.isAndroid13Arm64Verified());
        assertFalse(ReleaseRetestWorkflow.isHardwareAccessed());
        assertFalse(ReleaseRetestWorkflow.isProductionReady());
        assertFalse(ReleaseRetestWorkflow.isTargetHardwareValidated());
    }

    private static ReleaseRetestWorkflow.IssueSnapshot issue() {
        return ReleaseRetestWorkflow.IssueSnapshot.open(
                53,
                new ReleaseRetestWorkflow.ReleaseIdentity(
                        "android13-hwtest-v1.2.3-rc.3",
                        commit('a'),
                        DIGEST_A,
                        DIGEST_B));
    }

    private static ReleaseRetestWorkflow.IssueSnapshot readyForRetest() {
        ReleaseRetestWorkflow.IssueSnapshot reproduced = ReleaseRetestWorkflow.advance(
                issue(), ReleaseRetestWorkflow.ActorRole.MAINTAINER).getSnapshot();
        return ReleaseRetestWorkflow.advance(
                reproduced, ReleaseRetestWorkflow.ActorRole.MAINTAINER).getSnapshot();
    }

    private static ReleaseRetestWorkflow.ReplacementRelease replacement(
            int rc,
            char seed) {
        return new ReleaseRetestWorkflow.ReplacementRelease(
                new ReleaseRetestWorkflow.ReleaseIdentity(
                        "android13-hwtest-v1.2.3-rc." + rc,
                        commit(seed),
                        digest(seed),
                        digest((char) (seed + 1))),
                DIGEST_D);
    }

    private static ReleaseEvidenceEnvelope.Report targetReport(
            ReleaseRetestWorkflow.ReplacementRelease replacement,
            List<ReleaseEvidenceEnvelope.DiagnosticFact> facts,
            boolean includeTargetOwner) {
        return report(
                ReleaseEvidenceEnvelope.EvidenceMode.TARGET,
                replacement,
                facts,
                includeTargetOwner ? DIGEST_C : null);
    }

    private static ReleaseEvidenceEnvelope.Report hostReport(
            ReleaseRetestWorkflow.ReplacementRelease replacement,
            List<ReleaseEvidenceEnvelope.DiagnosticFact> facts) {
        return report(
                ReleaseEvidenceEnvelope.EvidenceMode.HOST_SYNTHETIC,
                replacement,
                facts,
                DIGEST_C);
    }

    private static ReleaseEvidenceEnvelope.Report report(
            ReleaseEvidenceEnvelope.EvidenceMode mode,
            ReleaseRetestWorkflow.ReplacementRelease replacement,
            List<ReleaseEvidenceEnvelope.DiagnosticFact> facts,
            String targetOwnerDigest) {
        ReleaseRetestWorkflow.ReleaseIdentity identity = replacement.getIdentity();
        return new ReleaseEvidenceEnvelope.Report(
                mode,
                new ReleaseEvidenceEnvelope.Identity(
                        identity.getReleaseTag(),
                        identity.getSourceGitCommit(),
                        identity.getArchiveSha256(),
                        "central-brain-android13-hybrid",
                        identity.getReleaseSetDigest(),
                        "cockpit-a13-lab-01",
                        "LAB-20260718-001",
                        targetOwnerDigest,
                        true,
                        true,
                        false,
                        false),
                facts);
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

    private static String commit(char value) {
        return String.valueOf(value).repeat(40);
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }
}
