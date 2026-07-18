package com.centralbrain.runtime.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class ProfileMemoryStoreTest {
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);
    private static final String EVIDENCE = "c".repeat(64);
    private static final String KEY_ALIAS = "d".repeat(64);

    @Test
    public void encryptionOwnerAndConsentFailClosed() {
        Harness harness = harness(limits(4, 4, 128, 64, 4, 100));
        ProfileMemoryStore.ProfileKey key = seatKey(OWNER_A, ProfileMemoryStore.Field.SEAT_HEAT_LEVEL);

        harness.ownerReady.set(false);
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.ENCRYPTION_OWNER_UNAVAILABLE,
                harness.store.updateOwned(update(key, integer(2), consentSeat(OWNER_A), 50))
                        .getOutcome());
        harness.ownerReady.set(true);
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.CONSENT_REQUIRED,
                harness.store.updateOwned(update(key, integer(2), null, 50)).getOutcome());
        harness.consentActive.set(false);
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.CONSENT_INVALID,
                harness.store.updateOwned(update(key, integer(2), consentSeat(OWNER_A), 50))
                        .getOutcome());
        assertEquals(0, harness.store.snapshot().getActiveRecordCount());
        assertTrue(harness.store.isEncryptionOwnerGateDefined());
        assertFalse(harness.store.isDurableEncryptedStorageAvailable());
        assertFalse(harness.store.isProductionEncryptionOwnerConfigured());
        assertFalse(harness.store.isConsentAuthorityProductionWired());
        assertFalse(harness.store.isRuntimeWired());
        assertFalse(harness.store.isContentLoggingEnabled());
        assertFalse(harness.store.isHardwareAccessed());
    }

    @Test
    public void fieldAllowlistScopeAndValueValidationAreExact() {
        Harness harness = harness(
                limits(4, 4, 128, 64, 4, 100),
                EnumSet.of(
                        ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10,
                        ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT));
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.FIELD_NOT_ALLOWED,
                harness.store.updateOwned(update(
                        userKey(OWNER_A, ProfileMemoryStore.Field.LANGUAGE_TAG),
                        ProfileMemoryStore.ProfileValue.textValue("zh-CN"),
                        consentUser(OWNER_A),
                        50)).getOutcome());
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.SCOPE_MISMATCH,
                harness.store.updateOwned(update(
                        userKey(OWNER_A, ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10),
                        integer(220),
                        consentUser(OWNER_A),
                        50)).getOutcome());
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.SCOPE_MISMATCH,
                harness.store.updateOwned(update(
                        seatKey(OWNER_A, ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT),
                        integer(30),
                        consentSeat(OWNER_A),
                        50)).getOutcome());
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.VALUE_INVALID,
                harness.store.updateOwned(update(
                        seatKey(OWNER_A, ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10),
                        integer(400),
                        consentSeat(OWNER_A),
                        50)).getOutcome());
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.CREATED,
                harness.store.updateOwned(update(
                        seatKey(OWNER_A, ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10),
                        integer(225),
                        consentSeat(OWNER_A),
                        50)).getOutcome());
    }

    @Test
    public void updateReadIsolationAndSealedReplacementAreDeterministic() {
        Harness harness = harness(limits(4, 4, 128, 64, 4, 100));
        ProfileMemoryStore.ProfileKey key = seatKey(
                OWNER_A,
                ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10);
        ProfileMemoryStore.UpdateResult created = harness.store.updateOwned(
                update(key, integer(220), consentSeat(OWNER_A), 50));
        assertEquals(ProfileMemoryStore.UpdateOutcome.CREATED, created.getOutcome());
        assertEquals(1L, created.getRecord().getRevision());
        assertNotEquals(
                "I:220",
                new String(harness.lastCiphertext, StandardCharsets.UTF_8));

        ProfileMemoryStore.ReadResult read = harness.store.readOwned(
                new ProfileMemoryStore.TrustedRead(key, consentSeat(OWNER_A)));
        assertEquals(ProfileMemoryStore.ReadOutcome.FOUND, read.getOutcome());
        assertEquals(220, read.getValue().getIntegerValue());
        assertEquals(
                ProfileMemoryStore.ReadOutcome.CONSENT_INVALID,
                harness.store.readOwned(new ProfileMemoryStore.TrustedRead(
                        key,
                        consentSeat(OWNER_B))).getOutcome());

        ProfileMemoryStore.UpdateResult updated = harness.store.updateOwned(
                update(key, integer(230), consentSeat(OWNER_A), 50));
        assertEquals(ProfileMemoryStore.UpdateOutcome.UPDATED, updated.getOutcome());
        assertEquals(2L, updated.getRecord().getRevision());
        assertEquals(
                230,
                harness.store.readOwned(new ProfileMemoryStore.TrustedRead(
                        key,
                        consentSeat(OWNER_A))).getValue().getIntegerValue());
        ProfileMemoryStore.Snapshot snapshot = harness.store.snapshot();
        assertEquals(1, snapshot.getActiveRecordCount());
        assertEquals(1L, snapshot.getCreatedCount());
        assertEquals(1L, snapshot.getUpdatedCount());
        assertTrue(snapshot.getWipedSealedByteCount() > 0L);
        assertFalse(snapshot.isRawProfileValueRetained());
    }

    @Test
    public void deleteRemainsAuthorizedAfterConsentRevocationAndZeroizes() {
        Harness harness = harness(limits(4, 4, 128, 64, 4, 100));
        ProfileMemoryStore.ProfileKey key = seatKey(
                OWNER_A,
                ProfileMemoryStore.Field.SEAT_VENT_LEVEL);
        harness.store.updateOwned(update(key, integer(3), consentSeat(OWNER_A), 50));
        harness.consentActive.set(false);
        assertEquals(
                ProfileMemoryStore.ReadOutcome.CONSENT_INVALID,
                harness.store.readOwned(new ProfileMemoryStore.TrustedRead(
                        key,
                        consentSeat(OWNER_A))).getOutcome());
        assertEquals(
                ProfileMemoryStore.DeleteOutcome.AUTHORIZATION_REQUIRED,
                harness.store.deleteOwned(new ProfileMemoryStore.TrustedDelete(key, null))
                        .getOutcome());
        ProfileMemoryStore.DeleteResult deleted = harness.store.deleteOwned(
                new ProfileMemoryStore.TrustedDelete(key, deleteAuthorization(OWNER_A, key.getField())));
        assertEquals(ProfileMemoryStore.DeleteOutcome.APPLIED, deleted.getOutcome());
        assertTrue(deleted.getWipedSealedByteCount() > 0L);
        assertEquals(0, harness.store.snapshot().getActiveRecordCount());
        assertEquals(
                ProfileMemoryStore.DeleteOutcome.NOT_FOUND,
                harness.store.deleteOwned(new ProfileMemoryStore.TrustedDelete(
                        key,
                        deleteAuthorization(OWNER_A, key.getField()))).getOutcome());
    }

    @Test
    public void exportRequiresConsentAuthorizationAndReturnsBoundedImmutableItems() {
        Harness harness = harness(limits(8, 8, 256, 64, 4, 100));
        ProfileMemoryStore.ProfileKey volume = userKey(
                OWNER_A,
                ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT);
        ProfileMemoryStore.ProfileKey avoid = userKey(
                OWNER_A,
                ProfileMemoryStore.Field.NAVIGATION_AVOID_HIGHWAY);
        ProfileMemoryStore.ConsentEvidence consent = consentUser(OWNER_A);
        harness.store.updateOwned(update(volume, integer(40), consent, 50));
        harness.store.updateOwned(update(
                avoid,
                ProfileMemoryStore.ProfileValue.booleanValue(true),
                consent,
                50));

        EnumSet<ProfileMemoryStore.Field> fields = EnumSet.of(
                ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT,
                ProfileMemoryStore.Field.NAVIGATION_AVOID_HIGHWAY);
        ProfileMemoryStore.TrustedExport missingAuthorization =
                new ProfileMemoryStore.TrustedExport(userScope(OWNER_A), fields, consent, null);
        assertEquals(
                ProfileMemoryStore.ExportOutcome.AUTHORIZATION_REQUIRED,
                harness.store.exportOwned(missingAuthorization).getOutcome());
        ProfileMemoryStore.ExportResult exported = harness.store.exportOwned(
                new ProfileMemoryStore.TrustedExport(
                        userScope(OWNER_A),
                        fields,
                        consent,
                        exportAuthorization(OWNER_A, fields)));
        assertEquals(ProfileMemoryStore.ExportOutcome.EXPORTED, exported.getOutcome());
        assertEquals(2, exported.getItems().size());
        assertEquals(
                ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT,
                exported.getItems().get(0).getRecord().getField());
        assertEquals(
                ProfileMemoryStore.Field.NAVIGATION_AVOID_HIGHWAY,
                exported.getItems().get(1).getRecord().getField());
        try {
            exported.getItems().clear();
            fail("Profile Memory export must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        assertEquals(2L, harness.store.snapshot().getExportedCount());
    }

    @Test
    public void retentionCapacityAndMalformedContractsFailClosed() {
        Harness harness = harness(limits(2, 1, 128, 64, 2, 20));
        ProfileMemoryStore.ProfileKey first = userKey(
                OWNER_A,
                ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT);
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.CREATED,
                harness.store.updateOwned(update(first, integer(50), consentUser(OWNER_A), 10))
                        .getOutcome());
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.OWNER_LIMIT,
                harness.store.updateOwned(update(
                        userKey(OWNER_A, ProfileMemoryStore.Field.NAVIGATION_AVOID_HIGHWAY),
                        ProfileMemoryStore.ProfileValue.booleanValue(false),
                        consentUser(OWNER_A),
                        10)).getOutcome());
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.CREATED,
                harness.store.updateOwned(update(
                        userKey(OWNER_B, ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT),
                        integer(60),
                        consentUser(OWNER_B),
                        10)).getOutcome());
        String ownerC = "e".repeat(64);
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.RECORD_LIMIT,
                harness.store.updateOwned(update(
                        userKey(ownerC, ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT),
                        integer(70),
                        consentUser(ownerC),
                        10)).getOutcome());
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.RETENTION_LIMIT,
                harness.store.updateOwned(update(first, integer(55), consentUser(OWNER_A), 21))
                        .getOutcome());
        harness.clock.set(1_010L);
        ProfileMemoryStore.Snapshot expired = harness.store.snapshot();
        assertEquals(0, expired.getActiveRecordCount());
        assertEquals(2L, expired.getExpiredCount());
        assertTrue(expired.getWipedSealedByteCount() > 0L);

        Harness byteHarness = harness(limits(4, 4, 4, 4, 2, 20));
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.CREATED,
                byteHarness.store.updateOwned(update(
                        userKey(OWNER_A, ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT),
                        integer(5),
                        consentUser(OWNER_A),
                        10)).getOutcome());
        assertEquals(
                ProfileMemoryStore.UpdateOutcome.OWNER_BYTE_LIMIT,
                byteHarness.store.updateOwned(update(
                        userKey(OWNER_A, ProfileMemoryStore.Field.NAVIGATION_AVOID_HIGHWAY),
                        ProfileMemoryStore.ProfileValue.booleanValue(false),
                        consentUser(OWNER_A),
                        10)).getOutcome());
        assertEquals(
                ProfileMemoryStore.ExportOutcome.EXPORT_LIMIT,
                harness.store.exportOwned(new ProfileMemoryStore.TrustedExport(
                        userScope(OWNER_A),
                        EnumSet.of(
                                ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT,
                                ProfileMemoryStore.Field.NAVIGATION_AVOID_HIGHWAY,
                                ProfileMemoryStore.Field.LANGUAGE_TAG),
                        consentUser(OWNER_A),
                        null)).getOutcome());

        try {
            limits(0, 1, 1, 1, 1, 1);
            fail("zero record capacity must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            ProfileMemoryStore.ProfileValue.textValue("contains space");
            ProfileMemoryStore.TrustedUpdate invalid = update(
                    userKey(OWNER_A, ProfileMemoryStore.Field.LANGUAGE_TAG),
                    ProfileMemoryStore.ProfileValue.textValue("contains space"),
                    consentUser(OWNER_A),
                    10);
            assertEquals(
                    ProfileMemoryStore.UpdateOutcome.VALUE_INVALID,
                    harness.store.updateOwned(invalid).getOutcome());
        } catch (IllegalArgumentException expected) {
            fail("field policy, not generic text construction, should reject language format");
        }
    }

    private static Harness harness(ProfileMemoryStore.Limits limits) {
        return harness(limits, EnumSet.allOf(ProfileMemoryStore.Field.class));
    }

    private static Harness harness(
            ProfileMemoryStore.Limits limits,
            EnumSet<ProfileMemoryStore.Field> fields) {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicBoolean consentActive = new AtomicBoolean(true);
        AtomicBoolean authorizationActive = new AtomicBoolean(true);
        AtomicBoolean ownerReady = new AtomicBoolean(true);
        Harness harness = new Harness(clock, consentActive, authorizationActive, ownerReady);
        ProfileMemoryStore.EncryptionOwner owner = new ProfileMemoryStore.EncryptionOwner() {
            @Override
            public ProfileMemoryStore.EncryptionOwnerState currentState() {
                return new ProfileMemoryStore.EncryptionOwnerState(
                        "contract-test-owner",
                        KEY_ALIAS,
                        1L,
                        "contract-test-xor",
                        ownerReady.get(),
                        ownerReady.get(),
                        false);
            }

            @Override
            public ProfileMemoryStore.SealedPayload seal(
                    ProfileMemoryStore.ProfileKey key,
                    long revision,
                    byte[] plaintext) {
                byte[] ciphertext = xor(plaintext);
                harness.lastCiphertext = ciphertext.clone();
                return new ProfileMemoryStore.SealedPayload(
                        "contract-test-owner",
                        KEY_ALIAS,
                        1L,
                        "contract-test-xor",
                        ciphertext);
            }

            @Override
            public byte[] open(
                    ProfileMemoryStore.ProfileKey key,
                    long revision,
                    ProfileMemoryStore.SealedPayload payload) {
                return xor(payload.getCiphertextCopy());
            }
        };
        harness.store = ProfileMemoryStore.createForContractTest(
                limits,
                new ProfileMemoryStore.FieldPolicy(fields),
                (evidence, now) -> consentActive.get(),
                (evidence, now) -> authorizationActive.get(),
                owner,
                clock::get);
        return harness;
    }

    private static ProfileMemoryStore.Limits limits(
            int records,
            int ownerRecords,
            int ownerBytes,
            int payloadBytes,
            int exportRecords,
            long retentionMs) {
        return new ProfileMemoryStore.Limits(
                records,
                ownerRecords,
                ownerBytes,
                payloadBytes,
                exportRecords,
                retentionMs);
    }

    private static ProfileMemoryStore.TrustedUpdate update(
            ProfileMemoryStore.ProfileKey key,
            ProfileMemoryStore.ProfileValue value,
            ProfileMemoryStore.ConsentEvidence consent,
            long retentionMs) {
        return new ProfileMemoryStore.TrustedUpdate(key, value, retentionMs, consent);
    }

    private static ProfileMemoryStore.ProfileValue integer(int value) {
        return ProfileMemoryStore.ProfileValue.integerValue(value);
    }

    private static ProfileMemoryStore.ProfileScope userScope(String owner) {
        return new ProfileMemoryStore.ProfileScope(
                owner,
                ProfileMemoryStore.SeatScope.USER_GLOBAL);
    }

    private static ProfileMemoryStore.ProfileKey userKey(
            String owner,
            ProfileMemoryStore.Field field) {
        return new ProfileMemoryStore.ProfileKey(userScope(owner), field);
    }

    private static ProfileMemoryStore.ProfileKey seatKey(
            String owner,
            ProfileMemoryStore.Field field) {
        return new ProfileMemoryStore.ProfileKey(
                new ProfileMemoryStore.ProfileScope(owner, ProfileMemoryStore.SeatScope.DRIVER),
                field);
    }

    private static ProfileMemoryStore.ConsentEvidence consentSeat(String owner) {
        return new ProfileMemoryStore.ConsentEvidence(
                "consent-seat",
                owner,
                EnumSet.of(
                        ProfileMemoryStore.Field.CABIN_TEMPERATURE_C_X10,
                        ProfileMemoryStore.Field.SEAT_RECLINE_DEGREE_X10,
                        ProfileMemoryStore.Field.SEAT_HEAT_LEVEL,
                        ProfileMemoryStore.Field.SEAT_VENT_LEVEL),
                EnumSet.of(ProfileMemoryStore.SeatScope.DRIVER),
                900L,
                1_100L,
                1L,
                EVIDENCE);
    }

    private static ProfileMemoryStore.ConsentEvidence consentUser(String owner) {
        return new ProfileMemoryStore.ConsentEvidence(
                "consent-user",
                owner,
                EnumSet.of(
                        ProfileMemoryStore.Field.MEDIA_VOLUME_PERCENT,
                        ProfileMemoryStore.Field.NAVIGATION_AVOID_HIGHWAY,
                        ProfileMemoryStore.Field.LANGUAGE_TAG),
                EnumSet.of(ProfileMemoryStore.SeatScope.USER_GLOBAL),
                900L,
                1_100L,
                1L,
                EVIDENCE);
    }

    private static ProfileMemoryStore.AuthorizationEvidence deleteAuthorization(
            String owner,
            ProfileMemoryStore.Field field) {
        return new ProfileMemoryStore.AuthorizationEvidence(
                "delete-authorization",
                owner,
                ProfileMemoryStore.AuthorizationOperation.DELETE,
                EnumSet.of(field),
                EnumSet.of(ProfileMemoryStore.SeatScope.DRIVER),
                900L,
                1_100L,
                1L,
                EVIDENCE);
    }

    private static ProfileMemoryStore.AuthorizationEvidence exportAuthorization(
            String owner,
            EnumSet<ProfileMemoryStore.Field> fields) {
        return new ProfileMemoryStore.AuthorizationEvidence(
                "export-authorization",
                owner,
                ProfileMemoryStore.AuthorizationOperation.EXPORT,
                fields,
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

    private static final class Harness {
        private final AtomicLong clock;
        private final AtomicBoolean consentActive;
        private final AtomicBoolean authorizationActive;
        private final AtomicBoolean ownerReady;
        private ProfileMemoryStore store;
        private byte[] lastCiphertext = new byte[0];

        private Harness(
                AtomicLong clock,
                AtomicBoolean consentActive,
                AtomicBoolean authorizationActive,
                AtomicBoolean ownerReady) {
            this.clock = clock;
            this.consentActive = consentActive;
            this.authorizationActive = authorizationActive;
            this.ownerReady = ownerReady;
        }
    }
}
