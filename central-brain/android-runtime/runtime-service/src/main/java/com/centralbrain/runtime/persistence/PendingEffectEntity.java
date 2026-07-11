package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pending_effect",
        foreignKeys = @ForeignKey(
                entity = RuntimeTaskEntity.class,
                parentColumns = "task_id",
                childColumns = "task_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(name = "index_pending_effect_task", value = "task_id"),
                @Index(
                        name = "index_pending_effect_idempotency",
                        value = "idempotency_key",
                        unique = true)
        })
public final class PendingEffectEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "effect_id")
    public String effectId = "";

    @NonNull
    @ColumnInfo(name = "task_id")
    public String taskId = "";

    @NonNull
    @ColumnInfo(name = "idempotency_key")
    public String idempotencyKey = "";

    @NonNull
    @ColumnInfo(name = "effect_type")
    public String effectType = "";

    @NonNull
    @ColumnInfo(name = "action_id")
    public String actionId = "";

    @NonNull
    @ColumnInfo(name = "payload_digest")
    public String payloadDigest = "";

    @NonNull
    public String state = "";

    @ColumnInfo(name = "created_at_wall_ms")
    public long createdAtWallMs;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;
}
