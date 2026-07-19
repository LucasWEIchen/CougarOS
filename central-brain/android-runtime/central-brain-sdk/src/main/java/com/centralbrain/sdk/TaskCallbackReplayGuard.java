package com.centralbrain.sdk;

import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

/** Fail-closed task callback identity, sequence and terminal replay guard. */
final class TaskCallbackReplayGuard {
    enum Decision {
        ACCEPT,
        DROP_REPLAY,
        REJECT
    }

    static final class Admission {
        private final Decision decision;
        private final String reason;

        private Admission(Decision decision, String reason) {
            this.decision = decision;
            this.reason = reason;
        }

        Decision getDecision() {
            return decision;
        }

        String getReason() {
            return reason;
        }
    }

    private String taskId = "";
    private long lastSequence;
    private int lastState = ICentralBrainRuntime.TASK_STATE_UNKNOWN;
    private int lastProgress;
    private boolean terminal;

    void bindTaskId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("task callback requires a taskId");
        }
        if (!taskId.isEmpty() && !taskId.equals(value)) {
            throw new IllegalStateException("task callback cannot be rebound");
        }
        taskId = value;
    }

    boolean isBound() {
        return !taskId.isEmpty();
    }

    Admission admitUpdate(TaskUpdate update) {
        if (terminal) {
            return drop("callback update arrived after terminal delivery");
        }
        if (!isBound()) {
            return reject("task callback is not bound");
        }
        if (update == null || update.schemaVersion != 1) {
            return reject("task update schema is invalid");
        }
        if (!taskId.equals(update.taskId)) {
            return reject("task update belongs to a different task");
        }
        if (update.sequence <= 0) {
            return reject("task update sequence is invalid");
        }
        if (update.sequence <= lastSequence) {
            return drop("task update sequence was replayed or stale");
        }
        if (lastSequence > 0 && isTerminalState(lastState)) {
            return drop("task update arrived after a terminal state");
        }
        if (!isKnownState(update.state)) {
            return reject("task update state is invalid");
        }
        if (update.progressPercent < 0 || update.progressPercent > 100) {
            return reject("task update progress is invalid");
        }
        if (lastSequence > 0
                && (stateRank(update.state) < stateRank(lastState)
                        || update.progressPercent < lastProgress)) {
            return reject("task update regressed state or progress");
        }
        lastSequence = update.sequence;
        lastState = update.state;
        lastProgress = update.progressPercent;
        return accept();
    }

    Admission admitCompleted(TaskResult result) {
        if (terminal) {
            return drop("task completion was replayed");
        }
        if (!isBound()) {
            return reject("task callback is not bound");
        }
        if (result == null
                || result.schemaVersion != 1
                || !taskId.equals(result.taskId)
                || result.completionCode != ICentralBrainRuntime.ERROR_NONE) {
            return reject("task completion identity or status is invalid");
        }
        terminal = true;
        return accept();
    }

    Admission admitFailed(TaskFailure failure) {
        if (terminal) {
            return drop("task failure was replayed");
        }
        if (!isBound()) {
            return reject("task callback is not bound");
        }
        if (failure == null
                || failure.schemaVersion != 1
                || !taskId.equals(failure.taskId)
                || failure.errorCode == ICentralBrainRuntime.ERROR_NONE) {
            return reject("task failure identity or status is invalid");
        }
        terminal = true;
        return accept();
    }

    private static boolean isKnownState(int state) {
        return state >= ICentralBrainRuntime.TASK_STATE_ACCEPTED
                && state <= ICentralBrainRuntime.TASK_STATE_CANCELLED;
    }

    private static int stateRank(int state) {
        if (state == ICentralBrainRuntime.TASK_STATE_ACCEPTED) {
            return 1;
        }
        if (state == ICentralBrainRuntime.TASK_STATE_RUNNING) {
            return 2;
        }
        return 3;
    }

    private static boolean isTerminalState(int state) {
        return state == ICentralBrainRuntime.TASK_STATE_COMPLETED
                || state == ICentralBrainRuntime.TASK_STATE_FAILED
                || state == ICentralBrainRuntime.TASK_STATE_CANCELLED;
    }

    private static Admission accept() {
        return new Admission(Decision.ACCEPT, "");
    }

    private static Admission drop(String reason) {
        return new Admission(Decision.DROP_REPLAY, reason);
    }

    private static Admission reject(String reason) {
        return new Admission(Decision.REJECT, reason);
    }
}
