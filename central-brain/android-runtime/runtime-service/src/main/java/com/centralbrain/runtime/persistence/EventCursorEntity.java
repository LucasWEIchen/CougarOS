package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "event_cursor",
        indices = @Index(
                name = "index_event_cursor_owner_client",
                value = {"owner_fingerprint", "client_subscription_id"},
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
    @ColumnInfo(name = "client_subscription_id")
    public String clientSubscriptionId = "";

    @NonNull
    @ColumnInfo(name = "topics_canonical")
    public String topicsCanonical = "";

    @ColumnInfo(name = "requested_after_sequence")
    public long requestedAfterSequence;

    @ColumnInfo(name = "acknowledged_sequence")
    public long acknowledgedSequence;

    @ColumnInfo(name = "queue_capacity")
    public int queueCapacity;

    @NonNull
    public String state = "";

    @ColumnInfo(name = "overflow_first_sequence")
    public long overflowFirstSequence;

    @ColumnInfo(name = "overflow_last_sequence")
    public long overflowLastSequence;

    @ColumnInfo(name = "overflow_count")
    public long overflowCount;

    @ColumnInfo(name = "created_at_wall_ms")
    public long createdAtWallMs;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;
}
