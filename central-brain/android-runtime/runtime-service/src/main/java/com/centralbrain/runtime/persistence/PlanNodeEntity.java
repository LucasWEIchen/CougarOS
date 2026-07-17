package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Durable bounded node state. Arbitrary serialized classes are not accepted. */
@Entity(
        tableName = "plan_nodes",
        primaryKeys = {"plan_id", "node_id"},
        foreignKeys = @ForeignKey(
                entity = PlanEntity.class,
                parentColumns = "plan_id",
                childColumns = "plan_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(name = "index_plan_nodes_plan", value = "plan_id"),
                @Index(
                        name = "index_plan_nodes_idempotency",
                        value = {"plan_id", "idempotency_key"},
                        unique = true)
        })
public final class PlanNodeEntity {
    @NonNull
    @ColumnInfo(name = "plan_id")
    public String planId = "";

    @NonNull
    @ColumnInfo(name = "node_id")
    public String nodeId = "";

    @NonNull
    @ColumnInfo(name = "node_type")
    public String nodeType = "";

    public int state;

    @ColumnInfo(name = "attempt_count")
    public int attemptCount;

    @ColumnInfo(name = "deadline_wall_ms")
    public long deadlineWallMs;

    @NonNull
    @ColumnInfo(name = "idempotency_key")
    public String idempotencyKey = "";

    @NonNull
    @ColumnInfo(name = "checkpoint_ref")
    public String checkpointRef = "";

    @NonNull
    @ColumnInfo(name = "payload_digest")
    public String payloadDigest = "";

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;
}
