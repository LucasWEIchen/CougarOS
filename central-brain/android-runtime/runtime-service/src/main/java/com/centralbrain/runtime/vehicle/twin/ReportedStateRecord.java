package com.centralbrain.runtime.vehicle.twin;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.Objects;

/** Immutable reported state plus Digital Twin store revision and snapshot-time quality. */
public final class ReportedStateRecord {
    private final SignalValue value;
    private final long storeRevision;
    private final long expiresElapsedRealtimeMs;
    private final SignalQuality effectiveQuality;

    static ReportedStateRecord accepted(
            SignalValue value,
            long storeRevision,
            long nowElapsedRealtimeMs) {
        Objects.requireNonNull(value, "value").validateFreshness(nowElapsedRealtimeMs);
        if (storeRevision < 1) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_TWIN: reported store revision must be positive");
        }
        long received = value.getTimestamp().getReceivedElapsedRealtimeMs();
        long maximumAge = value.getPath().getMaximumAgeMs();
        long expires = received > Long.MAX_VALUE - maximumAge
                ? Long.MAX_VALUE
                : received + maximumAge;
        return new ReportedStateRecord(
                value,
                storeRevision,
                expires,
                value.getQuality());
    }

    private ReportedStateRecord(
            SignalValue value,
            long storeRevision,
            long expiresElapsedRealtimeMs,
            SignalQuality effectiveQuality) {
        this.value = value;
        this.storeRevision = storeRevision;
        this.expiresElapsedRealtimeMs = expiresElapsedRealtimeMs;
        this.effectiveQuality = effectiveQuality;
    }

    ReportedStateRecord at(long nowElapsedRealtimeMs) {
        value.getTimestamp().ageMs(nowElapsedRealtimeMs);
        if (effectiveQuality == SignalQuality.VALID
                && nowElapsedRealtimeMs > expiresElapsedRealtimeMs) {
            return new ReportedStateRecord(
                    value,
                    storeRevision,
                    expiresElapsedRealtimeMs,
                    SignalQuality.STALE);
        }
        return this;
    }

    public SignalValue getValue() {
        return value;
    }

    public VehicleSignalPath getPath() {
        return value.getPath();
    }

    public String getArea() {
        return value.getArea();
    }

    public long getStoreRevision() {
        return storeRevision;
    }

    public long getExpiresElapsedRealtimeMs() {
        return expiresElapsedRealtimeMs;
    }

    public SignalQuality getEffectiveQuality() {
        return effectiveQuality;
    }

    public boolean isUsableForDecision() {
        return effectiveQuality == SignalQuality.VALID;
    }
}
