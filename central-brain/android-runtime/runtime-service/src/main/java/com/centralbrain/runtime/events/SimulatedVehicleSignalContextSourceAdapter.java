package com.centralbrain.runtime.events;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.Locale;
import java.util.Set;

/** Adapts an existing canonical SIMULATED signal without reading any vehicle provider. */
public final class SimulatedVehicleSignalContextSourceAdapter
        implements ContextSourceAdapter<SignalValue> {
    private static final Set<String> ALLOWED_GEAR_VALUES =
            Set.of("P", "R", "N", "D", "S", "L", "M", "UNKNOWN");
    private final Descriptor descriptor = new Descriptor(SourceId.SIMULATED_VEHICLE_SIGNAL);

    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public AdaptationResult adapt(SignalValue input, long nowElapsedMs) {
        if (input == null || nowElapsedMs < 0) {
            return new AdaptationResult(ResultCode.REJECTED_INPUT, null);
        }
        if (!input.getSource().isSimulated()) {
            return new AdaptationResult(ResultCode.REJECTED_PROVENANCE, null);
        }
        long observedAt = input.getTimestamp().getReceivedElapsedRealtimeMs();
        if (nowElapsedMs < observedAt) {
            return new AdaptationResult(ResultCode.REJECTED_FUTURE, null);
        }
        long maximumAgeMs = input.getPath().getMaximumAgeMs();
        boolean fresh = nowElapsedMs - observedAt <= maximumAgeMs;
        SignalQuality normalizedQuality = input.getQuality();
        if (normalizedQuality == SignalQuality.VALID && !fresh) {
            normalizedQuality = SignalQuality.STALE;
        } else if (normalizedQuality == SignalQuality.STALE && fresh) {
            return new AdaptationResult(ResultCode.REJECTED_INPUT, null);
        }

        if (input.hasValue()
                && input.getScalarType() == SignalValue.ScalarType.TEXT
                && (input.getPath() != VehicleSignalPath.CURRENT_GEAR
                || !ALLOWED_GEAR_VALUES.contains(input.getTextValue()))) {
            return new AdaptationResult(ResultCode.REJECTED_INPUT, null);
        }
        SourceValue value = input.hasValue() ? sourceValue(input) : null;
        ResultCode code = resultCode(normalizedQuality);
        String contextKey = descriptor.getKeyPrefix() + "."
                + input.getPath().name().toLowerCase(Locale.ROOT).replace('_', '-');
        String sourceEvidenceDigest = EventBroker.digest(
                descriptor.getDescriptorDigest() + "|"
                        + input.getPath().name() + "|" + input.getArea() + "|"
                        + input.getUnit() + "|" + input.getQuality() + "|"
                        + (value == null ? "none" : value.canonical()) + "|"
                        + input.getTimestamp().getSourceEpochMs() + "|" + observedAt + "|"
                        + input.getRevision());
        return new AdaptationResult(
                code,
                new Observation(
                        descriptor,
                        contextKey,
                        input.getArea(),
                        normalizedQuality,
                        value,
                        observedAt,
                        nowElapsedMs,
                        maximumAgeMs,
                        TrustClass.SIMULATED,
                        sourceEvidenceDigest));
    }

    private static SourceValue sourceValue(SignalValue input) {
        switch (input.getScalarType()) {
            case BOOLEAN:
                return SourceValue.ofBoolean(input.getBooleanValue());
            case INTEGER:
                return SourceValue.ofInteger(input.getIntegerValue());
            case DECIMAL:
                return SourceValue.ofDecimal(input.getDecimalValue());
            case TEXT:
                return SourceValue.ofText(input.getTextValue());
            default:
                throw new IllegalStateException("unknown vehicle scalar type");
        }
    }

    private static ResultCode resultCode(SignalQuality quality) {
        switch (quality) {
            case VALID:
                return ResultCode.ADAPTED;
            case STALE:
                return ResultCode.STALE;
            case UNAVAILABLE:
                return ResultCode.SOURCE_UNAVAILABLE;
            case ERROR:
                return ResultCode.SOURCE_ERROR;
            case CONFLICT:
                return ResultCode.SOURCE_CONFLICT;
            default:
                throw new IllegalStateException("unknown vehicle signal quality");
        }
    }
}
