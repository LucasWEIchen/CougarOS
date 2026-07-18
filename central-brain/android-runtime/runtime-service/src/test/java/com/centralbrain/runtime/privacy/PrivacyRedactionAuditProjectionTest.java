package com.centralbrain.runtime.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class PrivacyRedactionAuditProjectionTest {
    @Test
    public void currentDraftProjectionUsesExactAllowlistedKeysAndCounts() {
        PrivacyRedactionAuditProjection.Snapshot snapshot =
                PrivacyRedactionAuditProjection.evaluateCurrentDraft();
        assertTrue(snapshot.isProjectionVerified());
        String metadata = snapshot.auditMetadata();
        List<String> keys = new ArrayList<>();
        for (String token : metadata.split(" ")) {
            int separator = token.indexOf('=');
            assertTrue(separator > 0);
            keys.add(token.substring(0, separator));
        }
        assertEquals(PrivacyRedactionAuditProjection.AUDIT_KEY_COUNT, keys.size());
        assertEquals(PrivacyRedactionAuditProjection.allowedAuditKeys(), keys);
        assertTrue(metadata.contains("privacy_surface_count=12"));
        assertTrue(metadata.contains("privacy_unresolved_surface_count=2"));
        assertTrue(metadata.contains("privacy_admission_code_count=3"));
        assertTrue(metadata.contains("privacy_operation_code_count=1"));
    }

    @Test
    public void projectionContainsOnlyDigestsCountsAndBooleanMetadata() {
        String metadata = PrivacyRedactionAuditProjection.evaluateCurrentDraft()
                .auditMetadata();
        assertTrue(metadata.matches("[a-z0-9_= ]+"));
        assertFalse(metadata.contains("durable."));
        assertFalse(metadata.contains("memory."));
        assertFalse(metadata.contains("model."));
        assertFalse(metadata.contains("com/centralbrain"));
        assertFalse(metadata.contains("OWNER_INPUT_REQUIRED"));
        assertFalse(metadata.contains("PRIVACY"));
        assertFalse(metadata.contains("FUNCTIONAL_SAFETY"));
        assertFalse(metadata.contains("COMPLIANCE"));
    }

    @Test
    public void projectionKeepsContentAndAuthorityClaimsFalse() {
        String metadata = PrivacyRedactionAuditProjection.evaluateCurrentDraft()
                .auditMetadata();
        for (String marker : List.of(
                "privacy_current_policy_admitted=false",
                "privacy_raw_user_text_logged=false",
                "privacy_raw_model_output_logged=false",
                "privacy_raw_vehicle_payload_logged=false",
                "privacy_location_logged=false",
                "privacy_owner_reference_logged=false",
                "privacy_authorization_digest_logged=false",
                "privacy_consent_digest_logged=false",
                "privacy_repository_mutation_wired=false",
                "privacy_runtime_lifecycle_wiring_complete=false",
                "hardware_accessed=false",
                "production_ready=false",
                "target_hardware_validated=false")) {
            assertTrue(marker, metadata.contains(marker));
        }
    }

    @Test
    public void repositoryClaimsProbeAvailableButNotExecutedOrQualified() {
        assertTrue(PrivacyRedactionAuditProjection.isDebugProbeAvailable());
        assertFalse(PrivacyRedactionAuditProjection.isDebugProbeExecuted());
        assertFalse(PrivacyRedactionAuditProjection.isAndroid13Arm64Verified());
        assertFalse(PrivacyRedactionAuditProjection.isRuntimeWired());
        assertFalse(PrivacyRedactionAuditProjection.isHardwareAccessed());
        assertFalse(PrivacyRedactionAuditProjection.isProductionReady());
        assertFalse(PrivacyRedactionAuditProjection.isTargetHardwareValidated());
    }
}
