package com.centralbrain.runtime.release;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Content-free P9-W05b projection of installed Android release metadata. */
public final class ProductionReleaseMetadataProjection {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID =
            "android13-p9-production-release-metadata-probe-v1";
    public static final int REQUIRED_PACKAGE_COUNT = 3;
    public static final int SIGNER_PAIR_QUERY_COUNT = 2;
    public static final int AUDIT_KEY_COUNT = 27;

    private static final List<String> REQUIRED_PACKAGE_NAMES = List.of(
            "com.centralbrain.runtime",
            "com.centralbrain.demo",
            "com.tuanjie.urasclient2");
    private static final List<Long> REPOSITORY_VERSION_CODES = List.of(3L, 1L, 1L);
    private static final List<String> ALLOWED_AUDIT_KEYS = buildAllowedAuditKeys();

    private ProductionReleaseMetadataProjection() {
    }

    public static final class PackageObservation {
        private final boolean installed;
        private final boolean repositoryVersionMatched;
        private final boolean signerMatchedRuntime;

        private PackageObservation(
                boolean installed,
                boolean repositoryVersionMatched,
                boolean signerMatchedRuntime) {
            if (!installed && (repositoryVersionMatched || signerMatchedRuntime)) {
                throw new IllegalArgumentException(
                        "missing package cannot match version or signer");
            }
            this.installed = installed;
            this.repositoryVersionMatched = repositoryVersionMatched;
            this.signerMatchedRuntime = signerMatchedRuntime;
        }

        public static PackageObservation installed(
                boolean repositoryVersionMatched,
                boolean signerMatchedRuntime) {
            return new PackageObservation(
                    true, repositoryVersionMatched, signerMatchedRuntime);
        }

        public static PackageObservation notInstalled() {
            return new PackageObservation(false, false, false);
        }
    }

    public static final class Snapshot {
        private final int installedPackageCount;
        private final int repositoryVersionMatchCount;
        private final int signerPairMatchCount;
        private final boolean exactPackageSetObserved;
        private final boolean repositoryVersionSetObserved;
        private final boolean sameSignerCohortObserved;

        private Snapshot(
                int installedPackageCount,
                int repositoryVersionMatchCount,
                int signerPairMatchCount,
                boolean exactPackageSetObserved,
                boolean repositoryVersionSetObserved,
                boolean sameSignerCohortObserved) {
            this.installedPackageCount = installedPackageCount;
            this.repositoryVersionMatchCount = repositoryVersionMatchCount;
            this.signerPairMatchCount = signerPairMatchCount;
            this.exactPackageSetObserved = exactPackageSetObserved;
            this.repositoryVersionSetObserved = repositoryVersionSetObserved;
            this.sameSignerCohortObserved = sameSignerCohortObserved;
        }

        public boolean isProjectionVerified() {
            return true;
        }

        public String auditMetadata() {
            return "release_metadata_probe_complete=true"
                    + " release_metadata_projection_verified=true"
                    + " release_package_query_count=" + REQUIRED_PACKAGE_COUNT
                    + " release_installed_package_count=" + installedPackageCount
                    + " release_repository_version_match_count="
                    + repositoryVersionMatchCount
                    + " release_same_signer_pair_query_count="
                    + SIGNER_PAIR_QUERY_COUNT
                    + " release_same_signer_pair_match_count=" + signerPairMatchCount
                    + " release_exact_package_set_observed=" + exactPackageSetObserved
                    + " release_repository_version_set_observed="
                    + repositoryVersionSetObserved
                    + " release_same_signer_cohort_observed="
                    + sameSignerCohortObserved
                    + " release_candidate_metadata_complete=false"
                    + " release_dry_run_admitted=false"
                    + " production_signer_owner_approved=false"
                    + " production_release_candidate_admitted=false"
                    + " release_installer_wired=false"
                    + " release_install_executed=false"
                    + " release_uninstall_executed=false"
                    + " release_rollback_executor_wired=false"
                    + " release_rollback_executed=false"
                    + " release_signer_material_logged=false"
                    + " release_certificate_material_logged=false"
                    + " release_package_name_logged=false"
                    + " release_device_identity_logged=false"
                    + " release_raw_log_persisted=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false";
        }
    }

    public static Snapshot evaluate(List<PackageObservation> observations) {
        if (observations == null || observations.size() != REQUIRED_PACKAGE_COUNT) {
            throw new IllegalArgumentException("exact release package observation set required");
        }

        int installedCount = 0;
        int versionMatchCount = 0;
        int signerPairMatchCount = 0;
        for (int index = 0; index < observations.size(); index++) {
            PackageObservation observation = observations.get(index);
            if (observation == null) {
                throw new IllegalArgumentException("null package observation");
            }
            if (observation.installed) {
                installedCount++;
            }
            if (observation.repositoryVersionMatched) {
                versionMatchCount++;
            }
            if (index > 0 && observation.signerMatchedRuntime) {
                signerPairMatchCount++;
            }
        }

        boolean exactSet = installedCount == REQUIRED_PACKAGE_COUNT;
        return new Snapshot(
                installedCount,
                versionMatchCount,
                signerPairMatchCount,
                exactSet,
                exactSet && versionMatchCount == REQUIRED_PACKAGE_COUNT,
                exactSet && signerPairMatchCount == SIGNER_PAIR_QUERY_COUNT);
    }

    public static String requiredPackageName(int index) {
        return REQUIRED_PACKAGE_NAMES.get(index);
    }

    public static long repositoryVersionCode(int index) {
        return REPOSITORY_VERSION_CODES.get(index);
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

    public static boolean isInstallerDryRunAdapterDefined() {
        return true;
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
                "release_metadata_probe_complete",
                "release_metadata_projection_verified",
                "release_package_query_count",
                "release_installed_package_count",
                "release_repository_version_match_count",
                "release_same_signer_pair_query_count",
                "release_same_signer_pair_match_count",
                "release_exact_package_set_observed",
                "release_repository_version_set_observed",
                "release_same_signer_cohort_observed",
                "release_candidate_metadata_complete",
                "release_dry_run_admitted",
                "production_signer_owner_approved",
                "production_release_candidate_admitted",
                "release_installer_wired",
                "release_install_executed",
                "release_uninstall_executed",
                "release_rollback_executor_wired",
                "release_rollback_executed",
                "release_signer_material_logged",
                "release_certificate_material_logged",
                "release_package_name_logged",
                "release_device_identity_logged",
                "release_raw_log_persisted",
                "hardware_accessed",
                "production_ready",
                "target_hardware_validated"));
        if (keys.size() != AUDIT_KEY_COUNT) {
            throw new IllegalStateException("release metadata audit key count changed");
        }
        return Collections.unmodifiableList(keys);
    }
}
