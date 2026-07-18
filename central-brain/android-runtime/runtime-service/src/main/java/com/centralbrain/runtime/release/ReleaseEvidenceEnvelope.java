package com.centralbrain.runtime.release;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Metadata-only P9-W07a release and field-diagnostic evidence envelope. */
public final class ReleaseEvidenceEnvelope {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-release-evidence-envelope-v1";
    public static final int DIAGNOSTIC_CATEGORY_COUNT = 8;

    private static final String RELEASE_TAG_PATTERN =
            "android13-hwtest-v[0-9]+\\.[0-9]+\\.[0-9]+-rc\\.[0-9]+";
    private static final String SHA256_PATTERN = "[0-9a-f]{64}";
    private static final String COMMIT_PATTERN = "[0-9a-f]{40}";
    private static final String SHORT_IDENTIFIER_PATTERN =
            "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
    private static final String EVIDENCE_REFERENCE_PATTERN =
            "[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}";
    private static final List<DiagnosticCategory> REQUIRED_DIAGNOSTIC_CATEGORIES =
            Collections.unmodifiableList(List.of(DiagnosticCategory.values()));

    private ReleaseEvidenceEnvelope() {
    }

    public enum EvidenceMode {
        HOST_SYNTHETIC("host_synthetic"),
        TARGET("target");

        private final String canonicalId;

        EvidenceMode(String canonicalId) {
            this.canonicalId = canonicalId;
        }

        public String getCanonicalId() {
            return canonicalId;
        }
    }

    public enum DiagnosticCategory {
        RELEASE_BUNDLE("release.bundle"),
        INSTALLER_DRY_RUN("installer.dry_run"),
        INSTALLER_EXECUTE("installer.execute"),
        DEMO_LAUNCH("demo.launch"),
        CLIENT2_LAUNCH("client2.launch"),
        RUNTIME_SERVICE("runtime.service"),
        DIAGNOSTIC_SERVICE("diagnostics.service"),
        MANUAL_SCENARIO_MATRIX("manual.scenario_matrix");

        private final String canonicalId;

        DiagnosticCategory(String canonicalId) {
            this.canonicalId = canonicalId;
        }

        public String getCanonicalId() {
            return canonicalId;
        }
    }

    public enum DiagnosticStatus {
        PASS,
        FAIL,
        BLOCKED,
        NOT_RUN
    }

    public enum EvaluationCode {
        GITHUB_POLICY_REJECTED,
        HOST_SOFTWARE_ONLY,
        TARGET_OWNER_REVIEW_REQUIRED,
        TARGET_OWNER_REVIEW_ELIGIBLE
    }

    public static final class Identity {
        private final String releaseTag;
        private final String sourceGitCommit;
        private final String archiveSha256;
        private final String deliveryId;
        private final String releaseSetDigest;
        private final String deviceAlias;
        private final String evidenceReference;
        private final String targetOwnerApprovalDigest;
        private final boolean signerCohortObserved;
        private final boolean privacyConfirmed;
        private final boolean rawOrDerivedDeviceIdentityIncluded;
        private final boolean automaticUploadEnabled;

        public Identity(
                String releaseTag,
                String sourceGitCommit,
                String archiveSha256,
                String deliveryId,
                String releaseSetDigest,
                String deviceAlias,
                String evidenceReference,
                String targetOwnerApprovalDigest,
                boolean signerCohortObserved,
                boolean privacyConfirmed,
                boolean rawOrDerivedDeviceIdentityIncluded,
                boolean automaticUploadEnabled) {
            this.releaseTag = requirePattern(
                    releaseTag, RELEASE_TAG_PATTERN, "releaseTag");
            this.sourceGitCommit = requirePattern(
                    sourceGitCommit, COMMIT_PATTERN, "sourceGitCommit");
            this.archiveSha256 = requireDigest(archiveSha256, "archiveSha256");
            this.deliveryId = requirePattern(
                    deliveryId, SHORT_IDENTIFIER_PATTERN, "deliveryId");
            this.releaseSetDigest = requireDigest(releaseSetDigest, "releaseSetDigest");
            this.deviceAlias = requirePattern(
                    deviceAlias, SHORT_IDENTIFIER_PATTERN, "deviceAlias");
            this.evidenceReference = requirePattern(
                    evidenceReference, EVIDENCE_REFERENCE_PATTERN, "evidenceReference");
            this.targetOwnerApprovalDigest = optionalDigest(
                    targetOwnerApprovalDigest, "targetOwnerApprovalDigest");
            this.signerCohortObserved = signerCohortObserved;
            this.privacyConfirmed = privacyConfirmed;
            this.rawOrDerivedDeviceIdentityIncluded = rawOrDerivedDeviceIdentityIncluded;
            this.automaticUploadEnabled = automaticUploadEnabled;
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

        public String getDeliveryId() {
            return deliveryId;
        }

        public String getReleaseSetDigest() {
            return releaseSetDigest;
        }

        public String getDeviceAlias() {
            return deviceAlias;
        }

        public String getEvidenceReference() {
            return evidenceReference;
        }

        public String getTargetOwnerApprovalDigest() {
            return targetOwnerApprovalDigest;
        }

        public boolean isSignerCohortObserved() {
            return signerCohortObserved;
        }

        public boolean isPrivacyConfirmed() {
            return privacyConfirmed;
        }

        public boolean isRawOrDerivedDeviceIdentityIncluded() {
            return rawOrDerivedDeviceIdentityIncluded;
        }

        public boolean isAutomaticUploadEnabled() {
            return automaticUploadEnabled;
        }

        private String canonicalString() {
            return releaseTag + "|" + sourceGitCommit + "|" + archiveSha256 + "|"
                    + deliveryId + "|" + releaseSetDigest + "|" + deviceAlias + "|"
                    + evidenceReference + "|"
                    + (targetOwnerApprovalDigest == null ? "-" : targetOwnerApprovalDigest)
                    + "|" + signerCohortObserved + "|" + privacyConfirmed + "|"
                    + rawOrDerivedDeviceIdentityIncluded + "|" + automaticUploadEnabled;
        }
    }

    public static final class DiagnosticFact {
        private final DiagnosticCategory category;
        private final DiagnosticStatus status;
        private final int resultCode;
        private final String detailDigest;

        public DiagnosticFact(
                DiagnosticCategory category,
                DiagnosticStatus status,
                int resultCode,
                String detailDigest) {
            this.category = Objects.requireNonNull(category, "category");
            this.status = Objects.requireNonNull(status, "status");
            if (status == DiagnosticStatus.NOT_RUN) {
                if (resultCode != -1 || detailDigest != null) {
                    throw new IllegalArgumentException(
                            "not-run diagnostic requires resultCode=-1 and no digest");
                }
            } else {
                if (resultCode < 0 || resultCode > 255) {
                    throw new IllegalArgumentException("diagnostic resultCode out of range");
                }
                if (status == DiagnosticStatus.PASS && resultCode != 0) {
                    throw new IllegalArgumentException("passing diagnostic requires resultCode=0");
                }
                if ((status == DiagnosticStatus.FAIL || status == DiagnosticStatus.BLOCKED)
                        && resultCode == 0) {
                    throw new IllegalArgumentException(
                            "failed or blocked diagnostic requires non-zero resultCode");
                }
                requireDigest(detailDigest, "detailDigest");
            }
            this.resultCode = resultCode;
            this.detailDigest = detailDigest;
        }

        public DiagnosticCategory getCategory() {
            return category;
        }

        public DiagnosticStatus getStatus() {
            return status;
        }

        public int getResultCode() {
            return resultCode;
        }

        public String getDetailDigest() {
            return detailDigest;
        }

        private String canonicalString() {
            return category.canonicalId + ":" + status.name() + ":" + resultCode + ":"
                    + (detailDigest == null ? "-" : detailDigest);
        }
    }

    public static final class Report {
        private final EvidenceMode evidenceMode;
        private final Identity identity;
        private final List<DiagnosticFact> diagnosticFacts;
        private final String reportDigest;

        public Report(
                EvidenceMode evidenceMode,
                Identity identity,
                List<DiagnosticFact> diagnosticFacts) {
            this.evidenceMode = Objects.requireNonNull(evidenceMode, "evidenceMode");
            this.identity = Objects.requireNonNull(identity, "identity");
            this.diagnosticFacts = validateDiagnosticFacts(diagnosticFacts);
            this.reportDigest = sha256(canonicalString());
        }

        public EvidenceMode getEvidenceMode() {
            return evidenceMode;
        }

        public Identity getIdentity() {
            return identity;
        }

        public List<DiagnosticFact> getDiagnosticFacts() {
            return diagnosticFacts;
        }

        public String getReportDigest() {
            return reportDigest;
        }

        public int executedDiagnosticCount() {
            int count = 0;
            for (DiagnosticFact fact : diagnosticFacts) {
                if (fact.status != DiagnosticStatus.NOT_RUN) {
                    count++;
                }
            }
            return count;
        }

        private String canonicalString() {
            StringBuilder builder = new StringBuilder();
            builder.append(PROFILE_ID).append('|').append(SCHEMA_VERSION).append('|')
                    .append(evidenceMode.canonicalId).append('|')
                    .append(identity.canonicalString());
            for (DiagnosticFact fact : diagnosticFacts) {
                builder.append('|').append(fact.canonicalString());
            }
            return builder.toString();
        }
    }

    public static final class Evaluation {
        private final EvaluationCode code;
        private final boolean githubSafe;
        private final boolean targetOwnerReviewEligible;
        private final String reportDigest;

        private Evaluation(
                EvaluationCode code,
                boolean githubSafe,
                boolean targetOwnerReviewEligible,
                String reportDigest) {
            this.code = code;
            this.githubSafe = githubSafe;
            this.targetOwnerReviewEligible = targetOwnerReviewEligible;
            this.reportDigest = reportDigest;
        }

        public EvaluationCode getCode() {
            return code;
        }

        public boolean isGithubSafe() {
            return githubSafe;
        }

        public boolean isTargetOwnerReviewEligible() {
            return targetOwnerReviewEligible;
        }

        public String getReportDigest() {
            return reportDigest;
        }

        public boolean isProductionReady() {
            return false;
        }

        public boolean isTargetHardwareValidated() {
            return false;
        }
    }

    public static Evaluation evaluate(Report report) {
        Objects.requireNonNull(report, "report");
        Identity identity = report.identity;
        boolean githubSafe = identity.privacyConfirmed
                && !identity.rawOrDerivedDeviceIdentityIncluded
                && !identity.automaticUploadEnabled;
        if (!githubSafe) {
            return new Evaluation(
                    EvaluationCode.GITHUB_POLICY_REJECTED,
                    false,
                    false,
                    report.reportDigest);
        }
        if (report.evidenceMode == EvidenceMode.HOST_SYNTHETIC) {
            return new Evaluation(
                    EvaluationCode.HOST_SOFTWARE_ONLY,
                    true,
                    false,
                    report.reportDigest);
        }
        boolean targetEligible = identity.targetOwnerApprovalDigest != null
                && report.executedDiagnosticCount() == DIAGNOSTIC_CATEGORY_COUNT;
        return new Evaluation(
                targetEligible
                        ? EvaluationCode.TARGET_OWNER_REVIEW_ELIGIBLE
                        : EvaluationCode.TARGET_OWNER_REVIEW_REQUIRED,
                true,
                targetEligible,
                report.reportDigest);
    }

    public static List<DiagnosticCategory> requiredDiagnosticCategories() {
        return REQUIRED_DIAGNOSTIC_CATEGORIES;
    }

    public static boolean isContractDefined() {
        return true;
    }

    public static boolean isTargetOwnerApproved() {
        return false;
    }

    public static boolean isTargetReportAdmitted() {
        return false;
    }

    public static boolean isRuntimeDiagnosticsWired() {
        return false;
    }

    public static boolean isRetestWorkflowWired() {
        return false;
    }

    public static boolean isAutomaticUploadEnabled() {
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

    private static List<DiagnosticFact> validateDiagnosticFacts(
            List<DiagnosticFact> diagnosticFacts) {
        if (diagnosticFacts == null
                || diagnosticFacts.size() != DIAGNOSTIC_CATEGORY_COUNT) {
            throw new IllegalArgumentException("exact diagnostic fact set required");
        }
        List<DiagnosticFact> copy = new ArrayList<>(diagnosticFacts.size());
        for (int index = 0; index < REQUIRED_DIAGNOSTIC_CATEGORIES.size(); index++) {
            DiagnosticFact fact = Objects.requireNonNull(
                    diagnosticFacts.get(index), "diagnosticFact");
            if (fact.category != REQUIRED_DIAGNOSTIC_CATEGORIES.get(index)) {
                throw new IllegalArgumentException(
                        "diagnostic facts must use the exact ordered category catalog");
            }
            copy.add(fact);
        }
        return Collections.unmodifiableList(copy);
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
