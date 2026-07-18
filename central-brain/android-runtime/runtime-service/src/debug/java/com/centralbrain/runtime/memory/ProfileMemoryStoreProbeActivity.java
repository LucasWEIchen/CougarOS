package com.centralbrain.runtime.memory;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ProfileMemoryStoreProbeActivity extends Activity {
    private static final String TAG = "CbProfileMemory";
    private static final String OWNER = "a".repeat(64);
    private static final String EVIDENCE = "c".repeat(64);
    private static final String KEY_ALIAS = "d".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            AtomicLong clock = new AtomicLong(1_000L);
            AtomicBoolean consentActive = new AtomicBoolean(true);
            AtomicBoolean authorizationActive = new AtomicBoolean(true);
            AtomicBoolean ownerReady = new AtomicBoolean(false);
            ProfileMemoryStore store = createStore(
                    clock,
                    consentActive,
                    authorizationActive,
                    ownerReady);
            ProfileMemoryStore.ProfileKey key = new ProfileMemoryStore.ProfileKey(
                    seatScope(),
                    ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10);
            ProfileMemoryStore.ConsentEvidence consent = seatConsent();
            ProfileMemoryStore.UpdateOutcome unavailable = store.updateOwned(
                    update(key, 220, consent)).getOutcome();
            ownerReady.set(true);
            ProfileMemoryStore.UpdateResult created = store.updateOwned(update(key, 220, consent));
            ProfileMemoryStore.ReadResult read = store.readOwned(
                    new ProfileMemoryStore.TrustedRead(key, consent));
            ProfileMemoryStore.UpdateResult updated = store.updateOwned(update(key, 225, consent));
            boolean ownerGate = unavailable
                    == ProfileMemoryStore.UpdateOutcome.ENCRYPTION_OWNER_UNAVAILABLE
                    && created.getOutcome() == ProfileMemoryStore.UpdateOutcome.CREATED;
            boolean consentVerified = store.updateOwned(update(key, 225, null)).getOutcome()
                    == ProfileMemoryStore.UpdateOutcome.CONSENT_REQUIRED;
            boolean fieldScope = store.updateOwned(new ProfileMemoryStore.TrustedUpdate(
                    new ProfileMemoryStore.ProfileKey(
                            userScope(),
                            ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10),
                    ProfileMemoryStore.ProfileValue.integerValue(220),
                    50L,
                    userConsent())).getOutcome()
                    == ProfileMemoryStore.UpdateOutcome.SCOPE_MISMATCH;
            boolean readUpdate = read.getOutcome() == ProfileMemoryStore.ReadOutcome.FOUND
                    && read.getValue().getIntegerValue() == 220
                    && updated.getOutcome() == ProfileMemoryStore.UpdateOutcome.UPDATED
                    && updated.getRecord().getRevision() == 2L;

            ProfileMemoryStore.ProfileKey volume = new ProfileMemoryStore.ProfileKey(
                    userScope(),
                    ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT);
            store.updateOwned(new ProfileMemoryStore.TrustedUpdate(
                    volume,
                    ProfileMemoryStore.ProfileValue.integerValue(40),
                    50L,
                    userConsent()));
            ProfileMemoryStore.ExportResult export = store.exportOwned(
                    new ProfileMemoryStore.TrustedExport(
                            userScope(),
                            EnumSet.of(ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT),
                            userConsent(),
                            exportAuthorization()));
            boolean exportVerified = export.getOutcome()
                    == ProfileMemoryStore.ExportOutcome.EXPORTED
                    && export.getItems().size() == 1;

            consentActive.set(false);
            boolean revokedRead = store.readOwned(
                    new ProfileMemoryStore.TrustedRead(key, consent)).getOutcome()
                    == ProfileMemoryStore.ReadOutcome.CONSENT_INVALID;
            ProfileMemoryStore.DeleteResult deleted = store.deleteOwned(
                    new ProfileMemoryStore.TrustedDelete(key, deleteAuthorization()));
            ProfileMemoryStore.Snapshot snapshot = store.snapshot();
            boolean deleteVerified = deleted.getOutcome()
                    == ProfileMemoryStore.DeleteOutcome.APPLIED
                    && deleted.getWipedSealedByteCount() > 0L;
            boolean zeroized = snapshot.getWipedSealedByteCount() > 0L
                    && !snapshot.isRawProfileValueRetained();
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = ownerGate
                    && consentVerified
                    && fieldScope
                    && readUpdate
                    && exportVerified
                    && revokedRead
                    && deleteVerified
                    && zeroized
                    && store.isEncryptionOwnerGateDefined()
                    && !store.isDurableEncryptedStorageAvailable()
                    && !store.isProductionEncryptionOwnerConfigured()
                    && !store.isConsentAuthorityProductionWired()
                    && !store.isRuntimeWired()
                    && !store.isContentLoggingEnabled()
                    && !store.isHardwareAccessed()
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " profile_memory_store_probe_complete=" + complete
                    + " profile_memory_explicit_consent_verified=" + consentVerified
                    + " profile_memory_field_allowlist_verified=" + fieldScope
                    + " profile_memory_user_seat_scope_verified=" + fieldScope
                    + " profile_memory_read_update_verified=" + readUpdate
                    + " profile_memory_delete_verified=" + deleteVerified
                    + " profile_memory_export_verified=" + exportVerified
                    + " profile_memory_consent_revocation_fail_closed=" + revokedRead
                    + " profile_memory_encryption_owner_gate_verified=" + ownerGate
                    + " profile_memory_sealed_payload_zeroized=" + zeroized
                    + " profile_memory_android13_arm64_verified=" + android13Arm64
                    + " profile_memory_process_local=true"
                    + " profile_memory_durable_storage_wired=false"
                    + " profile_memory_production_encryption_owner_configured=false"
                    + " profile_memory_consent_authority_production_wired=false"
                    + " profile_memory_runtime_wired=false"
                    + " profile_memory_content_logged=false"
                    + " graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_readback_accessed=false"
                    + " model_invoked=false"
                    + " npu_accessed=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " profile_memory_store_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " profile_memory_runtime_wired=false"
                    + " model_invoked=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static ProfileMemoryStore createStore(
            AtomicLong clock,
            AtomicBoolean consentActive,
            AtomicBoolean authorizationActive,
            AtomicBoolean ownerReady) {
        ProfileMemoryStore.EncryptionOwner owner = new ProfileMemoryStore.EncryptionOwner() {
            @Override
            public ProfileMemoryStore.EncryptionOwnerState currentState() {
                return new ProfileMemoryStore.EncryptionOwnerState(
                        "debug-contract-owner",
                        KEY_ALIAS,
                        1L,
                        "debug-contract-xor",
                        ownerReady.get(),
                        ownerReady.get(),
                        false);
            }

            @Override
            public ProfileMemoryStore.SealedPayload seal(
                    ProfileMemoryStore.ProfileKey key,
                    long revision,
                    byte[] plaintext) {
                return new ProfileMemoryStore.SealedPayload(
                        "debug-contract-owner",
                        KEY_ALIAS,
                        1L,
                        "debug-contract-xor",
                        xor(plaintext));
            }

            @Override
            public byte[] open(
                    ProfileMemoryStore.ProfileKey key,
                    long revision,
                    ProfileMemoryStore.SealedPayload payload) {
                return xor(payload.getCiphertextCopy());
            }
        };
        return ProfileMemoryStore.createForContractTest(
                new ProfileMemoryStore.Limits(8, 8, 256, 64, 4, 100),
                ProfileMemoryStore.FieldPolicy.automotiveDefault(),
                (evidence, now) -> consentActive.get(),
                (evidence, now) -> authorizationActive.get(),
                owner,
                clock::get);
    }

    private static ProfileMemoryStore.TrustedUpdate update(
            ProfileMemoryStore.ProfileKey key,
            int value,
            ProfileMemoryStore.ConsentEvidence consent) {
        return new ProfileMemoryStore.TrustedUpdate(
                key,
                ProfileMemoryStore.ProfileValue.integerValue(value),
                50L,
                consent);
    }

    private static ProfileMemoryStore.ProfileScope userScope() {
        return new ProfileMemoryStore.ProfileScope(
                OWNER,
                ProfileMemoryStore.SeatScope.USER_GLOBAL);
    }

    private static ProfileMemoryStore.ProfileScope seatScope() {
        return new ProfileMemoryStore.ProfileScope(
                OWNER,
                ProfileMemoryStore.SeatScope.DRIVER);
    }

    private static ProfileMemoryStore.ConsentEvidence seatConsent() {
        return new ProfileMemoryStore.ConsentEvidence(
                "consent-seat",
                OWNER,
                EnumSet.of(ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10),
                EnumSet.of(ProfileMemoryStore.SeatScope.DRIVER),
                900L,
                1_100L,
                1L,
                EVIDENCE);
    }

    private static ProfileMemoryStore.ConsentEvidence userConsent() {
        return new ProfileMemoryStore.ConsentEvidence(
                "consent-user",
                OWNER,
                EnumSet.of(ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT),
                EnumSet.of(ProfileMemoryStore.SeatScope.USER_GLOBAL),
                900L,
                1_100L,
                1L,
                EVIDENCE);
    }

    private static ProfileMemoryStore.AuthorizationEvidence deleteAuthorization() {
        return new ProfileMemoryStore.AuthorizationEvidence(
                "delete-authorization",
                OWNER,
                ProfileMemoryStore.AuthorizationOperation.DELETE,
                EnumSet.of(ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10),
                EnumSet.of(ProfileMemoryStore.SeatScope.DRIVER),
                900L,
                1_100L,
                1L,
                EVIDENCE);
    }

    private static ProfileMemoryStore.AuthorizationEvidence exportAuthorization() {
        return new ProfileMemoryStore.AuthorizationEvidence(
                "export-authorization",
                OWNER,
                ProfileMemoryStore.AuthorizationOperation.EXPORT,
                EnumSet.of(ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT),
                EnumSet.of(ProfileMemoryStore.SeatScope.USER_GLOBAL),
                900L,
                1_100L,
                1L,
                EVIDENCE);
    }

    private static byte[] xor(byte[] input) {
        byte[] output = input.clone();
        for (int index = 0; index < output.length; index++) {
            output[index] = (byte) (output[index] ^ 0x5a);
        }
        return output;
    }
}
