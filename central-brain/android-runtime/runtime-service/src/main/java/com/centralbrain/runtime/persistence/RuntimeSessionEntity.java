package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "runtime_session",
        indices = @Index(
                name = "index_runtime_session_owner_key",
                value = {"owner_fingerprint", "session_key"},
                unique = true))
public final class RuntimeSessionEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @NonNull
    @ColumnInfo(name = "owner_fingerprint")
    public String ownerFingerprint = "";

    @NonNull
    @ColumnInfo(name = "session_key")
    public String sessionKey = "";

    @NonNull
    public String state = "";

    @ColumnInfo(name = "created_at_wall_ms")
    public long createdAtWallMs;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;
}
