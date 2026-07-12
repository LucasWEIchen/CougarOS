package com.centralbrain.runtime.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Process-local governed Memory metadata lifecycle. No raw content is accepted. */
public final class BoundedMemoryLifecycle {
    public static final long MAX_EPHEMERAL_TTL_MS = 5 * 60 * 1_000L;
    public static final long MAX_SESSION_TTL_MS = 24 * 60 * 60 * 1_000L;
    public static final long MAX_PROFILE_TTL_MS = 30L * 24 * 60 * 60 * 1_000L;

    private static final int MAX_ID_LENGTH = 128;

    public enum Scope {
        EPHEMERAL,
        SESSION,
        PROFILE
    }

    public enum Purpose {
        CONVERSATION_CONTEXT(false),
        COMFORT_PREFERENCE(true),
        ROUTE_CONTEXT(true),
        SAFETY_CONTEXT(false);

        private final boolean profileEligible;

        Purpose(boolean profileEligible) {
            this.profileEligible = profileEligible;
        }

        boolean isProfileEligible() {
            return profileEligible;
        }
    }

    public enum State {
        ACTIVE,
        EXPIRED,
        DELETED
    }

    public enum WriteOutcome {
        CREATED,
        REPLAYED,
        CONFLICT,
        CONSENT_REQUIRED,
        CONSENT_INVALID,
        SCOPE_POLICY_DENIED,
        GLOBAL_LIMIT,
        OWNER_LIMIT
    }

    public enum DeleteOutcome {
        APPLIED,
        REPLAYED,
        NOT_FOUND
    }

    public enum ExportOutcome {
        EXPORTED,
        NOT_FOUND,
        NOT_ACTIVE,
        SCOPE_NOT_EXPORTABLE,
        AUTHORIZATION_REQUIRED,
        AUTHORIZATION_INVALID
    }

    private final Limits limits;
    private final LongSupplier elapsedRealtimeMs;
    private final Supplier<String> memoryIdSource;
    private final Map<String, Entry> byId = new LinkedHashMap<>();
    private final Map<String, Entry> byClientKey = new LinkedHashMap<>();

    private long createdCount;
    private long expiredCount;
    private long deletedCount;
    private long exportedCount;
    private long terminalEvictionCount;

    private BoundedMemoryLifecycle(
            Limits limits,
            LongSupplier elapsedRealtimeMs,
            Supplier<String> memoryIdSource) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.elapsedRealtimeMs = Objects.requireNonNull(
                elapsedRealtimeMs,
                "elapsedRealtimeMs");
        this.memoryIdSource = Objects.requireNonNull(memoryIdSource, "memoryIdSource");
    }

    public static BoundedMemoryLifecycle createForContractTest(
            Limits limits,
            LongSupplier elapsedRealtimeMs,
            Supplier<String> memoryIdSource) {
        return new BoundedMemoryLifecycle(limits, elapsedRealtimeMs, memoryIdSource);
    }

    public synchronized WriteResult write(TrustedWrite request) {
        Objects.requireNonNull(request, "request");
        long now = now();
        expireAndTrim(now);

        String key = clientKey(request.ownerFingerprint, request.clientMemoryId);
        Entry existing = byClientKey.get(key);
        if (existing != null) {
            return new WriteResult(
                    existing.matches(request)
                            ? WriteOutcome.REPLAYED
                            : WriteOutcome.CONFLICT,
                    existing.lifecycleSnapshot());
        }

        long maxTtl = maximumTtl(request.scope);
        if (request.ttlMs > maxTtl
                || request.scope == Scope.PROFILE && !request.purpose.isProfileEligible()
                || request.scope != Scope.PROFILE && request.consent != null) {
            return new WriteResult(WriteOutcome.SCOPE_POLICY_DENIED, null);
        }
        long expiresAt = saturatedAdd(now, request.ttlMs);
        if (request.scope == Scope.PROFILE) {
            if (request.consent == null) {
                return new WriteResult(WriteOutcome.CONSENT_REQUIRED, null);
            }
            if (!request.consent.matches(
                    request.ownerFingerprint,
                    request.purpose,
                    expiresAt,
                    now)) {
                return new WriteResult(WriteOutcome.CONSENT_INVALID, null);
            }
        }
        if (activeCount() >= limits.maxActiveRecords) {
            return new WriteResult(WriteOutcome.GLOBAL_LIMIT, null);
        }
        if (activeCount(request.ownerFingerprint) >= limits.maxActiveRecordsPerOwner) {
            return new WriteResult(WriteOutcome.OWNER_LIMIT, null);
        }

        Entry entry = new Entry(
                nextMemoryId(),
                request,
                now,
                expiresAt);
        byId.put(entry.memoryId, entry);
        byClientKey.put(key, entry);
        createdCount++;
        return new WriteResult(WriteOutcome.CREATED, entry.lifecycleSnapshot());
    }

    public synchronized List<RedactedRecord> queryOwned(
            String ownerFingerprint,
            Scope scope,
            Purpose purpose,
            int maxRecords) {
        requireOwner(ownerFingerprint);
        if (maxRecords < 1 || maxRecords > limits.maxQueryRecords) {
            throw new IllegalArgumentException("maxRecords is out of range");
        }
        expireAndTrim(now());
        List<RedactedRecord> output = new ArrayList<>();
        for (Entry entry : byId.values()) {
            if (entry.state == State.ACTIVE
                    && entry.ownerFingerprint.equals(ownerFingerprint)
                    && (scope == null || entry.scope == scope)
                    && (purpose == null || entry.purpose == purpose)) {
                output.add(entry.redactedRecord());
                if (output.size() == maxRecords) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(output);
    }

    public synchronized LifecycleSnapshot findOwned(
            String memoryId,
            String ownerFingerprint) {
        requireId(memoryId, "memoryId");
        requireOwner(ownerFingerprint);
        expireAndTrim(now());
        Entry entry = byId.get(memoryId);
        return entry != null && entry.ownerFingerprint.equals(ownerFingerprint)
                ? entry.lifecycleSnapshot()
                : null;
    }

    public synchronized DeleteOutcome deleteOwned(
            String memoryId,
            String ownerFingerprint) {
        requireId(memoryId, "memoryId");
        requireOwner(ownerFingerprint);
        long now = now();
        expireAndTrim(now);
        Entry entry = byId.get(memoryId);
        if (entry == null || !entry.ownerFingerprint.equals(ownerFingerprint)) {
            return DeleteOutcome.NOT_FOUND;
        }
        if (entry.state == State.DELETED) {
            return DeleteOutcome.REPLAYED;
        }
        entry.state = State.DELETED;
        entry.contentDigest = "";
        deletedCount++;
        trimTerminalRecords(entry.memoryId);
        return DeleteOutcome.APPLIED;
    }

    public synchronized ExportResult exportOwned(
            String memoryId,
            String ownerFingerprint,
            TrustedExportAuthorization authorization) {
        requireId(memoryId, "memoryId");
        requireOwner(ownerFingerprint);
        long now = now();
        expireAndTrim(now);
        Entry entry = byId.get(memoryId);
        if (entry == null || !entry.ownerFingerprint.equals(ownerFingerprint)) {
            return new ExportResult(ExportOutcome.NOT_FOUND, null);
        }
        if (entry.state != State.ACTIVE) {
            return new ExportResult(ExportOutcome.NOT_ACTIVE, null);
        }
        if (entry.scope == Scope.EPHEMERAL) {
            return new ExportResult(ExportOutcome.SCOPE_NOT_EXPORTABLE, null);
        }
        if (authorization == null) {
            return new ExportResult(ExportOutcome.AUTHORIZATION_REQUIRED, null);
        }
        if (!authorization.matches(
                ownerFingerprint,
                entry.purpose,
                memoryId,
                now)) {
            return new ExportResult(ExportOutcome.AUTHORIZATION_INVALID, null);
        }
        exportedCount++;
        return new ExportResult(ExportOutcome.EXPORTED, entry.exportRecord());
    }

    public synchronized Snapshot snapshot() {
        expireAndTrim(now());
        return new Snapshot(
                activeCount(),
                terminalCount(),
                createdCount,
                expiredCount,
                deletedCount,
                exportedCount,
                terminalEvictionCount,
                false,
                false,
                false);
    }

    private void expireAndTrim(long now) {
        for (Entry entry : byId.values()) {
            if (entry.state == State.ACTIVE && entry.expiresAtElapsedRealtimeMs <= now) {
                entry.state = State.EXPIRED;
                entry.contentDigest = "";
                expiredCount++;
            }
        }
        trimTerminalRecords(null);
    }

    private void trimTerminalRecords(String retainedMemoryId) {
        while (terminalCount() > limits.maxTerminalRecords) {
            Iterator<Map.Entry<String, Entry>> iterator = byId.entrySet().iterator();
            boolean removed = false;
            while (iterator.hasNext()) {
                Map.Entry<String, Entry> candidate = iterator.next();
                Entry entry = candidate.getValue();
                if (entry.state != State.ACTIVE
                        && !entry.memoryId.equals(retainedMemoryId)) {
                    iterator.remove();
                    byClientKey.remove(clientKey(
                            entry.ownerFingerprint,
                            entry.clientMemoryId));
                    terminalEvictionCount++;
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                throw new IllegalStateException("terminal Memory retention cannot be bounded");
            }
        }
    }

    private int activeCount() {
        int count = 0;
        for (Entry entry : byId.values()) {
            if (entry.state == State.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    private int activeCount(String ownerFingerprint) {
        int count = 0;
        for (Entry entry : byId.values()) {
            if (entry.state == State.ACTIVE
                    && entry.ownerFingerprint.equals(ownerFingerprint)) {
                count++;
            }
        }
        return count;
    }

    private int terminalCount() {
        return byId.size() - activeCount();
    }

    private long now() {
        long value = elapsedRealtimeMs.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("elapsed realtime must be non-negative");
        }
        return value;
    }

    private String nextMemoryId() {
        for (int attempt = 0; attempt < 16; attempt++) {
            String id = "memory-" + requireId(
                    memoryIdSource.get(),
                    "generated memoryId");
            if (!byId.containsKey(id)) {
                return id;
            }
        }
        throw new IllegalStateException("memory ID source did not produce a unique ID");
    }

    private static long maximumTtl(Scope scope) {
        switch (scope) {
            case EPHEMERAL:
                return MAX_EPHEMERAL_TTL_MS;
            case SESSION:
                return MAX_SESSION_TTL_MS;
            case PROFILE:
                return MAX_PROFILE_TTL_MS;
            default:
                throw new IllegalStateException("unknown Memory scope");
        }
    }

    private static String clientKey(String owner, String clientMemoryId) {
        return owner + ":" + clientMemoryId;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String requestFingerprint(TrustedWrite request) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        updateDigest(digest, "central-brain-memory-request-v1");
        updateDigest(digest, request.ownerFingerprint);
        updateDigest(digest, request.clientMemoryId);
        updateDigest(digest, request.scope.name());
        updateDigest(digest, request.purpose.name());
        updateDigest(digest, request.sessionId);
        updateDigest(digest, request.schemaId);
        updateDigest(digest, request.contentDigest);
        updateDigest(digest, Long.toString(request.ttlMs));
        if (request.consent == null) {
            updateDigest(digest, "consent:none");
        } else {
            updateDigest(digest, "consent:present");
            updateDigest(digest, request.consent.ownerFingerprint);
            updateDigest(digest, request.consent.purpose.name());
            updateDigest(digest, request.consent.evidenceId);
            updateDigest(
                    digest,
                    Long.toString(request.consent.expiresAtElapsedRealtimeMs));
        }
        return toHex(digest.digest());
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = digits[value >>> 4];
            output[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(output);
    }

    private static String requireId(String value, String name) {
        if (value == null
                || value.isEmpty()
                || value.length() > MAX_ID_LENGTH
                || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    private static String requireOwner(String value) {
        return requireDigest(value, "ownerFingerprint");
    }

    public static final class Limits {
        private final int maxActiveRecords;
        private final int maxActiveRecordsPerOwner;
        private final int maxTerminalRecords;
        private final int maxQueryRecords;

        public Limits(
                int maxActiveRecords,
                int maxActiveRecordsPerOwner,
                int maxTerminalRecords,
                int maxQueryRecords) {
            if (maxActiveRecords < 1
                    || maxActiveRecordsPerOwner < 1
                    || maxActiveRecordsPerOwner > maxActiveRecords
                    || maxTerminalRecords < 1
                    || maxQueryRecords < 1
                    || maxQueryRecords > 100) {
                throw new IllegalArgumentException("Memory lifecycle limits are invalid");
            }
            this.maxActiveRecords = maxActiveRecords;
            this.maxActiveRecordsPerOwner = maxActiveRecordsPerOwner;
            this.maxTerminalRecords = maxTerminalRecords;
            this.maxQueryRecords = maxQueryRecords;
        }
    }

    public static final class TrustedConsentEvidence {
        private final String ownerFingerprint;
        private final Purpose purpose;
        private final String evidenceId;
        private final long expiresAtElapsedRealtimeMs;

        private TrustedConsentEvidence(
                String ownerFingerprint,
                Purpose purpose,
                String evidenceId,
                long expiresAtElapsedRealtimeMs) {
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.purpose = Objects.requireNonNull(purpose, "purpose");
            this.evidenceId = requireId(evidenceId, "evidenceId");
            if (expiresAtElapsedRealtimeMs < 0) {
                throw new IllegalArgumentException("consent expiry must be non-negative");
            }
            this.expiresAtElapsedRealtimeMs = expiresAtElapsedRealtimeMs;
        }

        public static TrustedConsentEvidence grantedByGovernance(
                String ownerFingerprint,
                Purpose purpose,
                String evidenceId,
                long expiresAtElapsedRealtimeMs) {
            return new TrustedConsentEvidence(
                    ownerFingerprint,
                    purpose,
                    evidenceId,
                    expiresAtElapsedRealtimeMs);
        }

        boolean matches(String owner, Purpose expectedPurpose, long memoryExpiry, long now) {
            return ownerFingerprint.equals(owner)
                    && purpose == expectedPurpose
                    && expiresAtElapsedRealtimeMs > now
                    && expiresAtElapsedRealtimeMs >= memoryExpiry;
        }
    }

    public static final class TrustedExportAuthorization {
        private final String ownerFingerprint;
        private final Purpose purpose;
        private final String memoryId;
        private final String authorizationId;
        private final long expiresAtElapsedRealtimeMs;

        private TrustedExportAuthorization(
                String ownerFingerprint,
                Purpose purpose,
                String memoryId,
                String authorizationId,
                long expiresAtElapsedRealtimeMs) {
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.purpose = Objects.requireNonNull(purpose, "purpose");
            this.memoryId = requireId(memoryId, "memoryId");
            this.authorizationId = requireId(authorizationId, "authorizationId");
            if (expiresAtElapsedRealtimeMs < 0) {
                throw new IllegalArgumentException("authorization expiry must be non-negative");
            }
            this.expiresAtElapsedRealtimeMs = expiresAtElapsedRealtimeMs;
        }

        public static TrustedExportAuthorization grantedByGovernance(
                String ownerFingerprint,
                Purpose purpose,
                String memoryId,
                String authorizationId,
                long expiresAtElapsedRealtimeMs) {
            return new TrustedExportAuthorization(
                    ownerFingerprint,
                    purpose,
                    memoryId,
                    authorizationId,
                    expiresAtElapsedRealtimeMs);
        }

        boolean matches(String owner, Purpose expectedPurpose, String expectedMemoryId, long now) {
            return ownerFingerprint.equals(owner)
                    && purpose == expectedPurpose
                    && memoryId.equals(expectedMemoryId)
                    && expiresAtElapsedRealtimeMs > now;
        }
    }

    public static final class TrustedWrite {
        private final String ownerFingerprint;
        private final String clientMemoryId;
        private final Scope scope;
        private final Purpose purpose;
        private final String sessionId;
        private final String schemaId;
        private final String contentDigest;
        private final long ttlMs;
        private final TrustedConsentEvidence consent;

        private TrustedWrite(
                String ownerFingerprint,
                String clientMemoryId,
                Scope scope,
                Purpose purpose,
                String sessionId,
                String schemaId,
                String contentDigest,
                long ttlMs,
                TrustedConsentEvidence consent) {
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.clientMemoryId = requireId(clientMemoryId, "clientMemoryId");
            this.scope = Objects.requireNonNull(scope, "scope");
            this.purpose = Objects.requireNonNull(purpose, "purpose");
            String normalizedSession = sessionId == null ? "" : sessionId;
            if (scope == Scope.PROFILE) {
                if (!normalizedSession.isEmpty()) {
                    throw new IllegalArgumentException("PROFILE Memory cannot bind a session");
                }
            } else {
                requireId(normalizedSession, "sessionId");
            }
            this.sessionId = normalizedSession;
            this.schemaId = requireId(schemaId, "schemaId");
            this.contentDigest = requireDigest(contentDigest, "contentDigest");
            if (ttlMs < 1) {
                throw new IllegalArgumentException("ttlMs must be positive");
            }
            this.ttlMs = ttlMs;
            this.consent = consent;
        }

        public static TrustedWrite fromRuntimePolicy(
                String ownerFingerprint,
                String clientMemoryId,
                Scope scope,
                Purpose purpose,
                String sessionId,
                String schemaId,
                String contentDigest,
                long ttlMs,
                TrustedConsentEvidence consent) {
            return new TrustedWrite(
                    ownerFingerprint,
                    clientMemoryId,
                    scope,
                    purpose,
                    sessionId,
                    schemaId,
                    contentDigest,
                    ttlMs,
                    consent);
        }
    }

    public static final class WriteResult {
        private final WriteOutcome outcome;
        private final LifecycleSnapshot snapshot;

        WriteResult(WriteOutcome outcome, LifecycleSnapshot snapshot) {
            this.outcome = outcome;
            this.snapshot = snapshot;
        }

        public WriteOutcome getOutcome() {
            return outcome;
        }

        public LifecycleSnapshot getSnapshot() {
            return snapshot;
        }
    }

    public static final class ExportResult {
        private final ExportOutcome outcome;
        private final ExportRecord record;

        ExportResult(ExportOutcome outcome, ExportRecord record) {
            this.outcome = outcome;
            this.record = record;
        }

        public ExportOutcome getOutcome() {
            return outcome;
        }

        public ExportRecord getRecord() {
            return record;
        }
    }

    public static final class LifecycleSnapshot {
        private final String memoryId;
        private final Scope scope;
        private final Purpose purpose;
        private final State state;
        private final long expiresAtElapsedRealtimeMs;
        private final boolean contentReferenceRetained;

        LifecycleSnapshot(Entry entry) {
            memoryId = entry.memoryId;
            scope = entry.scope;
            purpose = entry.purpose;
            state = entry.state;
            expiresAtElapsedRealtimeMs = entry.expiresAtElapsedRealtimeMs;
            contentReferenceRetained = !entry.contentDigest.isEmpty();
        }

        public String getMemoryId() {
            return memoryId;
        }

        public Scope getScope() {
            return scope;
        }

        public Purpose getPurpose() {
            return purpose;
        }

        public State getState() {
            return state;
        }

        public long getExpiresAtElapsedRealtimeMs() {
            return expiresAtElapsedRealtimeMs;
        }

        public boolean isContentReferenceRetained() {
            return contentReferenceRetained;
        }
    }

    public static final class RedactedRecord {
        private final String memoryId;
        private final Scope scope;
        private final Purpose purpose;
        private final String schemaId;
        private final long expiresAtElapsedRealtimeMs;
        private final boolean sessionScoped;
        private final boolean contentDigestExposed;

        RedactedRecord(Entry entry) {
            memoryId = entry.memoryId;
            scope = entry.scope;
            purpose = entry.purpose;
            schemaId = entry.schemaId;
            expiresAtElapsedRealtimeMs = entry.expiresAtElapsedRealtimeMs;
            sessionScoped = !entry.sessionId.isEmpty();
            contentDigestExposed = false;
        }

        public String getMemoryId() {
            return memoryId;
        }

        public Scope getScope() {
            return scope;
        }

        public Purpose getPurpose() {
            return purpose;
        }

        public String getSchemaId() {
            return schemaId;
        }

        public long getExpiresAtElapsedRealtimeMs() {
            return expiresAtElapsedRealtimeMs;
        }

        public boolean isSessionScoped() {
            return sessionScoped;
        }

        public boolean isContentDigestExposed() {
            return contentDigestExposed;
        }
    }

    public static final class ExportRecord {
        private final String memoryId;
        private final Scope scope;
        private final Purpose purpose;
        private final String schemaId;
        private final String contentDigest;
        private final long createdAtElapsedRealtimeMs;
        private final long expiresAtElapsedRealtimeMs;

        ExportRecord(Entry entry) {
            memoryId = entry.memoryId;
            scope = entry.scope;
            purpose = entry.purpose;
            schemaId = entry.schemaId;
            contentDigest = entry.contentDigest;
            createdAtElapsedRealtimeMs = entry.createdAtElapsedRealtimeMs;
            expiresAtElapsedRealtimeMs = entry.expiresAtElapsedRealtimeMs;
        }

        public String getMemoryId() {
            return memoryId;
        }

        public Scope getScope() {
            return scope;
        }

        public Purpose getPurpose() {
            return purpose;
        }

        public String getSchemaId() {
            return schemaId;
        }

        public String getContentDigest() {
            return contentDigest;
        }

        public long getCreatedAtElapsedRealtimeMs() {
            return createdAtElapsedRealtimeMs;
        }

        public long getExpiresAtElapsedRealtimeMs() {
            return expiresAtElapsedRealtimeMs;
        }
    }

    public static final class Snapshot {
        private final int activeRecordCount;
        private final int terminalRecordCount;
        private final long createdCount;
        private final long expiredCount;
        private final long deletedCount;
        private final long exportedCount;
        private final long terminalEvictionCount;
        private final boolean persistentStorageWired;
        private final boolean productionServiceWired;
        private final boolean rawContentStored;

        Snapshot(
                int activeRecordCount,
                int terminalRecordCount,
                long createdCount,
                long expiredCount,
                long deletedCount,
                long exportedCount,
                long terminalEvictionCount,
                boolean persistentStorageWired,
                boolean productionServiceWired,
                boolean rawContentStored) {
            this.activeRecordCount = activeRecordCount;
            this.terminalRecordCount = terminalRecordCount;
            this.createdCount = createdCount;
            this.expiredCount = expiredCount;
            this.deletedCount = deletedCount;
            this.exportedCount = exportedCount;
            this.terminalEvictionCount = terminalEvictionCount;
            this.persistentStorageWired = persistentStorageWired;
            this.productionServiceWired = productionServiceWired;
            this.rawContentStored = rawContentStored;
        }

        public int getActiveRecordCount() {
            return activeRecordCount;
        }

        public int getTerminalRecordCount() {
            return terminalRecordCount;
        }

        public long getCreatedCount() {
            return createdCount;
        }

        public long getExpiredCount() {
            return expiredCount;
        }

        public long getDeletedCount() {
            return deletedCount;
        }

        public long getExportedCount() {
            return exportedCount;
        }

        public long getTerminalEvictionCount() {
            return terminalEvictionCount;
        }

        public boolean isPersistentStorageWired() {
            return persistentStorageWired;
        }

        public boolean isProductionServiceWired() {
            return productionServiceWired;
        }

        public boolean isRawContentStored() {
            return rawContentStored;
        }
    }

    private static final class Entry {
        final String memoryId;
        final String ownerFingerprint;
        final String clientMemoryId;
        final Scope scope;
        final Purpose purpose;
        final String sessionId;
        final String schemaId;
        String contentDigest;
        final String requestFingerprint;
        final long createdAtElapsedRealtimeMs;
        final long expiresAtElapsedRealtimeMs;
        State state = State.ACTIVE;

        Entry(String memoryId, TrustedWrite request, long createdAt, long expiresAt) {
            this.memoryId = memoryId;
            ownerFingerprint = request.ownerFingerprint;
            clientMemoryId = request.clientMemoryId;
            scope = request.scope;
            purpose = request.purpose;
            sessionId = request.sessionId;
            schemaId = request.schemaId;
            contentDigest = request.contentDigest;
            requestFingerprint = BoundedMemoryLifecycle.requestFingerprint(request);
            createdAtElapsedRealtimeMs = createdAt;
            expiresAtElapsedRealtimeMs = expiresAt;
        }

        boolean matches(TrustedWrite request) {
            return requestFingerprint.equals(
                    BoundedMemoryLifecycle.requestFingerprint(request));
        }

        LifecycleSnapshot lifecycleSnapshot() {
            return new LifecycleSnapshot(this);
        }

        RedactedRecord redactedRecord() {
            return new RedactedRecord(this);
        }

        ExportRecord exportRecord() {
            return new ExportRecord(this);
        }
    }
}
