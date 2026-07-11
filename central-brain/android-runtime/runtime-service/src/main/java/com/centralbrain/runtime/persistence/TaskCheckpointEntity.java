package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "task_checkpoint",
        foreignKeys = @ForeignKey(
                entity = RuntimeTaskEntity.class,
                parentColumns = "task_id",
                childColumns = "task_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(name = "index_task_checkpoint_task", value = "task_id"),
                @Index(
                        name = "index_task_checkpoint_task_sequence",
                        value = {"task_id", "sequence"},
                        unique = true)
        })
public final class TaskCheckpointEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "checkpoint_id")
    public String checkpointId = "";

    @NonNull
    @ColumnInfo(name = "task_id")
    public String taskId = "";

    public long sequence;

    @ColumnInfo(name = "step_index")
    public int stepIndex;

    @NonNull
    public String state = "";

    @NonNull
    @ColumnInfo(name = "payload_digest")
    public String payloadDigest = "";

    @ColumnInfo(name = "created_at_wall_ms")
    public long createdAtWallMs;
}
