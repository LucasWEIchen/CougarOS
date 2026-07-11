package com.centralbrain.runtime.supervisor;

import com.centralbrain.runtime.identity.CallerIdentitySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded, deterministic task state machine owned by the AIOS Kernel runtime.
 * Req IDs: NV-F-001, FW-U-007, NV-G-005, NV-G-006, NV-G-007.
 */
public final class JobSupervisor {
    public enum State {
        ACCEPTED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum TransitionOutcome {
        APPLIED,
        ALREADY_CANCELLED,
        ALREADY_TERMINAL,
        NOT_FOUND_OR_NOT_OWNER
    }

    public interface ElapsedRealtimeClock {
        long now();
    }

    private final int maxRecords;
    private final long terminalRetentionMs;
    private final ElapsedRealtimeClock clock;
    private final LinkedHashMap<String, Record> records = new LinkedHashMap<>();

    public JobSupervisor(
            int maxRecords,
            long terminalRetentionMs,
            ElapsedRealtimeClock clock) {
        if (maxRecords < 1) {
            throw new IllegalArgumentException("maxRecords must be positive");
        }
        if (terminalRetentionMs < 0) {
            throw new IllegalArgumentException("terminalRetentionMs must be non-negative");
        }
        this.maxRecords = maxRecords;
        this.terminalRetentionMs = terminalRetentionMs;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Admission admit(
            String taskId,
            CallerIdentitySnapshot owner,
            String message) {
        requireTaskId(taskId);
        if (owner == null || !owner.isResolved()) {
            throw new SecurityException("resolved Binder caller identity is required");
        }
        if (records.containsKey(taskId)) {
            throw new IllegalArgumentException("duplicate taskId");
        }

        long now = clock.now();
        List<String> evictedTaskIds = pruneExpiredLocked(now);
        while (records.size() >= maxRecords) {
            String oldestTerminalTaskId = oldestTerminalTaskIdLocked();
            if (oldestTerminalTaskId == null) {
                throw new CapacityExceededException(maxRecords);
            }
            records.remove(oldestTerminalTaskId);
            evictedTaskIds.add(oldestTerminalTaskId);
        }

        Record record = new Record(taskId, owner, now, safe(message));
        records.put(taskId, record);
        return new Admission(snapshot(record), evictedTaskIds);
    }

    public synchronized Transition transition(
            String taskId,
            State target,
            int progressPercent,
            String message) {
        Record record = records.get(taskId);
        if (record == null) {
            return Transition.rejected(TransitionOutcome.NOT_FOUND_OR_NOT_OWNER, null);
        }
        if (isTerminal(record.state)) {
            return Transition.rejected(TransitionOutcome.ALREADY_TERMINAL, snapshot(record));
        }
        validateTransition(record.state, target);
        applyTransition(record, target, progressPercent, message);
        return Transition.applied(snapshot(record));
    }

    public synchronized Transition cancelOwned(
            String taskId,
            CallerIdentitySnapshot caller,
            String message) {
        Record record = records.get(taskId);
        if (record == null || !record.owner.samePrincipal(caller)) {
            return Transition.rejected(TransitionOutcome.NOT_FOUND_OR_NOT_OWNER, null);
        }
        return cancelLocked(record, message);
    }

    public synchronized Transition cancelSystem(String taskId, String message) {
        Record record = records.get(taskId);
        if (record == null) {
            return Transition.rejected(TransitionOutcome.NOT_FOUND_OR_NOT_OWNER, null);
        }
        return cancelLocked(record, message);
    }

    public synchronized Snapshot findOwned(
            String taskId,
            CallerIdentitySnapshot caller) {
        Record record = records.get(taskId);
        if (record == null || !record.owner.samePrincipal(caller)) {
            return null;
        }
        return snapshot(record);
    }

    public synchronized Snapshot find(String taskId) {
        Record record = records.get(taskId);
        return record == null ? null : snapshot(record);
    }

    public synchronized List<String> pruneExpired() {
        return Collections.unmodifiableList(pruneExpiredLocked(clock.now()));
    }

    public synchronized void markTerminalDeliverySettled(String taskId) {
        Record record = records.get(taskId);
        if (record == null) {
            return;
        }
        if (!isTerminal(record.state)) {
            throw new IllegalStateException("cannot settle delivery for a non-terminal task");
        }
        record.terminalDeliveryPending = false;
    }

    public synchronized boolean remove(String taskId) {
        return records.remove(taskId) != null;
    }

    public synchronized int size() {
        return records.size();
    }

    private Transition cancelLocked(Record record, String message) {
        if (record.state == State.CANCELLED) {
            return Transition.rejected(TransitionOutcome.ALREADY_CANCELLED, snapshot(record));
        }
        if (isTerminal(record.state)) {
            return Transition.rejected(TransitionOutcome.ALREADY_TERMINAL, snapshot(record));
        }
        applyTransition(record, State.CANCELLED, record.progressPercent, message);
        return Transition.applied(snapshot(record));
    }

    private void applyTransition(
            Record record,
            State target,
            int progressPercent,
            String message) {
        validateProgress(record, target, progressPercent);
        record.state = target;
        record.progressPercent = progressPercent;
        record.sequence++;
        record.message = safe(message);
        if (isTerminal(target)) {
            record.terminalAtElapsedRealtimeMs = clock.now();
            record.terminalDeliveryPending = true;
        }
    }

    private static void validateTransition(State source, State target) {
        Objects.requireNonNull(target, "target state");
        boolean valid = source == State.ACCEPTED
                && (target == State.RUNNING
                        || target == State.FAILED
                        || target == State.CANCELLED);
        valid = valid || source == State.RUNNING
                && (target == State.COMPLETED
                        || target == State.FAILED
                        || target == State.CANCELLED);
        if (!valid) {
            throw new IllegalStateException("invalid task transition " + source + " -> " + target);
        }
    }

    private static void validateProgress(Record record, State target, int progressPercent) {
        if (progressPercent < record.progressPercent || progressPercent > 100) {
            throw new IllegalArgumentException("task progress must be monotonic in range 0..100");
        }
        if (target == State.RUNNING && progressPercent >= 100) {
            throw new IllegalArgumentException("running progress must be below 100");
        }
        if (target == State.COMPLETED && progressPercent != 100) {
            throw new IllegalArgumentException("completed progress must be 100");
        }
    }

    private List<String> pruneExpiredLocked(long now) {
        List<String> removed = new ArrayList<>();
        Iterator<Map.Entry<String, Record>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Record> entry = iterator.next();
            Record record = entry.getValue();
            if (isTerminal(record.state)
                    && !record.terminalDeliveryPending
                    && now >= record.terminalAtElapsedRealtimeMs
                    && now - record.terminalAtElapsedRealtimeMs >= terminalRetentionMs) {
                removed.add(entry.getKey());
                iterator.remove();
            }
        }
        return removed;
    }

    private String oldestTerminalTaskIdLocked() {
        String taskId = null;
        long oldestTerminalAt = Long.MAX_VALUE;
        for (Record record : records.values()) {
            if (isTerminal(record.state)
                    && !record.terminalDeliveryPending
                    && record.terminalAtElapsedRealtimeMs < oldestTerminalAt) {
                taskId = record.taskId;
                oldestTerminalAt = record.terminalAtElapsedRealtimeMs;
            }
        }
        return taskId;
    }

    private static boolean isTerminal(State state) {
        return state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED;
    }

    private static Snapshot snapshot(Record record) {
        return new Snapshot(
                record.taskId,
                record.owner,
                record.state,
                record.progressPercent,
                record.sequence,
                record.message,
                record.acceptedAtElapsedRealtimeMs,
                record.terminalAtElapsedRealtimeMs);
    }

    private static void requireTaskId(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId is required");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Record {
        final String taskId;
        final CallerIdentitySnapshot owner;
        final long acceptedAtElapsedRealtimeMs;
        State state = State.ACCEPTED;
        int progressPercent;
        long sequence = 1;
        String message;
        long terminalAtElapsedRealtimeMs = -1;
        boolean terminalDeliveryPending;

        Record(
                String taskId,
                CallerIdentitySnapshot owner,
                long acceptedAtElapsedRealtimeMs,
                String message) {
            this.taskId = taskId;
            this.owner = owner;
            this.acceptedAtElapsedRealtimeMs = acceptedAtElapsedRealtimeMs;
            this.message = message;
        }
    }

    public static final class Admission {
        private final Snapshot snapshot;
        private final List<String> evictedTaskIds;

        Admission(Snapshot snapshot, List<String> evictedTaskIds) {
            this.snapshot = snapshot;
            this.evictedTaskIds = Collections.unmodifiableList(new ArrayList<>(evictedTaskIds));
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }

        public List<String> getEvictedTaskIds() {
            return evictedTaskIds;
        }
    }

    public static final class Transition {
        private final TransitionOutcome outcome;
        private final Snapshot snapshot;

        private Transition(TransitionOutcome outcome, Snapshot snapshot) {
            this.outcome = outcome;
            this.snapshot = snapshot;
        }

        static Transition applied(Snapshot snapshot) {
            return new Transition(TransitionOutcome.APPLIED, snapshot);
        }

        static Transition rejected(TransitionOutcome outcome, Snapshot snapshot) {
            return new Transition(outcome, snapshot);
        }

        public TransitionOutcome getOutcome() {
            return outcome;
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }

        public boolean wasApplied() {
            return outcome == TransitionOutcome.APPLIED;
        }
    }

    public static final class Snapshot {
        private final String taskId;
        private final CallerIdentitySnapshot owner;
        private final State state;
        private final int progressPercent;
        private final long sequence;
        private final String message;
        private final long acceptedAtElapsedRealtimeMs;
        private final long terminalAtElapsedRealtimeMs;

        Snapshot(
                String taskId,
                CallerIdentitySnapshot owner,
                State state,
                int progressPercent,
                long sequence,
                String message,
                long acceptedAtElapsedRealtimeMs,
                long terminalAtElapsedRealtimeMs) {
            this.taskId = taskId;
            this.owner = owner;
            this.state = state;
            this.progressPercent = progressPercent;
            this.sequence = sequence;
            this.message = message;
            this.acceptedAtElapsedRealtimeMs = acceptedAtElapsedRealtimeMs;
            this.terminalAtElapsedRealtimeMs = terminalAtElapsedRealtimeMs;
        }

        public String getTaskId() {
            return taskId;
        }

        public CallerIdentitySnapshot getOwner() {
            return owner;
        }

        public State getState() {
            return state;
        }

        public int getProgressPercent() {
            return progressPercent;
        }

        public long getSequence() {
            return sequence;
        }

        public String getMessage() {
            return message;
        }

        public long getAcceptedAtElapsedRealtimeMs() {
            return acceptedAtElapsedRealtimeMs;
        }

        public long getTerminalAtElapsedRealtimeMs() {
            return terminalAtElapsedRealtimeMs;
        }

        public boolean isTerminal() {
            return JobSupervisor.isTerminal(state);
        }
    }

    public static final class CapacityExceededException extends IllegalStateException {
        CapacityExceededException(int maxRecords) {
            super("job supervisor capacity exhausted: " + maxRecords);
        }
    }
}
