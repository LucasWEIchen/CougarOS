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
    @Query("SELECT * FROM sessions WHERE session_id = :sessionId LIMIT 1")
    SessionEntity findSession(String sessionId);

    @Nullable
    @Query("SELECT * FROM sessions WHERE session_id = :sessionId "
            + "AND owner_fingerprint = :ownerFingerprint "
            + "AND request_digest != '0000000000000000000000000000000000000000000000000000000000000000' "
            + "LIMIT 1")
    SessionEntity findSessionOwned(String sessionId, String ownerFingerprint);

    @Nullable
    @Query("SELECT * FROM sessions WHERE owner_fingerprint = :ownerFingerprint "
            + "AND client_request_id = :clientRequestId "
            + "AND request_digest != '0000000000000000000000000000000000000000000000000000000000000000' "
            + "LIMIT 1")
    SessionEntity findSessionByOwnerAndRequest(
            String ownerFingerprint,
            String clientRequestId);

    @Query("SELECT * FROM sessions WHERE owner_fingerprint = :ownerFingerprint "
            + "AND request_digest != '0000000000000000000000000000000000000000000000000000000000000000' "
            + "AND (:includeTerminal = 1 OR state NOT IN (8, 9, 10)) "
            + "AND (:stateFilter = -1 OR state = :stateFilter) "
            + "ORDER BY created_at_wall_ms, session_id LIMIT :limit OFFSET :offset")
    List<SessionEntity> listSessionsOwned(
            String ownerFingerprint,
            int stateFilter,
            boolean includeTerminal,
            int limit,
            int offset);

    @Query("SELECT COUNT(*) FROM sessions WHERE owner_fingerprint = :ownerFingerprint "
            + "AND request_digest != '0000000000000000000000000000000000000000000000000000000000000000' "
            + "AND (:includeTerminal = 1 OR state NOT IN (8, 9, 10)) "
            + "AND (:stateFilter = -1 OR state = :stateFilter)")
    int countSessionsOwned(
            String ownerFingerprint,
            int stateFilter,
            boolean includeTerminal);

    @Query("SELECT COUNT(*) FROM sessions")
    int countSessions();

    @Nullable
    @Query("SELECT * FROM plans WHERE plan_id = :planId LIMIT 1")
    PlanEntity findPlan(String planId);

    @Query("SELECT * FROM plans WHERE state NOT IN (5, 7, 8, 9, 10) "
            + "ORDER BY updated_at_wall_ms, plan_id")
    List<PlanEntity> listNonTerminalPlans();

    @Query("SELECT * FROM plan_nodes WHERE plan_id = :planId ORDER BY node_id")
    List<PlanNodeEntity> listPlanNodes(String planId);

    @Query("SELECT COUNT(*) FROM plan_nodes WHERE plan_id = :planId")
    int countPlanNodes(String planId);

    @Query("SELECT * FROM effect_observations WHERE session_id = :sessionId "
            + "ORDER BY effect_id, sequence LIMIT :limit")
    List<EffectObservationEntity> listEffectObservationsForRecovery(
            String sessionId,
            int limit);

    @Query("SELECT COUNT(*) FROM effect_observations WHERE session_id = :sessionId")
    int countEffectObservationsForRecovery(String sessionId);

    @Query("SELECT * FROM compensations WHERE session_id = :sessionId "
            + "ORDER BY compensation_id LIMIT :limit")
    List<CompensationEntity> listCompensationsForRecovery(
            String sessionId,
            int limit);

    @Query("SELECT COUNT(*) FROM compensations WHERE session_id = :sessionId")
    int countCompensationsForRecovery(String sessionId);

    @Nullable
    @Query("SELECT * FROM sessions WHERE state IN (8, 9, 10) "
            + "ORDER BY updated_at_wall_ms, session_id LIMIT 1")
    SessionEntity findOldestTerminalSession();

    @Query("SELECT * FROM runtime_events WHERE session_id = :sessionId "
            + "AND sequence > :afterSequence ORDER BY sequence LIMIT :limit")
    List<RuntimeEventEntity> listRuntimeEvents(
            String sessionId,
            long afterSequence,
            int limit);

    @Nullable
    @Query("SELECT * FROM runtime_events WHERE session_id = :sessionId "
            + "ORDER BY sequence DESC LIMIT 1")
    RuntimeEventEntity findLatestRuntimeEvent(String sessionId);

    @Query("SELECT COUNT(*) FROM runtime_events WHERE session_id = :sessionId")
    int countRuntimeEvents(String sessionId);

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
    @Query("SELECT * FROM audit_event WHERE event_id = :eventId LIMIT 1")
    AuditEventEntity findAuditEvent(String eventId);

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

    @Query("SELECT COUNT(*) FROM event_cursor WHERE state IN ('ACTIVE', 'RESYNC_REQUIRED')")
    int countActiveEventCursors();

    @Query("SELECT COUNT(*) FROM event_cursor "
            + "WHERE owner_fingerprint = :ownerFingerprint "
            + "AND state IN ('ACTIVE', 'RESYNC_REQUIRED')")
    int countActiveEventCursorsByOwner(String ownerFingerprint);

    @Query("SELECT COUNT(*) FROM event_cursor WHERE state = 'CANCELLED'")
    int countCancelledEventCursors();

    @Nullable
    @Query("SELECT * FROM event_cursor WHERE state = 'CANCELLED' "
            + "ORDER BY updated_at_wall_ms, cursor_id LIMIT 1")
    EventCursorEntity findOldestCancelledEventCursor();

    @Nullable
    @Query("SELECT * FROM event_cursor WHERE state = 'CANCELLED' "
            + "AND cursor_id != :retainedCursorId "
            + "ORDER BY updated_at_wall_ms, cursor_id LIMIT 1")
    EventCursorEntity findOldestCancelledEventCursorExcept(String retainedCursorId);

    @Query("SELECT * FROM approval_request "
            + "WHERE state = 'PENDING' AND expires_at_wall_ms <= :nowWallMs")
    List<ApprovalRequestEntity> findExpiredPendingApprovals(long nowWallMs);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertSession(SessionEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertRuntimeEvent(RuntimeEventEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertPlan(PlanEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertPlanNode(PlanNodeEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertEffectObservation(EffectObservationEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertCompensation(CompensationEntity entity);

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

    @Update
    int updateSession(SessionEntity entity);

    @Update
    int updatePlan(PlanEntity entity);

    @Update
    int updatePlanNode(PlanNodeEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertEventCursor(EventCursorEntity entity);

    @Query("DELETE FROM event_cursor WHERE cursor_id = :cursorId AND state = 'CANCELLED'")
    int deleteCancelledEventCursor(String cursorId);

    @Query("DELETE FROM sessions WHERE session_id = :sessionId AND state IN (8, 9, 10)")
    int deleteTerminalSession(String sessionId);

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
