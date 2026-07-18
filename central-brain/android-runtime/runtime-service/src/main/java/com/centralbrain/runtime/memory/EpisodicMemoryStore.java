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

/** Process-local store for bounded, categorical scenario summaries and results only. */
public final class EpisodicMemoryStore {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_RETENTION_MS = 30L * 24L * 60L * 60L * 1_000L;
    public static final long MAX_EPISODE_DURATION_MS = 24L * 60L * 60L * 1_000L;

    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_RECORDS = 512;
    private static final int MAX_RECORDS_PER_OWNER = 128;
    private static final int MAX_READ_RECORDS = 32;
    private static final int MAX_ACTION_COUNT = 64;

    public enum TriggerKind {
        EXPLICIT_USER_INTENT,
        CONTEXT_THRESHOLD,
        SCHEDULED_POLICY,
        MANUAL_CONTROL
    }

    public enum ResultKind {
        SUCCEEDED,
        PARTIAL,
        FAILED,
        CANCELLED,
        REJECTED
    }

    public enum OutcomeCode {
        COMPLETED,
        USER_CANCELLED,
        POLICY_BLOCKED,
        CAPABILITY_UNAVAILABLE,
        EFFECT_FAILED,
        READBACK_MISMATCH,
        DEADLINE_EXCEEDED
    }

    public enum StoreOutcome {
        STORED,
        REPLAYED,
        EPISODE_CONFLICT,
        SCENARIO_NOT_ALLOWED,
        POLICY_DENIED,
        RETENTION_LIMIT,
        EPISODE_DURATION_LIMIT,
        GLOBAL_CAPACITY,
        OWNER_CAPACITY
    }

    public enum EraseOutcome {
        ERASED,
        NOT_FOUND,
        AUTHORIZATION_DENIED
    }

    public enum ReadOutcome {
        AUTHORIZED,
        AUTHORIZATION_DENIED
    }

    public enum EraseOperation {
        EPISODE,
        OWNER
    }

    public interface ScenarioCatalogAuthority {
        boolean isBuildOwnedScenario(ScenarioReference scenarioReference);
    }

    public interface StoragePolicyAuthority {
        boolean mayStore(
                StoragePolicyEvidence evidence,
                ScenarioReference scenarioReference,
                long nowElapsedMs);
    }

    public interface EraseAuthority {
        boolean mayErase(EraseEvidence evidence, long nowElapsedMs);
    }

    public interface ReadAuthority {
        boolean mayRead(ReadEvidence evidence, long nowElapsedMs);
    }

    private final Limits limits;
    private final LongSupplier elapsedRealtimeMs;
    private final ScenarioCatalogAuthority scenarioCatalogAuthority;
    private final StoragePolicyAuthority storagePolicyAuthority;
    private final ReadAuthority readAuthority;
    private final EraseAuthority eraseAuthority;
    private final Map<String, Record> records = new LinkedHashMap<>();

    private long storedCount;
    private long replayedCount;
    private long expiredCount;
    private long erasedCount;
    private long erasedOwnerCount;

    private EpisodicMemoryStore(
            Limits limits,
            LongSupplier elapsedRealtimeMs,
            ScenarioCatalogAuthority scenarioCatalogAuthority,
            StoragePolicyAuthority storagePolicyAuthority,
            ReadAuthority readAuthority,
            EraseAuthority eraseAuthority) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.elapsedRealtimeMs = Objects.requireNonNull(elapsedRealtimeMs, "elapsedRealtimeMs");
        this.scenarioCatalogAuthority = Objects.requireNonNull(
                scenarioCatalogAuthority,
                "scenarioCatalogAuthority");
        this.storagePolicyAuthority = Objects.requireNonNull(
                storagePolicyAuthority,
                "storagePolicyAuthority");
        this.readAuthority = Objects.requireNonNull(readAuthority, "readAuthority");
        this.eraseAuthority = Objects.requireNonNull(eraseAuthority, "eraseAuthority");
    }

    public static EpisodicMemoryStore createForContractTest(
            Limits limits,
            LongSupplier elapsedRealtimeMs,
            ScenarioCatalogAuthority scenarioCatalogAuthority,
            StoragePolicyAuthority storagePolicyAuthority,
            ReadAuthority readAuthority,
            EraseAuthority eraseAuthority) {
        return new EpisodicMemoryStore(
                limits,
                elapsedRealtimeMs,
                scenarioCatalogAuthority,
                storagePolicyAuthority,
                readAuthority,
                eraseAuthority);
    }

    public synchronized StoreResult store(RecordRequest request) {
        Objects.requireNonNull(request, "request");
        long now = now();
        expireRecords(now);
        if (request.retentionMs > limits.maxRetentionMs) {
            return StoreResult.rejected(StoreOutcome.RETENTION_LIMIT);
        }
        if (request.finishedAtElapsedMs - request.startedAtElapsedMs
                > limits.maxEpisodeDurationMs) {
            return StoreResult.rejected(StoreOutcome.EPISODE_DURATION_LIMIT);
        }
        if (!isScenarioAllowed(request.scenarioReference)) {
            return StoreResult.rejected(StoreOutcome.SCENARIO_NOT_ALLOWED);
        }
        if (!isStorageAllowed(request, now)) {
            return StoreResult.rejected(StoreOutcome.POLICY_DENIED);
        }

        String key = recordKey(request.ownerFingerprint, request.episodeId);
        Record existing = records.get(key);
        String requestFingerprint = request.fingerprint();
        if (existing != null) {
            if (existing.requestFingerprint.equals(requestFingerprint)) {
                replayedCount++;
                return StoreResult.accepted(StoreOutcome.REPLAYED, existing.snapshot());
            }
            return StoreResult.rejected(StoreOutcome.EPISODE_CONFLICT);
        }
        if (records.size() >= limits.maxRecords) {
            return StoreResult.rejected(StoreOutcome.GLOBAL_CAPACITY);
        }
        if (countOwnerRecords(request.ownerFingerprint) >= limits.maxRecordsPerOwner) {
            return StoreResult.rejected(StoreOutcome.OWNER_CAPACITY);
        }

        Record record = new Record(
                request,
                requestFingerprint,
                now,
                saturatedAdd(now, request.retentionMs));
        records.put(key, record);
        storedCount++;
        return StoreResult.accepted(StoreOutcome.STORED, record.snapshot());
    }

    public synchronized ReadResult readOwner(
            String ownerFingerprint,
            int maxRecords,
            ReadEvidence evidence) {
        String owner = requireOwner(ownerFingerprint);
        if (maxRecords < 1 || maxRecords > limits.maxReadRecords) {
            throw new IllegalArgumentException("maxRecords is out of range");
        }
        long now = now();
        expireRecords(now);
        if (!isReadAllowed(evidence, owner, now)) {
            return ReadResult.denied();
        }
        List<RecordSnapshot> output = new ArrayList<>();
        for (Record record : records.values()) {
            if (record.ownerFingerprint.equals(owner)) {
                output.add(record.snapshot());
                if (output.size() == maxRecords) {
                    break;
                }
            }
        }
        return ReadResult.authorized(output);
    }

    public synchronized EraseResult eraseEpisode(
            String ownerFingerprint,
            String episodeId,
            EraseEvidence evidence) {
        String owner = requireOwner(ownerFingerprint);
        String episode = requireId(episodeId, "episodeId");
        long now = now();
        expireRecords(now);
        if (!isEraseAllowed(evidence, EraseOperation.EPISODE, owner, episode, now)) {
            return new EraseResult(EraseOutcome.AUTHORIZATION_DENIED, 0);
        }
        Record removed = records.remove(recordKey(owner, episode));
        if (removed == null) {
            return new EraseResult(EraseOutcome.NOT_FOUND, 0);
        }
        erasedCount++;
        return new EraseResult(EraseOutcome.ERASED, 1);
    }

    public synchronized EraseResult eraseOwner(
            String ownerFingerprint,
            EraseEvidence evidence) {
        String owner = requireOwner(ownerFingerprint);
        long now = now();
        expireRecords(now);
        if (!isEraseAllowed(evidence, EraseOperation.OWNER, owner, null, now)) {
            return new EraseResult(EraseOutcome.AUTHORIZATION_DENIED, 0);
        }
        int erased = 0;
        Iterator<Record> iterator = records.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().ownerFingerprint.equals(owner)) {
                iterator.remove();
                erased++;
            }
        }
        if (erased == 0) {
            return new EraseResult(EraseOutcome.NOT_FOUND, 0);
        }
        erasedCount += erased;
        erasedOwnerCount++;
        return new EraseResult(EraseOutcome.ERASED, erased);
    }

    public synchronized Snapshot snapshot() {
        expireRecords(now());
        return new Snapshot(
                records.size(),
                ownerCount(),
                storedCount,
                replayedCount,
                expiredCount,
                erasedCount,
                erasedOwnerCount);
    }

    public boolean isProcessLocal() {
        return true;
    }

    public boolean isRawContinuousSignalAccepted() {
        return false;
    }

    public boolean isRawContinuousSignalRetained() {
        return false;
    }

    public boolean isArbitraryPayloadAccepted() {
        return false;
    }

    public boolean isProductionStoragePolicyAuthorityWired() {
        return false;
    }

    public boolean isProductionEraseAuthorityWired() {
        return false;
    }

    public boolean isProductionReadAuthorityWired() {
        return false;
    }

    public boolean isPersistentStorageWired() {
        return false;
    }

    public boolean isRuntimeWired() {
        return false;
    }

    public boolean isModelContextPublicationEnabled() {
        return false;
    }

    public boolean isContentLoggingEnabled() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    private boolean isScenarioAllowed(ScenarioReference reference) {
        try {
            return scenarioCatalogAuthority.isBuildOwnedScenario(reference);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isStorageAllowed(RecordRequest request, long now) {
        StoragePolicyEvidence evidence = request.storagePolicyEvidence;
        if (!evidence.ownerFingerprint.equals(request.ownerFingerprint)
                || !evidence.episodeId.equals(request.episodeId)
                || !evidence.isActive(now)) {
            return false;
        }
        try {
            return storagePolicyAuthority.mayStore(
                    evidence,
                    request.scenarioReference,
                    now);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isEraseAllowed(
            EraseEvidence evidence,
            EraseOperation operation,
            String owner,
            String episode,
            long now) {
        if (evidence == null
                || evidence.operation != operation
                || !evidence.ownerFingerprint.equals(owner)
                || !Objects.equals(evidence.episodeId, episode)
                || !evidence.isActive(now)) {
            return false;
        }
        try {
            return eraseAuthority.mayErase(evidence, now);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isReadAllowed(ReadEvidence evidence, String owner, long now) {
        if (evidence == null
                || !evidence.ownerFingerprint.equals(owner)
                || !evidence.isActive(now)) {
            return false;
        }
        try {
            return readAuthority.mayRead(evidence, now);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void expireRecords(long now) {
        Iterator<Record> iterator = records.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAtElapsedMs <= now) {
                iterator.remove();
                expiredCount++;
            }
        }
    }

    private int countOwnerRecords(String owner) {
        int count = 0;
        for (Record record : records.values()) {
            if (record.ownerFingerprint.equals(owner)) {
                count++;
            }
        }
        return count;
    }

    private int ownerCount() {
        List<String> owners = new ArrayList<>();
        for (Record record : records.values()) {
            if (!owners.contains(record.ownerFingerprint)) {
                owners.add(record.ownerFingerprint);
            }
        }
        return owners.size();
    }

    private long now() {
        long value = elapsedRealtimeMs.getAsLong();
        if (value < 0L) {
            throw new IllegalStateException("elapsedRealtimeMs must be non-negative");
        }
        return value;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static String recordKey(String owner, String episode) {
        return owner + ':' + episode;
    }

    private static String requireOwner(String ownerFingerprint) {
        String normalized = requireId(ownerFingerprint, "ownerFingerprint");
        if (normalized.length() != 64) {
            throw new IllegalArgumentException("ownerFingerprint must be lowercase SHA-256");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char value = normalized.charAt(index);
            if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'))) {
                throw new IllegalArgumentException("ownerFingerprint must be lowercase SHA-256");
            }
        }
        return normalized;
    }

    private static String requireId(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            boolean allowed = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '.'
                    || character == '-'
                    || character == '_';
            if (!allowed) {
                throw new IllegalArgumentException(fieldName + " is invalid");
            }
        }
        return normalized;
    }

    private static String requireDigest(String digest, String fieldName) {
        return requireOwner(requireId(digest, fieldName));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(64);
            for (byte item : digest) {
                output.append(String.format("%02x", item));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static final class Limits {
        private final int maxRecords;
        private final int maxRecordsPerOwner;
        private final int maxReadRecords;
        private final long maxRetentionMs;
        private final long maxEpisodeDurationMs;

        public Limits(
                int maxRecords,
                int maxRecordsPerOwner,
                int maxReadRecords,
                long maxRetentionMs,
                long maxEpisodeDurationMs) {
            if (maxRecords < 1 || maxRecords > MAX_RECORDS
                    || maxRecordsPerOwner < 1
                    || maxRecordsPerOwner > Math.min(maxRecords, MAX_RECORDS_PER_OWNER)
                    || maxReadRecords < 1
                    || maxReadRecords > Math.min(maxRecordsPerOwner, MAX_READ_RECORDS)
                    || maxRetentionMs < 1L || maxRetentionMs > MAX_RETENTION_MS
                    || maxEpisodeDurationMs < 1L
                    || maxEpisodeDurationMs > MAX_EPISODE_DURATION_MS) {
                throw new IllegalArgumentException("Episodic Memory limits are invalid");
            }
            this.maxRecords = maxRecords;
            this.maxRecordsPerOwner = maxRecordsPerOwner;
            this.maxReadRecords = maxReadRecords;
            this.maxRetentionMs = maxRetentionMs;
            this.maxEpisodeDurationMs = maxEpisodeDurationMs;
        }
    }

    public static final class ScenarioReference {
        private final String scenarioId;
        private final String catalogDigest;

        public ScenarioReference(String scenarioId, String catalogDigest) {
            this.scenarioId = requireId(scenarioId, "scenarioId");
            this.catalogDigest = requireDigest(catalogDigest, "catalogDigest");
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public String getCatalogDigest() {
            return catalogDigest;
        }
    }

    public static final class StoragePolicyEvidence {
        private final String ownerFingerprint;
        private final String episodeId;
        private final String evidenceId;
        private final long issuedAtElapsedMs;
        private final long expiresAtElapsedMs;

        public StoragePolicyEvidence(
                String ownerFingerprint,
                String episodeId,
                String evidenceId,
                long issuedAtElapsedMs,
                long expiresAtElapsedMs) {
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.episodeId = requireId(episodeId, "episodeId");
            this.evidenceId = requireId(evidenceId, "evidenceId");
            if (issuedAtElapsedMs < 0L || expiresAtElapsedMs <= issuedAtElapsedMs) {
                throw new IllegalArgumentException("storage policy evidence window is invalid");
            }
            this.issuedAtElapsedMs = issuedAtElapsedMs;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
        }

        private boolean isActive(long now) {
            return issuedAtElapsedMs <= now && now < expiresAtElapsedMs;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getEpisodeId() {
            return episodeId;
        }

        public String getEvidenceId() {
            return evidenceId;
        }

        public long getIssuedAtElapsedMs() {
            return issuedAtElapsedMs;
        }

        public long getExpiresAtElapsedMs() {
            return expiresAtElapsedMs;
        }
    }

    public static final class EraseEvidence {
        private final EraseOperation operation;
        private final String ownerFingerprint;
        private final String episodeId;
        private final String evidenceId;
        private final long issuedAtElapsedMs;
        private final long expiresAtElapsedMs;

        public EraseEvidence(
                EraseOperation operation,
                String ownerFingerprint,
                String episodeId,
                String evidenceId,
                long issuedAtElapsedMs,
                long expiresAtElapsedMs) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.episodeId = operation == EraseOperation.EPISODE
                    ? requireId(episodeId, "episodeId")
                    : requireNullEpisode(episodeId);
            this.evidenceId = requireId(evidenceId, "evidenceId");
            if (issuedAtElapsedMs < 0L || expiresAtElapsedMs <= issuedAtElapsedMs) {
                throw new IllegalArgumentException("erase evidence window is invalid");
            }
            this.issuedAtElapsedMs = issuedAtElapsedMs;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
        }

        private static String requireNullEpisode(String episodeId) {
            if (episodeId != null) {
                throw new IllegalArgumentException("owner erase must not include episodeId");
            }
            return null;
        }

        private boolean isActive(long now) {
            return issuedAtElapsedMs <= now && now < expiresAtElapsedMs;
        }

        public EraseOperation getOperation() {
            return operation;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getEpisodeId() {
            return episodeId;
        }

        public String getEvidenceId() {
            return evidenceId;
        }

        public long getIssuedAtElapsedMs() {
            return issuedAtElapsedMs;
        }

        public long getExpiresAtElapsedMs() {
            return expiresAtElapsedMs;
        }
    }

    public static final class ReadEvidence {
        private final String ownerFingerprint;
        private final String evidenceId;
        private final long issuedAtElapsedMs;
        private final long expiresAtElapsedMs;

        public ReadEvidence(
                String ownerFingerprint,
                String evidenceId,
                long issuedAtElapsedMs,
                long expiresAtElapsedMs) {
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.evidenceId = requireId(evidenceId, "evidenceId");
            if (issuedAtElapsedMs < 0L || expiresAtElapsedMs <= issuedAtElapsedMs) {
                throw new IllegalArgumentException("read evidence window is invalid");
            }
            this.issuedAtElapsedMs = issuedAtElapsedMs;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
        }

        private boolean isActive(long now) {
            return issuedAtElapsedMs <= now && now < expiresAtElapsedMs;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getEvidenceId() {
            return evidenceId;
        }

        public long getIssuedAtElapsedMs() {
            return issuedAtElapsedMs;
        }

        public long getExpiresAtElapsedMs() {
            return expiresAtElapsedMs;
        }
    }

    public static final class RecordRequest {
        private final String ownerFingerprint;
        private final String episodeId;
        private final ScenarioReference scenarioReference;
        private final TriggerKind triggerKind;
        private final ResultKind resultKind;
        private final OutcomeCode outcomeCode;
        private final int plannedActionCount;
        private final int completedActionCount;
        private final long startedAtElapsedMs;
        private final long finishedAtElapsedMs;
        private final long retentionMs;
        private final StoragePolicyEvidence storagePolicyEvidence;

        private RecordRequest(
                String ownerFingerprint,
                String episodeId,
                ScenarioReference scenarioReference,
                TriggerKind triggerKind,
                ResultKind resultKind,
                OutcomeCode outcomeCode,
                int plannedActionCount,
                int completedActionCount,
                long startedAtElapsedMs,
                long finishedAtElapsedMs,
                long retentionMs,
                StoragePolicyEvidence storagePolicyEvidence) {
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.episodeId = requireId(episodeId, "episodeId");
            this.scenarioReference = Objects.requireNonNull(
                    scenarioReference,
                    "scenarioReference");
            this.triggerKind = Objects.requireNonNull(triggerKind, "triggerKind");
            this.resultKind = Objects.requireNonNull(resultKind, "resultKind");
            this.outcomeCode = Objects.requireNonNull(outcomeCode, "outcomeCode");
            if (plannedActionCount < 0 || plannedActionCount > MAX_ACTION_COUNT
                    || completedActionCount < 0
                    || completedActionCount > plannedActionCount) {
                throw new IllegalArgumentException("action counts are invalid");
            }
            if (startedAtElapsedMs < 0L
                    || finishedAtElapsedMs < startedAtElapsedMs
                    || finishedAtElapsedMs - startedAtElapsedMs > MAX_EPISODE_DURATION_MS) {
                throw new IllegalArgumentException("episode time range is invalid");
            }
            if (retentionMs < 1L || retentionMs > MAX_RETENTION_MS) {
                throw new IllegalArgumentException("retentionMs is invalid");
            }
            this.plannedActionCount = plannedActionCount;
            this.completedActionCount = completedActionCount;
            this.startedAtElapsedMs = startedAtElapsedMs;
            this.finishedAtElapsedMs = finishedAtElapsedMs;
            this.retentionMs = retentionMs;
            this.storagePolicyEvidence = Objects.requireNonNull(
                    storagePolicyEvidence,
                    "storagePolicyEvidence");
        }

        public static RecordRequest fromScenarioResult(
                String ownerFingerprint,
                String episodeId,
                ScenarioReference scenarioReference,
                TriggerKind triggerKind,
                ResultKind resultKind,
                OutcomeCode outcomeCode,
                int plannedActionCount,
                int completedActionCount,
                long startedAtElapsedMs,
                long finishedAtElapsedMs,
                long retentionMs,
                StoragePolicyEvidence storagePolicyEvidence) {
            return new RecordRequest(
                    ownerFingerprint,
                    episodeId,
                    scenarioReference,
                    triggerKind,
                    resultKind,
                    outcomeCode,
                    plannedActionCount,
                    completedActionCount,
                    startedAtElapsedMs,
                    finishedAtElapsedMs,
                    retentionMs,
                    storagePolicyEvidence);
        }

        private String fingerprint() {
            return sha256(SCHEMA_VERSION
                    + "|" + ownerFingerprint
                    + "|" + episodeId
                    + "|" + scenarioReference.scenarioId
                    + "|" + scenarioReference.catalogDigest
                    + "|" + triggerKind
                    + "|" + resultKind
                    + "|" + outcomeCode
                    + "|" + plannedActionCount
                    + "|" + completedActionCount
                    + "|" + startedAtElapsedMs
                    + "|" + finishedAtElapsedMs
                    + "|" + retentionMs);
        }
    }

    public static final class StoreResult {
        private final StoreOutcome outcome;
        private final RecordSnapshot record;

        private StoreResult(StoreOutcome outcome, RecordSnapshot record) {
            this.outcome = outcome;
            this.record = record;
        }

        private static StoreResult accepted(StoreOutcome outcome, RecordSnapshot record) {
            return new StoreResult(outcome, record);
        }

        private static StoreResult rejected(StoreOutcome outcome) {
            return new StoreResult(outcome, null);
        }

        public StoreOutcome getOutcome() {
            return outcome;
        }

        public RecordSnapshot getRecord() {
            return record;
        }
    }

    public static final class ReadResult {
        private final ReadOutcome outcome;
        private final List<RecordSnapshot> records;

        private ReadResult(ReadOutcome outcome, List<RecordSnapshot> records) {
            this.outcome = outcome;
            this.records = records;
        }

        private static ReadResult authorized(List<RecordSnapshot> records) {
            return new ReadResult(
                    ReadOutcome.AUTHORIZED,
                    Collections.unmodifiableList(new ArrayList<>(records)));
        }

        private static ReadResult denied() {
            return new ReadResult(ReadOutcome.AUTHORIZATION_DENIED, Collections.emptyList());
        }

        public ReadOutcome getOutcome() {
            return outcome;
        }

        public List<RecordSnapshot> getRecords() {
            return records;
        }
    }

    public static final class EraseResult {
        private final EraseOutcome outcome;
        private final int erasedRecordCount;

        private EraseResult(EraseOutcome outcome, int erasedRecordCount) {
            this.outcome = outcome;
            this.erasedRecordCount = erasedRecordCount;
        }

        public EraseOutcome getOutcome() {
            return outcome;
        }

        public int getErasedRecordCount() {
            return erasedRecordCount;
        }
    }

    public static final class RecordSnapshot {
        private final String episodeId;
        private final String scenarioId;
        private final String scenarioCatalogDigest;
        private final TriggerKind triggerKind;
        private final ResultKind resultKind;
        private final OutcomeCode outcomeCode;
        private final int plannedActionCount;
        private final int completedActionCount;
        private final long startedAtElapsedMs;
        private final long finishedAtElapsedMs;
        private final long storedAtElapsedMs;
        private final long expiresAtElapsedMs;

        private RecordSnapshot(Record record) {
            this.episodeId = record.episodeId;
            this.scenarioId = record.scenarioReference.scenarioId;
            this.scenarioCatalogDigest = record.scenarioReference.catalogDigest;
            this.triggerKind = record.triggerKind;
            this.resultKind = record.resultKind;
            this.outcomeCode = record.outcomeCode;
            this.plannedActionCount = record.plannedActionCount;
            this.completedActionCount = record.completedActionCount;
            this.startedAtElapsedMs = record.startedAtElapsedMs;
            this.finishedAtElapsedMs = record.finishedAtElapsedMs;
            this.storedAtElapsedMs = record.storedAtElapsedMs;
            this.expiresAtElapsedMs = record.expiresAtElapsedMs;
        }

        public String getEpisodeId() {
            return episodeId;
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public String getScenarioCatalogDigest() {
            return scenarioCatalogDigest;
        }

        public TriggerKind getTriggerKind() {
            return triggerKind;
        }

        public ResultKind getResultKind() {
            return resultKind;
        }

        public OutcomeCode getOutcomeCode() {
            return outcomeCode;
        }

        public int getPlannedActionCount() {
            return plannedActionCount;
        }

        public int getCompletedActionCount() {
            return completedActionCount;
        }

        public long getStartedAtElapsedMs() {
            return startedAtElapsedMs;
        }

        public long getFinishedAtElapsedMs() {
            return finishedAtElapsedMs;
        }

        public long getStoredAtElapsedMs() {
            return storedAtElapsedMs;
        }

        public long getExpiresAtElapsedMs() {
            return expiresAtElapsedMs;
        }
    }

    public static final class Snapshot {
        private final int activeRecordCount;
        private final int activeOwnerCount;
        private final long storedCount;
        private final long replayedCount;
        private final long expiredCount;
        private final long erasedCount;
        private final long erasedOwnerCount;

        private Snapshot(
                int activeRecordCount,
                int activeOwnerCount,
                long storedCount,
                long replayedCount,
                long expiredCount,
                long erasedCount,
                long erasedOwnerCount) {
            this.activeRecordCount = activeRecordCount;
            this.activeOwnerCount = activeOwnerCount;
            this.storedCount = storedCount;
            this.replayedCount = replayedCount;
            this.expiredCount = expiredCount;
            this.erasedCount = erasedCount;
            this.erasedOwnerCount = erasedOwnerCount;
        }

        public int getActiveRecordCount() {
            return activeRecordCount;
        }

        public int getActiveOwnerCount() {
            return activeOwnerCount;
        }

        public long getStoredCount() {
            return storedCount;
        }

        public long getReplayedCount() {
            return replayedCount;
        }

        public long getExpiredCount() {
            return expiredCount;
        }

        public long getErasedCount() {
            return erasedCount;
        }

        public long getErasedOwnerCount() {
            return erasedOwnerCount;
        }

        public boolean isRawContinuousSignalRetained() {
            return false;
        }
    }

    private static final class Record {
        private final String ownerFingerprint;
        private final String episodeId;
        private final ScenarioReference scenarioReference;
        private final TriggerKind triggerKind;
        private final ResultKind resultKind;
        private final OutcomeCode outcomeCode;
        private final int plannedActionCount;
        private final int completedActionCount;
        private final long startedAtElapsedMs;
        private final long finishedAtElapsedMs;
        private final String requestFingerprint;
        private final long storedAtElapsedMs;
        private final long expiresAtElapsedMs;

        private Record(
                RecordRequest request,
                String requestFingerprint,
                long storedAtElapsedMs,
                long expiresAtElapsedMs) {
            this.ownerFingerprint = request.ownerFingerprint;
            this.episodeId = request.episodeId;
            this.scenarioReference = request.scenarioReference;
            this.triggerKind = request.triggerKind;
            this.resultKind = request.resultKind;
            this.outcomeCode = request.outcomeCode;
            this.plannedActionCount = request.plannedActionCount;
            this.completedActionCount = request.completedActionCount;
            this.startedAtElapsedMs = request.startedAtElapsedMs;
            this.finishedAtElapsedMs = request.finishedAtElapsedMs;
            this.requestFingerprint = requestFingerprint;
            this.storedAtElapsedMs = storedAtElapsedMs;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
        }

        private RecordSnapshot snapshot() {
            return new RecordSnapshot(this);
        }
    }
}
