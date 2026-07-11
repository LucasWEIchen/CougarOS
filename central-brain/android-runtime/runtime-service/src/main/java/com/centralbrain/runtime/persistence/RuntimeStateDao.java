package com.centralbrain.runtime.persistence;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface RuntimeStateDao {
    @Nullable
    @Query("SELECT * FROM runtime_task WHERE task_id = :taskId LIMIT 1")
    RuntimeTaskEntity findTask(String taskId);

    @Nullable
    @Query("SELECT * FROM runtime_task "
            + "WHERE owner_fingerprint = :ownerFingerprint "
            + "AND idempotency_key = :idempotencyKey LIMIT 1")
    RuntimeTaskEntity findTaskByOwnerAndIdempotency(
            String ownerFingerprint,
            String idempotencyKey);

    @Nullable
    @Query("SELECT * FROM task_checkpoint WHERE task_id = :taskId "
            + "ORDER BY sequence DESC LIMIT 1")
    TaskCheckpointEntity findLatestCheckpoint(String taskId);

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

    @Update
    int updateTask(RuntimeTaskEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertEventCursor(EventCursorEntity entity);

    @Query("SELECT COUNT(*) FROM runtime_task")
    int countTasks();

    @Query("SELECT COUNT(*) FROM audit_event")
    int countAuditEvents();

    @Query("SELECT COUNT(*) FROM task_checkpoint")
    int countTaskCheckpoints();

    @Query("SELECT COUNT(*) FROM runtime_task WHERE state = :state")
    int countTasksInState(String state);

    @Query("SELECT COUNT(*) FROM runtime_task "
            + "WHERE terminal_delivery_settled = 1 "
            + "AND state IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    int countSettledTerminalTasks();

    @Query("SELECT COUNT(*) FROM audit_event WHERE event_type = :eventType")
    int countAuditEventsByType(String eventType);
}
