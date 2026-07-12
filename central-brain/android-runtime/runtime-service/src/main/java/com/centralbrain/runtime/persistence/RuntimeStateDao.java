package com.centralbrain.runtime.persistence;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

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

    @Query("SELECT * FROM runtime_task WHERE state IN ('ACCEPTED', 'RUNNING') "
            + "OR (state = 'COMPLETED' AND terminal_delivery_settled = 0) "
            + "ORDER BY accepted_at_wall_ms, task_id")
    List<RuntimeTaskEntity> findTasksNeedingRestartReconciliation();

    @Nullable
    @Query("SELECT * FROM pending_effect WHERE effect_id = :effectId LIMIT 1")
    PendingEffectEntity findPendingEffect(String effectId);

    @Nullable
    @Query("SELECT * FROM pending_effect WHERE idempotency_key = :idempotencyKey LIMIT 1")
    PendingEffectEntity findPendingEffectByIdempotency(String idempotencyKey);

    @Nullable
    @Query("SELECT * FROM effect_outbox WHERE effect_id = :effectId LIMIT 1")
    OutboxEntity findOutboxByEffect(String effectId);

    @Nullable
    @Query("SELECT effect_outbox.* FROM effect_outbox "
            + "INNER JOIN pending_effect "
            + "ON pending_effect.effect_id = effect_outbox.effect_id "
            + "INNER JOIN runtime_task "
            + "ON runtime_task.task_id = pending_effect.task_id "
            + "WHERE effect_outbox.destination = :destination "
            + "AND effect_outbox.state = 'PENDING' "
            + "AND effect_outbox.not_before_wall_ms <= :nowWallMs "
            + "AND effect_outbox.attempt_count < :maxAttempts "
            + "AND pending_effect.state = 'PREPARED' "
            + "AND runtime_task.state = 'RUNNING' "
            + "ORDER BY effect_outbox.not_before_wall_ms, "
            + "effect_outbox.created_at_wall_ms, effect_outbox.outbox_id LIMIT 1")
    OutboxEntity findNextClaimableOutbox(
            String destination,
            long nowWallMs,
            int maxAttempts);

    @Query("SELECT * FROM effect_outbox WHERE state = :state "
            + "ORDER BY updated_at_wall_ms, outbox_id")
    List<OutboxEntity> findOutboxesInState(String state);

    @Nullable
    @Query("SELECT * FROM audit_event "
            + "WHERE subject_id = :subjectId AND event_type = :eventType "
            + "ORDER BY sequence DESC LIMIT 1")
    AuditEventEntity findLatestAuditEvent(String subjectId, String eventType);

    @Nullable
    @Query("SELECT * FROM task_checkpoint WHERE task_id = :taskId "
            + "ORDER BY sequence DESC LIMIT 1")
    TaskCheckpointEntity findLatestCheckpoint(String taskId);

    @Nullable
    @Query("SELECT * FROM approval_request WHERE approval_id = :approvalId LIMIT 1")
    ApprovalRequestEntity findApproval(String approvalId);

    @Nullable
    @Query("SELECT * FROM approval_request "
            + "WHERE owner_fingerprint = :ownerFingerprint "
            + "AND idempotency_key = :idempotencyKey LIMIT 1")
    ApprovalRequestEntity findApprovalByOwnerAndIdempotency(
            String ownerFingerprint,
            String idempotencyKey);

    @Nullable
    @Query("SELECT * FROM event_cursor WHERE cursor_id = :cursorId LIMIT 1")
    EventCursorEntity findEventCursor(String cursorId);

    @Nullable
    @Query("SELECT * FROM event_cursor "
            + "WHERE owner_fingerprint = :ownerFingerprint "
            + "AND client_subscription_id = :clientSubscriptionId LIMIT 1")
    EventCursorEntity findEventCursorByOwnerAndClient(
            String ownerFingerprint,
            String clientSubscriptionId);

    @Query("SELECT * FROM approval_request "
            + "WHERE state = 'PENDING' AND expires_at_wall_ms <= :nowWallMs")
    List<ApprovalRequestEntity> findExpiredPendingApprovals(long nowWallMs);

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

    @Update
    int updatePendingEffect(PendingEffectEntity entity);

    @Update
    int updateOutbox(OutboxEntity entity);

    @Update
    int updateApproval(ApprovalRequestEntity entity);

    @Update
    int updateEventCursor(EventCursorEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertEventCursor(EventCursorEntity entity);

    @Query("SELECT COUNT(*) FROM runtime_task")
    int countTasks();

    @Query("SELECT COUNT(*) FROM audit_event")
    int countAuditEvents();

    @Query("SELECT COUNT(*) FROM task_checkpoint")
    int countTaskCheckpoints();

    @Query("SELECT COUNT(*) FROM pending_effect")
    int countPendingEffects();

    @Query("SELECT COUNT(*) FROM effect_outbox")
    int countOutboxRows();

    @Query("SELECT COUNT(*) FROM pending_effect WHERE state = :state")
    int countPendingEffectsInState(String state);

    @Query("SELECT COUNT(*) FROM effect_outbox WHERE state = :state")
    int countOutboxRowsInState(String state);

    @Query("SELECT COUNT(*) FROM runtime_task WHERE state = :state")
    int countTasksInState(String state);

    @Query("SELECT COUNT(*) FROM runtime_task "
            + "WHERE terminal_delivery_settled = 1 "
            + "AND state IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    int countSettledTerminalTasks();

    @Query("SELECT COUNT(*) FROM audit_event WHERE event_type = :eventType")
    int countAuditEventsByType(String eventType);

    @Query("SELECT COUNT(*) FROM approval_request")
    int countApprovals();

    @Query("SELECT COUNT(*) FROM approval_request WHERE state = :state")
    int countApprovalsInState(String state);
}
