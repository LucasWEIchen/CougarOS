package com.centralbrain.runtime.graph;

import java.util.EnumSet;

/** Stable P3 node lifecycle states. Req ID: S2-GRF-001. */
public enum NodeRunState {
    PENDING,
    READY,
    EXECUTING,
    WAITING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    CANCELLED,
    COMPENSATING,
    COMPENSATED,
    STUCK;

    private static final EnumSet<NodeRunState> TERMINAL = EnumSet.of(
            SUCCEEDED, FAILED, SKIPPED, CANCELLED, COMPENSATED, STUCK);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean satisfiesSuccessDependency() {
        return this == SUCCEEDED || this == COMPENSATED;
    }

    public boolean canTransitionTo(NodeRunState target) {
        if (target == null || isTerminal()) {
            return false;
        }
        switch (this) {
            case PENDING:
                return target == READY
                        || target == SKIPPED
                        || target == CANCELLED
                        || target == STUCK;
            case READY:
                return target == EXECUTING
                        || target == SKIPPED
                        || target == CANCELLED
                        || target == STUCK;
            case EXECUTING:
                return target == WAITING
                        || target == SUCCEEDED
                        || target == FAILED
                        || target == SKIPPED
                        || target == CANCELLED
                        || target == COMPENSATING
                        || target == STUCK;
            case WAITING:
                return target == READY
                        || target == SUCCEEDED
                        || target == FAILED
                        || target == SKIPPED
                        || target == CANCELLED
                        || target == STUCK;
            case COMPENSATING:
                return target == COMPENSATED
                        || target == FAILED
                        || target == CANCELLED
                        || target == STUCK;
            default:
                return false;
        }
    }
}
