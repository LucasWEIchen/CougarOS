package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Compensation intent metadata. It is not an authorization grant or database rollback. */
@Entity(
        tableName = "compensations",
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "session_id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(name = "index_compensations_session", value = "session_id"),
                @Index(
                        name = "index_compensations_idempotency",
                        value = "idempotency_key",
                        unique = true)
        })
public final class CompensationEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "compensation_id")
    public String compensationId = "";

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @NonNull
    @ColumnInfo(name = "effect_id")
    public String effectId = "";

    @NonNull
    @ColumnInfo(name = "idempotency_key")
    public String idempotencyKey = "";

    @NonNull
    @ColumnInfo(name = "before_snapshot_ref")
    public String beforeSnapshotRef = "";

    @NonNull
    @ColumnInfo(name = "compensation_digest")
    public String compensationDigest = "";

    public int state;

    @ColumnInfo(name = "expires_at_wall_ms")
    public long expiresAtWallMs;

    @ColumnInfo(name = "created_at_wall_ms")
    public long createdAtWallMs;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;
}
