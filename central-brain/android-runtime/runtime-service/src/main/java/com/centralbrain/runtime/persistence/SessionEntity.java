package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Durable owner-scoped Session V1 snapshot. Raw utterances are never stored. */
@Entity(
        tableName = "sessions",
        indices = {
                @Index(
                        name = "index_sessions_owner_request",
                        value = {"owner_fingerprint", "client_request_id"},
                        unique = true),
                @Index(
                        name = "index_sessions_owner_updated",
                        value = {"owner_fingerprint", "updated_at_wall_ms"})
        })
public final class SessionEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @NonNull
    @ColumnInfo(name = "owner_fingerprint")
    public String ownerFingerprint = "";

    @NonNull
    @ColumnInfo(name = "client_request_id")
    public String clientRequestId = "";

    @NonNull
    @ColumnInfo(name = "request_digest")
    public String requestDigest = "";

    @NonNull
    @ColumnInfo(name = "scenario_id")
    public String scenarioId = "";

    public int state;

    @ColumnInfo(name = "active_plan_revision")
    public int activePlanRevision;

    @ColumnInfo(name = "last_event_sequence")
    public long lastEventSequence;

    @ColumnInfo(name = "created_at_wall_ms")
    public long createdAtWallMs;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;

    @ColumnInfo(name = "deadline_wall_ms")
    public long deadlineWallMs;

    @NonNull
    public String summary = "";

    public long revision;
}
