package com.centralbrain.runtime.events;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded process-local atomic cooldown reservations for TriggerEngine. */
public final class CooldownStore {
    public static final int MAX_ENTRIES = 256;

    public enum ReservationCode {
        RESERVED,
        REPLAYED,
        COOLDOWN_ACTIVE,
        CAPACITY_EXCEEDED
    }

    public static final class Reservation {
        private final ReservationCode code;
        private final long blockedUntilElapsedMs;
        private final long remainingMs;
        private final String retainedSuggestionDigest;

        Reservation(
                ReservationCode code,
                long blockedUntilElapsedMs,
                long remainingMs,
                String retainedSuggestionDigest) {
            this.code = Objects.requireNonNull(code, "code");
            this.blockedUntilElapsedMs = blockedUntilElapsedMs;
            this.remainingMs = remainingMs;
            this.retainedSuggestionDigest = retainedSuggestionDigest;
        }

        public ReservationCode getCode() {
            return code;
        }

        public long getBlockedUntilElapsedMs() {
            return blockedUntilElapsedMs;
        }

        public long getRemainingMs() {
            return remainingMs;
        }

        public String getRetainedSuggestionDigest() {
            return retainedSuggestionDigest;
        }
    }

    public static final class Snapshot {
        private final int activeEntryCount;
        private final long reservedCount;
        private final long replayedCount;
        private final long suppressedCount;
        private final long capacityRejectedCount;

        Snapshot(
                int activeEntryCount,
                long reservedCount,
                long replayedCount,
                long suppressedCount,
                long capacityRejectedCount) {
            this.activeEntryCount = activeEntryCount;
            this.reservedCount = reservedCount;
            this.replayedCount = replayedCount;
            this.suppressedCount = suppressedCount;
            this.capacityRejectedCount = capacityRejectedCount;
        }

        public int getActiveEntryCount() {
            return activeEntryCount;
        }

        public long getReservedCount() {
            return reservedCount;
        }

        public long getReplayedCount() {
            return replayedCount;
        }

        public long getSuppressedCount() {
            return suppressedCount;
        }

        public long getCapacityRejectedCount() {
            return capacityRejectedCount;
        }

        public boolean isProcessLocal() {
            return true;
        }

        public boolean isDurablePersistenceWired() {
            return false;
        }
    }

    private final int capacity;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private long reservedCount;
    private long replayedCount;
    private long suppressedCount;
    private long capacityRejectedCount;

    private CooldownStore(int capacity) {
        if (capacity < 1 || capacity > MAX_ENTRIES) {
            throw new IllegalArgumentException("cooldown capacity is invalid");
        }
        this.capacity = capacity;
    }

    public static CooldownStore createForContractTest(int capacity) {
        return new CooldownStore(capacity);
    }

    public synchronized Reservation reserve(
            String ruleId,
            String scopeDigest,
            String suggestionDigest,
            long nowElapsedMs,
            long cooldownMs) {
        EventBroker.requireMetadata(ruleId, "ruleId", 96);
        EventBroker.requireDigest(scopeDigest, "scopeDigest");
        EventBroker.requireDigest(suggestionDigest, "suggestionDigest");
        if (nowElapsedMs < 0 || cooldownMs < 1
                || cooldownMs > TriggerRule.MAX_COOLDOWN_MS
                || nowElapsedMs > Long.MAX_VALUE - cooldownMs) {
            throw new IllegalArgumentException("cooldown reservation time is invalid");
        }
        pruneExpired(nowElapsedMs);
        String key = ruleId + '|' + scopeDigest;
        Entry existing = entries.get(key);
        if (existing != null) {
            if (existing.suggestionDigest.equals(suggestionDigest)) {
                replayedCount++;
                return result(
                        ReservationCode.REPLAYED,
                        existing,
                        nowElapsedMs);
            }
            suppressedCount++;
            return result(
                    ReservationCode.COOLDOWN_ACTIVE,
                    existing,
                    nowElapsedMs);
        }
        if (entries.size() >= capacity) {
            capacityRejectedCount++;
            return new Reservation(
                    ReservationCode.CAPACITY_EXCEEDED,
                    0,
                    0,
                    null);
        }
        Entry reserved = new Entry(suggestionDigest, nowElapsedMs + cooldownMs);
        entries.put(key, reserved);
        reservedCount++;
        return result(ReservationCode.RESERVED, reserved, nowElapsedMs);
    }

    public synchronized Snapshot snapshot(long nowElapsedMs) {
        if (nowElapsedMs < 0) {
            throw new IllegalArgumentException("nowElapsedMs must be non-negative");
        }
        pruneExpired(nowElapsedMs);
        return new Snapshot(
                entries.size(),
                reservedCount,
                replayedCount,
                suppressedCount,
                capacityRejectedCount);
    }

    private Reservation result(
            ReservationCode code,
            Entry entry,
            long nowElapsedMs) {
        return new Reservation(
                code,
                entry.blockedUntilElapsedMs,
                Math.max(0, entry.blockedUntilElapsedMs - nowElapsedMs),
                entry.suggestionDigest);
    }

    private void pruneExpired(long nowElapsedMs) {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().blockedUntilElapsedMs <= nowElapsedMs) {
                iterator.remove();
            }
        }
    }

    private static final class Entry {
        final String suggestionDigest;
        final long blockedUntilElapsedMs;

        Entry(String suggestionDigest, long blockedUntilElapsedMs) {
            this.suggestionDigest = suggestionDigest;
            this.blockedUntilElapsedMs = blockedUntilElapsedMs;
        }
    }
}
