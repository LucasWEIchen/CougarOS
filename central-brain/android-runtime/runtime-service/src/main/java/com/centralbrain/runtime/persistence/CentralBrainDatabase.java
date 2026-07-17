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
                SessionEntity.class,
                PlanEntity.class,
                PlanNodeEntity.class,
                RuntimeEventEntity.class,
                EffectObservationEntity.class,
                CompensationEntity.class,
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
    public static final int VERSION = 4;
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

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE event_cursor_v3 ("
                            + "cursor_id TEXT NOT NULL, owner_fingerprint TEXT NOT NULL, "
                            + "client_subscription_id TEXT NOT NULL, "
                            + "topics_canonical TEXT NOT NULL, "
                            + "requested_after_sequence INTEGER NOT NULL, "
                            + "acknowledged_sequence INTEGER NOT NULL, "
                            + "queue_capacity INTEGER NOT NULL, state TEXT NOT NULL, "
                            + "overflow_first_sequence INTEGER NOT NULL, "
                            + "overflow_last_sequence INTEGER NOT NULL, "
                            + "overflow_count INTEGER NOT NULL, "
                            + "created_at_wall_ms INTEGER NOT NULL, "
                            + "updated_at_wall_ms INTEGER NOT NULL, PRIMARY KEY(cursor_id))");
            database.execSQL(
                    "INSERT INTO event_cursor_v3("
                            + "cursor_id, owner_fingerprint, client_subscription_id, "
                            + "topics_canonical, requested_after_sequence, "
                            + "acknowledged_sequence, queue_capacity, state, "
                            + "overflow_first_sequence, overflow_last_sequence, overflow_count, "
                            + "created_at_wall_ms, updated_at_wall_ms) "
                            + "SELECT cursor_id, owner_fingerprint, 'legacy:' || cursor_id, "
                            + "topic, last_sequence, last_sequence, 1, 'ACTIVE', 0, 0, 0, "
                            + "updated_at_wall_ms, updated_at_wall_ms FROM event_cursor");
            database.execSQL("DROP TABLE event_cursor");
            database.execSQL("ALTER TABLE event_cursor_v3 RENAME TO event_cursor");
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_event_cursor_owner_client "
                            + "ON event_cursor(owner_fingerprint, client_subscription_id)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE sessions ("
                            + "session_id TEXT NOT NULL, owner_fingerprint TEXT NOT NULL, "
                            + "client_request_id TEXT NOT NULL, request_digest TEXT NOT NULL, "
                            + "scenario_id TEXT NOT NULL, state INTEGER NOT NULL, "
                            + "active_plan_revision INTEGER NOT NULL, "
                            + "last_event_sequence INTEGER NOT NULL, "
                            + "created_at_wall_ms INTEGER NOT NULL, "
                            + "updated_at_wall_ms INTEGER NOT NULL, "
                            + "deadline_wall_ms INTEGER NOT NULL, summary TEXT NOT NULL, "
                            + "revision INTEGER NOT NULL, PRIMARY KEY(session_id))");
            database.execSQL(
                    "INSERT INTO sessions("
                            + "session_id, owner_fingerprint, client_request_id, request_digest, "
                            + "scenario_id, state, active_plan_revision, last_event_sequence, "
                            + "created_at_wall_ms, updated_at_wall_ms, deadline_wall_ms, "
                            + "summary, revision) "
                            + "SELECT session_id, owner_fingerprint, session_key, "
                            + "'0000000000000000000000000000000000000000000000000000000000000000', "
                            + "'legacy.runtime.session', "
                            + "CASE state "
                            + "WHEN 'COMPLETED' THEN 8 WHEN 'CANCELLED' THEN 10 "
                            + "ELSE 9 END, "
                            + "0, 0, created_at_wall_ms, updated_at_wall_ms, 0, "
                            + "'Migrated legacy runtime session', 1 FROM runtime_session");
            database.execSQL("DROP TABLE runtime_session");
            database.execSQL(
                    "CREATE UNIQUE INDEX index_sessions_owner_request "
                            + "ON sessions(owner_fingerprint, client_request_id)");
            database.execSQL(
                    "CREATE INDEX index_sessions_owner_updated "
                            + "ON sessions(owner_fingerprint, updated_at_wall_ms)");

            database.execSQL(
                    "CREATE TABLE plans ("
                            + "plan_id TEXT NOT NULL, session_id TEXT NOT NULL, "
                            + "revision INTEGER NOT NULL, state INTEGER NOT NULL, "
                            + "plan_digest TEXT NOT NULL, context_digest TEXT NOT NULL, "
                            + "manifest_digest TEXT NOT NULL, created_at_wall_ms INTEGER NOT NULL, "
                            + "updated_at_wall_ms INTEGER NOT NULL, PRIMARY KEY(plan_id), "
                            + "FOREIGN KEY(session_id) REFERENCES sessions(session_id) "
                            + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX index_plans_session ON plans(session_id)");
            database.execSQL(
                    "CREATE UNIQUE INDEX index_plans_session_revision "
                            + "ON plans(session_id, revision)");

            database.execSQL(
                    "CREATE TABLE plan_nodes ("
                            + "plan_id TEXT NOT NULL, node_id TEXT NOT NULL, "
                            + "node_type TEXT NOT NULL, state INTEGER NOT NULL, "
                            + "attempt_count INTEGER NOT NULL, deadline_wall_ms INTEGER NOT NULL, "
                            + "idempotency_key TEXT NOT NULL, checkpoint_ref TEXT NOT NULL, "
                            + "payload_digest TEXT NOT NULL, updated_at_wall_ms INTEGER NOT NULL, "
                            + "PRIMARY KEY(plan_id, node_id), "
                            + "FOREIGN KEY(plan_id) REFERENCES plans(plan_id) "
                            + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX index_plan_nodes_plan ON plan_nodes(plan_id)");
            database.execSQL(
                    "CREATE UNIQUE INDEX index_plan_nodes_idempotency "
                            + "ON plan_nodes(plan_id, idempotency_key)");

            database.execSQL(
                    "CREATE TABLE runtime_events ("
                            + "event_id TEXT NOT NULL, session_id TEXT NOT NULL, "
                            + "sequence INTEGER NOT NULL, parent_event_id TEXT NOT NULL, "
                            + "parent_sequence INTEGER NOT NULL, event_type TEXT NOT NULL, "
                            + "source INTEGER NOT NULL, occurred_at_wall_ms INTEGER NOT NULL, "
                            + "privacy_class INTEGER NOT NULL, payload_digest TEXT NOT NULL, "
                            + "event_digest TEXT NOT NULL, payload_kind INTEGER NOT NULL, "
                            + "payload_canonical TEXT NOT NULL, PRIMARY KEY(event_id), "
                            + "FOREIGN KEY(session_id) REFERENCES sessions(session_id) "
                            + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL(
                    "CREATE INDEX index_runtime_events_session ON runtime_events(session_id)");
            database.execSQL(
                    "CREATE UNIQUE INDEX index_runtime_events_session_sequence "
                            + "ON runtime_events(session_id, sequence)");
            database.execSQL(
                    "CREATE INDEX index_runtime_events_parent "
                            + "ON runtime_events(parent_event_id)");

            database.execSQL(
                    "CREATE TABLE effect_observations ("
                            + "effect_id TEXT NOT NULL, sequence INTEGER NOT NULL, "
                            + "observation_id TEXT NOT NULL, session_id TEXT NOT NULL, "
                            + "state INTEGER NOT NULL, source INTEGER NOT NULL, "
                            + "attempt_count INTEGER NOT NULL, target_digest TEXT NOT NULL, "
                            + "reported_digest TEXT NOT NULL, evidence_digest TEXT NOT NULL, "
                            + "observation_digest TEXT NOT NULL, terminal INTEGER NOT NULL, "
                            + "observed_at_wall_ms INTEGER NOT NULL, "
                            + "PRIMARY KEY(effect_id, sequence), "
                            + "FOREIGN KEY(session_id) REFERENCES sessions(session_id) "
                            + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL(
                    "CREATE INDEX index_effect_observations_session "
                            + "ON effect_observations(session_id)");
            database.execSQL(
                    "CREATE UNIQUE INDEX index_effect_observations_observation "
                            + "ON effect_observations(observation_id)");

            database.execSQL(
                    "CREATE TABLE compensations ("
                            + "compensation_id TEXT NOT NULL, session_id TEXT NOT NULL, "
                            + "effect_id TEXT NOT NULL, idempotency_key TEXT NOT NULL, "
                            + "before_snapshot_ref TEXT NOT NULL, "
                            + "compensation_digest TEXT NOT NULL, state INTEGER NOT NULL, "
                            + "expires_at_wall_ms INTEGER NOT NULL, "
                            + "created_at_wall_ms INTEGER NOT NULL, "
                            + "updated_at_wall_ms INTEGER NOT NULL, "
                            + "PRIMARY KEY(compensation_id), "
                            + "FOREIGN KEY(session_id) REFERENCES sessions(session_id) "
                            + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL(
                    "CREATE INDEX index_compensations_session ON compensations(session_id)");
            database.execSQL(
                    "CREATE UNIQUE INDEX index_compensations_idempotency "
                            + "ON compensations(idempotency_key)");
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
