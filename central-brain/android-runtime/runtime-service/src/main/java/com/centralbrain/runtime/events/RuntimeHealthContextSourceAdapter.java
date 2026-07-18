package com.centralbrain.runtime.events;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;

import java.util.Objects;

/** Normalizes caller-owned Runtime health evidence; it does not inspect Runtime services. */
public final class RuntimeHealthContextSourceAdapter
        implements ContextSourceAdapter<RuntimeHealthContextSourceAdapter.RuntimeHealthSample> {
    public enum HealthState {
        HEALTHY,
        DEGRADED,
        UNAVAILABLE,
        ERROR
    }

    public static final class RuntimeHealthSample {
        private final HealthState state;
        private final long observedAtElapsedMs;
        private final long revision;
        private final String evidenceDigest;

        public RuntimeHealthSample(
                HealthState state,
                long observedAtElapsedMs,
                long revision,
                String evidenceDigest) {
            this.state = Objects.requireNonNull(state, "state");
            if (observedAtElapsedMs < 0) {
                throw new IllegalArgumentException("observedAtElapsedMs is invalid");
            }
            if (revision < 1) {
                throw new IllegalArgumentException("revision must be positive");
            }
            this.observedAtElapsedMs = observedAtElapsedMs;
            this.revision = revision;
            this.evidenceDigest = EventBroker.requireDigest(evidenceDigest, "evidenceDigest");
        }

        public HealthState getState() {
            return state;
        }

        public long getObservedAtElapsedMs() {
            return observedAtElapsedMs;
        }

        public long getRevision() {
            return revision;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }
    }

    private final Descriptor descriptor = new Descriptor(SourceId.RUNTIME_HEALTH);

    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public AdaptationResult adapt(RuntimeHealthSample input, long nowElapsedMs) {
        if (input == null || nowElapsedMs < 0) {
            return new AdaptationResult(ResultCode.REJECTED_INPUT, null);
        }
        if (nowElapsedMs < input.observedAtElapsedMs) {
            return new AdaptationResult(ResultCode.REJECTED_FUTURE, null);
        }
        SignalQuality quality;
        SourceValue value = null;
        ResultCode code;
        switch (input.state) {
            case HEALTHY:
            case DEGRADED:
                boolean stale = nowElapsedMs - input.observedAtElapsedMs
                        > descriptor.getMaximumAgeMs();
                quality = stale ? SignalQuality.STALE : SignalQuality.VALID;
                value = SourceValue.ofText(input.state.name());
                code = stale ? ResultCode.STALE : ResultCode.ADAPTED;
                break;
            case UNAVAILABLE:
                quality = SignalQuality.UNAVAILABLE;
                code = ResultCode.SOURCE_UNAVAILABLE;
                break;
            case ERROR:
                quality = SignalQuality.ERROR;
                code = ResultCode.SOURCE_ERROR;
                break;
            default:
                throw new IllegalStateException("unknown Runtime health state");
        }
        String sourceEvidenceDigest = EventBroker.digest(
                descriptor.getDescriptorDigest() + "|" + input.state + "|"
                        + input.observedAtElapsedMs + "|" + input.revision + "|"
                        + input.evidenceDigest);
        return new AdaptationResult(
                code,
                new Observation(
                        descriptor,
                        descriptor.getKeyPrefix(),
                        "cabin",
                        quality,
                        value,
                        input.observedAtElapsedMs,
                        nowElapsedMs,
                        descriptor.getMaximumAgeMs(),
                        TrustClass.PROCESS_LOCAL,
                        sourceEvidenceDigest));
    }
}
