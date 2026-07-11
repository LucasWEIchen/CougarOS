package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "runtime_task",
        indices = {
                @Index(name = "index_runtime_task_session", value = "session_id"),
                @Index(
                        name = "index_runtime_task_owner_idempotency",
                        value = {"owner_fingerprint", "idempotency_key"},
                        unique = true)
        })
public final class RuntimeTaskEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "task_id")
    public String taskId = "";

    @NonNull
    @ColumnInfo(name = "session_id", defaultValue = "''")
    public String sessionId = "";

    @NonNull
    @ColumnInfo(name = "owner_fingerprint")
    public String ownerFingerprint = "";

    @NonNull
    @ColumnInfo(name = "client_request_id", defaultValue = "''")
    public String clientRequestId = "";

    @NonNull
    @ColumnInfo(name = "idempotency_key", defaultValue = "''")
    public String idempotencyKey = "";

    @NonNull
    public String state = "";

    @ColumnInfo(name = "progress_percent", defaultValue = "0")
    public int progressPercent;

    @NonNull
    @ColumnInfo(name = "payload_digest", defaultValue = "''")
    public String payloadDigest = "";

    @ColumnInfo(name = "accepted_at_wall_ms", defaultValue = "0")
    public long acceptedAtWallMs;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;

    @ColumnInfo(name = "terminal_delivery_settled", defaultValue = "0")
    public boolean terminalDeliverySettled;
}
