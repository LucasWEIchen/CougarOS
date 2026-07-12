package com.centralbrain.runtime.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public final class MemoryRuntimeReadinessSnapshotTest {
    @Test
    public void currentSnapshotIsImmutableAndFailClosed() {
        MemoryRuntimeReadinessSnapshot current = MemoryRuntimeReadinessSnapshot.current();
        assertSame(current, MemoryRuntimeReadinessSnapshot.current());
        assertFalse(current.isActivationAllowed());
        assertTrue(current.isBoundedMemoryLifecycleImplementationAvailable());
        assertEquals(3, current.getScopeCount());
        assertFalse(current.isMemorySchemaReady());
        assertFalse(current.isMemoryRepositoryImplementationAvailable());
        assertFalse(current.isDurableEncryptedStorageAvailable());
        assertFalse(current.isEncryptionKeyLifecycleConfigured());
        assertFalse(current.isConsentAuthorityWired());
        assertFalse(current.isConsentRevocationWired());
        assertFalse(current.isTrustedRetentionClockWired());
        assertFalse(current.isMemoryRuntimeProductionWired());
        assertFalse(current.isMemoryRepositoryProductionWired());
        assertFalse(current.isMiddlewareChainWired());
        assertFalse(current.isRawMemoryContentStored());
        assertFalse(current.isProfileMemoryStorageDurable());
        assertFalse(current.isHardwareAccessed());
    }

    @Test
    public void blockersRemainOrderedAndImmutable() {
        MemoryRuntimeReadinessSnapshot current = MemoryRuntimeReadinessSnapshot.current();
        assertEquals(Arrays.asList(
                        MemoryRuntimeReadinessSnapshot.Blocker
                                .DURABLE_ENCRYPTED_STORAGE_MISSING,
                        MemoryRuntimeReadinessSnapshot.Blocker.KEY_LIFECYCLE_NOT_CONFIGURED,
                        MemoryRuntimeReadinessSnapshot.Blocker.CONSENT_AUTHORITY_NOT_WIRED,
                        MemoryRuntimeReadinessSnapshot.Blocker.CONSENT_REVOCATION_NOT_WIRED,
                        MemoryRuntimeReadinessSnapshot.Blocker
                                .TRUSTED_RETENTION_CLOCK_NOT_WIRED,
                        MemoryRuntimeReadinessSnapshot.Blocker
                                .MEMORY_REPOSITORY_NOT_IMPLEMENTED,
                        MemoryRuntimeReadinessSnapshot.Blocker.MEMORY_RUNTIME_NOT_WIRED,
                        MemoryRuntimeReadinessSnapshot.Blocker.MIDDLEWARE_CHAIN_NOT_WIRED),
                current.getBlockers());
        try {
            current.getBlockers().clear();
            fail("blockers must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void diagnosticDetailExposesEveryPrerequisite() {
        String detail = MemoryRuntimeReadinessSnapshot.current().diagnosticDetail();
        assertTrue(detail.contains("memory_runtime_activation_allowed=false"));
        assertTrue(detail.contains(
                "bounded_memory_lifecycle_implementation_available=true"));
        assertTrue(detail.contains("memory_scope_count=3"));
        assertTrue(detail.contains("memory_schema_ready=false"));
        assertTrue(detail.contains("memory_repository_implementation_available=false"));
        assertTrue(detail.contains("durable_encrypted_memory_storage_available=false"));
        assertTrue(detail.contains("memory_encryption_key_lifecycle_configured=false"));
        assertTrue(detail.contains("memory_consent_authority_wired=false"));
        assertTrue(detail.contains("memory_consent_revocation_wired=false"));
        assertTrue(detail.contains("trusted_memory_retention_clock_wired=false"));
        assertTrue(detail.contains("memory_runtime_production_wired=false"));
        assertTrue(detail.contains("memory_repository_production_wired=false"));
        assertTrue(detail.contains("memory_middleware_chain_wired=false"));
        assertTrue(detail.contains("raw_memory_content_stored=false"));
        assertTrue(detail.contains("profile_memory_storage_durable=false"));
        assertTrue(detail.contains("DURABLE_ENCRYPTED_STORAGE_MISSING"));
        assertTrue(detail.contains("MIDDLEWARE_CHAIN_NOT_WIRED"));
        assertTrue(detail.contains("service_dispatch_triggered=false"));
        assertTrue(detail.contains("hardware_accessed=false"));
    }
}
