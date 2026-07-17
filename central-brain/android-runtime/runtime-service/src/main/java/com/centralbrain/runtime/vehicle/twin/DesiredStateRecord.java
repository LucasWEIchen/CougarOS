package com.centralbrain.runtime.vehicle.twin;

import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.Objects;

/** Immutable desired vehicle state. A store assigns its monotonic store revision. */
public final class DesiredStateRecord {
    public static final long MAX_TTL_MS = 15 * 60 * 1_000L;
    public static final int MAX_TEXT_CHARS = 64;

    private final VehicleSignalPath path;
    private final boolean booleanValue;
    private final long integerValue;
    private final double decimalValue;
    private final String textValue;
    private final String unit;
    private final String area;
    private final long requestedElapsedRealtimeMs;
    private final long expiresElapsedRealtimeMs;
    private final long storeRevision;

    public static DesiredStateRecord ofBoolean(
            VehicleSignalPath path,
            boolean value,
            String unit,
            String area,
            long requestedElapsedRealtimeMs,
            long expiresElapsedRealtimeMs) {
        return valued(
                path,
                SignalValue.ScalarType.BOOLEAN,
                value,
                0,
                0,
                "",
                unit,
                area,
                requestedElapsedRealtimeMs,
                expiresElapsedRealtimeMs);
    }

    public static DesiredStateRecord ofInteger(
            VehicleSignalPath path,
            long value,
            String unit,
            String area,
            long requestedElapsedRealtimeMs,
            long expiresElapsedRealtimeMs) {
        return valued(
                path,
                SignalValue.ScalarType.INTEGER,
                false,
                value,
                0,
                "",
                unit,
                area,
                requestedElapsedRealtimeMs,
                expiresElapsedRealtimeMs);
    }

    public static DesiredStateRecord ofDecimal(
            VehicleSignalPath path,
            double value,
            String unit,
            String area,
            long requestedElapsedRealtimeMs,
            long expiresElapsedRealtimeMs) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_TWIN: desired decimal is not finite");
        }
        return valued(
                path,
                SignalValue.ScalarType.DECIMAL,
                false,
                0,
                value,
                "",
                unit,
                area,
                requestedElapsedRealtimeMs,
                expiresElapsedRealtimeMs);
    }

    public static DesiredStateRecord ofText(
            VehicleSignalPath path,
            String value,
            String unit,
            String area,
            long requestedElapsedRealtimeMs,
            long expiresElapsedRealtimeMs) {
        validateText(value);
        return valued(
                path,
                SignalValue.ScalarType.TEXT,
                false,
                0,
                0,
                value,
                unit,
                area,
                requestedElapsedRealtimeMs,
                expiresElapsedRealtimeMs);
    }

    private static DesiredStateRecord valued(
            VehicleSignalPath path,
            SignalValue.ScalarType type,
            boolean booleanValue,
            long integerValue,
            double decimalValue,
            String textValue,
            String unit,
            String area,
            long requestedElapsedRealtimeMs,
            long expiresElapsedRealtimeMs) {
        Objects.requireNonNull(path, "path");
        if (path.getScalarType() != type) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_TWIN: desired scalar type does not match path");
        }
        path.validateUnitAndArea(
                Objects.requireNonNull(unit, "unit"),
                Objects.requireNonNull(area, "area"));
        validateTimes(requestedElapsedRealtimeMs, expiresElapsedRealtimeMs);
        return new DesiredStateRecord(
                path,
                booleanValue,
                integerValue,
                decimalValue,
                textValue,
                unit,
                area,
                requestedElapsedRealtimeMs,
                expiresElapsedRealtimeMs,
                0);
    }

    private DesiredStateRecord(
            VehicleSignalPath path,
            boolean booleanValue,
            long integerValue,
            double decimalValue,
            String textValue,
            String unit,
            String area,
            long requestedElapsedRealtimeMs,
            long expiresElapsedRealtimeMs,
            long storeRevision) {
        this.path = path;
        this.booleanValue = booleanValue;
        this.integerValue = integerValue;
        this.decimalValue = decimalValue;
        this.textValue = textValue;
        this.unit = unit;
        this.area = area;
        this.requestedElapsedRealtimeMs = requestedElapsedRealtimeMs;
        this.expiresElapsedRealtimeMs = expiresElapsedRealtimeMs;
        this.storeRevision = storeRevision;
    }

    DesiredStateRecord assignStoreRevision(long revision) {
        if (storeRevision != 0 || revision < 1) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_TWIN: desired store revision assignment is invalid");
        }
        return new DesiredStateRecord(
                path,
                booleanValue,
                integerValue,
                decimalValue,
                textValue,
                unit,
                area,
                requestedElapsedRealtimeMs,
                expiresElapsedRealtimeMs,
                revision);
    }

    boolean isTemplate() {
        return storeRevision == 0;
    }

    boolean hasSameRequest(DesiredStateRecord other) {
        if (other == null
                || path != other.path
                || !area.equals(other.area)
                || !unit.equals(other.unit)
                || requestedElapsedRealtimeMs != other.requestedElapsedRealtimeMs
                || expiresElapsedRealtimeMs != other.expiresElapsedRealtimeMs) {
            return false;
        }
        switch (path.getScalarType()) {
            case BOOLEAN:
                return booleanValue == other.booleanValue;
            case INTEGER:
                return integerValue == other.integerValue;
            case DECIMAL:
                return Double.compare(decimalValue, other.decimalValue) == 0;
            case TEXT:
                return textValue.equals(other.textValue);
            default:
                throw new IllegalStateException("CB_VEHICLE_TWIN: unknown desired scalar type");
        }
    }

    public boolean matches(SignalValue reported) {
        if (reported == null
                || !reported.hasValue()
                || path != reported.getPath()
                || !area.equals(reported.getArea())
                || !unit.equals(reported.getUnit())) {
            return false;
        }
        switch (path.getScalarType()) {
            case BOOLEAN:
                return booleanValue == reported.getBooleanValue();
            case INTEGER:
                return integerValue == reported.getIntegerValue();
            case DECIMAL:
                return Double.compare(decimalValue, reported.getDecimalValue()) == 0;
            case TEXT:
                return textValue.equals(reported.getTextValue());
            default:
                throw new IllegalStateException("CB_VEHICLE_TWIN: unknown desired scalar type");
        }
    }

    public boolean isExpired(long nowElapsedRealtimeMs) {
        if (nowElapsedRealtimeMs < 0) {
            throw new IllegalArgumentException("CB_VEHICLE_TWIN: desired clock is negative");
        }
        return nowElapsedRealtimeMs >= expiresElapsedRealtimeMs;
    }

    public VehicleSignalPath getPath() {
        return path;
    }

    public SignalValue.ScalarType getScalarType() {
        return path.getScalarType();
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

    public String getUnit() {
        return unit;
    }

    public String getArea() {
        return area;
    }

    public long getRequestedElapsedRealtimeMs() {
        return requestedElapsedRealtimeMs;
    }

    public long getExpiresElapsedRealtimeMs() {
        return expiresElapsedRealtimeMs;
    }

    public long getStoreRevision() {
        return storeRevision;
    }

    private void requireType(SignalValue.ScalarType expected) {
        if (path.getScalarType() != expected) {
            throw new IllegalStateException(
                    "CB_VEHICLE_TWIN: desired scalar accessor type mismatch");
        }
    }

    private static void validateTimes(long requested, long expires) {
        if (requested < 0 || expires <= requested || expires - requested > MAX_TTL_MS) {
            throw new IllegalArgumentException("CB_VEHICLE_TWIN: desired TTL is invalid");
        }
    }

    private static void validateText(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("CB_VEHICLE_TWIN: desired text is invalid");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_TWIN: desired text contains a control character");
            }
        }
    }
}
