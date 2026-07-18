package com.centralbrain.runtime.events;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;

/** Deterministic time adapter over an injected sample; it never reads a system clock directly. */
public final class TimeContextSourceAdapter
        implements ContextSourceAdapter<TimeContextSourceAdapter.TimeSample> {
    private static final long MINUTES_PER_DAY = 24L * 60L;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MILLIS_PER_DAY = MINUTES_PER_DAY * MILLIS_PER_MINUTE;

    public static final class TimeSample {
        private final long epochMs;
        private final long observedAtElapsedMs;
        private final int utcOffsetMinutes;
        private final String evidenceDigest;

        public TimeSample(
                long epochMs,
                long observedAtElapsedMs,
                int utcOffsetMinutes,
                String evidenceDigest) {
            if (epochMs <= 0 || observedAtElapsedMs < 0) {
                throw new IllegalArgumentException("time sample is invalid");
            }
            if (utcOffsetMinutes < -14 * 60 || utcOffsetMinutes > 14 * 60) {
                throw new IllegalArgumentException("UTC offset is invalid");
            }
            this.epochMs = epochMs;
            this.observedAtElapsedMs = observedAtElapsedMs;
            this.utcOffsetMinutes = utcOffsetMinutes;
            this.evidenceDigest = EventBroker.requireDigest(evidenceDigest, "evidenceDigest");
        }

        public long getEpochMs() {
            return epochMs;
        }

        public long getObservedAtElapsedMs() {
            return observedAtElapsedMs;
        }

        public int getUtcOffsetMinutes() {
            return utcOffsetMinutes;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }
    }

    private final Descriptor descriptor = new Descriptor(SourceId.TIME);

    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public AdaptationResult adapt(TimeSample input, long nowElapsedMs) {
        if (input == null || nowElapsedMs < 0) {
            return new AdaptationResult(ResultCode.REJECTED_INPUT, null);
        }
        if (nowElapsedMs < input.observedAtElapsedMs) {
            return new AdaptationResult(ResultCode.REJECTED_FUTURE, null);
        }
        long ageMs = nowElapsedMs - input.observedAtElapsedMs;
        boolean stale = ageMs > descriptor.getMaximumAgeMs();
        long offsetMs = input.utcOffsetMinutes * MILLIS_PER_MINUTE;
        long minuteOfDay = Math.floorMod(input.epochMs, MILLIS_PER_DAY)
                / MILLIS_PER_MINUTE;
        minuteOfDay = Math.floorMod(
                minuteOfDay + input.utcOffsetMinutes,
                MINUTES_PER_DAY);
        String sourceEvidenceDigest = EventBroker.digest(
                descriptor.getDescriptorDigest() + "|" + input.epochMs + "|"
                        + input.observedAtElapsedMs + "|" + offsetMs + "|"
                        + input.evidenceDigest);
        return new AdaptationResult(
                stale ? ResultCode.STALE : ResultCode.ADAPTED,
                new Observation(
                        descriptor,
                        descriptor.getKeyPrefix(),
                        "cabin",
                        stale ? SignalQuality.STALE : SignalQuality.VALID,
                        SourceValue.ofInteger(minuteOfDay),
                        input.observedAtElapsedMs,
                        nowElapsedMs,
                        descriptor.getMaximumAgeMs(),
                        TrustClass.PROCESS_LOCAL,
                        sourceEvidenceDigest));
    }
}
