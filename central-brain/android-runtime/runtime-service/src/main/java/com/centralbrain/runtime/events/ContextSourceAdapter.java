package com.centralbrain.runtime.events;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Normalizes one allowlisted Context source without connecting a production provider. */
public interface ContextSourceAdapter<I> {
    int SCHEMA_VERSION = 1;
    int INITIAL_SOURCE_COUNT = 3;
    int MAX_TEXT_CHARS = 64;

    enum SourceId {
        RUNTIME_HEALTH(
                "context-source.runtime-health.v1",
                SourceType.RUNTIME,
                "context.runtime.health",
                1_000,
                false),
        SIMULATED_VEHICLE_SIGNAL(
                "context-source.simulated-vehicle-signal.v1",
                SourceType.SIMULATION,
                "context.vehicle",
                5_000,
                true),
        TIME(
                "context-source.time.v1",
                SourceType.SYSTEM,
                "context.time.local-minute-of-day",
                60_000,
                false);

        private final String sourceId;
        private final SourceType sourceType;
        private final String keyPrefix;
        private final long maximumAgeMs;
        private final boolean simulated;

        SourceId(
                String sourceId,
                SourceType sourceType,
                String keyPrefix,
                long maximumAgeMs,
                boolean simulated) {
            this.sourceId = sourceId;
            this.sourceType = sourceType;
            this.keyPrefix = keyPrefix;
            this.maximumAgeMs = maximumAgeMs;
            this.simulated = simulated;
        }

        public String getSourceId() {
            return sourceId;
        }

        public SourceType getSourceType() {
            return sourceType;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public long getMaximumAgeMs() {
            return maximumAgeMs;
        }

        public boolean isSimulated() {
            return simulated;
        }
    }

    enum SourceType {
        RUNTIME,
        SIMULATION,
        SYSTEM
    }

    enum TrustClass {
        PROCESS_LOCAL,
        SIMULATED
    }

    enum ResultCode {
        ADAPTED,
        STALE,
        SOURCE_UNAVAILABLE,
        SOURCE_ERROR,
        SOURCE_CONFLICT,
        REJECTED_FUTURE,
        REJECTED_PROVENANCE,
        REJECTED_INPUT
    }

    Descriptor descriptor();

    AdaptationResult adapt(I input, long nowElapsedMs);

    static List<Descriptor> initialDescriptors() {
        List<Descriptor> descriptors = new ArrayList<>();
        for (SourceId sourceId : SourceId.values()) {
            descriptors.add(new Descriptor(sourceId));
        }
        descriptors.sort(Comparator.comparing(Descriptor::getCanonicalSourceId));
        return Collections.unmodifiableList(descriptors);
    }

    final class Descriptor {
        private final SourceId sourceId;
        private final String descriptorDigest;

        Descriptor(SourceId sourceId) {
            this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
            this.descriptorDigest = EventBroker.digest(
                    SCHEMA_VERSION + "|" + sourceId.name() + "|"
                            + sourceId.sourceId + "|" + sourceId.sourceType + "|"
                            + sourceId.keyPrefix + "|" + sourceId.maximumAgeMs + "|"
                            + sourceId.simulated + "|false");
        }

        public SourceId getSourceId() {
            return sourceId;
        }

        public String getCanonicalSourceId() {
            return sourceId.sourceId;
        }

        public SourceType getSourceType() {
            return sourceId.sourceType;
        }

        public String getKeyPrefix() {
            return sourceId.keyPrefix;
        }

        public long getMaximumAgeMs() {
            return sourceId.maximumAgeMs;
        }

        public boolean isSimulated() {
            return sourceId.simulated;
        }

        public boolean isProductionPublished() {
            return false;
        }

        public String getDescriptorDigest() {
            return descriptorDigest;
        }
    }

    /** Closed typed scalar union; arbitrary Object, Bundle, JSON and Parcelable are absent. */
    final class SourceValue {
        private final SignalValue.ScalarType scalarType;
        private final boolean booleanValue;
        private final long integerValue;
        private final double decimalValue;
        private final String textValue;

        private SourceValue(
                SignalValue.ScalarType scalarType,
                boolean booleanValue,
                long integerValue,
                double decimalValue,
                String textValue) {
            this.scalarType = Objects.requireNonNull(scalarType, "scalarType");
            this.booleanValue = booleanValue;
            this.integerValue = integerValue;
            this.decimalValue = decimalValue;
            this.textValue = textValue;
        }

        static SourceValue ofBoolean(boolean value) {
            return new SourceValue(SignalValue.ScalarType.BOOLEAN, value, 0, 0, "");
        }

        static SourceValue ofInteger(long value) {
            return new SourceValue(SignalValue.ScalarType.INTEGER, false, value, 0, "");
        }

        static SourceValue ofDecimal(double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("source decimal is not finite");
            }
            return new SourceValue(SignalValue.ScalarType.DECIMAL, false, 0, value, "");
        }

        static SourceValue ofText(String value) {
            if (value == null || value.isEmpty() || value.length() > MAX_TEXT_CHARS) {
                throw new IllegalArgumentException("source text is invalid");
            }
            for (int index = 0; index < value.length(); index++) {
                if (Character.isISOControl(value.charAt(index))) {
                    throw new IllegalArgumentException("source text contains control character");
                }
            }
            return new SourceValue(SignalValue.ScalarType.TEXT, false, 0, 0, value);
        }

        public SignalValue.ScalarType getScalarType() {
            return scalarType;
        }

        public boolean getBooleanValue() {
            requireType(SignalValue.ScalarType.BOOLEAN);
            return booleanValue;
        }

        public long getIntegerValue() {
            requireType(SignalValue.ScalarType.INTEGER);
            return integerValue;
        }

        public double getDecimalValue() {
            requireType(SignalValue.ScalarType.DECIMAL);
            return decimalValue;
        }

        public String getTextValue() {
            requireType(SignalValue.ScalarType.TEXT);
            return textValue;
        }

        String canonical() {
            switch (scalarType) {
                case BOOLEAN:
                    return scalarType + "|" + booleanValue;
                case INTEGER:
                    return scalarType + "|" + integerValue;
                case DECIMAL:
                    return scalarType + "|"
                            + Long.toHexString(Double.doubleToLongBits(decimalValue));
                case TEXT:
                    return scalarType + "|" + textValue;
                default:
                    throw new IllegalStateException("unknown source scalar type");
            }
        }

        private void requireType(SignalValue.ScalarType expected) {
            if (scalarType != expected) {
                throw new IllegalStateException("source scalar type mismatch");
            }
        }
    }

    final class Observation {
        private static final Pattern KEY =
                Pattern.compile("context[.][a-z0-9][a-z0-9._-]{2,127}");
        private static final Pattern AREA =
                Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+){0,7}");

        private final Descriptor descriptor;
        private final String contextKey;
        private final String area;
        private final SignalQuality quality;
        private final SourceValue value;
        private final long observedAtElapsedMs;
        private final long normalizedAtElapsedMs;
        private final long maximumAgeMs;
        private final TrustClass trustClass;
        private final String sourceEvidenceDigest;
        private final String observationDigest;

        Observation(
                Descriptor descriptor,
                String contextKey,
                String area,
                SignalQuality quality,
                SourceValue value,
                long observedAtElapsedMs,
                long normalizedAtElapsedMs,
                long maximumAgeMs,
                TrustClass trustClass,
                String sourceEvidenceDigest) {
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            if (contextKey == null || !KEY.matcher(contextKey).matches()) {
                throw new IllegalArgumentException("contextKey is invalid");
            }
            if (area == null || !AREA.matcher(area).matches()) {
                throw new IllegalArgumentException("area is invalid");
            }
            this.contextKey = contextKey;
            this.area = area;
            this.quality = Objects.requireNonNull(quality, "quality");
            if (quality.hasScalarValue() != (value != null)) {
                throw new IllegalArgumentException("quality and source value are inconsistent");
            }
            if (observedAtElapsedMs < 0 || normalizedAtElapsedMs < observedAtElapsedMs) {
                throw new IllegalArgumentException("source observation time is invalid");
            }
            if (maximumAgeMs < 1 || maximumAgeMs > descriptor.getMaximumAgeMs()) {
                throw new IllegalArgumentException("maximumAgeMs exceeds descriptor bound");
            }
            this.value = value;
            this.observedAtElapsedMs = observedAtElapsedMs;
            this.normalizedAtElapsedMs = normalizedAtElapsedMs;
            this.maximumAgeMs = maximumAgeMs;
            this.trustClass = Objects.requireNonNull(trustClass, "trustClass");
            if (descriptor.isSimulated() != (trustClass == TrustClass.SIMULATED)) {
                throw new IllegalArgumentException("source trust does not match descriptor");
            }
            this.sourceEvidenceDigest = EventBroker.requireDigest(
                    sourceEvidenceDigest,
                    "sourceEvidenceDigest");
            this.observationDigest = EventBroker.digest(canonical());
        }

        public Descriptor getDescriptor() {
            return descriptor;
        }

        public String getContextKey() {
            return contextKey;
        }

        public String getArea() {
            return area;
        }

        public SignalQuality getQuality() {
            return quality;
        }

        public SourceValue getValue() {
            return value;
        }

        public long getObservedAtElapsedMs() {
            return observedAtElapsedMs;
        }

        public long getNormalizedAtElapsedMs() {
            return normalizedAtElapsedMs;
        }

        public long getMaximumAgeMs() {
            return maximumAgeMs;
        }

        public long getAgeMs() {
            return normalizedAtElapsedMs - observedAtElapsedMs;
        }

        public TrustClass getTrustClass() {
            return trustClass;
        }

        public String getSourceEvidenceDigest() {
            return sourceEvidenceDigest;
        }

        public String getObservationDigest() {
            return observationDigest;
        }

        public boolean isDecisionUsable() {
            return quality == SignalQuality.VALID;
        }

        public boolean isProductionTrusted() {
            return false;
        }

        private String canonical() {
            return SCHEMA_VERSION + "|" + descriptor.getDescriptorDigest() + "|"
                    + contextKey + "|" + area + "|" + quality + "|"
                    + (value == null ? "none" : value.canonical()) + "|"
                    + observedAtElapsedMs + "|" + normalizedAtElapsedMs + "|"
                    + maximumAgeMs + "|" + trustClass + "|" + sourceEvidenceDigest;
        }
    }

    final class AdaptationResult {
        private final ResultCode code;
        private final Observation observation;

        AdaptationResult(ResultCode code, Observation observation) {
            this.code = Objects.requireNonNull(code, "code");
            if ((code == ResultCode.REJECTED_FUTURE
                    || code == ResultCode.REJECTED_PROVENANCE
                    || code == ResultCode.REJECTED_INPUT) && observation != null) {
                throw new IllegalArgumentException("rejected input cannot emit observation");
            }
            if ((code == ResultCode.ADAPTED
                    || code == ResultCode.STALE
                    || code == ResultCode.SOURCE_UNAVAILABLE
                    || code == ResultCode.SOURCE_ERROR
                    || code == ResultCode.SOURCE_CONFLICT) && observation == null) {
                throw new IllegalArgumentException("normalized result requires observation");
            }
            this.observation = observation;
        }

        public ResultCode getCode() {
            return code;
        }

        public Observation getObservation() {
            return observation;
        }

        public boolean isAvailable() {
            return code == ResultCode.ADAPTED;
        }

        public boolean isTriggerInputPublished() {
            return false;
        }

        public boolean isRuntimeWired() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }
    }

    final class ContractSnapshot {
        private ContractSnapshot() {
        }

        public static ContractSnapshot current() {
            if (initialDescriptors().size() != INITIAL_SOURCE_COUNT) {
                throw new IllegalStateException("initial Context source catalog is inconsistent");
            }
            return new ContractSnapshot();
        }

        public int getSourceCount() {
            return INITIAL_SOURCE_COUNT;
        }

        public boolean isAllowlistDefined() {
            return true;
        }

        public boolean isFreshnessQualityNormalizationDefined() {
            return true;
        }

        public boolean isProductionRegistryPublished() {
            return false;
        }

        public boolean isRuntimeWired() {
            return false;
        }

        public boolean isTriggerEngineWired() {
            return false;
        }

        public boolean isVehiclePropertyMappingConfigured() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }
    }
}
