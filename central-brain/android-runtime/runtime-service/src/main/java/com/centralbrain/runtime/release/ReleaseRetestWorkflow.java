package com.centralbrain.runtime.release;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Metadata-only P9-W07c replacement-release and issue/retest state machine. */
public final class ReleaseRetestWorkflow {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-release-retest-workflow-v1";
    public static final int ISSUE_STATE_COUNT = 5;
    public static final int TRANSITION_COUNT = 5;

    private static final String RELEASE_TAG_PATTERN =
            "android13-hwtest-v[0-9]+\\.[0-9]+\\.[0-9]+-rc\\.[0-9]+";
    private static final Pattern RELEASE_VERSION_PATTERN = Pattern.compile(
            "android13-hwtest-v([0-9]+)\\.([0-9]+)\\.([0-9]+)-rc\\.([0-9]+)");
    private static final String SHA256_PATTERN = "[0-9a-f]{64}";
    private static final String COMMIT_PATTERN = "[0-9a-f]{40}";
    private static final List<IssueState> ISSUE_STATES =
            Collections.unmodifiableList(List.of(IssueState.values()));

    private ReleaseRetestWorkflow() {
    }

    public enum IssueState {
        TRIAGE("state/triage"),
        REPRODUCED("state/reproduced"),
        FIX_READY("state/fix-ready"),
        RETEST("state/retest"),
        VERIFIED("state/verified");

        private final String label;

        IssueState(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum ActorRole {
        MAINTAINER,
        TARGET_TESTER
    }

    public enum DecisionCode {
        ADVANCED,
        RETEST_REQUESTED,
        RETEST_VERIFIED,
        RETEST_FAILED_FIX_REQUIRED,
        INVALID_TRANSITION,
        ACTOR_NOT_AUTHORIZED,
        REPLACEMENT_REQUIRED,
        REPLACEMENT_NOT_NEWER,
        EVIDENCE_REJECTED
    }

    public static final class ReleaseIdentity {
        private final String releaseTag;
        private final String sourceGitCommit;
        private final String archiveSha256;
        private final String releaseSetDigest;

        public ReleaseIdentity(
                String releaseTag,
                String sourceGitCommit,
                String archiveSha256,
                String releaseSetDigest) {
            this.releaseTag = requirePattern(
                    releaseTag, RELEASE_TAG_PATTERN, "releaseTag");
            this.sourceGitCommit = requirePattern(
                    sourceGitCommit, COMMIT_PATTERN, "sourceGitCommit");
            this.archiveSha256 = requireDigest(archiveSha256, "archiveSha256");
            this.releaseSetDigest = requireDigest(releaseSetDigest, "releaseSetDigest");
        }

        public String getReleaseTag() {
            return releaseTag;
        }

        public String getSourceGitCommit() {
            return sourceGitCommit;
        }

        public String getArchiveSha256() {
            return archiveSha256;
        }

        public String getReleaseSetDigest() {
            return releaseSetDigest;
        }

        private String canonicalString() {
            return releaseTag + "|" + sourceGitCommit + "|" + archiveSha256 + "|"
                    + releaseSetDigest;
        }
    }

    public static final class ReplacementRelease {
        private final ReleaseIdentity identity;
        private final String releaseOwnerApprovalDigest;

        public ReplacementRelease(
                ReleaseIdentity identity,
                String releaseOwnerApprovalDigest) {
            this.identity = Objects.requireNonNull(identity, "identity");
            this.releaseOwnerApprovalDigest = requireDigest(
                    releaseOwnerApprovalDigest, "releaseOwnerApprovalDigest");
        }

        public ReleaseIdentity getIdentity() {
            return identity;
        }

        public String getReleaseOwnerApprovalDigest() {
            return releaseOwnerApprovalDigest;
        }

        private String canonicalString() {
            return identity.canonicalString() + "|" + releaseOwnerApprovalDigest;
        }
    }

    public static final class IssueSnapshot {
        private final long issueNumber;
        private final IssueState state;
        private final ReleaseIdentity originalRelease;
        private final ReplacementRelease replacementRelease;
        private final int retestCycle;
        private final String lastReportDigest;
        private final String workflowDigest;

        private IssueSnapshot(
                long issueNumber,
                IssueState state,
                ReleaseIdentity originalRelease,
                ReplacementRelease replacementRelease,
                int retestCycle,
                String lastReportDigest) {
            if (issueNumber < 1 || issueNumber > 999_999_999L) {
                throw new IllegalArgumentException("issueNumber out of range");
            }
            if (retestCycle < 0 || retestCycle > 999) {
                throw new IllegalArgumentException("retestCycle out of range");
            }
            this.issueNumber = issueNumber;
            this.state = Objects.requireNonNull(state, "state");
            this.originalRelease = Objects.requireNonNull(originalRelease, "originalRelease");
            this.replacementRelease = replacementRelease;
            this.retestCycle = retestCycle;
            this.lastReportDigest = optionalDigest(lastReportDigest, "lastReportDigest");
            this.workflowDigest = sha256(canonicalString());
        }

        public static IssueSnapshot open(long issueNumber, ReleaseIdentity originalRelease) {
            return new IssueSnapshot(
                    issueNumber, IssueState.TRIAGE, originalRelease, null, 0, null);
        }

        public long getIssueNumber() {
            return issueNumber;
        }

        public IssueState getState() {
            return state;
        }

        public ReleaseIdentity getOriginalRelease() {
            return originalRelease;
        }

        public ReplacementRelease getReplacementRelease() {
            return replacementRelease;
        }

        public int getRetestCycle() {
            return retestCycle;
        }

        public String getLastReportDigest() {
            return lastReportDigest;
        }

        public String getWorkflowDigest() {
            return workflowDigest;
        }

        private String canonicalString() {
            return PROFILE_ID + "|" + SCHEMA_VERSION + "|" + issueNumber + "|"
                    + state.label + "|" + originalRelease.canonicalString() + "|"
                    + (replacementRelease == null
                            ? "-" : replacementRelease.canonicalString())
                    + "|" + retestCycle + "|"
                    + (lastReportDigest == null ? "-" : lastReportDigest);
        }
    }

    public static final class Decision {
        private final DecisionCode code;
        private final IssueSnapshot snapshot;
        private final boolean accepted;
        private final boolean targetReportAdmitted;
        private final boolean issueCloseEligible;

        private Decision(
                DecisionCode code,
                IssueSnapshot snapshot,
                boolean accepted,
                boolean targetReportAdmitted,
                boolean issueCloseEligible) {
            this.code = Objects.requireNonNull(code, "code");
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.accepted = accepted;
            this.targetReportAdmitted = targetReportAdmitted;
            this.issueCloseEligible = issueCloseEligible;
        }

        public DecisionCode getCode() {
            return code;
        }

        public IssueSnapshot getSnapshot() {
            return snapshot;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public boolean isTargetReportAdmitted() {
            return targetReportAdmitted;
        }

        public boolean isIssueCloseEligible() {
            return issueCloseEligible;
        }

        public boolean isAutomaticIssueCloseAllowed() {
            return false;
        }

        public boolean isProductionReady() {
            return false;
        }

        public boolean isTargetHardwareValidated() {
            return false;
        }
    }

    public static Decision advance(IssueSnapshot snapshot, ActorRole actor) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(actor, "actor");
        if (actor != ActorRole.MAINTAINER) {
            return rejected(DecisionCode.ACTOR_NOT_AUTHORIZED, snapshot);
        }
        IssueState target;
        if (snapshot.state == IssueState.TRIAGE) {
            target = IssueState.REPRODUCED;
        } else if (snapshot.state == IssueState.REPRODUCED) {
            target = IssueState.FIX_READY;
        } else {
            return rejected(DecisionCode.INVALID_TRANSITION, snapshot);
        }
        return accepted(
                DecisionCode.ADVANCED,
                copy(snapshot, target, snapshot.replacementRelease,
                        snapshot.retestCycle, snapshot.lastReportDigest),
                false,
                false);
    }

    public static Decision requestRetest(
            IssueSnapshot snapshot,
            ActorRole actor,
            ReplacementRelease replacementRelease) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(actor, "actor");
        if (actor != ActorRole.MAINTAINER) {
            return rejected(DecisionCode.ACTOR_NOT_AUTHORIZED, snapshot);
        }
        if (snapshot.state != IssueState.FIX_READY) {
            return rejected(DecisionCode.INVALID_TRANSITION, snapshot);
        }
        if (replacementRelease == null) {
            return rejected(DecisionCode.REPLACEMENT_REQUIRED, snapshot);
        }
        ReleaseIdentity current = snapshot.replacementRelease == null
                ? snapshot.originalRelease : snapshot.replacementRelease.identity;
        if (!isStrictlyNewer(replacementRelease.identity.releaseTag, current.releaseTag)
                || sameArtifactIdentity(replacementRelease.identity, current)) {
            return rejected(DecisionCode.REPLACEMENT_NOT_NEWER, snapshot);
        }
        return accepted(
                DecisionCode.RETEST_REQUESTED,
                copy(snapshot, IssueState.RETEST, replacementRelease,
                        snapshot.retestCycle + 1, null),
                false,
                false);
    }

    public static Decision submitRetest(
            IssueSnapshot snapshot,
            ActorRole actor,
            ReleaseEvidenceEnvelope.Report report,
            String diagnosticsOwnerApprovalDigest,
            String testerVerificationDigest) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(actor, "actor");
        if (actor != ActorRole.TARGET_TESTER) {
            return rejected(DecisionCode.ACTOR_NOT_AUTHORIZED, snapshot);
        }
        if (snapshot.state != IssueState.RETEST) {
            return rejected(DecisionCode.INVALID_TRANSITION, snapshot);
        }
        if (!isDigest(diagnosticsOwnerApprovalDigest)
                || !isDigest(testerVerificationDigest)
                || report == null
                || snapshot.replacementRelease == null
                || !reportMatchesReplacement(report, snapshot.replacementRelease.identity)) {
            return rejected(DecisionCode.EVIDENCE_REJECTED, snapshot);
        }
        ReleaseEvidenceEnvelope.Evaluation evaluation = ReleaseEvidenceEnvelope.evaluate(report);
        if (report.getEvidenceMode() != ReleaseEvidenceEnvelope.EvidenceMode.TARGET
                || !evaluation.isGithubSafe()
                || !evaluation.isTargetOwnerReviewEligible()
                || !report.getIdentity().isSignerCohortObserved()
                || !approvalDigestsAreDistinct(
                        report.getIdentity().getTargetOwnerApprovalDigest(),
                        snapshot.replacementRelease.releaseOwnerApprovalDigest,
                        diagnosticsOwnerApprovalDigest,
                        testerVerificationDigest)) {
            return rejected(DecisionCode.EVIDENCE_REJECTED, snapshot);
        }
        boolean allPass = true;
        for (ReleaseEvidenceEnvelope.DiagnosticFact fact : report.getDiagnosticFacts()) {
            if (fact.getStatus() != ReleaseEvidenceEnvelope.DiagnosticStatus.PASS) {
                allPass = false;
                break;
            }
        }
        if (allPass) {
            return accepted(
                    DecisionCode.RETEST_VERIFIED,
                    copy(snapshot, IssueState.VERIFIED, snapshot.replacementRelease,
                            snapshot.retestCycle, report.getReportDigest()),
                    true,
                    true);
        }
        return accepted(
                DecisionCode.RETEST_FAILED_FIX_REQUIRED,
                copy(snapshot, IssueState.FIX_READY, snapshot.replacementRelease,
                        snapshot.retestCycle, report.getReportDigest()),
                false,
                false);
    }

    public static List<IssueState> requiredIssueStates() {
        return ISSUE_STATES;
    }

    public static boolean isStateMachineDefined() {
        return true;
    }

    public static boolean isReplacementReleasePublished() {
        return false;
    }

    public static boolean isTargetReportAdmitted() {
        return false;
    }

    public static boolean isGithubIssueMutationWired() {
        return false;
    }

    public static boolean isAutomaticIssueCloseAllowed() {
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

    private static boolean reportMatchesReplacement(
            ReleaseEvidenceEnvelope.Report report,
            ReleaseIdentity replacement) {
        ReleaseEvidenceEnvelope.Identity identity = report.getIdentity();
        return identity.getReleaseTag().equals(replacement.releaseTag)
                && identity.getSourceGitCommit().equals(replacement.sourceGitCommit)
                && identity.getArchiveSha256().equals(replacement.archiveSha256)
                && identity.getReleaseSetDigest().equals(replacement.releaseSetDigest);
    }

    private static boolean sameArtifactIdentity(ReleaseIdentity left, ReleaseIdentity right) {
        return left.sourceGitCommit.equals(right.sourceGitCommit)
                || left.archiveSha256.equals(right.archiveSha256)
                || left.releaseSetDigest.equals(right.releaseSetDigest);
    }

    private static boolean approvalDigestsAreDistinct(
            String targetOwner,
            String releaseOwner,
            String diagnosticsOwner,
            String tester) {
        String[] digests = {targetOwner, releaseOwner, diagnosticsOwner, tester};
        for (int left = 0; left < digests.length; left++) {
            if (!isDigest(digests[left])) {
                return false;
            }
            for (int right = left + 1; right < digests.length; right++) {
                if (digests[left].equals(digests[right])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isStrictlyNewer(String candidateTag, String currentTag) {
        long[] candidate = parseVersion(candidateTag);
        long[] current = parseVersion(currentTag);
        for (int index = 0; index < candidate.length; index++) {
            if (candidate[index] != current[index]) {
                return candidate[index] > current[index];
            }
        }
        return false;
    }

    private static long[] parseVersion(String releaseTag) {
        Matcher matcher = RELEASE_VERSION_PATTERN.matcher(releaseTag);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("releaseTag has invalid format");
        }
        long[] version = new long[4];
        try {
            for (int index = 0; index < version.length; index++) {
                version[index] = Long.parseLong(matcher.group(index + 1));
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("releaseTag version out of range", exception);
        }
        return version;
    }

    private static IssueSnapshot copy(
            IssueSnapshot source,
            IssueState state,
            ReplacementRelease replacementRelease,
            int retestCycle,
            String lastReportDigest) {
        return new IssueSnapshot(
                source.issueNumber,
                state,
                source.originalRelease,
                replacementRelease,
                retestCycle,
                lastReportDigest);
    }

    private static Decision accepted(
            DecisionCode code,
            IssueSnapshot snapshot,
            boolean targetReportAdmitted,
            boolean issueCloseEligible) {
        return new Decision(
                code, snapshot, true, targetReportAdmitted, issueCloseEligible);
    }

    private static Decision rejected(DecisionCode code, IssueSnapshot snapshot) {
        return new Decision(code, snapshot, false, false, false);
    }

    private static String requirePattern(String value, String pattern, String name) {
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException(name + " has invalid format");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        return requirePattern(value, SHA256_PATTERN, name);
    }

    private static String optionalDigest(String value, String name) {
        return value == null ? null : requireDigest(value, name);
    }

    private static boolean isDigest(String value) {
        return value != null && value.matches(SHA256_PATTERN);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
