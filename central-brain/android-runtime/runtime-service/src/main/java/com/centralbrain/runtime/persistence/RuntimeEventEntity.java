package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Immutable Event V1 record. Corrections are appended as later rows. */
@Entity(
        tableName = "runtime_events",
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "session_id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(name = "index_runtime_events_session", value = "session_id"),
                @Index(
                        name = "index_runtime_events_session_sequence",
                        value = {"session_id", "sequence"},
                        unique = true),
                @Index(name = "index_runtime_events_parent", value = "parent_event_id")
        })
public final class RuntimeEventEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "event_id")
    public String eventId = "";

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    public long sequence;

    @NonNull
    @ColumnInfo(name = "parent_event_id")
    public String parentEventId = "";

    @ColumnInfo(name = "parent_sequence")
    public long parentSequence;

    @NonNull
    @ColumnInfo(name = "event_type")
    public String eventType = "";

    public int source;

    @ColumnInfo(name = "occurred_at_wall_ms")
    public long occurredAtWallMs;

    @ColumnInfo(name = "privacy_class")
    public int privacyClass;

    @NonNull
    @ColumnInfo(name = "payload_digest")
    public String payloadDigest = "";

    @NonNull
    @ColumnInfo(name = "event_digest")
    public String eventDigest = "";

    @ColumnInfo(name = "payload_kind")
    public int payloadKind;

    /** Bounded canonical typed payload; never Java serialization or a Binder object. */
    @NonNull
    @ColumnInfo(name = "payload_canonical")
    public String payloadCanonical = "";
}
