package com.centralbrain.runtime.vehicle.twin;

import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Thread-safe in-process desired/reported store. It owns no vehicle adapter or persistence. */
public final class VehicleDigitalTwinStore {
    private final Map<DigitalTwinSnapshot.Key, ReportedStateRecord> reported =
            new LinkedHashMap<>();
    private final Map<DigitalTwinSnapshot.Key, DesiredStateRecord> desired =
            new LinkedHashMap<>();
    private long revision;

    public synchronized long getRevision() {
        return revision;
    }

    public synchronized long updateReported(
            SignalValue value,
            long nowElapsedRealtimeMs) {
        Objects.requireNonNull(value, "value")
                .getTimestamp()
                .ageMs(nowElapsedRealtimeMs);
        DigitalTwinSnapshot.Key key = new DigitalTwinSnapshot.Key(
                value.getPath(),
                value.getArea());
        ReportedStateRecord current = reported.get(key);
        if (current != null) {
            SignalValue currentValue = current.getValue();
            long incomingSourceTime = value.getTimestamp().getSourceEpochMs();
            long currentSourceTime = currentValue.getTimestamp().getSourceEpochMs();
            long incomingReceiveTime = value.getTimestamp().getReceivedElapsedRealtimeMs();
            long currentReceiveTime = currentValue.getTimestamp().getReceivedElapsedRealtimeMs();
            if (sameSignalValue(currentValue, value)) {
                return current.getStoreRevision();
            }
            if (incomingSourceTime < currentSourceTime
                    || incomingReceiveTime < currentReceiveTime
                    || (incomingSourceTime == currentSourceTime
                            && incomingReceiveTime == currentReceiveTime)) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_TWIN: stale or conflicting reported state");
            }
        }
        value.validateFreshness(nowElapsedRealtimeMs);
        long newRevision = nextRevision();
        reported.put(
                key,
                ReportedStateRecord.accepted(value, newRevision, nowElapsedRealtimeMs));
        return newRevision;
    }

    public synchronized long setDesired(
            DesiredStateRecord record,
            long nowElapsedRealtimeMs) {
        validateDesiredTemplate(record, nowElapsedRealtimeMs);
        DigitalTwinSnapshot.Key key = new DigitalTwinSnapshot.Key(
                record.getPath(),
                record.getArea());
        DesiredStateRecord current = desired.get(key);
        if (current != null && current.hasSameRequest(record)) {
            return current.getStoreRevision();
        }
        long newRevision = nextRevision();
        desired.put(key, record.assignStoreRevision(newRevision));
        return newRevision;
    }

    public synchronized boolean compareAndSetDesired(
            long expectedStoreRevision,
            DesiredStateRecord record,
            long nowElapsedRealtimeMs) {
        if (expectedStoreRevision < 0) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_TWIN: expected desired revision is negative");
        }
        validateDesiredTemplate(record, nowElapsedRealtimeMs);
        DigitalTwinSnapshot.Key key = new DigitalTwinSnapshot.Key(
                record.getPath(),
                record.getArea());
        DesiredStateRecord current = desired.get(key);
        long currentRevision = current == null ? 0 : current.getStoreRevision();
        if (currentRevision != expectedStoreRevision) {
            return false;
        }
        long newRevision = nextRevision();
        desired.put(key, record.assignStoreRevision(newRevision));
        return true;
    }

    public synchronized boolean clearDesired(
            VehicleSignalPath path,
            String area,
            long expectedStoreRevision) {
        DigitalTwinSnapshot.Key key = new DigitalTwinSnapshot.Key(path, area);
        DesiredStateRecord current = desired.get(key);
        if (current == null || current.getStoreRevision() != expectedStoreRevision) {
            return false;
        }
        nextRevision();
        desired.remove(key);
        return true;
    }

    public synchronized Optional<ReportedStateRecord> reported(
            VehicleSignalPath path,
            String area,
            long nowElapsedRealtimeMs) {
        ReportedStateRecord record = reported.get(new DigitalTwinSnapshot.Key(path, area));
        return record == null ? Optional.empty() : Optional.of(record.at(nowElapsedRealtimeMs));
    }

    public synchronized Optional<DesiredStateRecord> desired(
            VehicleSignalPath path,
            String area,
            long nowElapsedRealtimeMs) {
        DesiredStateRecord record = desired.get(new DigitalTwinSnapshot.Key(path, area));
        if (record == null || record.isExpired(nowElapsedRealtimeMs)) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    public synchronized DigitalTwinSnapshot snapshot(
            Set<VehicleSignalPath> paths,
            long nowElapsedRealtimeMs) {
        Objects.requireNonNull(paths, "paths");
        if (nowElapsedRealtimeMs < 0) {
            throw new IllegalArgumentException("CB_VEHICLE_TWIN: snapshot request is invalid");
        }
        for (VehicleSignalPath path : paths) {
            if (path == null) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_TWIN: snapshot path is null");
            }
        }
        Map<DigitalTwinSnapshot.Key, ReportedStateRecord> reportedCopy = new LinkedHashMap<>();
        Map<DigitalTwinSnapshot.Key, DesiredStateRecord> desiredCopy = new LinkedHashMap<>();
        for (Map.Entry<DigitalTwinSnapshot.Key, ReportedStateRecord> entry : reported.entrySet()) {
            if (paths.contains(entry.getValue().getPath())) {
                reportedCopy.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<DigitalTwinSnapshot.Key, DesiredStateRecord> entry : desired.entrySet()) {
            if (paths.contains(entry.getValue().getPath())) {
                desiredCopy.put(entry.getKey(), entry.getValue());
            }
        }
        return new DigitalTwinSnapshot(
                revision,
                nowElapsedRealtimeMs,
                reportedCopy,
                desiredCopy);
    }

    private void validateDesiredTemplate(
            DesiredStateRecord record,
            long nowElapsedRealtimeMs) {
        Objects.requireNonNull(record, "record");
        if (!record.isTemplate()) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_TWIN: desired record is already store-owned");
        }
        if (record.isExpired(nowElapsedRealtimeMs)) {
            throw new IllegalArgumentException("CB_VEHICLE_TWIN: desired record is expired");
        }
    }

    private long nextRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("CB_VEHICLE_TWIN: store revision exhausted");
        }
        revision++;
        return revision;
    }

    private static boolean sameSignalValue(SignalValue left, SignalValue right) {
        if (left.getPath() != right.getPath()
                || !left.getArea().equals(right.getArea())
                || !left.getUnit().equals(right.getUnit())
                || left.getSource() != right.getSource()
                || left.getQuality() != right.getQuality()
                || left.getRevision() != right.getRevision()
                || left.hasValue() != right.hasValue()
                || left.getTimestamp().getSourceEpochMs()
                        != right.getTimestamp().getSourceEpochMs()
                || left.getTimestamp().getReceivedElapsedRealtimeMs()
                        != right.getTimestamp().getReceivedElapsedRealtimeMs()) {
            return false;
        }
        if (!left.hasValue()) {
            return true;
        }
        switch (left.getScalarType()) {
            case BOOLEAN:
                return left.getBooleanValue() == right.getBooleanValue();
            case INTEGER:
                return left.getIntegerValue() == right.getIntegerValue();
            case DECIMAL:
                return Double.compare(left.getDecimalValue(), right.getDecimalValue()) == 0;
            case TEXT:
                return left.getTextValue().equals(right.getTextValue());
            default:
                throw new IllegalStateException("CB_VEHICLE_TWIN: unknown reported scalar type");
        }
    }
}
