package com.centralbrain.client2;

import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;

import java.util.Objects;

/** Immutable, fail-closed projection for approval, partial completion and compensation UX. */
public final class CockpitRecoveryState {
    public enum ApprovalStatus { UNAVAILABLE, REQUESTED, RESOLVED, EXPIRED }

    public enum AggregateStatus {
        NO_EVIDENCE,
        IN_PROGRESS,
        VERIFIED,
        PARTIALLY_COMPLETED,
        FAILED,
        INCONCLUSIVE,
        COMPLETED
    }

    public enum CompensationStatus { UNAVAILABLE, COMPENSATING, COMPENSATED, INCONCLUSIVE }

    private final ApprovalStatus approvalStatus;
    private final String approvalReasonCode;
    private final String approvalTarget;
    private final long approvalExpiresAtEpochMs;
    private final int sessionState;
    private final int verifiedCount;
    private final int failedCount;
    private final int inconclusiveCount;
    private final CompensationStatus compensationStatus;

    private CockpitRecoveryState(
            ApprovalStatus approvalStatus,
            String approvalReasonCode,
            String approvalTarget,
            long approvalExpiresAtEpochMs,
            int sessionState,
            int verifiedCount,
            int failedCount,
            int inconclusiveCount,
            CompensationStatus compensationStatus) {
        this.approvalStatus = Objects.requireNonNull(approvalStatus, "approvalStatus");
        this.approvalReasonCode = bounded(approvalReasonCode, 64, "approvalReasonCode");
        this.approvalTarget = bounded(approvalTarget, 96, "approvalTarget");
        this.approvalExpiresAtEpochMs = Math.max(0, approvalExpiresAtEpochMs);
        this.sessionState = Math.max(0, sessionState);
        this.verifiedCount = nonNegative(verifiedCount, "verifiedCount");
        this.failedCount = nonNegative(failedCount, "failedCount");
        this.inconclusiveCount = nonNegative(inconclusiveCount, "inconclusiveCount");
        this.compensationStatus = Objects.requireNonNull(
                compensationStatus, "compensationStatus");
    }

    public static CockpitRecoveryState initial() {
        return new CockpitRecoveryState(
                ApprovalStatus.UNAVAILABLE,
                "UNAVAILABLE",
                "UNAVAILABLE",
                0,
                ICentralBrainSessionRuntime.SESSION_STATE_UNKNOWN,
                0,
                0,
                0,
                CompensationStatus.UNAVAILABLE);
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public String getApprovalReasonCode() {
        return approvalReasonCode;
    }

    public String getApprovalTarget() {
        return approvalTarget;
    }

    public long getApprovalExpiresAtEpochMs() {
        return approvalExpiresAtEpochMs;
    }

    public int getVerifiedCount() {
        return verifiedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getInconclusiveCount() {
        return inconclusiveCount;
    }

    public CompensationStatus getCompensationStatus() {
        return compensationStatus;
    }

    public AggregateStatus getAggregateStatus() {
        if (sessionState == ICentralBrainSessionRuntime.SESSION_STATE_PARTIALLY_COMPLETED) {
            return AggregateStatus.PARTIALLY_COMPLETED;
        }
        if (sessionState == ICentralBrainSessionRuntime.SESSION_STATE_COMPLETED) {
            return AggregateStatus.COMPLETED;
        }
        if (verifiedCount > 0 && (failedCount > 0 || inconclusiveCount > 0)) {
            return AggregateStatus.PARTIALLY_COMPLETED;
        }
        if (failedCount > 0) {
            return AggregateStatus.FAILED;
        }
        if (verifiedCount > 0) {
            return AggregateStatus.VERIFIED;
        }
        if (inconclusiveCount > 0) {
            return AggregateStatus.INCONCLUSIVE;
        }
        if (sessionState == ICentralBrainSessionRuntime.SESSION_STATE_PLANNING
                || sessionState == ICentralBrainSessionRuntime.SESSION_STATE_WAITING_FOR_CONFIRMATION
                || sessionState == ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING
                || sessionState == ICentralBrainSessionRuntime.SESSION_STATE_COMPENSATING) {
            return AggregateStatus.IN_PROGRESS;
        }
        if (sessionState == ICentralBrainSessionRuntime.SESSION_STATE_FAILED
                || sessionState == ICentralBrainSessionRuntime.SESSION_STATE_STUCK
                || sessionState == ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED) {
            return AggregateStatus.FAILED;
        }
        return AggregateStatus.NO_EVIDENCE;
    }

    /** ApprovalPrompt is not delivered through the current Client2 Session/Event surface. */
    public boolean isApproveEnabled() {
        return false;
    }

    public boolean isRejectEnabled() {
        return false;
    }

    /** EffectObservation.retryable is not delivered through Event V1. */
    public boolean isRetryEnabled() {
        return false;
    }

    /** UndoHandle is not published to Client2. */
    public boolean isUndoEnabled() {
        return false;
    }

    CockpitRecoveryState scenarioRequested() {
        return initial();
    }

    CockpitRecoveryState snapshot(int newSessionState) {
        ApprovalStatus nextApproval = approvalStatus;
        if (newSessionState == ICentralBrainSessionRuntime.SESSION_STATE_WAITING_FOR_CONFIRMATION
                && approvalStatus == ApprovalStatus.UNAVAILABLE) {
            nextApproval = ApprovalStatus.REQUESTED;
        }
        CompensationStatus nextCompensation = compensationStatus;
        if (newSessionState == ICentralBrainSessionRuntime.SESSION_STATE_COMPENSATING
                && compensationStatus == CompensationStatus.UNAVAILABLE) {
            nextCompensation = CompensationStatus.COMPENSATING;
        }
        return copy(
                nextApproval,
                approvalReasonCode,
                approvalTarget,
                approvalExpiresAtEpochMs,
                newSessionState,
                verifiedCount,
                failedCount,
                inconclusiveCount,
                nextCompensation);
    }

    CockpitRecoveryState runtimeEvent(CockpitExecutionTimeline.TraceItem item) {
        Objects.requireNonNull(item, "item");
        ApprovalStatus nextApproval = approvalStatus;
        String nextReason = approvalReasonCode;
        String nextTarget = approvalTarget;
        long nextExpiry = approvalExpiresAtEpochMs;
        int nextVerified = verifiedCount;
        int nextFailed = failedCount;
        int nextInconclusive = inconclusiveCount;
        CompensationStatus nextCompensation = compensationStatus;

        switch (item.getEventType()) {
            case "ApprovalRequested":
                nextApproval = ApprovalStatus.REQUESTED;
                nextReason = "UNAVAILABLE";
                nextTarget = usableTarget(item.getTarget());
                nextExpiry = 0;
                break;
            case "ApprovalResolved":
                nextApproval = ApprovalStatus.RESOLVED;
                nextTarget = usableTarget(item.getTarget());
                break;
            case "ApprovalExpired":
                nextApproval = ApprovalStatus.EXPIRED;
                nextTarget = usableTarget(item.getTarget());
                break;
            case "EffectVerified":
                if (item.getStatus() == CockpitExecutionTimeline.Status.VERIFIED) {
                    nextVerified = increment(nextVerified);
                } else {
                    nextInconclusive = increment(nextInconclusive);
                }
                break;
            case "EffectFailed":
                nextFailed = increment(nextFailed);
                break;
            case "CompensationStarted":
                nextCompensation = CompensationStatus.COMPENSATING;
                break;
            case "CompensationObserved":
                nextCompensation = item.getStatus()
                        == CockpitExecutionTimeline.Status.COMPENSATED
                        ? CompensationStatus.COMPENSATED
                        : CompensationStatus.INCONCLUSIVE;
                break;
            default:
                break;
        }
        return copy(
                nextApproval,
                nextReason,
                nextTarget,
                nextExpiry,
                sessionState,
                nextVerified,
                nextFailed,
                nextInconclusive,
                nextCompensation);
    }

    private CockpitRecoveryState copy(
            ApprovalStatus nextApproval,
            String nextReason,
            String nextTarget,
            long nextExpiry,
            int nextSessionState,
            int nextVerified,
            int nextFailed,
            int nextInconclusive,
            CompensationStatus nextCompensation) {
        return new CockpitRecoveryState(
                nextApproval,
                nextReason,
                nextTarget,
                nextExpiry,
                nextSessionState,
                nextVerified,
                nextFailed,
                nextInconclusive,
                nextCompensation);
    }

    private static String usableTarget(String value) {
        if (value == null || value.isEmpty() || "TYPED_EVENT".equals(value)) {
            return "UNAVAILABLE";
        }
        return value;
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    private static int nonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private static String bounded(String value, int max, String field) {
        if (value == null || value.length() > max) {
            throw new IllegalArgumentException("invalid recovery " + field);
        }
        return value;
    }
}
