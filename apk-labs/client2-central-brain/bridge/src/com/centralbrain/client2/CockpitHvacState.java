package com.centralbrain.client2;

import java.util.Objects;

/** Immutable HVAC HMI projection. Device readback remains absent until trusted evidence arrives. */
public final class CockpitHvacState {
    public enum RequestState { IDLE, DIRTY, DEBOUNCING, SUBMITTING, ACCEPTED, FAILED }

    public enum EvidenceSource { UNAVAILABLE, SIMULATED, TARGET }

    public enum EvidenceQuality { NO_EVIDENCE, STALE, OBSERVED, VERIFIED }

    public enum EffectState { NOT_DISPATCHED, REQUESTED, DISPATCHED, APPLIED, VERIFIED, FAILED }

    private final HvacControlIntent desired;
    private final long desiredRevision;
    private final long submittedRevision;
    private final RequestState requestState;
    private final HvacControlIntent reported;
    private final EvidenceSource source;
    private final EvidenceQuality quality;
    private final EffectState effectState;

    private CockpitHvacState(
            HvacControlIntent desired,
            long desiredRevision,
            long submittedRevision,
            RequestState requestState,
            HvacControlIntent reported,
            EvidenceSource source,
            EvidenceQuality quality,
            EffectState effectState) {
        this.desired = Objects.requireNonNull(desired, "desired");
        this.desiredRevision = Math.max(0, desiredRevision);
        this.submittedRevision = Math.max(0, submittedRevision);
        this.requestState = Objects.requireNonNull(requestState, "requestState");
        this.reported = reported;
        this.source = Objects.requireNonNull(source, "source");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.effectState = Objects.requireNonNull(effectState, "effectState");
        if (reported == null && (source != EvidenceSource.UNAVAILABLE
                || quality != EvidenceQuality.NO_EVIDENCE
                || effectState == EffectState.VERIFIED)) {
            throw new IllegalArgumentException("HVAC evidence cannot exist without readback");
        }
    }

    public static CockpitHvacState initial() {
        return new CockpitHvacState(
                HvacControlIntent.defaults(),
                0,
                0,
                RequestState.IDLE,
                null,
                EvidenceSource.UNAVAILABLE,
                EvidenceQuality.NO_EVIDENCE,
                EffectState.NOT_DISPATCHED);
    }

    public HvacControlIntent getDesired() {
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

    public HvacControlIntent getReported() {
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

    public boolean hasReportedEvidence() {
        return reported != null;
    }

    CockpitHvacState desiredChanged(HvacControlIntent value) {
        Objects.requireNonNull(value, "value");
        if (desired.equals(value)) {
            return this;
        }
        long nextRevision = desiredRevision == Long.MAX_VALUE
                ? Long.MAX_VALUE : desiredRevision + 1;
        return new CockpitHvacState(
                value,
                nextRevision,
                submittedRevision,
                RequestState.DEBOUNCING,
                reported,
                source,
                quality,
                effectState);
    }

    CockpitHvacState submitted(long revision) {
        if (revision != desiredRevision || revision <= 0) {
            throw new IllegalArgumentException("stale HVAC desired revision");
        }
        return new CockpitHvacState(
                desired,
                desiredRevision,
                revision,
                RequestState.SUBMITTING,
                reported,
                source,
                quality,
                EffectState.NOT_DISPATCHED);
    }

    CockpitHvacState requestAccepted() {
        if (requestState != RequestState.SUBMITTING) {
            return this;
        }
        return new CockpitHvacState(
                desired,
                desiredRevision,
                submittedRevision,
                RequestState.ACCEPTED,
                reported,
                source,
                quality,
                EffectState.REQUESTED);
    }

    CockpitHvacState requestFailed() {
        return new CockpitHvacState(
                desired,
                desiredRevision,
                submittedRevision,
                RequestState.FAILED,
                reported,
                source,
                quality,
                effectState);
    }
}
