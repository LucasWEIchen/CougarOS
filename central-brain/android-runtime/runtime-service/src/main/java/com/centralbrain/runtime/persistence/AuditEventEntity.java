package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "audit_event",
        indices = @Index(name = "index_audit_event_id", value = "event_id", unique = true))
public final class AuditEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long sequence;

    @NonNull
    @ColumnInfo(name = "event_id")
    public String eventId = "";

    @NonNull
    @ColumnInfo(name = "event_type")
    public String eventType = "";

    @NonNull
    @ColumnInfo(name = "subject_id")
    public String subjectId = "";

    @NonNull
    @ColumnInfo(name = "owner_fingerprint")
    public String ownerFingerprint = "";

    @NonNull
    public String outcome = "";

    @NonNull
    @ColumnInfo(name = "detail_digest")
    public String detailDigest = "";

    @ColumnInfo(name = "observed_at_wall_ms")
    public long observedAtWallMs;
}
