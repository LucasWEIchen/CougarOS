package com.centralbrain.client2;

import java.util.Objects;

/** Immutable seat HMI projection. Safety context and device readback remain separate evidence domains. */
public final class CockpitSeatState {
    public enum RequestState {
        IDLE, DEBOUNCING, SUBMITTING, ACCEPTED, WAITING_APPROVAL, BLOCKED, FAILED
    }

    public enum EvidenceSource { UNAVAILABLE, SIMULATED, TARGET }

    public enum EvidenceQuality { NO_EVIDENCE, STALE, OBSERVED, VERIFIED }

    public enum EffectState { NOT_DISPATCHED, REQUESTED, DISPATCHED, APPLIED, VERIFIED, FAILED }

    public enum DrivingState { UNKNOWN_RESTRICTED, PARKED, MOVING }

    public enum OccupancyState { UNKNOWN, EMPTY, OCCUPIED }

    public enum BeltState { UNKNOWN, BELTED, UNBELTED }

    public enum SafetyDecision {
        NOT_EVALUATED,
        ALLOWED_LOW_RISK,
        ALLOWED_POSITION,
        APPROVAL_REQUIRED,
        DENIED_UNKNOWN_CONTEXT,
        DENIED_MOVING_DRIVER,
        DENIED_OCCUPANCY,
        DENIED_BELT
    }

    /** Typed Context evidence; the maintained Client2 path currently exposes unavailable only. */
    public static final class SafetyContext {
        private final DrivingState drivingState;
        private final OccupancyState occupancyState;
        private final BeltState beltState;
        private final EvidenceSource source;
        private final EvidenceQuality quality;
        private final long revision;

        private SafetyContext(
                DrivingState drivingState,
                OccupancyState occupancyState,
                BeltState beltState,
                EvidenceSource source,
                EvidenceQuality quality,
                long revision) {
            this.drivingState = Objects.requireNonNull(drivingState, "drivingState");
            this.occupancyState = Objects.requireNonNull(occupancyState, "occupancyState");
            this.beltState = Objects.requireNonNull(beltState, "beltState");
            this.source = Objects.requireNonNull(source, "source");
            this.quality = Objects.requireNonNull(quality, "quality");
            this.revision = Math.max(0, revision);
            if (drivingState == DrivingState.UNKNOWN_RESTRICTED
                    && (source != EvidenceSource.UNAVAILABLE
                    || quality != EvidenceQuality.NO_EVIDENCE)) {
                throw new IllegalArgumentException("unknown driving state cannot claim trusted context");
            }
        }

        public static SafetyContext unavailable() {
            return new SafetyContext(
                    DrivingState.UNKNOWN_RESTRICTED,
                    OccupancyState.UNKNOWN,
                    BeltState.UNKNOWN,
                    EvidenceSource.UNAVAILABLE,
                    EvidenceQuality.NO_EVIDENCE,
                    0);
        }

        public static SafetyContext observed(
                DrivingState drivingState,
                OccupancyState occupancyState,
                BeltState beltState,
                EvidenceSource source,
                long revision) {
            if (drivingState == DrivingState.UNKNOWN_RESTRICTED
                    || source == EvidenceSource.UNAVAILABLE
                    || revision <= 0) {
                throw new IllegalArgumentException("observed seat safety context must be trusted");
            }
            return new SafetyContext(
                    drivingState,
                    occupancyState,
                    beltState,
                    source,
                    EvidenceQuality.OBSERVED,
                    revision);
        }

        public DrivingState getDrivingState() {
            return drivingState;
        }

        public OccupancyState getOccupancyState() {
            return occupancyState;
        }

        public BeltState getBeltState() {
            return beltState;
        }

        public EvidenceSource getSource() {
            return source;
        }

        public EvidenceQuality getQuality() {
            return quality;
        }

        public long getRevision() {
            return revision;
        }
    }

    private final SeatControlIntent desired;
    private final long desiredRevision;
    private final long submittedRevision;
    private final RequestState requestState;
    private final SeatControlIntent reported;
    private final EvidenceSource source;
    private final EvidenceQuality quality;
    private final EffectState effectState;
    private final SafetyContext safetyContext;
    private final SafetyDecision safetyDecision;

    private CockpitSeatState(
            SeatControlIntent desired,
            long desiredRevision,
            long submittedRevision,
            RequestState requestState,
            SeatControlIntent reported,
            EvidenceSource source,
            EvidenceQuality quality,
            EffectState effectState,
            SafetyContext safetyContext,
            SafetyDecision safetyDecision) {
        this.desired = Objects.requireNonNull(desired, "desired");
        this.desiredRevision = Math.max(0, desiredRevision);
        this.submittedRevision = Math.max(0, submittedRevision);
        this.requestState = Objects.requireNonNull(requestState, "requestState");
        this.reported = reported;
        this.source = Objects.requireNonNull(source, "source");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.effectState = Objects.requireNonNull(effectState, "effectState");
        this.safetyContext = Objects.requireNonNull(safetyContext, "safetyContext");
        this.safetyDecision = Objects.requireNonNull(safetyDecision, "safetyDecision");
        if (reported == null && (source != EvidenceSource.UNAVAILABLE
                || quality != EvidenceQuality.NO_EVIDENCE
                || effectState == EffectState.VERIFIED)) {
            throw new IllegalArgumentException("seat evidence cannot exist without readback");
        }
    }

    public static CockpitSeatState initial() {
        return new CockpitSeatState(
                SeatControlIntent.defaults(),
                0,
                0,
                RequestState.IDLE,
                null,
                EvidenceSource.UNAVAILABLE,
                EvidenceQuality.NO_EVIDENCE,
                EffectState.NOT_DISPATCHED,
                SafetyContext.unavailable(),
                SafetyDecision.NOT_EVALUATED);
    }

    public SeatControlIntent getDesired() {
        return desired;
    }

    public long getDesiredRevision() {
        return desiredRevision;
    }

    public long getSubmittedRevision() {
        return submittedRevision;
    }

    public RequestState getRequestState() {
        return requestState;
    }

    public SeatControlIntent getReported() {
        return reported;
    }

    public EvidenceSource getSource() {
        return source;
    }

    public EvidenceQuality getQuality() {
        return quality;
    }

    public EffectState getEffectState() {
        return effectState;
    }

    public SafetyContext getSafetyContext() {
        return safetyContext;
    }

    public SafetyDecision getSafetyDecision() {
        return safetyDecision;
    }

    public boolean hasReportedEvidence() {
        return reported != null;
    }

    CockpitSeatState safetyContextChanged(SafetyContext value) {
        return new CockpitSeatState(
                desired,
                desiredRevision,
                submittedRevision,
                requestState,
                reported,
                source,
                quality,
                effectState,
                Objects.requireNonNull(value, "value"),
                SafetyDecision.NOT_EVALUATED);
    }

    CockpitSeatState desiredChanged(SeatControlIntent value) {
        Objects.requireNonNull(value, "value");
        if (desired.equals(value)) {
            return this;
        }
        SafetyDecision decision = evaluate(value);
        if (isDenied(decision)) {
            return new CockpitSeatState(
                    desired,
                    desiredRevision,
                    submittedRevision,
                    RequestState.BLOCKED,
                    reported,
                    source,
                    quality,
                    effectState,
                    safetyContext,
                    decision);
        }
        long nextRevision = desiredRevision == Long.MAX_VALUE
                ? Long.MAX_VALUE : desiredRevision + 1;
        return new CockpitSeatState(
                value,
                nextRevision,
                submittedRevision,
                decision == SafetyDecision.APPROVAL_REQUIRED
                        ? RequestState.WAITING_APPROVAL : RequestState.DEBOUNCING,
                reported,
                source,
                quality,
                effectState,
                safetyContext,
                decision);
    }

    CockpitSeatState submitted(long revision) {
        if (revision != desiredRevision || revision <= 0
                || requestState != RequestState.DEBOUNCING) {
            throw new IllegalArgumentException("stale or blocked seat desired revision");
        }
        return new CockpitSeatState(
                desired,
                desiredRevision,
                revision,
                RequestState.SUBMITTING,
                reported,
                source,
                quality,
                EffectState.NOT_DISPATCHED,
                safetyContext,
                safetyDecision);
    }

    CockpitSeatState requestAccepted() {
        if (requestState != RequestState.SUBMITTING) {
            return this;
        }
        return new CockpitSeatState(
                desired,
                desiredRevision,
                submittedRevision,
                RequestState.ACCEPTED,
                reported,
                source,
                quality,
                EffectState.REQUESTED,
                safetyContext,
                safetyDecision);
    }

    CockpitSeatState requestFailed() {
        return new CockpitSeatState(
                desired,
                desiredRevision,
                submittedRevision,
                RequestState.FAILED,
                reported,
                source,
                quality,
                effectState,
                safetyContext,
                safetyDecision);
    }

    private SafetyDecision evaluate(SeatControlIntent value) {
        if (!value.changesPositionComparedTo(desired)) {
            return SafetyDecision.ALLOWED_LOW_RISK;
        }
        if (safetyContext.drivingState == DrivingState.UNKNOWN_RESTRICTED) {
            return SafetyDecision.DENIED_UNKNOWN_CONTEXT;
        }
        if (value.getZone() == SeatControlIntent.Zone.DRIVER
                && safetyContext.drivingState == DrivingState.MOVING) {
            return SafetyDecision.DENIED_MOVING_DRIVER;
        }
        if (safetyContext.occupancyState != OccupancyState.OCCUPIED) {
            return SafetyDecision.DENIED_OCCUPANCY;
        }
        if (safetyContext.beltState != BeltState.UNBELTED) {
            return SafetyDecision.DENIED_BELT;
        }
        if (value.getPreset() == SeatControlIntent.Preset.REST) {
            return SafetyDecision.APPROVAL_REQUIRED;
        }
        return SafetyDecision.ALLOWED_POSITION;
    }

    private static boolean isDenied(SafetyDecision decision) {
        switch (decision) {
            case DENIED_UNKNOWN_CONTEXT:
            case DENIED_MOVING_DRIVER:
            case DENIED_OCCUPANCY:
            case DENIED_BELT:
                return true;
            default:
                return false;
        }
    }
}
