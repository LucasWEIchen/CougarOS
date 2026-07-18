package com.centralbrain.runtime.release;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Fail-closed P9-W05a admission contract for an Android application release set. */
public final class ProductionReleaseAdmission {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-production-release-admission-v1";
    public static final int REQUIRED_PACKAGE_COUNT = 3;

    private static final String DIGEST_PATTERN = "[0-9a-f]{64}";
    private static final String ID_PATTERN = "[a-z0-9][a-z0-9.-]{0,95}";
    private static final List<PackageIdentity> REQUIRED_PACKAGES = List.of(
            new PackageIdentity("runtime-service", "com.centralbrain.runtime", true),
            new PackageIdentity("demo-hmi", "com.centralbrain.demo", false),
            new PackageIdentity("client2-demo", "com.tuanjie.urasclient2", false));

    private ProductionReleaseAdmission() {
    }

    public enum Mode {
        UPGRADE,
        ROLLBACK
    }

    public enum DecisionCode {
        ADMITTED,
        RELEASE_ID_INVALID,
        RELEASE_EVIDENCE_INVALID,
        PACKAGE_SET_MISMATCH,
        PACKAGE_NAME_MISMATCH,
        PACKAGE_EVIDENCE_INVALID,
        PRODUCTION_SIGNER_APPROVAL_MISSING,
        RELEASE_OWNER_APPROVAL_MISSING,
        SIGNER_MISMATCH,
        SIGNER_COHORT_MISMATCH,
        RELEASE_SEQUENCE_INVALID,
        PACKAGE_VERSION_INVALID,
        PACKAGE_VERSION_UNCHANGED,
        DATA_SCHEMA_INVALID,
        DATA_SCHEMA_UNREADABLE,
        DATA_SCHEMA_DOWNGRADE,
        MIGRATION_EVIDENCE_MISSING,
        ROLLBACK_OWNER_APPROVAL_MISSING,
        ROLLBACK_DECISION_MISSING,
        ROLLBACK_DATA_COMPATIBILITY_MISSING
    }

    public static final class PackageIdentity {
        private final String packageId;
        private final String packageName;
        private final boolean durableDataOwner;

        private PackageIdentity(String packageId, String packageName, boolean durableDataOwner) {
            this.packageId = packageId;
            this.packageName = packageName;
            this.durableDataOwner = durableDataOwner;
        }

        public String getPackageId() {
            return packageId;
        }

        public String getPackageName() {
            return packageName;
        }

        public boolean isDurableDataOwner() {
            return durableDataOwner;
        }
    }

    public static final class PackageSnapshot {
        private final String packageId;
        private final String packageName;
        private final long versionCode;
        private final String signerDigest;
        private final String artifactDigest;
        private final int dataSchemaVersion;
        private final int minimumReadableDataSchemaVersion;
        private final int maximumReadableDataSchemaVersion;

        public PackageSnapshot(
                String packageId,
                String packageName,
                long versionCode,
                String signerDigest,
                String artifactDigest,
                int dataSchemaVersion,
                int minimumReadableDataSchemaVersion,
                int maximumReadableDataSchemaVersion) {
            this.packageId = packageId;
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.signerDigest = signerDigest;
            this.artifactDigest = artifactDigest;
            this.dataSchemaVersion = dataSchemaVersion;
            this.minimumReadableDataSchemaVersion = minimumReadableDataSchemaVersion;
            this.maximumReadableDataSchemaVersion = maximumReadableDataSchemaVersion;
        }

        public String getPackageId() {
            return packageId;
        }

        public String getPackageName() {
            return packageName;
        }

        public long getVersionCode() {
            return versionCode;
        }

        public String getSignerDigest() {
            return signerDigest;
        }

        public String getArtifactDigest() {
            return artifactDigest;
        }

        public int getDataSchemaVersion() {
            return dataSchemaVersion;
        }

        public int getMinimumReadableDataSchemaVersion() {
            return minimumReadableDataSchemaVersion;
        }

        public int getMaximumReadableDataSchemaVersion() {
            return maximumReadableDataSchemaVersion;
        }

        private String canonicalForm() {
            return packageId + '|' + packageName + '|' + versionCode + '|'
                    + signerDigest + '|' + artifactDigest + '|' + dataSchemaVersion + '|'
                    + minimumReadableDataSchemaVersion + '|'
                    + maximumReadableDataSchemaVersion;
        }
    }

    public static final class ReleaseSet {
        private final String releaseId;
        private final long releaseSequence;
        private final String sourceCommitDigest;
        private final String archiveDigest;
        private final List<PackageSnapshot> packages;

        public ReleaseSet(
                String releaseId,
                long releaseSequence,
                String sourceCommitDigest,
                String archiveDigest,
                List<PackageSnapshot> packages) {
            this.releaseId = releaseId;
            this.releaseSequence = releaseSequence;
            this.sourceCommitDigest = sourceCommitDigest;
            this.archiveDigest = archiveDigest;
            this.packages = packages == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(packages));
        }

        public String getReleaseId() {
            return releaseId;
        }

        public long getReleaseSequence() {
            return releaseSequence;
        }

        public String getSourceCommitDigest() {
            return sourceCommitDigest;
        }

        public String getArchiveDigest() {
            return archiveDigest;
        }

        public List<PackageSnapshot> getPackages() {
            return packages;
        }

        private String canonicalForm() {
            StringBuilder builder = new StringBuilder()
                    .append(releaseId).append('|').append(releaseSequence).append('|')
                    .append(sourceCommitDigest).append('|').append(archiveDigest);
            for (PackageSnapshot packageSnapshot : packages) {
                builder.append('|').append(packageSnapshot.canonicalForm());
            }
            return builder.toString();
        }
    }

    public static final class AdmissionRequest {
        private final Mode mode;
        private final String productionSignerApprovalDigest;
        private final String releaseOwnerApprovalDigest;
        private final String migrationEvidenceDigest;
        private final String rollbackOwnerApprovalDigest;
        private final String rollbackDecisionDigest;
        private final String rollbackDataCompatibilityDigest;

        public AdmissionRequest(
                Mode mode,
                String productionSignerApprovalDigest,
                String releaseOwnerApprovalDigest,
                String migrationEvidenceDigest,
                String rollbackOwnerApprovalDigest,
                String rollbackDecisionDigest,
                String rollbackDataCompatibilityDigest) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.productionSignerApprovalDigest = productionSignerApprovalDigest;
            this.releaseOwnerApprovalDigest = releaseOwnerApprovalDigest;
            this.migrationEvidenceDigest = migrationEvidenceDigest;
            this.rollbackOwnerApprovalDigest = rollbackOwnerApprovalDigest;
            this.rollbackDecisionDigest = rollbackDecisionDigest;
            this.rollbackDataCompatibilityDigest = rollbackDataCompatibilityDigest;
        }

        public Mode getMode() {
            return mode;
        }

        private String canonicalForm() {
            return mode.name() + '|' + nullToEmpty(productionSignerApprovalDigest) + '|'
                    + nullToEmpty(releaseOwnerApprovalDigest) + '|'
                    + nullToEmpty(migrationEvidenceDigest) + '|'
                    + nullToEmpty(rollbackOwnerApprovalDigest) + '|'
                    + nullToEmpty(rollbackDecisionDigest) + '|'
                    + nullToEmpty(rollbackDataCompatibilityDigest);
        }
    }

    public static final class Decision {
        private final DecisionCode code;
        private final Mode mode;
        private final String decisionDigest;

        private Decision(DecisionCode code, Mode mode, String decisionDigest) {
            this.code = code;
            this.mode = mode;
            this.decisionDigest = decisionDigest;
        }

        public DecisionCode getCode() {
            return code;
        }

        public Mode getMode() {
            return mode;
        }

        public String getDecisionDigest() {
            return decisionDigest;
        }

        public boolean isAdmitted() {
            return code == DecisionCode.ADMITTED;
        }

        public boolean mutatesDatabase() {
            return false;
        }

        public boolean installsPackages() {
            return false;
        }

        public boolean uninstallsPackages() {
            return false;
        }

        public boolean executesRollback() {
            return false;
        }
    }

    public static List<PackageIdentity> requiredPackages() {
        return REQUIRED_PACKAGES;
    }

    public static Decision evaluate(
            ReleaseSet installed,
            ReleaseSet candidate,
            AdmissionRequest request) {
        Objects.requireNonNull(installed, "installed");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(request, "request");

        DecisionCode code = validateReleaseIdentity(installed);
        if (code == DecisionCode.ADMITTED) {
            code = validateReleaseIdentity(candidate);
        }
        if (code == DecisionCode.ADMITTED) {
            code = validatePackageSet(installed);
        }
        if (code == DecisionCode.ADMITTED) {
            code = validatePackageSet(candidate);
        }
        if (code == DecisionCode.ADMITTED
                && !isDigest(request.productionSignerApprovalDigest)) {
            code = DecisionCode.PRODUCTION_SIGNER_APPROVAL_MISSING;
        }
        if (code == DecisionCode.ADMITTED
                && !isDigest(request.releaseOwnerApprovalDigest)) {
            code = DecisionCode.RELEASE_OWNER_APPROVAL_MISSING;
        }
        if (code == DecisionCode.ADMITTED) {
            code = validateSigners(installed, candidate);
        }
        if (code == DecisionCode.ADMITTED) {
            code = request.mode == Mode.UPGRADE
                    ? validateUpgrade(installed, candidate, request)
                    : validateRollback(installed, candidate, request);
        }
        return new Decision(
                code,
                request.mode,
                sha256(SCHEMA_VERSION + "|" + PROFILE_ID + "|" + code.name() + '|'
                        + installed.canonicalForm() + '|' + candidate.canonicalForm() + '|'
                        + request.canonicalForm()));
    }

    private static DecisionCode validateReleaseIdentity(ReleaseSet releaseSet) {
        if (releaseSet.releaseId == null || !releaseSet.releaseId.matches(ID_PATTERN)
                || releaseSet.releaseSequence < 1L) {
            return DecisionCode.RELEASE_ID_INVALID;
        }
        if (!isDigest(releaseSet.sourceCommitDigest) || !isDigest(releaseSet.archiveDigest)) {
            return DecisionCode.RELEASE_EVIDENCE_INVALID;
        }
        return DecisionCode.ADMITTED;
    }

    private static DecisionCode validatePackageSet(ReleaseSet releaseSet) {
        if (releaseSet.packages.size() != REQUIRED_PACKAGE_COUNT) {
            return DecisionCode.PACKAGE_SET_MISMATCH;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < REQUIRED_PACKAGE_COUNT; index++) {
            PackageIdentity required = REQUIRED_PACKAGES.get(index);
            PackageSnapshot actual = releaseSet.packages.get(index);
            if (!required.packageId.equals(actual.packageId) || !seen.add(actual.packageId)) {
                return DecisionCode.PACKAGE_SET_MISMATCH;
            }
            if (!required.packageName.equals(actual.packageName)) {
                return DecisionCode.PACKAGE_NAME_MISMATCH;
            }
            if (actual.versionCode < 1L || !isDigest(actual.signerDigest)
                    || !isDigest(actual.artifactDigest)) {
                return DecisionCode.PACKAGE_EVIDENCE_INVALID;
            }
            if (required.durableDataOwner) {
                if (actual.dataSchemaVersion < 1
                        || actual.minimumReadableDataSchemaVersion < 1
                        || actual.maximumReadableDataSchemaVersion
                        < actual.minimumReadableDataSchemaVersion) {
                    return DecisionCode.DATA_SCHEMA_INVALID;
                }
            } else if (actual.dataSchemaVersion != 0
                    || actual.minimumReadableDataSchemaVersion != 0
                    || actual.maximumReadableDataSchemaVersion != 0) {
                return DecisionCode.DATA_SCHEMA_INVALID;
            }
        }
        return DecisionCode.ADMITTED;
    }

    private static DecisionCode validateSigners(ReleaseSet installed, ReleaseSet candidate) {
        String cohortSigner = null;
        for (int index = 0; index < REQUIRED_PACKAGE_COUNT; index++) {
            PackageSnapshot installedPackage = installed.packages.get(index);
            PackageSnapshot candidatePackage = candidate.packages.get(index);
            if (!installedPackage.signerDigest.equals(candidatePackage.signerDigest)) {
                return DecisionCode.SIGNER_MISMATCH;
            }
            if (cohortSigner == null) {
                cohortSigner = candidatePackage.signerDigest;
            } else if (!cohortSigner.equals(candidatePackage.signerDigest)) {
                return DecisionCode.SIGNER_COHORT_MISMATCH;
            }
        }
        return DecisionCode.ADMITTED;
    }

    private static DecisionCode validateUpgrade(
            ReleaseSet installed,
            ReleaseSet candidate,
            AdmissionRequest request) {
        if (candidate.releaseSequence <= installed.releaseSequence) {
            return DecisionCode.RELEASE_SEQUENCE_INVALID;
        }
        boolean packageIncreased = false;
        for (int index = 0; index < REQUIRED_PACKAGE_COUNT; index++) {
            long installedVersion = installed.packages.get(index).versionCode;
            long candidateVersion = candidate.packages.get(index).versionCode;
            if (candidateVersion < installedVersion) {
                return DecisionCode.PACKAGE_VERSION_INVALID;
            }
            packageIncreased |= candidateVersion > installedVersion;
        }
        if (!packageIncreased) {
            return DecisionCode.PACKAGE_VERSION_UNCHANGED;
        }
        PackageSnapshot installedRuntime = installed.packages.get(0);
        PackageSnapshot candidateRuntime = candidate.packages.get(0);
        if (!canRead(candidateRuntime, installedRuntime.dataSchemaVersion)) {
            return DecisionCode.DATA_SCHEMA_UNREADABLE;
        }
        if (candidateRuntime.dataSchemaVersion < installedRuntime.dataSchemaVersion) {
            return DecisionCode.DATA_SCHEMA_DOWNGRADE;
        }
        if (candidateRuntime.dataSchemaVersion > installedRuntime.dataSchemaVersion
                && !isDigest(request.migrationEvidenceDigest)) {
            return DecisionCode.MIGRATION_EVIDENCE_MISSING;
        }
        return DecisionCode.ADMITTED;
    }

    private static DecisionCode validateRollback(
            ReleaseSet installed,
            ReleaseSet candidate,
            AdmissionRequest request) {
        if (!isDigest(request.rollbackOwnerApprovalDigest)) {
            return DecisionCode.ROLLBACK_OWNER_APPROVAL_MISSING;
        }
        if (!isDigest(request.rollbackDecisionDigest)) {
            return DecisionCode.ROLLBACK_DECISION_MISSING;
        }
        if (!isDigest(request.rollbackDataCompatibilityDigest)) {
            return DecisionCode.ROLLBACK_DATA_COMPATIBILITY_MISSING;
        }
        if (candidate.releaseSequence >= installed.releaseSequence) {
            return DecisionCode.RELEASE_SEQUENCE_INVALID;
        }
        boolean packageDecreased = false;
        for (int index = 0; index < REQUIRED_PACKAGE_COUNT; index++) {
            long installedVersion = installed.packages.get(index).versionCode;
            long candidateVersion = candidate.packages.get(index).versionCode;
            if (candidateVersion > installedVersion) {
                return DecisionCode.PACKAGE_VERSION_INVALID;
            }
            packageDecreased |= candidateVersion < installedVersion;
        }
        if (!packageDecreased) {
            return DecisionCode.PACKAGE_VERSION_UNCHANGED;
        }
        PackageSnapshot installedRuntime = installed.packages.get(0);
        PackageSnapshot candidateRuntime = candidate.packages.get(0);
        if (!canRead(candidateRuntime, installedRuntime.dataSchemaVersion)) {
            return DecisionCode.DATA_SCHEMA_UNREADABLE;
        }
        return DecisionCode.ADMITTED;
    }

    private static boolean canRead(PackageSnapshot target, int installedSchemaVersion) {
        return installedSchemaVersion >= target.minimumReadableDataSchemaVersion
                && installedSchemaVersion <= target.maximumReadableDataSchemaVersion;
    }

    private static boolean isDigest(String value) {
        return value != null && value.matches(DIGEST_PATTERN);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(64);
            for (byte item : digest) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static boolean isProductionSignerOwnerApproved() {
        return false;
    }

    public static boolean isProductionReleaseCandidateAdmitted() {
        return false;
    }

    public static boolean isInstallerWired() {
        return false;
    }

    public static boolean isRollbackExecutorWired() {
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
}
