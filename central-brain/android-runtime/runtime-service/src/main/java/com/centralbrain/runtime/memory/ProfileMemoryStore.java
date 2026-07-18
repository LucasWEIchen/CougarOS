package com.centralbrain.runtime.memory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Consent-governed, process-local profile memory contract.
 *
 * <p>The store retains only payloads sealed by an injected encryption owner. Production
 * persistence, key ownership and consent authorities are intentionally not provided here.</p>
 */
public final class ProfileMemoryStore {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_RETENTION_MS = 30L * 24L * 60L * 60L * 1_000L;

    private static final int MAX_RECORDS = 512;
    private static final int MAX_RECORDS_PER_OWNER = 64;
    private static final int MAX_SEALED_BYTES_PER_OWNER = 256 * 1024;
    private static final int MAX_EXPORT_RECORDS = 32;
    private static final int MAX_SEALED_PAYLOAD_BYTES = 8 * 1024;
    private static final int MAX_ID_LENGTH = 128;

    public enum ScopeKind {
        USER,
        SEAT
    }

    public enum SeatScope {
        USER_GLOBAL,
        DRIVER,
        FRONT_PASSENGER,
        REAR_LEFT,
        REAR_RIGHT
    }

    public enum ValueKind {
        INTEGER,
        BOOLEAN,
        TEXT
    }

    public enum Field {
        CABIN_TEMPERATURE_C_X10(ScopeKind.SEAT, ValueKind.INTEGER, 160, 300, 0),
        SEAT_RECLINE_DEGREE_X10(ScopeKind.SEAT, ValueKind.INTEGER, 0, 450, 0),
        SEAT_HEAT_LEVEL(ScopeKind.SEAT, ValueKind.INTEGER, 0, 3, 0),
        SEAT_VENT_LEVEL(ScopeKind.SEAT, ValueKind.INTEGER, 0, 3, 0),
        MEDIA_VOLUME_PERCENT(ScopeKind.USER, ValueKind.INTEGER, 0, 100, 0),
        NAVIGATION_AVOID_HIGHWAY(ScopeKind.USER, ValueKind.BOOLEAN, 0, 0, 0),
        LANGUAGE_TAG(ScopeKind.USER, ValueKind.TEXT, 0, 0, 16);

        private final ScopeKind scopeKind;
        private final ValueKind valueKind;
        private final int minimum;
        private final int maximum;
        private final int maxTextBytes;

        Field(
                ScopeKind scopeKind,
                ValueKind valueKind,
                int minimum,
                int maximum,
                int maxTextBytes) {
            this.scopeKind = scopeKind;
            this.valueKind = valueKind;
            this.minimum = minimum;
            this.maximum = maximum;
            this.maxTextBytes = maxTextBytes;
        }

        public ScopeKind getScopeKind() {
            return scopeKind;
        }

        public ValueKind getValueKind() {
            return valueKind;
        }

        private boolean accepts(ProfileValue value) {
            if (value == null || value.kind != valueKind) {
                return false;
            }
            if (valueKind == ValueKind.INTEGER) {
                return value.integerValue >= minimum && value.integerValue <= maximum;
            }
            if (valueKind == ValueKind.TEXT) {
                byte[] encoded = value.textValue.getBytes(StandardCharsets.UTF_8);
                return encoded.length <= maxTextBytes && value.textValue.matches("[A-Za-z0-9-]{2,16}");
            }
            return true;
        }
    }

    public enum AuthorizationOperation {
        DELETE,
        EXPORT
    }

    public enum UpdateOutcome {
        CREATED,
        UPDATED,
        CONSENT_REQUIRED,
        CONSENT_INVALID,
        FIELD_NOT_ALLOWED,
        SCOPE_MISMATCH,
        VALUE_INVALID,
        RETENTION_LIMIT,
        ENCRYPTION_OWNER_UNAVAILABLE,
        SEAL_FAILED,
        RECORD_LIMIT,
        OWNER_LIMIT,
        OWNER_BYTE_LIMIT
    }

    public enum ReadOutcome {
        FOUND,
        NOT_FOUND,
        CONSENT_REQUIRED,
        CONSENT_INVALID,
        FIELD_NOT_ALLOWED,
        SCOPE_MISMATCH,
        ENCRYPTION_OWNER_UNAVAILABLE,
        OPEN_FAILED,
        VALUE_INVALID
    }

    public enum DeleteOutcome {
        APPLIED,
        NOT_FOUND,
        AUTHORIZATION_REQUIRED,
        AUTHORIZATION_INVALID,
        FIELD_NOT_ALLOWED,
        SCOPE_MISMATCH,
        ENCRYPTION_OWNER_UNAVAILABLE
    }

    public enum ExportOutcome {
        EXPORTED,
        CONSENT_REQUIRED,
        CONSENT_INVALID,
        AUTHORIZATION_REQUIRED,
        AUTHORIZATION_INVALID,
        FIELD_NOT_ALLOWED,
        SCOPE_MISMATCH,
        EXPORT_LIMIT,
        ENCRYPTION_OWNER_UNAVAILABLE,
        OPEN_FAILED,
        VALUE_INVALID
    }

    public interface ConsentAuthority {
        boolean isConsentActive(ConsentEvidence evidence, long nowElapsedRealtimeMs);
    }

    public interface AuthorizationAuthority {
        boolean isAuthorizationActive(
                AuthorizationEvidence evidence,
                long nowElapsedRealtimeMs);
    }

    public interface EncryptionOwner {
        EncryptionOwnerState currentState();

        SealedPayload seal(ProfileKey key, long revision, byte[] plaintext);

        byte[] open(ProfileKey key, long revision, SealedPayload payload);
    }

    private final Limits limits;
    private final FieldPolicy fieldPolicy;
    private final ConsentAuthority consentAuthority;
    private final AuthorizationAuthority authorizationAuthority;
    private final EncryptionOwner encryptionOwner;
    private final LongSupplier elapsedRealtimeMs;
    private final Map<ProfileKey, Entry> records = new LinkedHashMap<>();

    private long createdCount;
    private long updatedCount;
    private long deletedCount;
    private long expiredCount;
    private long exportedCount;
    private long wipedSealedByteCount;

    private ProfileMemoryStore(
            Limits limits,
            FieldPolicy fieldPolicy,
            ConsentAuthority consentAuthority,
            AuthorizationAuthority authorizationAuthority,
            EncryptionOwner encryptionOwner,
            LongSupplier elapsedRealtimeMs) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.fieldPolicy = Objects.requireNonNull(fieldPolicy, "fieldPolicy");
        this.consentAuthority = Objects.requireNonNull(consentAuthority, "consentAuthority");
        this.authorizationAuthority = Objects.requireNonNull(
                authorizationAuthority,
                "authorizationAuthority");
        this.encryptionOwner = Objects.requireNonNull(encryptionOwner, "encryptionOwner");
        this.elapsedRealtimeMs = Objects.requireNonNull(
                elapsedRealtimeMs,
                "elapsedRealtimeMs");
    }

    public static ProfileMemoryStore createForContractTest(
            Limits limits,
            FieldPolicy fieldPolicy,
            ConsentAuthority consentAuthority,
            AuthorizationAuthority authorizationAuthority,
            EncryptionOwner encryptionOwner,
            LongSupplier elapsedRealtimeMs) {
        return new ProfileMemoryStore(
                limits,
                fieldPolicy,
                consentAuthority,
                authorizationAuthority,
                encryptionOwner,
                elapsedRealtimeMs);
    }

    public synchronized UpdateResult updateOwned(TrustedUpdate request) {
        Objects.requireNonNull(request, "request");
        long now = now();
        expireRecords(now);
        UpdateOutcome structural = validateUpdateStructure(request);
        if (structural != null) {
            return UpdateResult.rejected(structural);
        }
        if (request.consent == null) {
            return UpdateResult.rejected(UpdateOutcome.CONSENT_REQUIRED);
        }
        if (!consentAllows(request.consent, request.key, request.key.field, now)) {
            return UpdateResult.rejected(UpdateOutcome.CONSENT_INVALID);
        }
        EncryptionOwnerState ownerState = readyOwnerState();
        if (ownerState == null) {
            return UpdateResult.rejected(UpdateOutcome.ENCRYPTION_OWNER_UNAVAILABLE);
        }

        Entry existing = records.get(request.key);
        if (existing == null && records.size() >= limits.maxRecords) {
            return UpdateResult.rejected(UpdateOutcome.RECORD_LIMIT);
        }
        if (existing == null
                && countOwned(request.key.ownerFingerprint) >= limits.maxRecordsPerOwner) {
            return UpdateResult.rejected(UpdateOutcome.OWNER_LIMIT);
        }

        long revision = existing == null ? 1L : existing.revision + 1L;
        byte[] plaintext = request.value.encode();
        SealedPayload sealed;
        try {
            sealed = encryptionOwner.seal(request.key, revision, plaintext);
        } catch (RuntimeException exception) {
            sealed = null;
        } finally {
            wipe(plaintext);
        }
        if (!validSealedPayload(sealed, ownerState)) {
            wipeSealed(sealed);
            return UpdateResult.rejected(UpdateOutcome.SEAL_FAILED);
        }

        int currentOwnerBytes = sealedBytesOwned(request.key.ownerFingerprint);
        int previousBytes = existing == null ? 0 : existing.sealed.ciphertext.length;
        int projectedBytes = currentOwnerBytes - previousBytes + sealed.ciphertext.length;
        if (projectedBytes > limits.maxSealedBytesPerOwner) {
            wipeSealed(sealed);
            return UpdateResult.rejected(UpdateOutcome.OWNER_BYTE_LIMIT);
        }

        long expiresAt = saturatedAdd(now, request.retentionMs);
        expiresAt = Math.min(expiresAt, request.consent.validUntilElapsedRealtimeMs);
        Entry replacement = new Entry(request.key, revision, expiresAt, sealed);
        records.put(request.key, replacement);
        if (existing == null) {
            createdCount++;
        } else {
            wipeSealed(existing.sealed);
            updatedCount++;
        }
        return UpdateResult.accepted(
                existing == null ? UpdateOutcome.CREATED : UpdateOutcome.UPDATED,
                replacement.metadata());
    }

    public synchronized ReadResult readOwned(TrustedRead request) {
        Objects.requireNonNull(request, "request");
        long now = now();
        expireRecords(now);
        ReadOutcome structural = validateReadStructure(request);
        if (structural != null) {
            return ReadResult.rejected(structural);
        }
        if (request.consent == null) {
            return ReadResult.rejected(ReadOutcome.CONSENT_REQUIRED);
        }
        if (!consentAllows(request.consent, request.key, request.key.field, now)) {
            return ReadResult.rejected(ReadOutcome.CONSENT_INVALID);
        }
        EncryptionOwnerState ownerState = readyOwnerState();
        if (ownerState == null) {
            return ReadResult.rejected(ReadOutcome.ENCRYPTION_OWNER_UNAVAILABLE);
        }
        Entry entry = records.get(request.key);
        if (entry == null) {
            return ReadResult.rejected(ReadOutcome.NOT_FOUND);
        }
        OpenedValue opened = openValue(entry, ownerState);
        if (opened.outcome != null) {
            return ReadResult.rejected(opened.outcome);
        }
        return ReadResult.found(entry.metadata(), opened.value);
    }

    public synchronized DeleteResult deleteOwned(TrustedDelete request) {
        Objects.requireNonNull(request, "request");
        long now = now();
        expireRecords(now);
        DeleteOutcome structural = validateDeleteStructure(request);
        if (structural != null) {
            return DeleteResult.of(structural, 0L);
        }
        if (request.authorization == null) {
            return DeleteResult.of(DeleteOutcome.AUTHORIZATION_REQUIRED, 0L);
        }
        if (!authorizationAllows(
                request.authorization,
                AuthorizationOperation.DELETE,
                request.key,
                Collections.singleton(request.key.field),
                now)) {
            return DeleteResult.of(DeleteOutcome.AUTHORIZATION_INVALID, 0L);
        }
        if (readyOwnerState() == null) {
            return DeleteResult.of(DeleteOutcome.ENCRYPTION_OWNER_UNAVAILABLE, 0L);
        }
        Entry removed = records.remove(request.key);
        if (removed == null) {
            return DeleteResult.of(DeleteOutcome.NOT_FOUND, 0L);
        }
        int wipedBytes = removed.sealed.ciphertext.length;
        wipeSealed(removed.sealed);
        deletedCount++;
        return DeleteResult.of(DeleteOutcome.APPLIED, wipedBytes);
    }

    public synchronized ExportResult exportOwned(TrustedExport request) {
        Objects.requireNonNull(request, "request");
        long now = now();
        expireRecords(now);
        ExportOutcome structural = validateExportStructure(request);
        if (structural != null) {
            return ExportResult.rejected(structural);
        }
        if (request.consent == null) {
            return ExportResult.rejected(ExportOutcome.CONSENT_REQUIRED);
        }
        for (Field field : request.fields) {
            if (!consentAllows(
                    request.consent,
                    new ProfileKey(request.scope, field),
                    field,
                    now)) {
                return ExportResult.rejected(ExportOutcome.CONSENT_INVALID);
            }
        }
        if (request.authorization == null) {
            return ExportResult.rejected(ExportOutcome.AUTHORIZATION_REQUIRED);
        }
        ProfileKey authorizationKey = new ProfileKey(
                request.scope,
                request.fields.iterator().next());
        if (!authorizationAllows(
                request.authorization,
                AuthorizationOperation.EXPORT,
                authorizationKey,
                request.fields,
                now)) {
            return ExportResult.rejected(ExportOutcome.AUTHORIZATION_INVALID);
        }
        EncryptionOwnerState ownerState = readyOwnerState();
        if (ownerState == null) {
            return ExportResult.rejected(ExportOutcome.ENCRYPTION_OWNER_UNAVAILABLE);
        }

        List<Field> fields = new ArrayList<>(request.fields);
        fields.sort(Comparator.comparing(Enum::name));
        List<ExportItem> output = new ArrayList<>();
        for (Field field : fields) {
            Entry entry = records.get(new ProfileKey(request.scope, field));
            if (entry == null) {
                continue;
            }
            OpenedValue opened = openValue(entry, ownerState);
            if (opened.outcome == ReadOutcome.OPEN_FAILED) {
                return ExportResult.rejected(ExportOutcome.OPEN_FAILED);
            }
            if (opened.outcome != null) {
                return ExportResult.rejected(ExportOutcome.VALUE_INVALID);
            }
            output.add(new ExportItem(entry.metadata(), opened.value));
        }
        exportedCount += output.size();
        return ExportResult.exported(output);
    }

    public synchronized Snapshot snapshot() {
        expireRecords(now());
        int sealedBytes = 0;
        for (Entry entry : records.values()) {
            sealedBytes += entry.sealed.ciphertext.length;
        }
        return new Snapshot(
                records.size(),
                sealedBytes,
                createdCount,
                updatedCount,
                deletedCount,
                expiredCount,
                exportedCount,
                wipedSealedByteCount,
                false,
                false,
                false,
                false);
    }

    public boolean isEncryptionOwnerGateDefined() {
        return true;
    }

    public boolean isDurableEncryptedStorageAvailable() {
        return false;
    }

    public boolean isProductionEncryptionOwnerConfigured() {
        return false;
    }

    public boolean isConsentAuthorityProductionWired() {
        return false;
    }

    public boolean isRuntimeWired() {
        return false;
    }

    public boolean isContentLoggingEnabled() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    private UpdateOutcome validateUpdateStructure(TrustedUpdate request) {
        if (!fieldPolicy.allowedFields.contains(request.key.field)) {
            return UpdateOutcome.FIELD_NOT_ALLOWED;
        }
        if (!scopeMatches(request.key.scope, request.key.field)) {
            return UpdateOutcome.SCOPE_MISMATCH;
        }
        if (!request.key.field.accepts(request.value)) {
            return UpdateOutcome.VALUE_INVALID;
        }
        if (request.retentionMs > limits.maxRetentionMs) {
            return UpdateOutcome.RETENTION_LIMIT;
        }
        return null;
    }

    private ReadOutcome validateReadStructure(TrustedRead request) {
        if (!fieldPolicy.allowedFields.contains(request.key.field)) {
            return ReadOutcome.FIELD_NOT_ALLOWED;
        }
        return scopeMatches(request.key.scope, request.key.field)
                ? null
                : ReadOutcome.SCOPE_MISMATCH;
    }

    private DeleteOutcome validateDeleteStructure(TrustedDelete request) {
        if (!fieldPolicy.allowedFields.contains(request.key.field)) {
            return DeleteOutcome.FIELD_NOT_ALLOWED;
        }
        return scopeMatches(request.key.scope, request.key.field)
                ? null
                : DeleteOutcome.SCOPE_MISMATCH;
    }

    private ExportOutcome validateExportStructure(TrustedExport request) {
        if (request.fields.size() > limits.maxExportRecords) {
            return ExportOutcome.EXPORT_LIMIT;
        }
        for (Field field : request.fields) {
            if (!fieldPolicy.allowedFields.contains(field)) {
                return ExportOutcome.FIELD_NOT_ALLOWED;
            }
            if (!scopeMatches(request.scope, field)) {
                return ExportOutcome.SCOPE_MISMATCH;
            }
        }
        return null;
    }

    private boolean consentAllows(
            ConsentEvidence consent,
            ProfileKey key,
            Field field,
            long now) {
        if (!consent.structurallyAllows(key.scope, field, now)) {
            return false;
        }
        try {
            return consentAuthority.isConsentActive(consent, now);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean authorizationAllows(
            AuthorizationEvidence authorization,
            AuthorizationOperation operation,
            ProfileKey key,
            Set<Field> fields,
            long now) {
        if (!authorization.structurallyAllows(operation, key.scope, fields, now)) {
            return false;
        }
        try {
            return authorizationAuthority.isAuthorizationActive(authorization, now);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private EncryptionOwnerState readyOwnerState() {
        try {
            EncryptionOwnerState state = encryptionOwner.currentState();
            return state != null && state.isReady() ? state : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private OpenedValue openValue(Entry entry, EncryptionOwnerState ownerState) {
        if (!validSealedPayload(entry.sealed, ownerState)) {
            return OpenedValue.failed(ReadOutcome.OPEN_FAILED);
        }
        byte[] plaintext;
        try {
            plaintext = encryptionOwner.open(entry.key, entry.revision, entry.sealed.copy());
        } catch (RuntimeException exception) {
            plaintext = null;
        }
        if (plaintext == null || plaintext.length == 0 || plaintext.length > 256) {
            wipe(plaintext);
            return OpenedValue.failed(ReadOutcome.OPEN_FAILED);
        }
        try {
            ProfileValue value = ProfileValue.decode(plaintext);
            return entry.key.field.accepts(value)
                    ? OpenedValue.opened(value)
                    : OpenedValue.failed(ReadOutcome.VALUE_INVALID);
        } catch (RuntimeException exception) {
            return OpenedValue.failed(ReadOutcome.VALUE_INVALID);
        } finally {
            wipe(plaintext);
        }
    }

    private boolean validSealedPayload(
            SealedPayload payload,
            EncryptionOwnerState ownerState) {
        return payload != null
                && payload.ciphertext.length > 0
                && payload.ciphertext.length <= limits.maxSealedPayloadBytes
                && payload.ownerId.equals(ownerState.ownerId)
                && payload.keyAliasDigest.equals(ownerState.keyAliasDigest)
                && payload.keyGeneration == ownerState.keyGeneration
                && payload.algorithmId.equals(ownerState.algorithmId);
    }

    private void expireRecords(long now) {
        List<ProfileKey> expiredKeys = new ArrayList<>();
        for (Map.Entry<ProfileKey, Entry> record : records.entrySet()) {
            if (record.getValue().expiresAtElapsedRealtimeMs <= now) {
                expiredKeys.add(record.getKey());
            }
        }
        for (ProfileKey key : expiredKeys) {
            Entry removed = records.remove(key);
            if (removed != null) {
                wipeSealed(removed.sealed);
                expiredCount++;
            }
        }
    }

    private int countOwned(String ownerFingerprint) {
        int count = 0;
        for (ProfileKey key : records.keySet()) {
            if (key.ownerFingerprint.equals(ownerFingerprint)) {
                count++;
            }
        }
        return count;
    }

    private int sealedBytesOwned(String ownerFingerprint) {
        int count = 0;
        for (Entry entry : records.values()) {
            if (entry.key.ownerFingerprint.equals(ownerFingerprint)) {
                count += entry.sealed.ciphertext.length;
            }
        }
        return count;
    }

    private void wipeSealed(SealedPayload payload) {
        if (payload != null) {
            wipedSealedByteCount += payload.ciphertext.length;
            wipe(payload.ciphertext);
        }
    }

    private long now() {
        long now = elapsedRealtimeMs.getAsLong();
        if (now < 0L) {
            throw new IllegalStateException("elapsed realtime must be nonnegative");
        }
        return now;
    }

    private static boolean scopeMatches(ProfileScope scope, Field field) {
        return field.scopeKind == ScopeKind.USER
                ? scope.seatScope == SeatScope.USER_GLOBAL
                : scope.seatScope != SeatScope.USER_GLOBAL;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static void wipe(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static String requireId(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > MAX_ID_LENGTH
                || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private static String requireDigest(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 digest");
        }
        return normalized;
    }

    public static final class ProfileScope {
        private final String ownerFingerprint;
        private final SeatScope seatScope;

        public ProfileScope(String ownerFingerprint, SeatScope seatScope) {
            this.ownerFingerprint = requireDigest(ownerFingerprint, "ownerFingerprint");
            this.seatScope = Objects.requireNonNull(seatScope, "seatScope");
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public SeatScope getSeatScope() {
            return seatScope;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProfileScope)) {
                return false;
            }
            ProfileScope that = (ProfileScope) other;
            return ownerFingerprint.equals(that.ownerFingerprint) && seatScope == that.seatScope;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerFingerprint, seatScope);
        }
    }

    public static final class ProfileKey {
        private final ProfileScope scope;
        private final String ownerFingerprint;
        private final Field field;

        public ProfileKey(ProfileScope scope, Field field) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.ownerFingerprint = scope.ownerFingerprint;
            this.field = Objects.requireNonNull(field, "field");
        }

        public ProfileScope getScope() {
            return scope;
        }

        public Field getField() {
            return field;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProfileKey)) {
                return false;
            }
            ProfileKey that = (ProfileKey) other;
            return scope.equals(that.scope) && field == that.field;
        }

        @Override
        public int hashCode() {
            return Objects.hash(scope, field);
        }
    }

    public static final class ProfileValue {
        private final ValueKind kind;
        private final int integerValue;
        private final boolean booleanValue;
        private final String textValue;

        private ProfileValue(
                ValueKind kind,
                int integerValue,
                boolean booleanValue,
                String textValue) {
            this.kind = kind;
            this.integerValue = integerValue;
            this.booleanValue = booleanValue;
            this.textValue = textValue;
        }

        public static ProfileValue integerValue(int value) {
            return new ProfileValue(ValueKind.INTEGER, value, false, "");
        }

        public static ProfileValue booleanValue(boolean value) {
            return new ProfileValue(ValueKind.BOOLEAN, 0, value, "");
        }

        public static ProfileValue textValue(String value) {
            String normalized = Objects.requireNonNull(value, "value").trim();
            if (normalized.isEmpty()
                    || normalized.getBytes(StandardCharsets.UTF_8).length > 64
                    || normalized.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("text profile value is invalid");
            }
            return new ProfileValue(ValueKind.TEXT, 0, false, normalized);
        }

        public ValueKind getKind() {
            return kind;
        }

        public int getIntegerValue() {
            return integerValue;
        }

        public boolean getBooleanValue() {
            return booleanValue;
        }

        public String getTextValue() {
            return textValue;
        }

        private byte[] encode() {
            String canonical;
            if (kind == ValueKind.INTEGER) {
                canonical = "I:" + integerValue;
            } else if (kind == ValueKind.BOOLEAN) {
                canonical = booleanValue ? "B:1" : "B:0";
            } else {
                canonical = "T:" + textValue;
            }
            return canonical.getBytes(StandardCharsets.UTF_8);
        }

        private static ProfileValue decode(byte[] encoded) {
            String canonical = new String(encoded, StandardCharsets.UTF_8);
            if (canonical.startsWith("I:")) {
                return integerValue(Integer.parseInt(canonical.substring(2)));
            }
            if (canonical.equals("B:1") || canonical.equals("B:0")) {
                return booleanValue(canonical.endsWith("1"));
            }
            if (canonical.startsWith("T:")) {
                return textValue(canonical.substring(2));
            }
            throw new IllegalArgumentException("profile value encoding is invalid");
        }
    }

    public static final class FieldPolicy {
        private final Set<Field> allowedFields;

        public FieldPolicy(Set<Field> allowedFields) {
            Objects.requireNonNull(allowedFields, "allowedFields");
            if (allowedFields.isEmpty()) {
                throw new IllegalArgumentException("allowedFields must not be empty");
            }
            this.allowedFields = Collections.unmodifiableSet(EnumSet.copyOf(allowedFields));
        }

        public static FieldPolicy automotiveDefault() {
            return new FieldPolicy(EnumSet.allOf(Field.class));
        }

        public Set<Field> getAllowedFields() {
            return allowedFields;
        }
    }

    public static final class ConsentEvidence {
        private final String consentId;
        private final String ownerFingerprint;
        private final Set<Field> allowedFields;
        private final Set<SeatScope> allowedSeatScopes;
        private final long validFromElapsedRealtimeMs;
        private final long validUntilElapsedRealtimeMs;
        private final long revision;
        private final String evidenceDigest;

        public ConsentEvidence(
                String consentId,
                String ownerFingerprint,
                Set<Field> allowedFields,
                Set<SeatScope> allowedSeatScopes,
                long validFromElapsedRealtimeMs,
                long validUntilElapsedRealtimeMs,
                long revision,
                String evidenceDigest) {
            this.consentId = requireId(consentId, "consentId");
            this.ownerFingerprint = requireDigest(ownerFingerprint, "ownerFingerprint");
            Objects.requireNonNull(allowedFields, "allowedFields");
            Objects.requireNonNull(allowedSeatScopes, "allowedSeatScopes");
            if (allowedFields.isEmpty() || allowedSeatScopes.isEmpty()
                    || validFromElapsedRealtimeMs < 0L
                    || validUntilElapsedRealtimeMs <= validFromElapsedRealtimeMs
                    || revision < 1L) {
                throw new IllegalArgumentException("consent evidence is invalid");
            }
            this.allowedFields = Collections.unmodifiableSet(EnumSet.copyOf(allowedFields));
            this.allowedSeatScopes = Collections.unmodifiableSet(
                    EnumSet.copyOf(allowedSeatScopes));
            this.validFromElapsedRealtimeMs = validFromElapsedRealtimeMs;
            this.validUntilElapsedRealtimeMs = validUntilElapsedRealtimeMs;
            this.revision = revision;
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
        }

        private boolean structurallyAllows(ProfileScope scope, Field field, long now) {
            return ownerFingerprint.equals(scope.ownerFingerprint)
                    && allowedFields.contains(field)
                    && allowedSeatScopes.contains(scope.seatScope)
                    && validFromElapsedRealtimeMs <= now
                    && now < validUntilElapsedRealtimeMs;
        }

        public String getConsentId() {
            return consentId;
        }

        public long getRevision() {
            return revision;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }
    }

    public static final class AuthorizationEvidence {
        private final String authorizationId;
        private final String ownerFingerprint;
        private final AuthorizationOperation operation;
        private final Set<Field> allowedFields;
        private final Set<SeatScope> allowedSeatScopes;
        private final long validFromElapsedRealtimeMs;
        private final long validUntilElapsedRealtimeMs;
        private final long revision;
        private final String evidenceDigest;

        public AuthorizationEvidence(
                String authorizationId,
                String ownerFingerprint,
                AuthorizationOperation operation,
                Set<Field> allowedFields,
                Set<SeatScope> allowedSeatScopes,
                long validFromElapsedRealtimeMs,
                long validUntilElapsedRealtimeMs,
                long revision,
                String evidenceDigest) {
            this.authorizationId = requireId(authorizationId, "authorizationId");
            this.ownerFingerprint = requireDigest(ownerFingerprint, "ownerFingerprint");
            this.operation = Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(allowedFields, "allowedFields");
            Objects.requireNonNull(allowedSeatScopes, "allowedSeatScopes");
            if (allowedFields.isEmpty() || allowedSeatScopes.isEmpty()
                    || validFromElapsedRealtimeMs < 0L
                    || validUntilElapsedRealtimeMs <= validFromElapsedRealtimeMs
                    || revision < 1L) {
                throw new IllegalArgumentException("authorization evidence is invalid");
            }
            this.allowedFields = Collections.unmodifiableSet(EnumSet.copyOf(allowedFields));
            this.allowedSeatScopes = Collections.unmodifiableSet(
                    EnumSet.copyOf(allowedSeatScopes));
            this.validFromElapsedRealtimeMs = validFromElapsedRealtimeMs;
            this.validUntilElapsedRealtimeMs = validUntilElapsedRealtimeMs;
            this.revision = revision;
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
        }

        private boolean structurallyAllows(
                AuthorizationOperation requestedOperation,
                ProfileScope scope,
                Set<Field> requestedFields,
                long now) {
            return operation == requestedOperation
                    && ownerFingerprint.equals(scope.ownerFingerprint)
                    && allowedFields.containsAll(requestedFields)
                    && allowedSeatScopes.contains(scope.seatScope)
                    && validFromElapsedRealtimeMs <= now
                    && now < validUntilElapsedRealtimeMs;
        }

        public String getAuthorizationId() {
            return authorizationId;
        }

        public long getRevision() {
            return revision;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }
    }

    public static final class EncryptionOwnerState {
        private final String ownerId;
        private final String keyAliasDigest;
        private final long keyGeneration;
        private final String algorithmId;
        private final boolean encryptionAtRestAvailable;
        private final boolean keyLifecycleConfigured;
        private final boolean productionEvidence;

        public EncryptionOwnerState(
                String ownerId,
                String keyAliasDigest,
                long keyGeneration,
                String algorithmId,
                boolean encryptionAtRestAvailable,
                boolean keyLifecycleConfigured,
                boolean productionEvidence) {
            this.ownerId = requireId(ownerId, "ownerId");
            this.keyAliasDigest = requireDigest(keyAliasDigest, "keyAliasDigest");
            if (keyGeneration < 1L) {
                throw new IllegalArgumentException("keyGeneration must be positive");
            }
            this.keyGeneration = keyGeneration;
            this.algorithmId = requireId(algorithmId, "algorithmId");
            this.encryptionAtRestAvailable = encryptionAtRestAvailable;
            this.keyLifecycleConfigured = keyLifecycleConfigured;
            this.productionEvidence = productionEvidence;
        }

        public boolean isReady() {
            return encryptionAtRestAvailable && keyLifecycleConfigured;
        }

        public boolean isProductionEvidence() {
            return productionEvidence;
        }
    }

    public static final class SealedPayload {
        private final String ownerId;
        private final String keyAliasDigest;
        private final long keyGeneration;
        private final String algorithmId;
        private final byte[] ciphertext;

        public SealedPayload(
                String ownerId,
                String keyAliasDigest,
                long keyGeneration,
                String algorithmId,
                byte[] ciphertext) {
            this.ownerId = requireId(ownerId, "ownerId");
            this.keyAliasDigest = requireDigest(keyAliasDigest, "keyAliasDigest");
            if (keyGeneration < 1L) {
                throw new IllegalArgumentException("keyGeneration must be positive");
            }
            this.keyGeneration = keyGeneration;
            this.algorithmId = requireId(algorithmId, "algorithmId");
            Objects.requireNonNull(ciphertext, "ciphertext");
            if (ciphertext.length < 1 || ciphertext.length > MAX_SEALED_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("ciphertext length is invalid");
            }
            this.ciphertext = ciphertext.clone();
        }

        public byte[] getCiphertextCopy() {
            return ciphertext.clone();
        }

        private SealedPayload copy() {
            return new SealedPayload(
                    ownerId,
                    keyAliasDigest,
                    keyGeneration,
                    algorithmId,
                    ciphertext);
        }
    }

    public static final class TrustedUpdate {
        private final ProfileKey key;
        private final ProfileValue value;
        private final long retentionMs;
        private final ConsentEvidence consent;

        public TrustedUpdate(
                ProfileKey key,
                ProfileValue value,
                long retentionMs,
                ConsentEvidence consent) {
            this.key = Objects.requireNonNull(key, "key");
            this.value = Objects.requireNonNull(value, "value");
            if (retentionMs < 1L || retentionMs > MAX_RETENTION_MS) {
                throw new IllegalArgumentException("retentionMs is invalid");
            }
            this.retentionMs = retentionMs;
            this.consent = consent;
        }
    }

    public static final class TrustedRead {
        private final ProfileKey key;
        private final ConsentEvidence consent;

        public TrustedRead(ProfileKey key, ConsentEvidence consent) {
            this.key = Objects.requireNonNull(key, "key");
            this.consent = consent;
        }
    }

    public static final class TrustedDelete {
        private final ProfileKey key;
        private final AuthorizationEvidence authorization;

        public TrustedDelete(ProfileKey key, AuthorizationEvidence authorization) {
            this.key = Objects.requireNonNull(key, "key");
            this.authorization = authorization;
        }
    }

    public static final class TrustedExport {
        private final ProfileScope scope;
        private final Set<Field> fields;
        private final ConsentEvidence consent;
        private final AuthorizationEvidence authorization;

        public TrustedExport(
                ProfileScope scope,
                Set<Field> fields,
                ConsentEvidence consent,
                AuthorizationEvidence authorization) {
            this.scope = Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(fields, "fields");
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("export fields must not be empty");
            }
            this.fields = Collections.unmodifiableSet(EnumSet.copyOf(fields));
            this.consent = consent;
            this.authorization = authorization;
        }
    }

    public static final class Limits {
        private final int maxRecords;
        private final int maxRecordsPerOwner;
        private final int maxSealedBytesPerOwner;
        private final int maxSealedPayloadBytes;
        private final int maxExportRecords;
        private final long maxRetentionMs;

        public Limits(
                int maxRecords,
                int maxRecordsPerOwner,
                int maxSealedBytesPerOwner,
                int maxSealedPayloadBytes,
                int maxExportRecords,
                long maxRetentionMs) {
            if (maxRecords < 1 || maxRecords > MAX_RECORDS
                    || maxRecordsPerOwner < 1 || maxRecordsPerOwner > maxRecords
                    || maxSealedBytesPerOwner < 1
                    || maxSealedBytesPerOwner > MAX_SEALED_BYTES_PER_OWNER
                    || maxSealedPayloadBytes < 1
                    || maxSealedPayloadBytes > MAX_SEALED_PAYLOAD_BYTES
                    || maxSealedPayloadBytes > maxSealedBytesPerOwner
                    || maxExportRecords < 1 || maxExportRecords > MAX_EXPORT_RECORDS
                    || maxRetentionMs < 1L || maxRetentionMs > MAX_RETENTION_MS) {
                throw new IllegalArgumentException("Profile Memory limits are invalid");
            }
            this.maxRecords = maxRecords;
            this.maxRecordsPerOwner = maxRecordsPerOwner;
            this.maxSealedBytesPerOwner = maxSealedBytesPerOwner;
            this.maxSealedPayloadBytes = maxSealedPayloadBytes;
            this.maxExportRecords = maxExportRecords;
            this.maxRetentionMs = maxRetentionMs;
        }
    }

    public static final class RecordMetadata {
        private final ProfileScope scope;
        private final Field field;
        private final long revision;
        private final long expiresAtElapsedRealtimeMs;
        private final int sealedByteCount;

        private RecordMetadata(Entry entry) {
            this.scope = entry.key.scope;
            this.field = entry.key.field;
            this.revision = entry.revision;
            this.expiresAtElapsedRealtimeMs = entry.expiresAtElapsedRealtimeMs;
            this.sealedByteCount = entry.sealed.ciphertext.length;
        }

        public ProfileScope getScope() {
            return scope;
        }

        public Field getField() {
            return field;
        }

        public long getRevision() {
            return revision;
        }

        public long getExpiresAtElapsedRealtimeMs() {
            return expiresAtElapsedRealtimeMs;
        }

        public int getSealedByteCount() {
            return sealedByteCount;
        }
    }

    public static final class UpdateResult {
        private final UpdateOutcome outcome;
        private final RecordMetadata record;

        private UpdateResult(UpdateOutcome outcome, RecordMetadata record) {
            this.outcome = outcome;
            this.record = record;
        }

        private static UpdateResult rejected(UpdateOutcome outcome) {
            return new UpdateResult(outcome, null);
        }

        private static UpdateResult accepted(UpdateOutcome outcome, RecordMetadata record) {
            return new UpdateResult(outcome, record);
        }

        public UpdateOutcome getOutcome() {
            return outcome;
        }

        public RecordMetadata getRecord() {
            return record;
        }
    }

    public static final class ReadResult {
        private final ReadOutcome outcome;
        private final RecordMetadata record;
        private final ProfileValue value;

        private ReadResult(ReadOutcome outcome, RecordMetadata record, ProfileValue value) {
            this.outcome = outcome;
            this.record = record;
            this.value = value;
        }

        private static ReadResult rejected(ReadOutcome outcome) {
            return new ReadResult(outcome, null, null);
        }

        private static ReadResult found(RecordMetadata record, ProfileValue value) {
            return new ReadResult(ReadOutcome.FOUND, record, value);
        }

        public ReadOutcome getOutcome() {
            return outcome;
        }

        public RecordMetadata getRecord() {
            return record;
        }

        public ProfileValue getValue() {
            return value;
        }
    }

    public static final class DeleteResult {
        private final DeleteOutcome outcome;
        private final long wipedSealedByteCount;

        private DeleteResult(DeleteOutcome outcome, long wipedSealedByteCount) {
            this.outcome = outcome;
            this.wipedSealedByteCount = wipedSealedByteCount;
        }

        private static DeleteResult of(DeleteOutcome outcome, long wipedSealedByteCount) {
            return new DeleteResult(outcome, wipedSealedByteCount);
        }

        public DeleteOutcome getOutcome() {
            return outcome;
        }

        public long getWipedSealedByteCount() {
            return wipedSealedByteCount;
        }
    }

    public static final class ExportItem {
        private final RecordMetadata record;
        private final ProfileValue value;

        private ExportItem(RecordMetadata record, ProfileValue value) {
            this.record = record;
            this.value = value;
        }

        public RecordMetadata getRecord() {
            return record;
        }

        public ProfileValue getValue() {
            return value;
        }
    }

    public static final class ExportResult {
        private final ExportOutcome outcome;
        private final List<ExportItem> items;

        private ExportResult(ExportOutcome outcome, List<ExportItem> items) {
            this.outcome = outcome;
            this.items = items;
        }

        private static ExportResult rejected(ExportOutcome outcome) {
            return new ExportResult(outcome, Collections.emptyList());
        }

        private static ExportResult exported(List<ExportItem> items) {
            return new ExportResult(
                    ExportOutcome.EXPORTED,
                    Collections.unmodifiableList(new ArrayList<>(items)));
        }

        public ExportOutcome getOutcome() {
            return outcome;
        }

        public List<ExportItem> getItems() {
            return items;
        }
    }

    public static final class Snapshot {
        private final int activeRecordCount;
        private final int activeSealedByteCount;
        private final long createdCount;
        private final long updatedCount;
        private final long deletedCount;
        private final long expiredCount;
        private final long exportedCount;
        private final long wipedSealedByteCount;
        private final boolean rawProfileValueRetained;
        private final boolean durableStorageWired;
        private final boolean productionEncryptionOwnerConfigured;
        private final boolean hardwareAccessed;

        private Snapshot(
                int activeRecordCount,
                int activeSealedByteCount,
                long createdCount,
                long updatedCount,
                long deletedCount,
                long expiredCount,
                long exportedCount,
                long wipedSealedByteCount,
                boolean rawProfileValueRetained,
                boolean durableStorageWired,
                boolean productionEncryptionOwnerConfigured,
                boolean hardwareAccessed) {
            this.activeRecordCount = activeRecordCount;
            this.activeSealedByteCount = activeSealedByteCount;
            this.createdCount = createdCount;
            this.updatedCount = updatedCount;
            this.deletedCount = deletedCount;
            this.expiredCount = expiredCount;
            this.exportedCount = exportedCount;
            this.wipedSealedByteCount = wipedSealedByteCount;
            this.rawProfileValueRetained = rawProfileValueRetained;
            this.durableStorageWired = durableStorageWired;
            this.productionEncryptionOwnerConfigured = productionEncryptionOwnerConfigured;
            this.hardwareAccessed = hardwareAccessed;
        }

        public int getActiveRecordCount() {
            return activeRecordCount;
        }

        public int getActiveSealedByteCount() {
            return activeSealedByteCount;
        }

        public long getCreatedCount() {
            return createdCount;
        }

        public long getUpdatedCount() {
            return updatedCount;
        }

        public long getDeletedCount() {
            return deletedCount;
        }

        public long getExpiredCount() {
            return expiredCount;
        }

        public long getExportedCount() {
            return exportedCount;
        }

        public long getWipedSealedByteCount() {
            return wipedSealedByteCount;
        }

        public boolean isRawProfileValueRetained() {
            return rawProfileValueRetained;
        }

        public boolean isDurableStorageWired() {
            return durableStorageWired;
        }

        public boolean isProductionEncryptionOwnerConfigured() {
            return productionEncryptionOwnerConfigured;
        }

        public boolean isHardwareAccessed() {
            return hardwareAccessed;
        }
    }

    private static final class Entry {
        private final ProfileKey key;
        private final long revision;
        private final long expiresAtElapsedRealtimeMs;
        private final SealedPayload sealed;

        private Entry(
                ProfileKey key,
                long revision,
                long expiresAtElapsedRealtimeMs,
                SealedPayload sealed) {
            this.key = key;
            this.revision = revision;
            this.expiresAtElapsedRealtimeMs = expiresAtElapsedRealtimeMs;
            this.sealed = sealed;
        }

        private RecordMetadata metadata() {
            return new RecordMetadata(this);
        }
    }

    private static final class OpenedValue {
        private final ReadOutcome outcome;
        private final ProfileValue value;

        private OpenedValue(ReadOutcome outcome, ProfileValue value) {
            this.outcome = outcome;
            this.value = value;
        }

        private static OpenedValue failed(ReadOutcome outcome) {
            return new OpenedValue(outcome, null);
        }

        private static OpenedValue opened(ProfileValue value) {
            return new OpenedValue(null, value);
        }
    }
}
