package com.centralbrain.runtime.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Process-local, session-scoped working memory with deterministic resource bounds. */
public final class WorkingMemoryStore {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_TTL_MS = 24L * 60L * 60L * 1_000L;

    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_ACTIVE_SESSIONS = 128;
    private static final int MAX_ITEMS_PER_SESSION = 256;
    private static final int MAX_BYTES_PER_SESSION = 1024 * 1024;
    private static final int MAX_TOKENS_PER_SESSION = 262_144;
    private static final int MAX_ITEM_BYTES = 64 * 1024;
    private static final int MAX_ITEM_TOKENS = 16_384;
    private static final int MAX_TERMINAL_SESSIONS = 256;

    public enum PutOutcome {
        CREATED,
        REPLACED,
        REPLAYED,
        SESSION_TERMINAL,
        SESSION_LIMIT,
        ITEM_LIMIT,
        ITEM_BYTE_LIMIT,
        ITEM_TOKEN_LIMIT,
        SESSION_BYTE_LIMIT,
        SESSION_TOKEN_LIMIT,
        TTL_LIMIT
    }

    public enum RemoveOutcome {
        APPLIED,
        NOT_FOUND,
        SESSION_TERMINAL
    }

    public enum TerminalOutcome {
        APPLIED,
        REPLAYED
    }

    private final Limits limits;
    private final LongSupplier elapsedRealtimeMs;
    private final Map<String, SessionState> activeSessions = new LinkedHashMap<>();
    private final Map<String, TerminalRecord> terminalSessions = new LinkedHashMap<>();

    private long createdItemCount;
    private long replacedItemCount;
    private long expiredItemCount;
    private long removedItemCount;
    private long terminalCleanupCount;
    private long terminalCleanedItemCount;
    private long wipedByteCount;
    private long terminalEvictionCount;

    public WorkingMemoryStore(Limits limits, LongSupplier elapsedRealtimeMs) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.elapsedRealtimeMs = Objects.requireNonNull(
                elapsedRealtimeMs,
                "elapsedRealtimeMs");
    }

    public synchronized PutResult put(PutRequest request) {
        Objects.requireNonNull(request, "request");
        long now = now();
        expireItems(now);

        String sessionKey = sessionKey(request.ownerFingerprint, request.sessionId);
        if (terminalSessions.containsKey(sessionKey)) {
            return PutResult.rejected(PutOutcome.SESSION_TERMINAL);
        }
        if (request.ttlMs > limits.maxTtlMs) {
            return PutResult.rejected(PutOutcome.TTL_LIMIT);
        }
        if (request.payload.length > limits.maxItemBytes) {
            return PutResult.rejected(PutOutcome.ITEM_BYTE_LIMIT);
        }
        if (request.tokenCount > limits.maxItemTokens) {
            return PutResult.rejected(PutOutcome.ITEM_TOKEN_LIMIT);
        }

        SessionState session = activeSessions.get(sessionKey);
        Item existing = session == null ? null : session.items.get(request.itemId);
        if (existing != null && existing.matches(request)) {
            return PutResult.accepted(PutOutcome.REPLAYED, existing.snapshot());
        }
        if (session == null && activeSessions.size() >= limits.maxActiveSessions) {
            return PutResult.rejected(PutOutcome.SESSION_LIMIT);
        }

        int currentItems = session == null ? 0 : session.items.size();
        int currentBytes = session == null ? 0 : session.byteCount;
        int currentTokens = session == null ? 0 : session.tokenCount;
        int previousBytes = existing == null ? 0 : existing.payload.length;
        int previousTokens = existing == null ? 0 : existing.tokenCount;
        int projectedItems = currentItems + (existing == null ? 1 : 0);
        int projectedBytes = currentBytes - previousBytes + request.payload.length;
        int projectedTokens = currentTokens - previousTokens + request.tokenCount;

        if (projectedItems > limits.maxItemsPerSession) {
            return PutResult.rejected(PutOutcome.ITEM_LIMIT);
        }
        if (projectedBytes > limits.maxBytesPerSession) {
            return PutResult.rejected(PutOutcome.SESSION_BYTE_LIMIT);
        }
        if (projectedTokens > limits.maxTokensPerSession) {
            return PutResult.rejected(PutOutcome.SESSION_TOKEN_LIMIT);
        }

        if (session == null) {
            session = new SessionState(request.ownerFingerprint, request.sessionId);
            activeSessions.put(sessionKey, session);
        }
        Item replacement = new Item(request, now, saturatedAdd(now, request.ttlMs));
        if (existing == null) {
            createdItemCount++;
        } else {
            wipe(existing.payload);
            replacedItemCount++;
        }
        session.items.put(request.itemId, replacement);
        session.byteCount = projectedBytes;
        session.tokenCount = projectedTokens;
        return PutResult.accepted(
                existing == null ? PutOutcome.CREATED : PutOutcome.REPLACED,
                replacement.snapshot());
    }

    public synchronized List<ItemSnapshot> readSessionOwned(
            String ownerFingerprint,
            String sessionId,
            int maxItems) {
        String owner = requireOwner(ownerFingerprint);
        String session = requireId(sessionId, "sessionId");
        if (maxItems < 1 || maxItems > limits.maxReadItems) {
            throw new IllegalArgumentException("maxItems is out of range");
        }
        expireItems(now());
        SessionState state = activeSessions.get(sessionKey(owner, session));
        if (state == null) {
            return Collections.emptyList();
        }
        List<ItemSnapshot> output = new ArrayList<>();
        for (Item item : state.items.values()) {
            output.add(item.snapshot());
            if (output.size() == maxItems) {
                break;
            }
        }
        return Collections.unmodifiableList(output);
    }

    public synchronized RemoveOutcome removeOwned(
            String ownerFingerprint,
            String sessionId,
            String itemId) {
        String owner = requireOwner(ownerFingerprint);
        String session = requireId(sessionId, "sessionId");
        String item = requireId(itemId, "itemId");
        expireItems(now());
        String key = sessionKey(owner, session);
        if (terminalSessions.containsKey(key)) {
            return RemoveOutcome.SESSION_TERMINAL;
        }
        SessionState state = activeSessions.get(key);
        if (state == null) {
            return RemoveOutcome.NOT_FOUND;
        }
        Item removed = state.items.remove(item);
        if (removed == null) {
            return RemoveOutcome.NOT_FOUND;
        }
        state.byteCount -= removed.payload.length;
        state.tokenCount -= removed.tokenCount;
        removedItemCount++;
        wipe(removed.payload);
        if (state.items.isEmpty()) {
            activeSessions.remove(key);
        }
        return RemoveOutcome.APPLIED;
    }

    public synchronized TerminalResult terminateSessionOwned(
            String ownerFingerprint,
            String sessionId) {
        String owner = requireOwner(ownerFingerprint);
        String session = requireId(sessionId, "sessionId");
        long now = now();
        expireItems(now);
        String key = sessionKey(owner, session);
        TerminalRecord terminal = terminalSessions.get(key);
        if (terminal != null) {
            return new TerminalResult(TerminalOutcome.REPLAYED, 0, 0, 0);
        }

        SessionState state = activeSessions.remove(key);
        int cleanedItems = 0;
        int cleanedBytes = 0;
        int cleanedTokens = 0;
        if (state != null) {
            cleanedItems = state.items.size();
            cleanedBytes = state.byteCount;
            cleanedTokens = state.tokenCount;
            for (Item item : state.items.values()) {
                wipe(item.payload);
            }
            state.items.clear();
            state.byteCount = 0;
            state.tokenCount = 0;
        }
        terminalSessions.put(key, new TerminalRecord(now));
        terminalCleanupCount++;
        terminalCleanedItemCount += cleanedItems;
        trimTerminalSessions();
        return new TerminalResult(
                TerminalOutcome.APPLIED,
                cleanedItems,
                cleanedBytes,
                cleanedTokens);
    }

    public synchronized Snapshot snapshot() {
        expireItems(now());
        int activeItems = 0;
        int activeBytes = 0;
        int activeTokens = 0;
        for (SessionState session : activeSessions.values()) {
            activeItems += session.items.size();
            activeBytes += session.byteCount;
            activeTokens += session.tokenCount;
        }
        return new Snapshot(
                activeSessions.size(),
                activeItems,
                activeBytes,
                activeTokens,
                terminalSessions.size(),
                createdItemCount,
                replacedItemCount,
                expiredItemCount,
                removedItemCount,
                terminalCleanupCount,
                terminalCleanedItemCount,
                wipedByteCount,
                terminalEvictionCount);
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

    public boolean isTokenCountVerifiedByModelTokenizer() {
        return false;
    }

    public boolean isContentLoggingEnabled() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    private void expireItems(long now) {
        Iterator<Map.Entry<String, SessionState>> sessions =
                activeSessions.entrySet().iterator();
        while (sessions.hasNext()) {
            SessionState session = sessions.next().getValue();
            Iterator<Map.Entry<String, Item>> items = session.items.entrySet().iterator();
            while (items.hasNext()) {
                Item item = items.next().getValue();
                if (item.expiresAtElapsedRealtimeMs <= now) {
                    items.remove();
                    session.byteCount -= item.payload.length;
                    session.tokenCount -= item.tokenCount;
                    expiredItemCount++;
                    wipe(item.payload);
                }
            }
            if (session.items.isEmpty()) {
                sessions.remove();
            }
        }
    }

    private void trimTerminalSessions() {
        while (terminalSessions.size() > limits.maxTerminalSessions) {
            Iterator<Map.Entry<String, TerminalRecord>> iterator =
                    terminalSessions.entrySet().iterator();
            if (!iterator.hasNext()) {
                throw new IllegalStateException("terminal Working Memory retention failed");
            }
            iterator.next();
            iterator.remove();
            terminalEvictionCount++;
        }
    }

    private void wipe(byte[] payload) {
        wipedByteCount += payload.length;
        Arrays.fill(payload, (byte) 0);
    }

    private long now() {
        long value = elapsedRealtimeMs.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("elapsed realtime must be non-negative");
        }
        return value;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String sessionKey(String ownerFingerprint, String sessionId) {
        return ownerFingerprint + ":" + sessionId;
    }

    private static String fingerprint(PutRequest request) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        updateDigest(digest, "central-brain-working-memory-v1");
        updateDigest(digest, request.ownerFingerprint);
        updateDigest(digest, request.sessionId);
        updateDigest(digest, request.itemId);
        updateDigest(digest, request.schemaId);
        updateDigest(digest, Integer.toString(request.tokenCount));
        updateDigest(digest, Long.toString(request.ttlMs));
        updateDigest(digest, request.payload);
        return toHex(digest.digest());
    }

    private static void updateDigest(MessageDigest digest, String value) {
        updateDigest(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void updateDigest(MessageDigest digest, byte[] bytes) {
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

    private static String requireOwner(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "ownerFingerprint must be a lowercase SHA-256 digest");
        }
        return value;
    }

    public static final class Limits {
        private final int maxActiveSessions;
        private final int maxItemsPerSession;
        private final int maxBytesPerSession;
        private final int maxTokensPerSession;
        private final int maxItemBytes;
        private final int maxItemTokens;
        private final int maxTerminalSessions;
        private final int maxReadItems;
        private final long maxTtlMs;

        public Limits(
                int maxActiveSessions,
                int maxItemsPerSession,
                int maxBytesPerSession,
                int maxTokensPerSession,
                int maxItemBytes,
                int maxItemTokens,
                int maxTerminalSessions,
                int maxReadItems,
                long maxTtlMs) {
            if (maxActiveSessions < 1 || maxActiveSessions > MAX_ACTIVE_SESSIONS
                    || maxItemsPerSession < 1
                    || maxItemsPerSession > MAX_ITEMS_PER_SESSION
                    || maxBytesPerSession < 1
                    || maxBytesPerSession > MAX_BYTES_PER_SESSION
                    || maxTokensPerSession < 1
                    || maxTokensPerSession > MAX_TOKENS_PER_SESSION
                    || maxItemBytes < 1 || maxItemBytes > MAX_ITEM_BYTES
                    || maxItemBytes > maxBytesPerSession
                    || maxItemTokens < 1 || maxItemTokens > MAX_ITEM_TOKENS
                    || maxItemTokens > maxTokensPerSession
                    || maxTerminalSessions < 1
                    || maxTerminalSessions > MAX_TERMINAL_SESSIONS
                    || maxReadItems < 1 || maxReadItems > maxItemsPerSession
                    || maxTtlMs < 1 || maxTtlMs > MAX_TTL_MS) {
                throw new IllegalArgumentException("Working Memory limits are invalid");
            }
            this.maxActiveSessions = maxActiveSessions;
            this.maxItemsPerSession = maxItemsPerSession;
            this.maxBytesPerSession = maxBytesPerSession;
            this.maxTokensPerSession = maxTokensPerSession;
            this.maxItemBytes = maxItemBytes;
            this.maxItemTokens = maxItemTokens;
            this.maxTerminalSessions = maxTerminalSessions;
            this.maxReadItems = maxReadItems;
            this.maxTtlMs = maxTtlMs;
        }
    }

    public static final class PutRequest {
        private final String ownerFingerprint;
        private final String sessionId;
        private final String itemId;
        private final String schemaId;
        private final byte[] payload;
        private final int tokenCount;
        private final long ttlMs;

        private PutRequest(
                String ownerFingerprint,
                String sessionId,
                String itemId,
                String schemaId,
                byte[] payload,
                int tokenCount,
                long ttlMs) {
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.sessionId = requireId(sessionId, "sessionId");
            this.itemId = requireId(itemId, "itemId");
            this.schemaId = requireId(schemaId, "schemaId");
            if (payload == null || payload.length == 0 || payload.length > MAX_ITEM_BYTES) {
                throw new IllegalArgumentException(
                        "payload must be within the absolute item byte bound");
            }
            if (tokenCount < 1 || tokenCount > MAX_ITEM_TOKENS) {
                throw new IllegalArgumentException(
                        "tokenCount must be within the absolute item token bound");
            }
            if (ttlMs < 1 || ttlMs > MAX_TTL_MS) {
                throw new IllegalArgumentException(
                        "ttlMs must be within the absolute Working Memory TTL bound");
            }
            this.payload = Arrays.copyOf(payload, payload.length);
            this.tokenCount = tokenCount;
            this.ttlMs = ttlMs;
        }

        public static PutRequest fromRuntimePolicy(
                String ownerFingerprint,
                String sessionId,
                String itemId,
                String schemaId,
                byte[] payload,
                int tokenCount,
                long ttlMs) {
            return new PutRequest(
                    ownerFingerprint,
                    sessionId,
                    itemId,
                    schemaId,
                    payload,
                    tokenCount,
                    ttlMs);
        }
    }

    public static final class PutResult {
        private final PutOutcome outcome;
        private final ItemSnapshot item;

        private PutResult(PutOutcome outcome, ItemSnapshot item) {
            this.outcome = outcome;
            this.item = item;
        }

        static PutResult accepted(PutOutcome outcome, ItemSnapshot item) {
            return new PutResult(outcome, item);
        }

        static PutResult rejected(PutOutcome outcome) {
            return new PutResult(outcome, null);
        }

        public PutOutcome getOutcome() {
            return outcome;
        }

        public ItemSnapshot getItem() {
            return item;
        }
    }

    public static final class TerminalResult {
        private final TerminalOutcome outcome;
        private final int cleanedItemCount;
        private final int cleanedByteCount;
        private final int cleanedTokenCount;

        TerminalResult(
                TerminalOutcome outcome,
                int cleanedItemCount,
                int cleanedByteCount,
                int cleanedTokenCount) {
            this.outcome = outcome;
            this.cleanedItemCount = cleanedItemCount;
            this.cleanedByteCount = cleanedByteCount;
            this.cleanedTokenCount = cleanedTokenCount;
        }

        public TerminalOutcome getOutcome() {
            return outcome;
        }

        public int getCleanedItemCount() {
            return cleanedItemCount;
        }

        public int getCleanedByteCount() {
            return cleanedByteCount;
        }

        public int getCleanedTokenCount() {
            return cleanedTokenCount;
        }
    }

    public static final class ItemSnapshot {
        private final String itemId;
        private final String schemaId;
        private final byte[] payload;
        private final int tokenCount;
        private final long createdAtElapsedRealtimeMs;
        private final long expiresAtElapsedRealtimeMs;

        ItemSnapshot(Item item) {
            itemId = item.itemId;
            schemaId = item.schemaId;
            payload = Arrays.copyOf(item.payload, item.payload.length);
            tokenCount = item.tokenCount;
            createdAtElapsedRealtimeMs = item.createdAtElapsedRealtimeMs;
            expiresAtElapsedRealtimeMs = item.expiresAtElapsedRealtimeMs;
        }

        public String getItemId() {
            return itemId;
        }

        public String getSchemaId() {
            return schemaId;
        }

        public byte[] getPayloadCopy() {
            return Arrays.copyOf(payload, payload.length);
        }

        public int getByteCount() {
            return payload.length;
        }

        public int getTokenCount() {
            return tokenCount;
        }

        public long getCreatedAtElapsedRealtimeMs() {
            return createdAtElapsedRealtimeMs;
        }

        public long getExpiresAtElapsedRealtimeMs() {
            return expiresAtElapsedRealtimeMs;
        }
    }

    public static final class Snapshot {
        private final int activeSessionCount;
        private final int activeItemCount;
        private final int activeByteCount;
        private final int activeTokenCount;
        private final int terminalSessionCount;
        private final long createdItemCount;
        private final long replacedItemCount;
        private final long expiredItemCount;
        private final long removedItemCount;
        private final long terminalCleanupCount;
        private final long terminalCleanedItemCount;
        private final long wipedByteCount;
        private final long terminalEvictionCount;

        Snapshot(
                int activeSessionCount,
                int activeItemCount,
                int activeByteCount,
                int activeTokenCount,
                int terminalSessionCount,
                long createdItemCount,
                long replacedItemCount,
                long expiredItemCount,
                long removedItemCount,
                long terminalCleanupCount,
                long terminalCleanedItemCount,
                long wipedByteCount,
                long terminalEvictionCount) {
            this.activeSessionCount = activeSessionCount;
            this.activeItemCount = activeItemCount;
            this.activeByteCount = activeByteCount;
            this.activeTokenCount = activeTokenCount;
            this.terminalSessionCount = terminalSessionCount;
            this.createdItemCount = createdItemCount;
            this.replacedItemCount = replacedItemCount;
            this.expiredItemCount = expiredItemCount;
            this.removedItemCount = removedItemCount;
            this.terminalCleanupCount = terminalCleanupCount;
            this.terminalCleanedItemCount = terminalCleanedItemCount;
            this.wipedByteCount = wipedByteCount;
            this.terminalEvictionCount = terminalEvictionCount;
        }

        public int getActiveSessionCount() {
            return activeSessionCount;
        }

        public int getActiveItemCount() {
            return activeItemCount;
        }

        public int getActiveByteCount() {
            return activeByteCount;
        }

        public int getActiveTokenCount() {
            return activeTokenCount;
        }

        public int getTerminalSessionCount() {
            return terminalSessionCount;
        }

        public long getCreatedItemCount() {
            return createdItemCount;
        }

        public long getReplacedItemCount() {
            return replacedItemCount;
        }

        public long getExpiredItemCount() {
            return expiredItemCount;
        }

        public long getRemovedItemCount() {
            return removedItemCount;
        }

        public long getTerminalCleanupCount() {
            return terminalCleanupCount;
        }

        public long getTerminalCleanedItemCount() {
            return terminalCleanedItemCount;
        }

        public long getWipedByteCount() {
            return wipedByteCount;
        }

        public long getTerminalEvictionCount() {
            return terminalEvictionCount;
        }

        public boolean isRawPayloadRetained() {
            return activeByteCount > 0;
        }
    }

    private static final class SessionState {
        final String ownerFingerprint;
        final String sessionId;
        final Map<String, Item> items = new LinkedHashMap<>();
        int byteCount;
        int tokenCount;

        SessionState(String ownerFingerprint, String sessionId) {
            this.ownerFingerprint = ownerFingerprint;
            this.sessionId = sessionId;
        }
    }

    private static final class Item {
        final String itemId;
        final String schemaId;
        final byte[] payload;
        final int tokenCount;
        final long createdAtElapsedRealtimeMs;
        final long expiresAtElapsedRealtimeMs;
        final String requestFingerprint;

        Item(PutRequest request, long createdAt, long expiresAt) {
            itemId = request.itemId;
            schemaId = request.schemaId;
            payload = Arrays.copyOf(request.payload, request.payload.length);
            tokenCount = request.tokenCount;
            createdAtElapsedRealtimeMs = createdAt;
            expiresAtElapsedRealtimeMs = expiresAt;
            requestFingerprint = WorkingMemoryStore.fingerprint(request);
        }

        boolean matches(PutRequest request) {
            return requestFingerprint.equals(WorkingMemoryStore.fingerprint(request));
        }

        ItemSnapshot snapshot() {
            return new ItemSnapshot(this);
        }
    }

    private static final class TerminalRecord {
        final long terminalAtElapsedRealtimeMs;

        TerminalRecord(long terminalAtElapsedRealtimeMs) {
            this.terminalAtElapsedRealtimeMs = terminalAtElapsedRealtimeMs;
        }
    }
}
