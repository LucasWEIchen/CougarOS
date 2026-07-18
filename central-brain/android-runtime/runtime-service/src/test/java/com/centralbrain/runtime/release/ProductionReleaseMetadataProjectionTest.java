package com.centralbrain.runtime.release;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class ProductionReleaseMetadataProjectionTest {
    @Test
    public void exactInstalledSetProducesRedactedCountsButNeverAdmission() {
        String metadata = ProductionReleaseMetadataProjection.evaluate(List.of(
                installed(true, true),
                installed(true, true),
                installed(true, true))).auditMetadata();

        assertTrue(metadata.contains("release_installed_package_count=3"));
        assertTrue(metadata.contains("release_repository_version_match_count=3"));
        assertTrue(metadata.contains("release_same_signer_pair_match_count=2"));
        assertTrue(metadata.contains("release_exact_package_set_observed=true"));
        assertTrue(metadata.contains("release_same_signer_cohort_observed=true"));
        assertTrue(metadata.contains("release_dry_run_admitted=false"));
        assertTrue(metadata.contains("production_release_candidate_admitted=false"));
    }

    @Test
    public void missingPackageRemainsValidObservationAndFailsClosed() {
        String metadata = ProductionReleaseMetadataProjection.evaluate(List.of(
                installed(true, true),
                installed(true, true),
                ProductionReleaseMetadataProjection.PackageObservation.notInstalled()))
                .auditMetadata();

        assertTrue(metadata.contains("release_metadata_projection_verified=true"));
        assertTrue(metadata.contains("release_installed_package_count=2"));
        assertTrue(metadata.contains("release_exact_package_set_observed=false"));
        assertTrue(metadata.contains("release_same_signer_cohort_observed=false"));
        assertTrue(metadata.contains("release_candidate_metadata_complete=false"));
    }

    @Test
    public void projectionUsesExactAllowlistedKeys() {
        String metadata = completeSnapshot();
        List<String> keys = new ArrayList<>();
        for (String token : metadata.split(" ")) {
            int separator = token.indexOf('=');
            assertTrue(separator > 0);
            keys.add(token.substring(0, separator));
        }

        assertEquals(ProductionReleaseMetadataProjection.AUDIT_KEY_COUNT, keys.size());
        assertEquals(ProductionReleaseMetadataProjection.allowedAuditKeys(), keys);
    }

    @Test
    public void projectionContainsOnlyCountsBooleansAndNoIdentifiers() {
        String metadata = completeSnapshot();

        assertTrue(metadata.matches("[a-z0-9_= ]+"));
        assertFalse(metadata.contains("com.centralbrain"));
        assertFalse(metadata.contains("com.tuanjie"));
        assertFalse(metadata.contains("runtime-service"));
        assertFalse(metadata.contains("certificate" + "="));
        assertFalse(metadata.matches(".*[0-9a-f]{64}.*"));
    }

    @Test
    public void projectionKeepsExecutionAndReadinessClaimsFalse() {
        String metadata = completeSnapshot();
        for (String marker : List.of(
                "release_candidate_metadata_complete=false",
                "release_dry_run_admitted=false",
                "production_signer_owner_approved=false",
                "production_release_candidate_admitted=false",
                "release_installer_wired=false",
                "release_install_executed=false",
                "release_uninstall_executed=false",
                "release_rollback_executor_wired=false",
                "release_rollback_executed=false",
                "release_signer_material_logged=false",
                "release_certificate_material_logged=false",
                "release_package_name_logged=false",
                "release_device_identity_logged=false",
                "release_raw_log_persisted=false",
                "hardware_accessed=false",
                "production_ready=false",
                "target_hardware_validated=false")) {
            assertTrue(marker, metadata.contains(marker));
        }
    }

    @Test
    public void repositoryClaimsProbeAvailableButNotExecuted() {
        assertTrue(ProductionReleaseMetadataProjection.isDebugProbeAvailable());
        assertFalse(ProductionReleaseMetadataProjection.isDebugProbeExecuted());
        assertTrue(ProductionReleaseMetadataProjection.isInstallerDryRunAdapterDefined());
        assertFalse(ProductionReleaseMetadataProjection.isAndroid13Arm64Verified());
        assertFalse(ProductionReleaseMetadataProjection.isHardwareAccessed());
        assertFalse(ProductionReleaseMetadataProjection.isProductionReady());
        assertFalse(ProductionReleaseMetadataProjection.isTargetHardwareValidated());
    }

    @Test(expected = IllegalArgumentException.class)
    public void observationSetMustRemainExact() {
        ProductionReleaseMetadataProjection.evaluate(List.of(installed(true, true)));
    }

    private static ProductionReleaseMetadataProjection.PackageObservation installed(
            boolean versionMatched,
            boolean signerMatched) {
        return ProductionReleaseMetadataProjection.PackageObservation.installed(
                versionMatched, signerMatched);
    }

    private static String completeSnapshot() {
        return ProductionReleaseMetadataProjection.evaluate(List.of(
                installed(true, true),
                installed(true, true),
                installed(true, true))).auditMetadata();
    }
}
