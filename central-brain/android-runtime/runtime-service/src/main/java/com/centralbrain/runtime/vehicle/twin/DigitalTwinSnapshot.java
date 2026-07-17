package com.centralbrain.runtime.vehicle.twin;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Atomic immutable view of desired and reported Digital Twin state. */
public final class DigitalTwinSnapshot {
    public enum ReconciliationState {
        NO_DESIRED,
        DESIRED_EXPIRED,
        PENDING_REPORTED,
        REPORTED_STALE,
        REPORTED_UNAVAILABLE,
        MATCHED,
        MISMATCH
    }

    static final class Key {
        private final VehicleSignalPath path;
        private final String area;

        Key(VehicleSignalPath path, String area) {
            this.path = Objects.requireNonNull(path, "path");
            this.area = Objects.requireNonNull(area, "area");
            if (!path.getAreas().contains(area)) {
                throw new IllegalArgumentException(
                        "CB_VEHICLE_TWIN: area is not allowed for path");
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key) other;
            return path == key.path && area.equals(key.area);
        }

        @Override
        public int hashCode() {
            return 31 * path.hashCode() + area.hashCode();
        }
    }

    private final long revision;
    private final long capturedElapsedRealtimeMs;
    private final Map<Key, ReportedStateRecord> reported;
    private final Map<Key, DesiredStateRecord> desired;
    private final List<ReportedStateRecord> reportedRecords;
    private final List<DesiredStateRecord> desiredRecords;

    DigitalTwinSnapshot(
            long revision,
            long capturedElapsedRealtimeMs,
            Map<Key, ReportedStateRecord> reported,
            Map<Key, DesiredStateRecord> desired) {
        if (revision < 0 || capturedElapsedRealtimeMs < 0) {
            throw new IllegalArgumentException("CB_VEHICLE_TWIN: snapshot metadata is invalid");
        }
        this.revision = revision;
        this.capturedElapsedRealtimeMs = capturedElapsedRealtimeMs;
        Map<Key, ReportedStateRecord> reportedCopy = new LinkedHashMap<>();
        for (Map.Entry<Key, ReportedStateRecord> entry : reported.entrySet()) {
            reportedCopy.put(entry.getKey(), entry.getValue().at(capturedElapsedRealtimeMs));
        }
        this.reported = Collections.unmodifiableMap(reportedCopy);
        this.desired = Collections.unmodifiableMap(new LinkedHashMap<>(desired));
        this.reportedRecords = Collections.unmodifiableList(
                new ArrayList<>(reportedCopy.values()));
        this.desiredRecords = Collections.unmodifiableList(
                new ArrayList<>(this.desired.values()));
    }

    public long getRevision() {
        return revision;
    }

    public long getCapturedElapsedRealtimeMs() {
        return capturedElapsedRealtimeMs;
    }

    public List<ReportedStateRecord> getReportedRecords() {
        return reportedRecords;
    }

    public List<DesiredStateRecord> getDesiredRecords() {
        return desiredRecords;
    }

    public Optional<ReportedStateRecord> reported(VehicleSignalPath path, String area) {
        return Optional.ofNullable(reported.get(new Key(path, area)));
    }

    public Optional<DesiredStateRecord> desired(VehicleSignalPath path, String area) {
        DesiredStateRecord record = desired.get(new Key(path, area));
        if (record == null || record.isExpired(capturedElapsedRealtimeMs)) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    public Optional<DesiredStateRecord> desiredIncludingExpired(
            VehicleSignalPath path,
            String area) {
        return Optional.ofNullable(desired.get(new Key(path, area)));
    }

    public ReconciliationState reconcile(VehicleSignalPath path, String area) {
        Key key = new Key(path, area);
        DesiredStateRecord desiredRecord = desired.get(key);
        if (desiredRecord == null) {
            return ReconciliationState.NO_DESIRED;
        }
        if (desiredRecord.isExpired(capturedElapsedRealtimeMs)) {
            return ReconciliationState.DESIRED_EXPIRED;
        }
        ReportedStateRecord reportedRecord = reported.get(key);
        if (reportedRecord == null) {
            return ReconciliationState.PENDING_REPORTED;
        }
        SignalQuality quality = reportedRecord.getEffectiveQuality();
        if (quality == SignalQuality.STALE) {
            return ReconciliationState.REPORTED_STALE;
        }
        if (quality != SignalQuality.VALID) {
            return ReconciliationState.REPORTED_UNAVAILABLE;
        }
        return desiredRecord.matches(reportedRecord.getValue())
                ? ReconciliationState.MATCHED
                : ReconciliationState.MISMATCH;
    }
}
