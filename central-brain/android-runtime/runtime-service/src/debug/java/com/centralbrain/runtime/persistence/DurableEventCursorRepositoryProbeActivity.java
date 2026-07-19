package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.events.BoundedEventRuntime;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** DUMP-protected repository/reopen evidence. It never opens the production database. */
public final class DurableEventCursorRepositoryProbeActivity extends Activity {
    private static final String TAG = "CbEventCursorRepo";
    private static final String DATABASE_NAME = "central_brain_event_cursor_probe.db";
    private static final String OWNER_A = repeat("a", 64);
    private static final String OWNER_B = repeat("b", 64);
    private static final String OWNER_C = repeat("c", 64);
    private static final String EVENT_V2_CLIENT_ID =
            "6fa9e1c1-0ecf-4c87-98ec-cb6c73086a3a";
    private static final String EVENT_V2_SESSION_ID =
            "7d8f6fc4-22e1-4f7b-a1a2-1f39dbc8eb92";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        executor.execute(() -> runProbe(nonce == null ? "" : nonce));
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runProbe(String nonce) {
        CentralBrainDatabase database = null;
        AtomicLong wallClock = new AtomicLong(1_000);
        AtomicInteger ids = new AtomicInteger();
        try {
            deleteDatabase(DATABASE_NAME);
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableEventCursorRepository repository = repository(database, wallClock, ids);

            DurableEventCursorRepository.RegisterResult createdA = repository.register(
                    OWNER_A,
                    "client-a",
                    Arrays.asList(
                            BoundedEventRuntime.TOPIC_MODEL_HEALTH,
                            BoundedEventRuntime.TOPIC_TASK_STATE),
                    2,
                    2,
                    10);
            String cursorA = createdA.getSnapshot().getCursorId();
            DurableEventCursorRepository.RegisterResult replayedA = repository.register(
                    OWNER_A,
                    "client-a",
                    Arrays.asList(
                            BoundedEventRuntime.TOPIC_TASK_STATE,
                            BoundedEventRuntime.TOPIC_MODEL_HEALTH),
                    2,
                    2,
                    10);
            DurableEventCursorRepository.RegisterResult conflictA = repository.register(
                    OWNER_A,
                    "client-a",
                    Arrays.asList(
                            BoundedEventRuntime.TOPIC_TASK_STATE,
                            BoundedEventRuntime.TOPIC_MODEL_HEALTH),
                    2,
                    3,
                    10);
            boolean registrationIdempotencyVerified = createdA.getOutcome()
                    == DurableEventCursorRepository.RegisterOutcome.CREATED
                    && replayedA.getOutcome()
                    == DurableEventCursorRepository.RegisterOutcome.REPLAYED
                    && cursorA.equals(replayedA.getSnapshot().getCursorId())
                    && conflictA.getOutcome()
                    == DurableEventCursorRepository.RegisterOutcome.CONFLICT
                    && createdA.getSnapshot().getTopicIds().equals(Arrays.asList(
                            BoundedEventRuntime.TOPIC_MODEL_HEALTH,
                            BoundedEventRuntime.TOPIC_TASK_STATE));

            DurableEventCursorRepository.RegisterResult future = repository.register(
                    OWNER_C,
                    "client-future",
                    Collections.singletonList(BoundedEventRuntime.TOPIC_POLICY_DECISION),
                    11,
                    1,
                    10);
            DurableEventCursorRepository.RegisterResult ownerLimited = repository.register(
                    OWNER_A,
                    "client-a-2",
                    Collections.singletonList(BoundedEventRuntime.TOPIC_POLICY_DECISION),
                    0,
                    1,
                    10);
            DurableEventCursorRepository.RegisterResult createdB = repository.register(
                    OWNER_B,
                    "client-b",
                    Collections.singletonList(BoundedEventRuntime.TOPIC_POLICY_DECISION),
                    0,
                    1,
                    10);
            String cursorB = createdB.getSnapshot().getCursorId();
            DurableEventCursorRepository.RegisterResult globallyLimited = repository.register(
                    OWNER_C,
                    "client-c-limited",
                    Collections.singletonList(BoundedEventRuntime.TOPIC_TASK_STATE),
                    0,
                    1,
                    10);
            boolean admissionBoundsVerified = future.getOutcome()
                    == DurableEventCursorRepository.RegisterOutcome.FUTURE_CURSOR
                    && ownerLimited.getOutcome()
                    == DurableEventCursorRepository.RegisterOutcome.OWNER_LIMIT
                    && createdB.getOutcome()
                    == DurableEventCursorRepository.RegisterOutcome.CREATED
                    && globallyLimited.getOutcome()
                    == DurableEventCursorRepository.RegisterOutcome.GLOBAL_LIMIT;

            boolean ownerIsolationVerified = repository.findOwned(cursorA, OWNER_B) == null
                    && repository.acknowledgeOwned(cursorA, OWNER_B, 3, 10)
                    == DurableEventCursorRepository.AckOutcome.NOT_FOUND
                    && repository.markOverflowOwned(cursorA, OWNER_B, 3, 4, 10)
                    == DurableEventCursorRepository.OverflowOutcome.NOT_FOUND
                    && repository.cancelOwned(cursorA, OWNER_B)
                    == DurableEventCursorRepository.CancelOutcome.NOT_FOUND;

            boolean ackMonotonicVerified = repository.acknowledgeOwned(
                    cursorA,
                    OWNER_A,
                    5,
                    10) == DurableEventCursorRepository.AckOutcome.APPLIED
                    && repository.acknowledgeOwned(cursorA, OWNER_A, 5, 10)
                    == DurableEventCursorRepository.AckOutcome.REPLAYED
                    && repository.acknowledgeOwned(cursorA, OWNER_A, 4, 10)
                    == DurableEventCursorRepository.AckOutcome.REGRESSION
                    && repository.acknowledgeOwned(cursorA, OWNER_A, 11, 10)
                    == DurableEventCursorRepository.AckOutcome.FUTURE_SEQUENCE;
            boolean sourceRegressionBlocked = repository.acknowledgeOwned(
                    cursorA,
                    OWNER_A,
                    5,
                    4) == DurableEventCursorRepository.AckOutcome.SOURCE_REGRESSION
                    && repository.register(
                            OWNER_A,
                            "client-a",
                            Arrays.asList(
                                    BoundedEventRuntime.TOPIC_MODEL_HEALTH,
                                    BoundedEventRuntime.TOPIC_TASK_STATE),
                            2,
                            2,
                            4).getOutcome()
                            == DurableEventCursorRepository.RegisterOutcome.SOURCE_REGRESSION;

            boolean overflowMarked = repository.markOverflowOwned(
                    cursorA,
                    OWNER_A,
                    3,
                    5,
                    10) == DurableEventCursorRepository.OverflowOutcome.STALE_RANGE
                    && repository.markOverflowOwned(cursorA, OWNER_A, 6, 11, 10)
                    == DurableEventCursorRepository.OverflowOutcome.FUTURE_SEQUENCE
                    && repository.markOverflowOwned(cursorA, OWNER_A, 6, 7, 10)
                    == DurableEventCursorRepository.OverflowOutcome.APPLIED
                    && repository.markOverflowOwned(cursorA, OWNER_A, 6, 7, 10)
                    == DurableEventCursorRepository.OverflowOutcome.REPLAYED
                    && repository.acknowledgeOwned(cursorA, OWNER_A, 7, 10)
                    == DurableEventCursorRepository.AckOutcome.RESYNC_REQUIRED;

            database.close();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            repository = repository(database, wallClock, ids);
            DurableEventCursorRepository.Snapshot reopened = repository.findOwned(
                    cursorA,
                    OWNER_A);
            boolean reopenRecoveryVerified = reopened != null
                    && DurableEventCursorRepository.STATE_RESYNC_REQUIRED.equals(
                            reopened.getState())
                    && reopened.getAcknowledgedSequence() == 5
                    && reopened.getOverflowFirstSequence() == 6
                    && reopened.getOverflowLastSequence() == 7
                    && reopened.getOverflowCount() == 2;
            boolean overflowResyncVerified = overflowMarked
                    && repository.completeResyncOwned(cursorA, OWNER_A, 6, 10)
                    == DurableEventCursorRepository.ResyncOutcome.INCOMPLETE
                    && repository.completeResyncOwned(cursorA, OWNER_A, 8, 10)
                    == DurableEventCursorRepository.ResyncOutcome.APPLIED
                    && repository.completeResyncOwned(cursorA, OWNER_A, 8, 10)
                    == DurableEventCursorRepository.ResyncOutcome.REPLAYED
                    && repository.acknowledgeOwned(cursorA, OWNER_A, 9, 10)
                    == DurableEventCursorRepository.AckOutcome.APPLIED;

            boolean cancelIdempotencyVerified = repository.cancelOwned(cursorA, OWNER_A)
                    == DurableEventCursorRepository.CancelOutcome.APPLIED
                    && repository.cancelOwned(cursorA, OWNER_A)
                    == DurableEventCursorRepository.CancelOutcome.REPLAYED
                    && repository.cancelOwned(cursorB, OWNER_B)
                    == DurableEventCursorRepository.CancelOutcome.APPLIED;
            DurableEventCursorRepository.RegisterResult createdC = repository.register(
                    OWNER_C,
                    "client-c",
                    Collections.singletonList(BoundedEventRuntime.TOPIC_TASK_STATE),
                    1,
                    1,
                    10);
            RuntimeStateDao dao = database.runtimeStateDao();
            boolean recordBoundsVerified = cancelIdempotencyVerified
                    && repository.findOwned(cursorA, OWNER_A) == null
                    && DurableEventCursorRepository.STATE_CANCELLED.equals(
                            repository.findOwned(cursorB, OWNER_B).getState())
                    && createdC.getOutcome()
                    == DurableEventCursorRepository.RegisterOutcome.CREATED
                    && dao.countActiveEventCursors() == 1
                    && dao.countCancelledEventCursors() == 1;
            boolean auditExactlyOnceVerified = dao.countAuditEventsByType(
                    DurableEventCursorRepository.AUDIT_REGISTERED) == 3
                    && dao.countAuditEventsByType(
                            DurableEventCursorRepository.AUDIT_ACKNOWLEDGED) == 2
                    && dao.countAuditEventsByType(
                            DurableEventCursorRepository.AUDIT_OVERFLOWED) == 1
                    && dao.countAuditEventsByType(
                            DurableEventCursorRepository.AUDIT_RESYNCED) == 1
                    && dao.countAuditEventsByType(
                            DurableEventCursorRepository.AUDIT_CANCELLED) == 2;

            String cursorC = createdC.getSnapshot().getCursorId();
            database.close();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            repository = repository(database, wallClock, ids);
            boolean finalReopenVerified = repository.findOwned(cursorC, OWNER_C) != null
                    && repository.findOwned(cursorB, OWNER_B) != null
                    && database.runtimeStateDao().countActiveEventCursors() == 1
                    && database.runtimeStateDao().countCancelledEventCursors() == 1;

            DurableEventCursorRepository.RegisterResult eventV2Created =
                    repository.registerSession(
                            OWNER_A,
                            EVENT_V2_CLIENT_ID,
                            EVENT_V2_SESSION_ID,
                            2,
                            4,
                            5);
            String eventV2CursorId = eventV2Created.getSnapshot().getCursorId();
            boolean eventV2AdmissionVerified = eventV2Created.getOutcome()
                            == DurableEventCursorRepository.RegisterOutcome.CREATED
                    && repository.registerSession(
                                    OWNER_A,
                                    EVENT_V2_CLIENT_ID,
                                    EVENT_V2_SESSION_ID,
                                    2,
                                    4,
                                    5).getOutcome()
                            == DurableEventCursorRepository.RegisterOutcome.REPLAYED
                    && repository.registerSession(
                                    OWNER_A,
                                    EVENT_V2_CLIENT_ID,
                                    EVENT_V2_SESSION_ID,
                                    3,
                                    4,
                                    5).getOutcome()
                            == DurableEventCursorRepository.RegisterOutcome.UNACKNOWLEDGED_CURSOR;
            boolean eventV2OwnerSessionIsolationVerified =
                    repository.findSessionOwned(
                                    eventV2CursorId,
                                    OWNER_B,
                                    EVENT_V2_SESSION_ID) == null
                    && repository.findSessionOwned(
                                    eventV2CursorId,
                                    OWNER_A,
                                    "9c369041-e7d4-4f6a-9757-26a9ac0fd136") == null
                    && repository.acknowledgeSessionOwned(
                                    eventV2CursorId,
                                    OWNER_B,
                                    EVENT_V2_SESSION_ID,
                                    3,
                                    5)
                            == DurableEventCursorRepository.AckOutcome.NOT_FOUND;
            boolean eventV2AckVerified = repository.acknowledgeSessionOwned(
                                    eventV2CursorId,
                                    OWNER_A,
                                    EVENT_V2_SESSION_ID,
                                    4,
                                    5)
                            == DurableEventCursorRepository.AckOutcome.APPLIED
                    && repository.acknowledgeSessionOwned(
                                    eventV2CursorId,
                                    OWNER_A,
                                    EVENT_V2_SESSION_ID,
                                    4,
                                    5)
                            == DurableEventCursorRepository.AckOutcome.REPLAYED
                    && repository.acknowledgeSessionOwned(
                                    eventV2CursorId,
                                    OWNER_A,
                                    EVENT_V2_SESSION_ID,
                                    3,
                                    5)
                            == DurableEventCursorRepository.AckOutcome.REGRESSION
                    && repository.acknowledgeSessionOwned(
                                    eventV2CursorId,
                                    OWNER_A,
                                    EVENT_V2_SESSION_ID,
                                    6,
                                    5)
                            == DurableEventCursorRepository.AckOutcome.FUTURE_SEQUENCE
                    && repository.registerSession(
                                    OWNER_A,
                                    EVENT_V2_CLIENT_ID,
                                    EVENT_V2_SESSION_ID,
                                    2,
                                    4,
                                    5).getOutcome()
                            == DurableEventCursorRepository.RegisterOutcome.STALE_CURSOR;

            database.close();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            repository = repository(database, wallClock, ids);
            DurableEventCursorRepository.Snapshot eventV2Reopened =
                    repository.findSessionOwned(
                            eventV2CursorId,
                            OWNER_A,
                            EVENT_V2_SESSION_ID);
            boolean eventV2ReopenVerified = eventV2Reopened != null
                    && EVENT_V2_SESSION_ID.equals(eventV2Reopened.getSessionId())
                    && eventV2Reopened.getAcknowledgedSequence() == 4
                    && repository.cancelSessionOwned(
                                    eventV2CursorId,
                                    OWNER_A,
                                    EVENT_V2_SESSION_ID)
                            == DurableEventCursorRepository.CancelOutcome.APPLIED
                    && repository.registerSession(
                                    OWNER_A,
                                    EVENT_V2_CLIENT_ID,
                                    EVENT_V2_SESSION_ID,
                                    4,
                                    4,
                                    5).getOutcome()
                            == DurableEventCursorRepository.RegisterOutcome.REOPENED;
            boolean eventV2DurableSessionCursorVerified = eventV2AdmissionVerified
                    && eventV2OwnerSessionIsolationVerified
                    && eventV2AckVerified
                    && eventV2ReopenVerified;

            boolean contractVerified = registrationIdempotencyVerified
                    && admissionBoundsVerified
                    && ownerIsolationVerified
                    && ackMonotonicVerified
                    && sourceRegressionBlocked
                    && overflowResyncVerified
                    && reopenRecoveryVerified
                    && cancelIdempotencyVerified
                    && recordBoundsVerified
                    && auditExactlyOnceVerified
                    && finalReopenVerified
                    && eventV2DurableSessionCursorVerified
                    && repository.isDurable()
                    && !repository.isProductionWired()
                    && repository.requiresDurableMonotonicEventSource();
            Log.i(TAG, "nonce=" + nonce
                    + " durable_event_cursor_probe_complete=" + contractVerified
                    + " durable_event_cursor_repository_verified=" + contractVerified
                    + " event_cursor_registration_idempotency_verified="
                    + registrationIdempotencyVerified
                    + " event_cursor_admission_bounds_verified=" + admissionBoundsVerified
                    + " event_cursor_owner_isolation_verified=" + ownerIsolationVerified
                    + " event_cursor_ack_monotonic_verified=" + ackMonotonicVerified
                    + " event_cursor_source_regression_blocked="
                    + sourceRegressionBlocked
                    + " event_cursor_overflow_resync_verified=" + overflowResyncVerified
                    + " event_cursor_reopen_recovery_verified="
                    + (reopenRecoveryVerified && finalReopenVerified)
                    + " event_cursor_cancel_idempotency_verified="
                    + cancelIdempotencyVerified
                    + " event_cursor_record_bounds_verified=" + recordBoundsVerified
                    + " event_cursor_audit_exactly_once_verified="
                    + auditExactlyOnceVerified
                    + " event_v2_durable_session_cursor_verified="
                    + eventV2DurableSessionCursorVerified
                    + " event_v2_owner_session_isolation_verified="
                    + eventV2OwnerSessionIsolationVerified
                    + " event_v2_ack_monotonic_verified=" + eventV2AckVerified
                    + " event_v2_reopen_recovery_verified=" + eventV2ReopenVerified
                    + " event_cursor_probe_persistence_verified=true"
                    + " event_cursor_repository_implementation_available=true"
                    + " event_cursor_repository_production_wired=false"
                    + " event_cursor_persistence_wired=false"
                    + " durable_event_source_available=false"
                    + " event_broker_production_wired=false"
                    + " event_callback_binder_wired=false"
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " durable_event_cursor_probe_complete=false"
                    + " event_cursor_repository_production_wired=false"
                    + " event_broker_production_wired=false"
                    + " hardware_accessed=false", exception);
        } finally {
            if (database != null) {
                database.close();
            }
            deleteDatabase(DATABASE_NAME);
            runOnUiThread(this::finish);
        }
    }

    private static DurableEventCursorRepository repository(
            CentralBrainDatabase database,
            AtomicLong wallClock,
            AtomicInteger ids) {
        return new DurableEventCursorRepository(
                database,
                2,
                1,
                1,
                wallClock::incrementAndGet,
                () -> "probe-" + ids.incrementAndGet());
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
