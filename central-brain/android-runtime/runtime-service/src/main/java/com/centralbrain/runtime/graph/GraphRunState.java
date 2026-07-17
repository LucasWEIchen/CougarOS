package com.centralbrain.runtime.graph;

import java.util.EnumSet;

/** Stable P3 graph lifecycle states. Req ID: S2-GRF-001. */
public enum GraphRunState {
    CREATED,
    PLANNING,
    WAITING,
    EXECUTING,
    PARTIAL,
    COMPENSATING,
    COMPLETED,
    FAILED,
    CANCELLED,
    STUCK;

    private static final EnumSet<GraphRunState> TERMINAL = EnumSet.of(
            PARTIAL, COMPLETED, FAILED, CANCELLED, STUCK);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(GraphRunState target) {
        if (target == null || isTerminal()) {
            return false;
        }
        switch (this) {
            case CREATED:
                return target == PLANNING
                        || target == FAILED
                        || target == CANCELLED
                        || target == STUCK;
            case PLANNING:
                return target == WAITING
                        || target == FAILED
                        || target == CANCELLED
                        || target == STUCK;
            case WAITING:
                return target == EXECUTING
                        || target == COMPLETED
                        || target == PARTIAL
                        || target == FAILED
                        || target == CANCELLED
                        || target == COMPENSATING
                        || target == STUCK;
            case EXECUTING:
                return target == WAITING
                        || target == COMPLETED
                        || target == PARTIAL
                        || target == FAILED
                        || target == CANCELLED
                        || target == COMPENSATING
                        || target == STUCK;
            case COMPENSATING:
                return target == COMPLETED
                        || target == PARTIAL
                        || target == FAILED
                        || target == CANCELLED
                        || target == STUCK;
            default:
                return false;
        }
    }
}
