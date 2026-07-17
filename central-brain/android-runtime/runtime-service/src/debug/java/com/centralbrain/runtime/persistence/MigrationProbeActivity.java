package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** DUMP-protected API 33 migration evidence. It never opens the production database. */
public final class MigrationProbeActivity extends Activity {
    private static final String TAG = "CbMigrationProbe";
    private static final String PROBE_DATABASE_NAME = "central_brain_migration_probe.db";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor.execute(this::runProbe);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runProbe() {
        CentralBrainDatabase room = null;
        String nonce = getIntent().getStringExtra("nonce");
        if (nonce == null) {
            nonce = "";
        }
        try {
            deleteDatabase(PROBE_DATABASE_NAME);
            createVersionOneDatabase();
            migrateToVersionTwoAndSeedCursor();
            room = CentralBrainDatabase.open(this, PROBE_DATABASE_NAME);
            RuntimeTaskEntity task = room.runtimeStateDao().findTask("legacy-task");
            ApprovalRequestEntity approval = room.runtimeStateDao().findApproval(
                    "legacy-approval");
            EventCursorEntity eventCursor = room.runtimeStateDao().findEventCursor(
                    "legacy-event-cursor");
            SessionEntity session = room.runtimeStateDao().findSession("legacy-session");
            boolean legacySessionV1ExposureBlocked = room.runtimeStateDao().findSessionOwned(
                    "legacy-session",
                    "legacy-owner") == null;
            SupportSQLiteDatabase database = room.getOpenHelper().getWritableDatabase();
            int tableCount = queryInt(database, "SELECT count(*) FROM sqlite_master "
                    + "WHERE type = 'table' AND name IN ("
                    + "'sessions','plans','plan_nodes','runtime_events','effect_observations',"
                    + "'compensations','runtime_task','task_checkpoint','pending_effect',"
                    + "'effect_outbox','approval_request','audit_event','event_cursor')");
            String journalMode = queryString(database, "PRAGMA journal_mode");
            int eventCursorColumnCount = queryInt(
                    database,
                    "SELECT count(*) FROM pragma_table_info('event_cursor')");
            int eventCursorOwnerClientIndexCount = queryInt(
                    database,
                    "SELECT count(*) FROM sqlite_master WHERE type = 'index' "
                            + "AND name = 'index_event_cursor_owner_client'");
            int legacyOwnerTopicIndexCount = queryInt(
                    database,
                    "SELECT count(*) FROM sqlite_master WHERE type = 'index' "
                            + "AND name = 'index_event_cursor_owner_topic'");
            int roomV4TableCount = queryInt(
                    database,
                    "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name IN ("
                            + "'sessions','plans','plan_nodes','runtime_events',"
                            + "'effect_observations','compensations')");
            int sessionOwnerRequestIndexCount = queryInt(
                    database,
                    "SELECT count(*) FROM sqlite_master WHERE type = 'index' "
                            + "AND name = 'index_sessions_owner_request'");
            boolean roomV4ForeignKeysVerified = queryRowCount(
                    database,
                    "PRAGMA foreign_key_check") == 0;
            boolean roomV4QueryIndexVerified = queryPlanUses(
                    database,
                    "EXPLAIN QUERY PLAN SELECT * FROM sessions "
                            + "WHERE owner_fingerprint = 'legacy-owner' "
                            + "AND client_request_id = 'legacy-session-key'",
                    "index_sessions_owner_request");
            boolean roomV4CrashTransactionRollbackVerified =
                    verifyCrashTransactionRollback(database);

            boolean taskPreserved = task != null
                    && "legacy-owner".equals(task.ownerFingerprint)
                    && "RUNNING".equals(task.state)
                    && "legacy-task".equals(task.clientRequestId)
                    && "legacy:legacy-task".equals(task.idempotencyKey)
                    && task.acceptedAtWallMs == 1000;
            boolean approvalPreserved = approval != null
                    && "legacy-owner".equals(approval.ownerFingerprint)
                    && "system.ota.install".equals(approval.actionId)
                    && "PENDING".equals(approval.state)
                    && "legacy:legacy-approval".equals(approval.idempotencyKey)
                    && approval.createdAtWallMs == 1000;
            boolean legacyEventCursorPreserved = eventCursor != null
                    && "legacy-owner".equals(eventCursor.ownerFingerprint)
                    && "legacy:legacy-event-cursor".equals(
                            eventCursor.clientSubscriptionId)
                    && "runtime.task.state".equals(eventCursor.topicsCanonical)
                    && eventCursor.requestedAfterSequence == 7
                    && eventCursor.acknowledgedSequence == 7
                    && eventCursor.queueCapacity == 1
                    && "ACTIVE".equals(eventCursor.state)
                    && eventCursor.overflowFirstSequence == 0
                    && eventCursor.overflowLastSequence == 0
                    && eventCursor.overflowCount == 0
                    && eventCursor.createdAtWallMs == 2000
                    && eventCursor.updatedAtWallMs == 2000;
            boolean eventCursorSchemaV3 = eventCursorColumnCount == 13
                    && eventCursorOwnerClientIndexCount == 1
                    && legacyOwnerTopicIndexCount == 0;
            boolean migrationOneToTwoVerified = taskPreserved && approvalPreserved;
            boolean migrationTwoToThreeVerified = legacyEventCursorPreserved
                    && eventCursorSchemaV3;
            boolean legacyRuntimeSessionPreserved = session != null
                    && "legacy-owner".equals(session.ownerFingerprint)
                    && "legacy-session-key".equals(session.clientRequestId)
                    && "legacy.runtime.session".equals(session.scenarioId)
                    && session.state == 9
                    && session.createdAtWallMs == 1100
                    && session.updatedAtWallMs == 1200
                    && session.revision == 1;
            boolean roomSchemaV4 = roomV4TableCount == 6
                    && sessionOwnerRequestIndexCount == 1
                    && roomV4ForeignKeysVerified
                    && roomV4QueryIndexVerified
                    && roomV4CrashTransactionRollbackVerified;
            boolean migrationThreeToFourVerified = legacyRuntimeSessionPreserved
                    && legacySessionV1ExposureBlocked
                    && roomSchemaV4;
            boolean passed = database.getVersion() == CentralBrainDatabase.VERSION
                    && tableCount == 13
                    && "wal".equalsIgnoreCase(journalMode)
                    && migrationOneToTwoVerified
                    && migrationTwoToThreeVerified
                    && migrationThreeToFourVerified;
            Log.i(TAG, "nonce=" + nonce
                    + " migration_probe_complete=" + passed
                    + " room_migration_1_2_verified=" + migrationOneToTwoVerified
                    + " room_migration_2_3_verified=" + migrationTwoToThreeVerified
                    + " room_migration_3_4_verified=" + migrationThreeToFourVerified
                    + " room_schema_version=" + database.getVersion()
                    + " room_table_count=" + tableCount
                    + " room_wal_enabled=" + "wal".equalsIgnoreCase(journalMode)
                    + " legacy_task_preserved=" + taskPreserved
                    + " legacy_approval_preserved=" + approvalPreserved
                    + " legacy_event_cursor_preserved=" + legacyEventCursorPreserved
                    + " legacy_runtime_session_preserved="
                    + legacyRuntimeSessionPreserved
                    + " legacy_session_v1_exposure_blocked="
                    + legacySessionV1ExposureBlocked
                    + " event_cursor_schema_v3_verified=" + eventCursorSchemaV3
                    + " event_cursor_schema_ready=" + eventCursorSchemaV3
                    + " room_schema_v4_verified=" + roomSchemaV4
                    + " room_v4_foreign_keys_verified=" + roomV4ForeignKeysVerified
                    + " room_v4_query_index_verified=" + roomV4QueryIndexVerified
                    + " room_v4_crash_transaction_rollback_verified="
                    + roomV4CrashTransactionRollbackVerified
                    + " event_cursor_repository_wired=false"
                    + " durable_dispatch_enabled=false"
                    + " event_broker_production_wired=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " migration_probe_complete=false hardware_accessed=false", exception);
        } finally {
            if (room != null) {
                room.close();
            }
            deleteDatabase(PROBE_DATABASE_NAME);
            runOnUiThread(this::finish);
        }
    }

    private void createVersionOneDatabase() {
        SQLiteDatabase database = openOrCreateDatabase(
                PROBE_DATABASE_NAME,
                MODE_PRIVATE,
                null);
        try {
            database.execSQL(
                    "CREATE TABLE runtime_task (task_id TEXT NOT NULL PRIMARY KEY, "
                            + "owner_fingerprint TEXT NOT NULL, state TEXT NOT NULL, "
                            + "updated_at_wall_ms INTEGER NOT NULL)");
            database.execSQL(
                    "CREATE TABLE approval_request (approval_id TEXT NOT NULL PRIMARY KEY, "
                            + "owner_fingerprint TEXT NOT NULL, action_id TEXT NOT NULL, "
                            + "state TEXT NOT NULL, updated_at_wall_ms INTEGER NOT NULL)");
            database.execSQL(
                    "INSERT INTO runtime_task(task_id, owner_fingerprint, state, "
                            + "updated_at_wall_ms) VALUES('legacy-task','legacy-owner',"
                            + "'RUNNING',1000)");
            database.execSQL(
                    "INSERT INTO approval_request(approval_id, owner_fingerprint, action_id, "
                            + "state, updated_at_wall_ms) VALUES('legacy-approval','legacy-owner',"
                            + "'system.ota.install','PENDING',1000)");
            database.setVersion(1);
        } finally {
            database.close();
        }
    }

    private void migrateToVersionTwoAndSeedCursor() {
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(this)
                        .name(PROBE_DATABASE_NAME)
                        .callback(new SupportSQLiteOpenHelper.Callback(2) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase database) {
                                throw new IllegalStateException(
                                        "version-one probe database is missing");
                            }

                            @Override
                            public void onUpgrade(
                                    SupportSQLiteDatabase database,
                                    int oldVersion,
                                    int newVersion) {
                                if (oldVersion != 1 || newVersion != 2) {
                                    throw new IllegalStateException(
                                            "unexpected probe migration path");
                                }
                                CentralBrainDatabase.MIGRATION_1_2.migrate(database);
                            }
                        })
                        .build());
        try {
            SupportSQLiteDatabase database = helper.getWritableDatabase();
            database.execSQL(
                    "INSERT INTO event_cursor(cursor_id, owner_fingerprint, topic, "
                            + "last_sequence, updated_at_wall_ms) VALUES("
                            + "'legacy-event-cursor','legacy-owner','runtime.task.state',7,2000)");
            database.execSQL(
                    "INSERT INTO runtime_session(session_id, owner_fingerprint, session_key, "
                            + "state, created_at_wall_ms, updated_at_wall_ms) VALUES("
                            + "'legacy-session','legacy-owner','legacy-session-key',"
                            + "'RUNNING',1100,1200)");
        } finally {
            helper.close();
        }
    }

    private static int queryInt(SupportSQLiteDatabase database, String sql) {
        try (Cursor cursor = database.query(sql)) {
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("query returned no rows");
            }
            return cursor.getInt(0);
        }
    }

    private static String queryString(SupportSQLiteDatabase database, String sql) {
        try (Cursor cursor = database.query(sql)) {
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("query returned no rows");
            }
            return cursor.getString(0);
        }
    }

    private static int queryRowCount(SupportSQLiteDatabase database, String sql) {
        int count = 0;
        try (Cursor cursor = database.query(sql)) {
            while (cursor.moveToNext()) {
                count++;
            }
        }
        return count;
    }

    private static boolean queryPlanUses(
            SupportSQLiteDatabase database,
            String sql,
            String expectedIndex) {
        try (Cursor cursor = database.query(sql)) {
            while (cursor.moveToNext()) {
                String detail = cursor.getString(cursor.getColumnIndexOrThrow("detail"));
                if (detail != null && detail.contains(expectedIndex)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean verifyCrashTransactionRollback(SupportSQLiteDatabase database) {
        database.beginTransaction();
        try {
            database.execSQL(
                    "INSERT INTO sessions(session_id, owner_fingerprint, client_request_id, "
                            + "request_digest, scenario_id, state, active_plan_revision, "
                            + "last_event_sequence, created_at_wall_ms, updated_at_wall_ms, "
                            + "deadline_wall_ms, summary, revision) VALUES("
                            + "'10000000-0000-4000-8000-000000000001',"
                            + "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',"
                            + "'10000000-0000-4000-8000-000000000002',"
                            + "'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',"
                            + "'scene.migration.probe.v1',1,0,1,3000,3000,4000,'probe',1)");
            database.execSQL(
                    "INSERT INTO runtime_events(event_id, session_id, sequence, "
                            + "parent_event_id, parent_sequence, event_type, source, "
                            + "occurred_at_wall_ms, privacy_class, payload_digest, event_digest, "
                            + "payload_kind, payload_canonical) VALUES("
                            + "'10000000-0000-4000-8000-000000000003',"
                            + "'10000000-0000-4000-8000-000000000001',1,'',0,"
                            + "'ScenarioRequested',1,3000,2,"
                            + "'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',"
                            + "'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',"
                            + "0,'')");
            throw new IllegalStateException("simulated process crash before commit");
        } catch (IllegalStateException expected) {
            // Deliberately omit setTransactionSuccessful(); both rows must roll back.
        } finally {
            database.endTransaction();
        }
        return queryInt(
                database,
                "SELECT count(*) FROM sessions WHERE "
                        + "session_id = '10000000-0000-4000-8000-000000000001'") == 0
                && queryInt(
                        database,
                        "SELECT count(*) FROM runtime_events WHERE "
                                + "event_id = '10000000-0000-4000-8000-000000000003'") == 0;
    }
}
