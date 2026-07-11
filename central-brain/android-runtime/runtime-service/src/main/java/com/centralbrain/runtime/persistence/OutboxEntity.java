package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "effect_outbox",
        foreignKeys = @ForeignKey(
                entity = PendingEffectEntity.class,
                parentColumns = "effect_id",
                childColumns = "effect_id",
                onDelete = ForeignKey.CASCADE),
        indices = @Index(
                name = "index_effect_outbox_effect",
                value = "effect_id",
                unique = true))
public final class OutboxEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "outbox_id")
    public String outboxId = "";

    @NonNull
    @ColumnInfo(name = "effect_id")
    public String effectId = "";

    @NonNull
    public String destination = "";

    @NonNull
    @ColumnInfo(name = "envelope_digest")
    public String envelopeDigest = "";

    @NonNull
    public String state = "";

    @ColumnInfo(name = "attempt_count")
    public int attemptCount;

    @ColumnInfo(name = "not_before_wall_ms")
    public long notBeforeWallMs;

    @ColumnInfo(name = "created_at_wall_ms")
    public long createdAtWallMs;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;
}
