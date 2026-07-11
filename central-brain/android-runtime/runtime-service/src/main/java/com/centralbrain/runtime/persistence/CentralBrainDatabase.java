package com.centralbrain.runtime.persistence;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** R4 durable state owner. No effect dispatcher is activated by this database. */
@Database(
        entities = {
                RuntimeSessionEntity.class,
                RuntimeTaskEntity.class,
                TaskCheckpointEntity.class,
                PendingEffectEntity.class,
                OutboxEntity.class,
                ApprovalRequestEntity.class,
                AuditEventEntity.class,
                EventCursorEntity.class
        },
        version = CentralBrainDatabase.VERSION,
        exportSchema = true)
public abstract class CentralBrainDatabase extends RoomDatabase {
    public static final int VERSION = 2;
    public static final String DATABASE_NAME = "central_brain_runtime.db";

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE runtime_task ADD COLUMN session_id TEXT NOT NULL DEFAULT ''");
            database.execSQL(
                    "ALTER TABLE runtime_task ADD COLUMN client_request_id TEXT NOT NULL DEFAULT ''");
            database.execSQL(
                    "ALTER TABLE runtime_task ADD COLUMN idempotency_key TEXT NOT NULL DEFAULT ''");
            database.execSQL(
                    "ALTER TABLE runtime_task ADD COLUMN progress_percent INTEGER NOT NULL DEFAULT 0");
            database.execSQL(
                    "ALTER TABLE runtime_task ADD COLUMN payload_digest TEXT NOT NULL DEFAULT ''");
            database.execSQL(
                    "ALTER TABLE runtime_task ADD COLUMN accepted_at_wall_ms INTEGER NOT NULL DEFAULT 0");
            database.execSQL(
                    "ALTER TABLE runtime_task ADD COLUMN terminal_delivery_settled INTEGER NOT NULL DEFAULT 0");
            database.execSQL(
                    "UPDATE runtime_task SET client_request_id = task_id, "
                            + "idempotency_key = 'legacy:' || task_id, "
                            + "accepted_at_wall_ms = updated_at_wall_ms");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_runtime_task_session "
                            + "ON runtime_task(session_id)");
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_runtime_task_owner_idempotency "
                            + "ON runtime_task(owner_fingerprint, idempotency_key)");

            database.execSQL(
                    "ALTER TABLE approval_request ADD COLUMN risk_class TEXT NOT NULL "
                            + "DEFAULT 'UNKNOWN'");
            database.execSQL(
                    "ALTER TABLE approval_request ADD COLUMN reason_code TEXT NOT NULL DEFAULT ''");
            database.execSQL(
                    "ALTER TABLE approval_request ADD COLUMN idempotency_key TEXT NOT NULL DEFAULT ''");
            database.execSQL(
                    "ALTER TABLE approval_request ADD COLUMN created_at_wall_ms INTEGER NOT NULL "
                            + "DEFAULT 0");
            database.execSQL(
                    "ALTER TABLE approval_request ADD COLUMN expires_at_wall_ms INTEGER NOT NULL "
                            + "DEFAULT 0");
            database.execSQL(
                    "UPDATE approval_request SET idempotency_key = 'legacy:' || approval_id, "
                            + "created_at_wall_ms = updated_at_wall_ms");
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_approval_owner_idempotency "
                            + "ON approval_request(owner_fingerprint, idempotency_key)");

            createNewTables(database);
        }
    };

    public abstract RuntimeStateDao runtimeStateDao();

    public static CentralBrainDatabase open(Context context) {
        return open(context, DATABASE_NAME);
    }

    static CentralBrainDatabase open(Context context, String databaseName) {
        return Room.databaseBuilder(
                        context.getApplicationContext(),
                        CentralBrainDatabase.class,
                        databaseName)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                .build();
    }

    private static void createNewTables(SupportSQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS runtime_session ("
                        + "session_id TEXT NOT NULL, owner_fingerprint TEXT NOT NULL, "
                        + "session_key TEXT NOT NULL, state TEXT NOT NULL, "
                        + "created_at_wall_ms INTEGER NOT NULL, updated_at_wall_ms INTEGER NOT NULL, "
                        + "PRIMARY KEY(session_id))");
        database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_runtime_session_owner_key "
                        + "ON runtime_session(owner_fingerprint, session_key)");

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS task_checkpoint ("
                        + "checkpoint_id TEXT NOT NULL, task_id TEXT NOT NULL, "
                        + "sequence INTEGER NOT NULL, step_index INTEGER NOT NULL, "
                        + "state TEXT NOT NULL, payload_digest TEXT NOT NULL, "
                        + "created_at_wall_ms INTEGER NOT NULL, PRIMARY KEY(checkpoint_id), "
                        + "FOREIGN KEY(task_id) REFERENCES runtime_task(task_id) "
                        + "ON UPDATE NO ACTION ON DELETE CASCADE)");
        database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_task_checkpoint_task "
                        + "ON task_checkpoint(task_id)");
        database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_task_checkpoint_task_sequence "
                        + "ON task_checkpoint(task_id, sequence)");

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS pending_effect ("
                        + "effect_id TEXT NOT NULL, task_id TEXT NOT NULL, "
                        + "idempotency_key TEXT NOT NULL, effect_type TEXT NOT NULL, "
                        + "action_id TEXT NOT NULL, payload_digest TEXT NOT NULL, "
                        + "state TEXT NOT NULL, created_at_wall_ms INTEGER NOT NULL, "
                        + "updated_at_wall_ms INTEGER NOT NULL, PRIMARY KEY(effect_id), "
                        + "FOREIGN KEY(task_id) REFERENCES runtime_task(task_id) "
                        + "ON UPDATE NO ACTION ON DELETE CASCADE)");
        database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_pending_effect_task "
                        + "ON pending_effect(task_id)");
        database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_pending_effect_idempotency "
                        + "ON pending_effect(idempotency_key)");

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS effect_outbox ("
                        + "outbox_id TEXT NOT NULL, effect_id TEXT NOT NULL, "
                        + "destination TEXT NOT NULL, envelope_digest TEXT NOT NULL, "
                        + "state TEXT NOT NULL, attempt_count INTEGER NOT NULL, "
                        + "not_before_wall_ms INTEGER NOT NULL, created_at_wall_ms INTEGER NOT NULL, "
                        + "updated_at_wall_ms INTEGER NOT NULL, PRIMARY KEY(outbox_id), "
                        + "FOREIGN KEY(effect_id) REFERENCES pending_effect(effect_id) "
                        + "ON UPDATE NO ACTION ON DELETE CASCADE)");
        database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_effect_outbox_effect "
                        + "ON effect_outbox(effect_id)");

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS audit_event ("
                        + "sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                        + "event_id TEXT NOT NULL, event_type TEXT NOT NULL, "
                        + "subject_id TEXT NOT NULL, owner_fingerprint TEXT NOT NULL, "
                        + "outcome TEXT NOT NULL, detail_digest TEXT NOT NULL, "
                        + "observed_at_wall_ms INTEGER NOT NULL)");
        database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_audit_event_id "
                        + "ON audit_event(event_id)");

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS event_cursor ("
                        + "cursor_id TEXT NOT NULL, owner_fingerprint TEXT NOT NULL, "
                        + "topic TEXT NOT NULL, last_sequence INTEGER NOT NULL, "
                        + "updated_at_wall_ms INTEGER NOT NULL, PRIMARY KEY(cursor_id))");
        database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_event_cursor_owner_topic "
                        + "ON event_cursor(owner_fingerprint, topic)");
    }
}
