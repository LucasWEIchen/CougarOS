package com.centralbrain.runtime.release;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Redacted P9-W07b preflight projection for the field-diagnostics adapter. */
public final class FieldDiagnosticsProjection {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-field-diagnostics-probe-v1";
    public static final int PACKAGE_QUERY_COUNT = 3;
    public static final int SIGNER_PAIR_QUERY_COUNT = 2;
    public static final int LAUNCH_TARGET_QUERY_COUNT = 2;
    public static final int SERVICE_QUERY_COUNT = 2;
    public static final int AUDIT_KEY_COUNT = 31;

    private static final List<String> ALLOWED_AUDIT_KEYS = buildAllowedAuditKeys();

    private FieldDiagnosticsProjection() {
    }

    public static final class ProbeObservation {
        private final int installedPackageCount;
        private final int repositoryVersionMatchCount;
        private final int signerPairMatchCount;
        private final boolean demoLaunchable;
        private final boolean client2Launchable;
        private final boolean runtimeServiceDeclared;
        private final boolean diagnosticServiceDeclared;

        public ProbeObservation(
                int installedPackageCount,
                int repositoryVersionMatchCount,
                int signerPairMatchCount,
                boolean demoLaunchable,
                boolean client2Launchable,
                boolean runtimeServiceDeclared,
                boolean diagnosticServiceDeclared) {
            requireCount(installedPackageCount, PACKAGE_QUERY_COUNT, "installedPackageCount");
            requireCount(
                    repositoryVersionMatchCount,
                    installedPackageCount,
                    "repositoryVersionMatchCount");
            requireCount(
                    signerPairMatchCount,
                    Math.min(SIGNER_PAIR_QUERY_COUNT, Math.max(0, installedPackageCount - 1)),
                    "signerPairMatchCount");
            int launchableCount = (demoLaunchable ? 1 : 0) + (client2Launchable ? 1 : 0);
            requireCount(
                    launchableCount,
                    Math.min(LAUNCH_TARGET_QUERY_COUNT, installedPackageCount),
                    "launchableTargetCount");
            this.installedPackageCount = installedPackageCount;
            this.repositoryVersionMatchCount = repositoryVersionMatchCount;
            this.signerPairMatchCount = signerPairMatchCount;
            this.demoLaunchable = demoLaunchable;
            this.client2Launchable = client2Launchable;
            this.runtimeServiceDeclared = runtimeServiceDeclared;
            this.diagnosticServiceDeclared = diagnosticServiceDeclared;
        }
    }

    public static final class Snapshot {
        private final ProbeObservation observation;
        private final int launchableTargetCount;
        private final int declaredServiceCount;
        private final boolean releaseBundlePreflightPassed;

        private Snapshot(ProbeObservation observation) {
            this.observation = observation;
            launchableTargetCount = (observation.demoLaunchable ? 1 : 0)
                    + (observation.client2Launchable ? 1 : 0);
            declaredServiceCount = (observation.runtimeServiceDeclared ? 1 : 0)
                    + (observation.diagnosticServiceDeclared ? 1 : 0);
            releaseBundlePreflightPassed = observation.installedPackageCount
                    == PACKAGE_QUERY_COUNT
                    && observation.repositoryVersionMatchCount == PACKAGE_QUERY_COUNT
                    && observation.signerPairMatchCount == SIGNER_PAIR_QUERY_COUNT;
        }

        public boolean isProjectionVerified() {
            return true;
        }

        public boolean isReleaseBundlePreflightPassed() {
            return releaseBundlePreflightPassed;
        }

        public String auditMetadata() {
            return "field_diagnostics_probe_complete=true"
                    + " field_diagnostics_projection_verified=true"
                    + " field_diagnostics_package_query_count=" + PACKAGE_QUERY_COUNT
                    + " field_diagnostics_installed_package_count="
                    + observation.installedPackageCount
                    + " field_diagnostics_repository_version_match_count="
                    + observation.repositoryVersionMatchCount
                    + " field_diagnostics_signer_pair_query_count="
                    + SIGNER_PAIR_QUERY_COUNT
                    + " field_diagnostics_signer_pair_match_count="
                    + observation.signerPairMatchCount
                    + " field_diagnostics_launch_target_query_count="
                    + LAUNCH_TARGET_QUERY_COUNT
                    + " field_diagnostics_launchable_target_count="
                    + launchableTargetCount
                    + " field_diagnostics_service_query_count=" + SERVICE_QUERY_COUNT
                    + " field_diagnostics_declared_service_count=" + declaredServiceCount
                    + " field_diagnostics_release_bundle_preflight_passed="
                    + releaseBundlePreflightPassed
                    + " field_diagnostics_demo_launch_preflight_passed="
                    + observation.demoLaunchable
                    + " field_diagnostics_client2_launch_preflight_passed="
                    + observation.client2Launchable
                    + " field_diagnostics_runtime_service_preflight_passed="
                    + observation.runtimeServiceDeclared
                    + " field_diagnostics_diagnostic_service_preflight_passed="
                    + observation.diagnosticServiceDeclared
                    + " field_diagnostics_external_activity_started=false"
                    + " field_diagnostics_service_invoked=false"
                    + " field_diagnostics_installer_dry_run_executed=false"
                    + " field_diagnostics_installer_execute_executed=false"
                    + " field_diagnostics_manual_scenario_matrix_executed=false"
                    + " field_diagnostics_raw_log_persisted=false"
                    + " field_diagnostics_package_name_logged=false"
                    + " field_diagnostics_device_identity_logged=false"
                    + " field_diagnostics_signer_material_logged=false"
                    + " field_diagnostics_target_input_logged=false"
                    + " field_diagnostics_user_model_vehicle_payload_logged=false"
                    + " field_diagnostics_automatic_upload_enabled=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false";
        }
    }

    public static Snapshot evaluate(ProbeObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("probe observation is required");
        }
        return new Snapshot(observation);
    }

    public static List<String> allowedAuditKeys() {
        return ALLOWED_AUDIT_KEYS;
    }

    public static boolean isAndroidDebugProbeAvailable() {
        return true;
    }

    public static boolean isAndroidDebugProbeExecuted() {
        return false;
    }

    public static boolean isTargetAdapterDefined() {
        return true;
    }

    public static boolean isTargetCategoryExecutionComplete() {
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

    private static void requireCount(int value, int maximum, String name) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(name + " out of range");
        }
    }

    private static List<String> buildAllowedAuditKeys() {
        List<String> keys = new ArrayList<>(List.of(
                "field_diagnostics_probe_complete",
                "field_diagnostics_projection_verified",
                "field_diagnostics_package_query_count",
                "field_diagnostics_installed_package_count",
                "field_diagnostics_repository_version_match_count",
                "field_diagnostics_signer_pair_query_count",
                "field_diagnostics_signer_pair_match_count",
                "field_diagnostics_launch_target_query_count",
                "field_diagnostics_launchable_target_count",
                "field_diagnostics_service_query_count",
                "field_diagnostics_declared_service_count",
                "field_diagnostics_release_bundle_preflight_passed",
                "field_diagnostics_demo_launch_preflight_passed",
                "field_diagnostics_client2_launch_preflight_passed",
                "field_diagnostics_runtime_service_preflight_passed",
                "field_diagnostics_diagnostic_service_preflight_passed",
                "field_diagnostics_external_activity_started",
                "field_diagnostics_service_invoked",
                "field_diagnostics_installer_dry_run_executed",
                "field_diagnostics_installer_execute_executed",
                "field_diagnostics_manual_scenario_matrix_executed",
                "field_diagnostics_raw_log_persisted",
                "field_diagnostics_package_name_logged",
                "field_diagnostics_device_identity_logged",
                "field_diagnostics_signer_material_logged",
                "field_diagnostics_target_input_logged",
                "field_diagnostics_user_model_vehicle_payload_logged",
                "field_diagnostics_automatic_upload_enabled",
                "hardware_accessed",
                "production_ready",
                "target_hardware_validated"));
        if (keys.size() != AUDIT_KEY_COUNT) {
            throw new IllegalStateException("field diagnostics audit key count changed");
        }
        return Collections.unmodifiableList(keys);
    }
}
