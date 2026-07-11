package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "event_cursor",
        indices = @Index(
                name = "index_event_cursor_owner_topic",
                value = {"owner_fingerprint", "topic"},
                unique = true))
public final class EventCursorEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "cursor_id")
    public String cursorId = "";

    @NonNull
    @ColumnInfo(name = "owner_fingerprint")
    public String ownerFingerprint = "";

    @NonNull
    public String topic = "";

    @ColumnInfo(name = "last_sequence")
    public long lastSequence;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;
}
