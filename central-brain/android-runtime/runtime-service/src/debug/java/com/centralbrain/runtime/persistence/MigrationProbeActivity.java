package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

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
            room = CentralBrainDatabase.open(this, PROBE_DATABASE_NAME);
            RuntimeTaskEntity task = room.runtimeStateDao().findTask("legacy-task");
            ApprovalRequestEntity approval = room.runtimeStateDao().findApproval(
                    "legacy-approval");
            SupportSQLiteDatabase database = room.getOpenHelper().getWritableDatabase();
            int tableCount = queryInt(database, "SELECT count(*) FROM sqlite_master "
                    + "WHERE type = 'table' AND name IN ("
                    + "'runtime_session','runtime_task','task_checkpoint','pending_effect',"
                    + "'effect_outbox','approval_request','audit_event','event_cursor')");
            String journalMode = queryString(database, "PRAGMA journal_mode");

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
            boolean passed = database.getVersion() == CentralBrainDatabase.VERSION
                    && tableCount == 8
                    && "wal".equalsIgnoreCase(journalMode)
                    && taskPreserved
                    && approvalPreserved;
            Log.i(TAG, "nonce=" + nonce
                    + " migration_probe_complete=true"
                    + " room_migration_1_2_verified=" + passed
                    + " room_schema_version=" + database.getVersion()
                    + " room_table_count=" + tableCount
                    + " room_wal_enabled=" + "wal".equalsIgnoreCase(journalMode)
                    + " legacy_task_preserved=" + taskPreserved
                    + " legacy_approval_preserved=" + approvalPreserved
                    + " durable_dispatch_enabled=false"
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
}
