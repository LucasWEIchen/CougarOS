package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Durable immutable plan identity plus mutable execution status. */
@Entity(
        tableName = "plans",
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "session_id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(name = "index_plans_session", value = "session_id"),
                @Index(
                        name = "index_plans_session_revision",
                        value = {"session_id", "revision"},
                        unique = true)
        })
public final class PlanEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "plan_id")
    public String planId = "";

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    public int revision;

    public int state;

    @NonNull
    @ColumnInfo(name = "plan_digest")
    public String planDigest = "";

    @NonNull
    @ColumnInfo(name = "context_digest")
    public String contextDigest = "";

    @NonNull
    @ColumnInfo(name = "manifest_digest")
    public String manifestDigest = "";

    @ColumnInfo(name = "created_at_wall_ms")
    public long createdAtWallMs;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;
}
