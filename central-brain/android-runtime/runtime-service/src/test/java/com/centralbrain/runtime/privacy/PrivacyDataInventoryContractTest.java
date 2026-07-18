package com.centralbrain.runtime.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class PrivacyDataInventoryContractTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");

    @Test
    public void inventoryHasTwelveExactStorageSurfacesAndExistingSources() {
        List<PrivacyDataInventoryContract.DataSurface> surfaces =
                PrivacyDataInventoryContract.surfaces();
        assertEquals(12, surfaces.size());
        assertEquals(6, countStorage(
                surfaces, PrivacyDataInventoryContract.StorageMode.DURABLE_ROOM));
        assertEquals(5, countStorage(
                surfaces, PrivacyDataInventoryContract.StorageMode.PROCESS_LOCAL));
        assertEquals(1, countStorage(
                surfaces, PrivacyDataInventoryContract.StorageMode.TRANSIENT_ONLY));

        for (PrivacyDataInventoryContract.DataSurface surface : surfaces) {
            for (String sourceClass : surface.getSourceClasses()) {
                assertTrue(sourceClass, Files.isRegularFile(MAIN_JAVA.resolve(sourceClass)));
            }
        }
        assertTrue(PrivacyDataInventoryContract.inventoryDigest().matches("[0-9a-f]{64}"));
    }

    @Test
    public void ownerApprovedRetentionGapsRemainExplicitAndBounded() {
        List<String> gaps = PrivacyDataInventoryContract.surfaces().stream()
                .filter(surface -> surface.getEnforcementState()
                        == PrivacyDataInventoryContract.EnforcementState.POLICY_GAP)
                .map(PrivacyDataInventoryContract.DataSurface::getSurfaceId)
                .collect(Collectors.toList());
        assertEquals(List.of("durable.effect_recovery", "durable.audit"), gaps);
        for (PrivacyDataInventoryContract.DataSurface surface
                : PrivacyDataInventoryContract.surfaces()) {
            assertEquals(
                    surface.getEnforcementState()
                            == PrivacyDataInventoryContract.EnforcementState.POLICY_GAP,
                    surface.getRetentionMode()
                            == PrivacyDataInventoryContract.RetentionMode.OWNER_POLICY_MISSING);
        }
    }

    @Test
    public void contentPayloadSurfacesForbidContentLoggingAndProductionRetentionClaims() {
        List<String> contentSurfaces = PrivacyDataInventoryContract.surfaces().stream()
                .filter(PrivacyDataInventoryContract.DataSurface::acceptsContentPayload)
                .map(PrivacyDataInventoryContract.DataSurface::getSurfaceId)
                .collect(Collectors.toList());
        assertEquals(List.of(
                "memory.working", "memory.profile", "model.inference_boundary"),
                contentSurfaces);
        for (PrivacyDataInventoryContract.DataSurface surface
                : PrivacyDataInventoryContract.surfaces()) {
            if (surface.acceptsContentPayload()) {
                assertEquals(
                        PrivacyDataInventoryContract.LogMode.CONTENT_FORBIDDEN,
                        surface.getLogMode());
            }
        }
        assertFalse(PrivacyDataInventoryContract.isRawUserTextPersisted());
        assertFalse(PrivacyDataInventoryContract.isRawModelOutputPersisted());
        assertFalse(PrivacyDataInventoryContract.isRawVehiclePayloadPersisted());
        assertFalse(PrivacyDataInventoryContract.isLocationPersisted());
        assertFalse(PrivacyDataInventoryContract.isAuditContentLogged());
    }

    @Test
    public void onlyExplicitConsentProfileSurfaceAllowsBoundedExport() {
        List<PrivacyDataInventoryContract.DataSurface> exported =
                PrivacyDataInventoryContract.surfaces().stream()
                        .filter(surface -> surface.getExportMode()
                                == PrivacyDataInventoryContract.ExportMode.AUTHORIZED_BOUNDED)
                        .collect(Collectors.toList());
        assertEquals(1, exported.size());
        assertEquals("memory.profile", exported.get(0).getSurfaceId());
        assertEquals(
                PrivacyDataInventoryContract.ConsentMode.EXPLICIT_CONSENT,
                exported.get(0).getConsentMode());
        assertEquals(
                PrivacyDataInventoryContract.DeletionMode.AUTHORIZED_DELETE,
                exported.get(0).getDeletionMode());
    }

    @Test
    public void inventoryDoesNotClaimPolicyApprovalAndroidOrProductionCompletion() {
        assertTrue(PrivacyDataInventoryContract.isInventoryComplete());
        assertFalse(PrivacyDataInventoryContract.isOwnerPolicyApproved());
        assertFalse(PrivacyDataInventoryContract.isProductionLifecycleComplete());
        assertFalse(PrivacyDataInventoryContract.isRuntimeLifecycleWiringComplete());
        assertFalse(PrivacyDataInventoryContract.isAndroid13Arm64Verified());
        assertFalse(PrivacyDataInventoryContract.isHardwareAccessed());
        assertFalse(PrivacyDataInventoryContract.isProductionReady());
        assertFalse(PrivacyDataInventoryContract.isTargetHardwareValidated());
    }

    private static long countStorage(
            List<PrivacyDataInventoryContract.DataSurface> surfaces,
            PrivacyDataInventoryContract.StorageMode storageMode) {
        return surfaces.stream()
                .filter(surface -> surface.getStorageMode() == storageMode)
                .count();
    }
}
