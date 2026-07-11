package com.centralbrain.runtime.persistence;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface RuntimeStateDao {
    @Nullable
    @Query("SELECT * FROM runtime_task WHERE task_id = :taskId LIMIT 1")
    RuntimeTaskEntity findTask(String taskId);

    @Nullable
    @Query("SELECT * FROM approval_request WHERE approval_id = :approvalId LIMIT 1")
    ApprovalRequestEntity findApproval(String approvalId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertSession(RuntimeSessionEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertTask(RuntimeTaskEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertCheckpoint(TaskCheckpointEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertPendingEffect(PendingEffectEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertOutbox(OutboxEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertApproval(ApprovalRequestEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertAuditEvent(AuditEventEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertEventCursor(EventCursorEntity entity);
}
