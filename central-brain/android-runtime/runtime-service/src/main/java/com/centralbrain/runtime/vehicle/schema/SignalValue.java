package com.centralbrain.runtime.vehicle.schema;

import java.util.Objects;

/** Immutable typed scalar observation; arbitrary Object and JSON payloads are forbidden. */
public final class SignalValue {
    public static final int MAX_TEXT_CHARS = 64;

    public enum ScalarType {
        BOOLEAN,
        INTEGER,
        DECIMAL,
        TEXT
    }

    private final VehicleSignalPath path;
    private final boolean hasValue;
    private final boolean booleanValue;
    private final long integerValue;
    private final double decimalValue;
    private final String textValue;
    private final String unit;
    private final String area;
    private final SignalTimestamp timestamp;
    private final SignalQuality quality;
    private final SignalSource source;
    private final long revision;

    public static SignalValue ofBoolean(
            VehicleSignalPath path,
            boolean value,
            String unit,
            String area,
            SignalTimestamp timestamp,
            SignalQuality quality,
            SignalSource source,
            long revision) {
        return valued(
                path, ScalarType.BOOLEAN, value, 0, 0, "", unit, area,
                timestamp, quality, source, revision);
    }

    public static SignalValue ofInteger(
            VehicleSignalPath path,
            long value,
            String unit,
            String area,
            SignalTimestamp timestamp,
            SignalQuality quality,
            SignalSource source,
            long revision) {
        return valued(
                path, ScalarType.INTEGER, false, value, 0, "", unit, area,
                timestamp, quality, source, revision);
    }

    public static SignalValue ofDecimal(
            VehicleSignalPath path,
            double value,
            String unit,
            String area,
            SignalTimestamp timestamp,
            SignalQuality quality,
            SignalSource source,
            long revision) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: decimal value is not finite");
        }
        return valued(
                path, ScalarType.DECIMAL, false, 0, value, "", unit, area,
                timestamp, quality, source, revision);
    }

    public static SignalValue ofText(
            VehicleSignalPath path,
            String value,
            String unit,
            String area,
            SignalTimestamp timestamp,
            SignalQuality quality,
            SignalSource source,
            long revision) {
        if (value == null || value.isEmpty() || value.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: text value is invalid");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_SIGNAL: text value contains a control character");
            }
        }
        return valued(
                path, ScalarType.TEXT, false, 0, 0, value, unit, area,
                timestamp, quality, source, revision);
    }

    public static SignalValue withoutValue(
            VehicleSignalPath path,
            String unit,
            String area,
            SignalTimestamp timestamp,
            SignalQuality quality,
            SignalSource source,
            long revision) {
        if (quality == null || quality.hasScalarValue()) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_SIGNAL: value-bearing quality requires a typed scalar");
        }
        return new SignalValue(
                path, false, false, 0, 0, "", unit, area,
                timestamp, quality, source, revision);
    }

    private static SignalValue valued(
            VehicleSignalPath path,
            ScalarType scalarType,
            boolean booleanValue,
            long integerValue,
            double decimalValue,
            String textValue,
            String unit,
            String area,
            SignalTimestamp timestamp,
            SignalQuality quality,
            SignalSource source,
            long revision) {
        Objects.requireNonNull(path, "path");
        if (path.getScalarType() != scalarType) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: scalar type does not match path");
        }
        if (quality == null || !quality.hasScalarValue()) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_SIGNAL: unavailable quality cannot carry a scalar");
        }
        return new SignalValue(
                path, true, booleanValue, integerValue, decimalValue, textValue,
                unit, area, timestamp, quality, source, revision);
    }

    private SignalValue(
            VehicleSignalPath path,
            boolean hasValue,
            boolean booleanValue,
            long integerValue,
            double decimalValue,
            String textValue,
            String unit,
            String area,
            SignalTimestamp timestamp,
            SignalQuality quality,
            SignalSource source,
            long revision) {
        this.path = Objects.requireNonNull(path, "path");
        path.validateUnitAndArea(
                Objects.requireNonNull(unit, "unit"),
                Objects.requireNonNull(area, "area"));
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.source = Objects.requireNonNull(source, "source");
        if (revision < 1) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: revision must be positive");
        }
        this.hasValue = hasValue;
        this.booleanValue = booleanValue;
        this.integerValue = integerValue;
        this.decimalValue = decimalValue;
        this.textValue = textValue;
        this.unit = unit;
        this.area = area;
        this.revision = revision;
    }

    public void validateFreshness(long nowElapsedRealtimeMs) {
        boolean fresh = timestamp.isFresh(nowElapsedRealtimeMs, path.getMaximumAgeMs());
        if (quality == SignalQuality.VALID && !fresh) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: stale value is marked VALID");
        }
        if (quality == SignalQuality.STALE && fresh) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: fresh value is marked STALE");
        }
    }

    public VehicleSignalPath getPath() {
        return path;
    }

    public ScalarType getScalarType() {
        return path.getScalarType();
    }

    public boolean hasValue() {
        return hasValue;
    }

    public boolean getBooleanValue() {
        requireType(ScalarType.BOOLEAN);
        return booleanValue;
    }

    public long getIntegerValue() {
        requireType(ScalarType.INTEGER);
        return integerValue;
    }

    public double getDecimalValue() {
        requireType(ScalarType.DECIMAL);
        return decimalValue;
    }

    public String getTextValue() {
        requireType(ScalarType.TEXT);
        return textValue;
    }

    public String getUnit() {
        return unit;
    }

    public String getArea() {
        return area;
    }

    public SignalTimestamp getTimestamp() {
        return timestamp;
    }

    public SignalQuality getQuality() {
        return quality;
    }

    public SignalSource getSource() {
        return source;
    }

    public long getRevision() {
        return revision;
    }

    private void requireType(ScalarType expected) {
        if (!hasValue || path.getScalarType() != expected) {
            throw new IllegalStateException("CB_VEHICLE_SIGNAL: scalar accessor type mismatch");
        }
    }
}
