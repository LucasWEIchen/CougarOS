package com.centralbrain.runtime.release;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public final class FieldDiagnosticsProjectionTest {
    @Test
    public void exactAuditCatalogIsStableUniqueAndOrdered() {
        assertEquals(
                FieldDiagnosticsProjection.AUDIT_KEY_COUNT,
                FieldDiagnosticsProjection.allowedAuditKeys().size());
        assertEquals(
                FieldDiagnosticsProjection.allowedAuditKeys().size(),
                new HashSet<>(FieldDiagnosticsProjection.allowedAuditKeys()).size());
        assertEquals(
                "field_diagnostics_probe_complete",
                FieldDiagnosticsProjection.allowedAuditKeys().get(0));
        assertEquals(
                "target_hardware_validated",
                FieldDiagnosticsProjection.allowedAuditKeys().get(
                        FieldDiagnosticsProjection.AUDIT_KEY_COUNT - 1));
    }

    @Test
    public void completeObservationPassesOnlyRedactedPreflight() {
        FieldDiagnosticsProjection.Snapshot snapshot = FieldDiagnosticsProjection.evaluate(
                observation(3, 3, 2, true, true, true, true));

        assertTrue(snapshot.isProjectionVerified());
        assertTrue(snapshot.isReleaseBundlePreflightPassed());
        assertTrue(snapshot.auditMetadata().contains(
                "field_diagnostics_launchable_target_count=2"));
        assertTrue(snapshot.auditMetadata().contains(
                "field_diagnostics_declared_service_count=2"));
        assertTrue(snapshot.auditMetadata().contains(
                "field_diagnostics_external_activity_started=false"));
    }

    @Test
    public void incompleteObservationRemainsAValidFailedPreflight() {
        FieldDiagnosticsProjection.Snapshot snapshot = FieldDiagnosticsProjection.evaluate(
                observation(2, 1, 0, true, false, true, false));

        assertTrue(snapshot.isProjectionVerified());
        assertFalse(snapshot.isReleaseBundlePreflightPassed());
        assertTrue(snapshot.auditMetadata().contains(
                "field_diagnostics_release_bundle_preflight_passed=false"));
        assertTrue(snapshot.auditMetadata().contains(
                "field_diagnostics_launchable_target_count=1"));
        assertTrue(snapshot.auditMetadata().contains(
                "field_diagnostics_declared_service_count=1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedCountRelationshipFailsClosed() {
        observation(1, 2, 0, false, false, true, true);
    }

    @Test
    public void auditMetadataUsesOnlyAllowlistedCountAndBooleanKeys() {
        String metadata = FieldDiagnosticsProjection.evaluate(
                observation(3, 3, 2, true, true, true, true)).auditMetadata();
        String[] fields = metadata.split(" ");
        Set<String> observedKeys = new HashSet<>();
        for (String field : fields) {
            assertTrue(field.matches("[a-z0-9_]+=(true|false|[0-9]+)"));
            observedKeys.add(field.substring(0, field.indexOf('=')));
        }
        assertEquals(
                new HashSet<>(FieldDiagnosticsProjection.allowedAuditKeys()),
                observedKeys);
    }

    @Test
    public void auditMetadataContainsNoIdentitySignerLogOrPayload() {
        String metadata = FieldDiagnosticsProjection.evaluate(
                observation(3, 3, 2, true, true, true, true)).auditMetadata();
        for (String forbidden : new String[] {
                "com.centralbrain", "com.tuanjie", "serial=", "fingerprint=",
                "certificate=", "signature=", "path=", "raw_log=", "payload=",
                "user_text=", "model_text=", "vehicle_value="}) {
            assertFalse(metadata.contains(forbidden));
        }
    }

    @Test
    public void repositoryClaimsProbeAvailableButUnexecutedAndUnqualified() {
        assertTrue(FieldDiagnosticsProjection.isAndroidDebugProbeAvailable());
        assertFalse(FieldDiagnosticsProjection.isAndroidDebugProbeExecuted());
        assertTrue(FieldDiagnosticsProjection.isTargetAdapterDefined());
        assertFalse(FieldDiagnosticsProjection.isTargetCategoryExecutionComplete());
        assertFalse(FieldDiagnosticsProjection.isTargetReportAdmitted());
        assertFalse(FieldDiagnosticsProjection.isRuntimeDiagnosticsWired());
        assertFalse(FieldDiagnosticsProjection.isRetestWorkflowWired());
        assertFalse(FieldDiagnosticsProjection.isAutomaticUploadEnabled());
        assertFalse(FieldDiagnosticsProjection.isAndroid13Arm64Verified());
        assertFalse(FieldDiagnosticsProjection.isHardwareAccessed());
        assertFalse(FieldDiagnosticsProjection.isProductionReady());
        assertFalse(FieldDiagnosticsProjection.isTargetHardwareValidated());
    }

    private static FieldDiagnosticsProjection.ProbeObservation observation(
            int installed,
            int versionMatched,
            int signerMatched,
            boolean demoLaunchable,
            boolean client2Launchable,
            boolean runtimeServiceDeclared,
            boolean diagnosticServiceDeclared) {
        return new FieldDiagnosticsProjection.ProbeObservation(
                installed,
                versionMatched,
                signerMatched,
                demoLaunchable,
                client2Launchable,
                runtimeServiceDeclared,
                diagnosticServiceDeclared);
    }
}
