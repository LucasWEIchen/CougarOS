package com.centralbrain.runtime.memory;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryLifecycleProbeActivity extends Activity {
    private static final String TAG = "CbMemoryLifecycle";
    private static final String OWNER_A = repeat("a", 64);
    private static final String OWNER_B = repeat("b", 64);
    private static final String DIGEST_A = repeat("c", 64);
    private static final String DIGEST_B = repeat("d", 64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            AtomicLong clock = new AtomicLong(1_000);
            AtomicInteger ids = new AtomicInteger();
            BoundedMemoryLifecycle lifecycle = BoundedMemoryLifecycle.createForContractTest(
                    new BoundedMemoryLifecycle.Limits(4, 3, 2, 10),
                    clock::get,
                    () -> "probe-" + ids.incrementAndGet());

            BoundedMemoryLifecycle.TrustedWrite ephemeral = write(
                    OWNER_A,
                    "ephemeral-a",
                    BoundedMemoryLifecycle.Scope.EPHEMERAL,
                    BoundedMemoryLifecycle.Purpose.CONVERSATION_CONTEXT,
                    "session-a",
                    DIGEST_A,
                    10,
                    null);
            BoundedMemoryLifecycle.WriteResult ephemeralCreated = lifecycle.write(ephemeral);
            BoundedMemoryLifecycle.TrustedWrite session = write(
                    OWNER_A,
                    "session-a",
                    BoundedMemoryLifecycle.Scope.SESSION,
                    BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                    "session-a",
                    DIGEST_B,
                    100,
                    null);
            BoundedMemoryLifecycle.WriteResult sessionCreated = lifecycle.write(session);
            String sessionMemoryId = sessionCreated.getSnapshot().getMemoryId();
            boolean scopePolicyVerified = ephemeralCreated.getOutcome()
                    == BoundedMemoryLifecycle.WriteOutcome.CREATED
                    && sessionCreated.getOutcome()
                    == BoundedMemoryLifecycle.WriteOutcome.CREATED
                    && lifecycle.write(write(
                            OWNER_B,
                            "ttl-denied",
                            BoundedMemoryLifecycle.Scope.EPHEMERAL,
                            BoundedMemoryLifecycle.Purpose.CONVERSATION_CONTEXT,
                            "session-b",
                            DIGEST_A,
                            BoundedMemoryLifecycle.MAX_EPHEMERAL_TTL_MS + 1,
                            null)).getOutcome()
                            == BoundedMemoryLifecycle.WriteOutcome.SCOPE_POLICY_DENIED;

            BoundedMemoryLifecycle.WriteOutcome consentRequired = lifecycle.write(write(
                    OWNER_B,
                    "profile-no-consent",
                    BoundedMemoryLifecycle.Scope.PROFILE,
                    BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                    "",
                    DIGEST_A,
                    100,
                    null)).getOutcome();
            BoundedMemoryLifecycle.TrustedConsentEvidence consent =
                    BoundedMemoryLifecycle.TrustedConsentEvidence.grantedByGovernance(
                            OWNER_B,
                            BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                            "consent-probe",
                            10_000);
            BoundedMemoryLifecycle.WriteResult profileCreated = lifecycle.write(write(
                    OWNER_B,
                    "profile-b",
                    BoundedMemoryLifecycle.Scope.PROFILE,
                    BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                    "",
                    DIGEST_A,
                    100,
                    consent));
            boolean profileConsentVerified = consentRequired
                    == BoundedMemoryLifecycle.WriteOutcome.CONSENT_REQUIRED
                    && profileCreated.getOutcome()
                    == BoundedMemoryLifecycle.WriteOutcome.CREATED;

            boolean idempotencyVerified = lifecycle.write(session).getOutcome()
                    == BoundedMemoryLifecycle.WriteOutcome.REPLAYED
                    && lifecycle.write(write(
                            OWNER_A,
                            "session-a",
                            BoundedMemoryLifecycle.Scope.SESSION,
                            BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                            "session-a",
                            DIGEST_A,
                            100,
                            null)).getOutcome()
                            == BoundedMemoryLifecycle.WriteOutcome.CONFLICT;
            List<BoundedMemoryLifecycle.RedactedRecord> query = lifecycle.queryOwned(
                    OWNER_A,
                    null,
                    null,
                    10);
            boolean redactionVerified = query.size() == 2
                    && !query.get(0).isContentDigestExposed()
                    && !query.get(1).isContentDigestExposed();
            boolean ownerIsolationVerified = lifecycle.queryOwned(
                    repeat("e", 64),
                    null,
                    null,
                    10).isEmpty()
                    && lifecycle.findOwned(sessionMemoryId, OWNER_B) == null;

            boolean exportAuthorizationVerified = lifecycle.exportOwned(
                    sessionMemoryId,
                    OWNER_A,
                    null).getOutcome()
                    == BoundedMemoryLifecycle.ExportOutcome.AUTHORIZATION_REQUIRED
                    && lifecycle.exportOwned(
                            ephemeralCreated.getSnapshot().getMemoryId(),
                            OWNER_A,
                            exportAuthorization(
                                    OWNER_A,
                                    BoundedMemoryLifecycle.Purpose.CONVERSATION_CONTEXT,
                                    ephemeralCreated.getSnapshot().getMemoryId(),
                                    10_000)).getOutcome()
                            == BoundedMemoryLifecycle.ExportOutcome.SCOPE_NOT_EXPORTABLE
                    && DIGEST_B.equals(lifecycle.exportOwned(
                            sessionMemoryId,
                            OWNER_A,
                            exportAuthorization(
                                    OWNER_A,
                                    BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                                    sessionMemoryId,
                                    10_000)).getRecord().getContentDigest());

            clock.set(1_011);
            BoundedMemoryLifecycle.LifecycleSnapshot expired = lifecycle.findOwned(
                    ephemeralCreated.getSnapshot().getMemoryId(),
                    OWNER_A);
            boolean ttlExpiryVerified = expired != null
                    && expired.getState() == BoundedMemoryLifecycle.State.EXPIRED
                    && !expired.isContentReferenceRetained();
            boolean deleteIdempotencyVerified = lifecycle.deleteOwned(
                    sessionMemoryId,
                    OWNER_A) == BoundedMemoryLifecycle.DeleteOutcome.APPLIED
                    && lifecycle.deleteOwned(sessionMemoryId, OWNER_A)
                    == BoundedMemoryLifecycle.DeleteOutcome.REPLAYED
                    && lifecycle.write(session).getOutcome()
                    == BoundedMemoryLifecycle.WriteOutcome.REPLAYED;

            String profileMemoryId = profileCreated.getSnapshot().getMemoryId();
            lifecycle.deleteOwned(profileMemoryId, OWNER_B);
            String extra = lifecycle.write(write(
                    OWNER_B,
                    "session-b",
                    BoundedMemoryLifecycle.Scope.SESSION,
                    BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                    "session-b",
                    DIGEST_B,
                    100,
                    null)).getSnapshot().getMemoryId();
            lifecycle.deleteOwned(extra, OWNER_B);
            BoundedMemoryLifecycle.Snapshot snapshot = lifecycle.snapshot();
            boolean recordBoundsVerified = snapshot.getTerminalRecordCount() == 2
                    && snapshot.getTerminalEvictionCount() >= 1
                    && !snapshot.isPersistentStorageWired()
                    && !snapshot.isProductionServiceWired()
                    && !snapshot.isRawContentStored();
            boolean contractVerified = scopePolicyVerified
                    && profileConsentVerified
                    && idempotencyVerified
                    && ownerIsolationVerified
                    && redactionVerified
                    && ttlExpiryVerified
                    && deleteIdempotencyVerified
                    && exportAuthorizationVerified
                    && recordBoundsVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " memory_lifecycle_probe_complete=" + contractVerified
                    + " memory_lifecycle_contract_verified=" + contractVerified
                    + " memory_scope_policy_verified=" + scopePolicyVerified
                    + " memory_profile_consent_verified=" + profileConsentVerified
                    + " memory_write_idempotency_verified=" + idempotencyVerified
                    + " memory_owner_isolation_verified=" + ownerIsolationVerified
                    + " memory_query_redaction_verified=" + redactionVerified
                    + " memory_ttl_expiry_verified=" + ttlExpiryVerified
                    + " memory_delete_idempotency_verified=" + deleteIdempotencyVerified
                    + " memory_export_authorization_verified="
                    + exportAuthorizationVerified
                    + " memory_record_bounds_verified=" + recordBoundsVerified
                    + " memory_process_only=true"
                    + " memory_persistence_wired=false"
                    + " memory_production_service_wired=false"
                    + " raw_memory_content_stored=false"
                    + " memory_profile_storage_durable=false"
                    + " memory_consent_revocation_wired=false"
                    + " memory_encryption_key_configured=false"
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " memory_lifecycle_probe_complete=false"
                    + " memory_persistence_wired=false"
                    + " raw_memory_content_stored=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static BoundedMemoryLifecycle.TrustedWrite write(
            String owner,
            String clientId,
            BoundedMemoryLifecycle.Scope scope,
            BoundedMemoryLifecycle.Purpose purpose,
            String sessionId,
            String digest,
            long ttlMs,
            BoundedMemoryLifecycle.TrustedConsentEvidence consent) {
        return BoundedMemoryLifecycle.TrustedWrite.fromRuntimePolicy(
                owner,
                clientId,
                scope,
                purpose,
                sessionId,
                "central.memory.v1",
                digest,
                ttlMs,
                consent);
    }

    private static BoundedMemoryLifecycle.TrustedExportAuthorization exportAuthorization(
            String owner,
            BoundedMemoryLifecycle.Purpose purpose,
            String memoryId,
            long expiresAt) {
        return BoundedMemoryLifecycle.TrustedExportAuthorization.grantedByGovernance(
                owner,
                purpose,
                memoryId,
                "export-probe",
                expiresAt);
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
