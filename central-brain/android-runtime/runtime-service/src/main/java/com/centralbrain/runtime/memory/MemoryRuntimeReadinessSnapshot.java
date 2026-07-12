package com.centralbrain.runtime.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Immutable production visibility for the blocked Memory runtime prerequisites. */
public final class MemoryRuntimeReadinessSnapshot {
    public enum Blocker {
        DURABLE_ENCRYPTED_STORAGE_MISSING,
        KEY_LIFECYCLE_NOT_CONFIGURED,
        CONSENT_AUTHORITY_NOT_WIRED,
        CONSENT_REVOCATION_NOT_WIRED,
        TRUSTED_RETENTION_CLOCK_NOT_WIRED,
        MEMORY_REPOSITORY_NOT_IMPLEMENTED,
        MEMORY_RUNTIME_NOT_WIRED,
        MIDDLEWARE_CHAIN_NOT_WIRED
    }

    private static final MemoryRuntimeReadinessSnapshot CURRENT = createCurrent();

    private final List<Blocker> blockers;

    private MemoryRuntimeReadinessSnapshot(List<Blocker> blockers) {
        this.blockers = Collections.unmodifiableList(new ArrayList<>(blockers));
    }

    public static MemoryRuntimeReadinessSnapshot current() {
        return CURRENT;
    }

    private static MemoryRuntimeReadinessSnapshot createCurrent() {
        if (!Arrays.equals(
                BoundedMemoryLifecycle.Scope.values(),
                new BoundedMemoryLifecycle.Scope[] {
                        BoundedMemoryLifecycle.Scope.EPHEMERAL,
                        BoundedMemoryLifecycle.Scope.SESSION,
                        BoundedMemoryLifecycle.Scope.PROFILE
                })
                || BoundedMemoryLifecycle.MAX_EPHEMERAL_TTL_MS != 5 * 60 * 1_000L
                || BoundedMemoryLifecycle.MAX_SESSION_TTL_MS != 24 * 60 * 60 * 1_000L
                || BoundedMemoryLifecycle.MAX_PROFILE_TTL_MS
                        != 30L * 24 * 60 * 60 * 1_000L) {
            throw new IllegalStateException("bounded Memory lifecycle baseline is inconsistent");
        }
        return new MemoryRuntimeReadinessSnapshot(Arrays.asList(
                Blocker.DURABLE_ENCRYPTED_STORAGE_MISSING,
                Blocker.KEY_LIFECYCLE_NOT_CONFIGURED,
                Blocker.CONSENT_AUTHORITY_NOT_WIRED,
                Blocker.CONSENT_REVOCATION_NOT_WIRED,
                Blocker.TRUSTED_RETENTION_CLOCK_NOT_WIRED,
                Blocker.MEMORY_REPOSITORY_NOT_IMPLEMENTED,
                Blocker.MEMORY_RUNTIME_NOT_WIRED,
                Blocker.MIDDLEWARE_CHAIN_NOT_WIRED));
    }

    public boolean isActivationAllowed() {
        return false;
    }

    public boolean isBoundedMemoryLifecycleImplementationAvailable() {
        return true;
    }

    public int getScopeCount() {
        return BoundedMemoryLifecycle.Scope.values().length;
    }

    public boolean isMemorySchemaReady() {
        return false;
    }

    public boolean isMemoryRepositoryImplementationAvailable() {
        return false;
    }

    public boolean isDurableEncryptedStorageAvailable() {
        return false;
    }

    public boolean isEncryptionKeyLifecycleConfigured() {
        return false;
    }

    public boolean isConsentAuthorityWired() {
        return false;
    }

    public boolean isConsentRevocationWired() {
        return false;
    }

    public boolean isTrustedRetentionClockWired() {
        return false;
    }

    public boolean isMemoryRuntimeProductionWired() {
        return false;
    }

    public boolean isMemoryRepositoryProductionWired() {
        return false;
    }

    public boolean isMiddlewareChainWired() {
        return false;
    }

    public boolean isRawMemoryContentStored() {
        return false;
    }

    public boolean isProfileMemoryStorageDurable() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    public List<Blocker> getBlockers() {
        return blockers;
    }

    public String getBlockersCsv() {
        return blockers.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public String diagnosticDetail() {
        return "memory_runtime_activation_allowed=false"
                + ";bounded_memory_lifecycle_implementation_available=true"
                + ";memory_scope_count=" + getScopeCount()
                + ";memory_schema_ready=false"
                + ";memory_repository_implementation_available=false"
                + ";durable_encrypted_memory_storage_available=false"
                + ";memory_encryption_key_lifecycle_configured=false"
                + ";memory_consent_authority_wired=false"
                + ";memory_consent_revocation_wired=false"
                + ";trusted_memory_retention_clock_wired=false"
                + ";memory_runtime_production_wired=false"
                + ";memory_repository_production_wired=false"
                + ";memory_middleware_chain_wired=false"
                + ";raw_memory_content_stored=false"
                + ";profile_memory_storage_durable=false"
                + ";blockers=" + getBlockersCsv()
                + ";service_dispatch_triggered=false"
                + ";hardware_accessed=false";
    }
}
