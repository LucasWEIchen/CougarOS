package com.centralbrain.runtime.governance;

import com.centralbrain.runtime.governance.ActionGovernancePolicy.Decision;
import com.centralbrain.runtime.governance.ActionGovernancePolicy.Outcome;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Bounded R3 pending-approval owner; R4 replaces it with durable storage. */
public final class InMemoryApprovalRegistry {
    public enum Status {
        PENDING,
        CANCELLED,
        EXPIRED
    }

    private final int maxRecords;
    private final long pendingTtlMs;
    private final long terminalRetentionMs;
    private final LongSupplier elapsedRealtimeMs;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long nextId = 1;

    public InMemoryApprovalRegistry(
            int maxRecords,
            long pendingTtlMs,
            long terminalRetentionMs,
            LongSupplier elapsedRealtimeMs) {
        if (maxRecords <= 0 || pendingTtlMs <= 0 || terminalRetentionMs < 0) {
            throw new IllegalArgumentException("invalid approval registry bounds");
        }
        this.maxRecords = maxRecords;
        this.pendingTtlMs = pendingTtlMs;
        this.terminalRetentionMs = terminalRetentionMs;
        this.elapsedRealtimeMs = Objects.requireNonNull(
                elapsedRealtimeMs,
                "elapsedRealtimeMs");
    }

    public synchronized Snapshot request(
            String actionId,
            Decision decision,
            CallerIdentitySnapshot owner) {
        if (actionId == null || actionId.trim().isEmpty()) {
            throw new IllegalArgumentException("actionId is required");
        }
        if (decision == null
                || decision.getOutcome() != Outcome.APPROVAL_REQUIRED
                || !ActionGovernancePolicy.isHighRisk(decision.getRiskClass())) {
            throw new IllegalArgumentException("only high-risk approval decisions are accepted");
        }
        if (owner == null || !owner.isResolved()) {
            throw new SecurityException("resolved approval owner is required");
        }

        long now = elapsedRealtimeMs.getAsLong();
        refreshAndPrune(now);
        if (entries.size() >= maxRecords) {
            String evicted = oldestTerminalId();
            if (evicted == null) {
                throw new CapacityExceededException("pending approval capacity exhausted");
            }
            entries.remove(evicted);
        }

        String approvalId = "approval-" + nextId++;
        Entry entry = new Entry(
                approvalId,
                actionId,
                decision,
                owner,
                now,
                now + pendingTtlMs);
        entries.put(approvalId, entry);
        return entry.snapshot();
    }

    public synchronized Snapshot findOwned(
            String approvalId,
            CallerIdentitySnapshot owner) {
        long now = elapsedRealtimeMs.getAsLong();
        refreshAndPrune(now);
        Entry entry = entries.get(approvalId);
        return entry != null && entry.owner.samePrincipal(owner) ? entry.snapshot() : null;
    }

    public synchronized boolean cancelOwned(
            String approvalId,
            CallerIdentitySnapshot owner) {
        long now = elapsedRealtimeMs.getAsLong();
        refreshAndPrune(now);
        Entry entry = entries.get(approvalId);
        if (entry == null || !entry.owner.samePrincipal(owner)) {
            return false;
        }
        if (entry.status == Status.CANCELLED) {
            return true;
        }
        if (entry.status != Status.PENDING) {
            return false;
        }
        entry.status = Status.CANCELLED;
        entry.terminalAtElapsedRealtimeMs = now;
        return true;
    }

    public synchronized List<String> pruneExpired() {
        return refreshAndPrune(elapsedRealtimeMs.getAsLong());
    }

    public synchronized int size() {
        refreshAndPrune(elapsedRealtimeMs.getAsLong());
        return entries.size();
    }

    public boolean supportsApprovalGrant() {
        return false;
    }

    public boolean isDurable() {
        return false;
    }

    private List<String> refreshAndPrune(long now) {
        for (Entry entry : entries.values()) {
            if (entry.status == Status.PENDING && now >= entry.expiresAtElapsedRealtimeMs) {
                entry.status = Status.EXPIRED;
                entry.terminalAtElapsedRealtimeMs = now;
            }
        }
        List<String> removed = new ArrayList<>();
        for (Entry entry : new ArrayList<>(entries.values())) {
            if (entry.status != Status.PENDING
                    && now - entry.terminalAtElapsedRealtimeMs >= terminalRetentionMs) {
                entries.remove(entry.approvalId);
                removed.add(entry.approvalId);
            }
        }
        return removed;
    }

    private String oldestTerminalId() {
        return entries.values().stream()
                .filter(entry -> entry.status != Status.PENDING)
                .min(Comparator.comparingLong(entry -> entry.terminalAtElapsedRealtimeMs))
                .map(entry -> entry.approvalId)
                .orElse(null);
    }

    private static final class Entry {
        final String approvalId;
        final String actionId;
        final Decision decision;
        final CallerIdentitySnapshot owner;
        final long createdAtElapsedRealtimeMs;
        final long expiresAtElapsedRealtimeMs;
        Status status = Status.PENDING;
        long terminalAtElapsedRealtimeMs;

        Entry(
                String approvalId,
                String actionId,
                Decision decision,
                CallerIdentitySnapshot owner,
                long createdAtElapsedRealtimeMs,
                long expiresAtElapsedRealtimeMs) {
            this.approvalId = approvalId;
            this.actionId = actionId;
            this.decision = decision;
            this.owner = owner;
            this.createdAtElapsedRealtimeMs = createdAtElapsedRealtimeMs;
            this.expiresAtElapsedRealtimeMs = expiresAtElapsedRealtimeMs;
        }

        Snapshot snapshot() {
            return new Snapshot(
                    approvalId,
                    actionId,
                    decision,
                    status,
                    createdAtElapsedRealtimeMs,
                    expiresAtElapsedRealtimeMs);
        }
    }

    public static final class Snapshot {
        private final String approvalId;
        private final String actionId;
        private final Decision decision;
        private final Status status;
        private final long createdAtElapsedRealtimeMs;
        private final long expiresAtElapsedRealtimeMs;

        Snapshot(
                String approvalId,
                String actionId,
                Decision decision,
                Status status,
                long createdAtElapsedRealtimeMs,
                long expiresAtElapsedRealtimeMs) {
            this.approvalId = approvalId;
            this.actionId = actionId;
            this.decision = decision;
            this.status = status;
            this.createdAtElapsedRealtimeMs = createdAtElapsedRealtimeMs;
            this.expiresAtElapsedRealtimeMs = expiresAtElapsedRealtimeMs;
        }

        public String getApprovalId() {
            return approvalId;
        }

        public String getActionId() {
            return actionId;
        }

        public Decision getDecision() {
            return decision;
        }

        public Status getStatus() {
            return status;
        }

        public long getCreatedAtElapsedRealtimeMs() {
            return createdAtElapsedRealtimeMs;
        }

        public long getExpiresAtElapsedRealtimeMs() {
            return expiresAtElapsedRealtimeMs;
        }
    }

    public static final class CapacityExceededException extends IllegalStateException {
        public CapacityExceededException(String message) {
            super(message);
        }
    }
}
