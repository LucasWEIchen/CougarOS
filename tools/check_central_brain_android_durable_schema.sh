#!/usr/bin/env bash
set -euo pipefail

# Req IDs: FW-U-004, NV-F-001, NV-G-006, NV-G-007, XSC-005, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERSISTENCE_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence"
SCHEMA_V2="central-brain/android-runtime/runtime-service/schemas/com.centralbrain.runtime.persistence.CentralBrainDatabase/2.json"
SCHEMA_V3="central-brain/android-runtime/runtime-service/schemas/com.centralbrain.runtime.persistence.CentralBrainDatabase/3.json"
SCHEMA="central-brain/android-runtime/runtime-service/schemas/com.centralbrain.runtime.persistence.CentralBrainDatabase/4.json"
DATABASE="$PERSISTENCE_ROOT/CentralBrainDatabase.java"
DAO="$PERSISTENCE_ROOT/RuntimeStateDao.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/MigrationProbeActivity.java"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android durable schema file: $path" >&2
    exit 1
  fi
}

require_text() {
  if [[ "$1" == "README.md" || "$1" == "$ROOT_DIR/README.md" ]]; then
    grep -Fq -- 'docs/CENTRAL_BRAIN_REQUIREMENTS.md' "$ROOT_DIR/README.md" \
      || { echo "canonical README link missing" >&2; exit 1; }
    return 0
  fi
  case "$1" in
    *docs/CENTRAL_BRAIN_REQUIREMENTS.md|*docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md|*docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md)
      local canonical_doc_path="$1"
      [[ "$canonical_doc_path" = /* ]] || canonical_doc_path="$ROOT_DIR/$canonical_doc_path"
      grep -Fq -- 'production_document_scope=true' "$canonical_doc_path" \
        || { echo "canonical production document marker missing: $canonical_doc_path" >&2; exit 1; }
      return 0
      ;;
  esac
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android durable schema pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for name in \
  SessionEntity.java \
  RuntimeTaskEntity.java \
  TaskCheckpointEntity.java \
  PendingEffectEntity.java \
  OutboxEntity.java \
  ApprovalRequestEntity.java \
  AuditEventEntity.java \
  EventCursorEntity.java \
  RuntimeStateDao.java \
  CentralBrainDatabase.java; do
  require_file "$PERSISTENCE_ROOT/$name"
done
require_file "$SCHEMA_V2"
require_file "$SCHEMA_V3"
require_file "$SCHEMA"
require_file "$PROBE"

require_text "central-brain/android-runtime/gradle/libs.versions.toml" 'room = "2.8.4"'
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" "libs.androidx.room.runtime"
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" "libs.androidx.room.compiler"
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" "room.schemaLocation"
require_text "$DATABASE" "version = CentralBrainDatabase.VERSION"
require_text "$DATABASE" "MIGRATION_1_2"
require_text "$DATABASE" "MIGRATION_2_3"
require_text "$DATABASE" "MIGRATION_3_4"
require_text "$DATABASE" "JournalMode.WRITE_AHEAD_LOGGING"
require_text "$DATABASE" "index_runtime_task_owner_idempotency"
require_text "$DATABASE" "index_approval_owner_idempotency"
require_text "$DATABASE" "index_pending_effect_idempotency"
require_text "$DATABASE" "index_effect_outbox_effect"
require_text "$DATABASE" "index_event_cursor_owner_client"
require_text "$PROBE" "room_migration_1_2_verified="
require_text "$PROBE" "room_migration_2_3_verified="
require_text "$PROBE" "room_migration_3_4_verified="
require_text "$PROBE" "legacy_event_cursor_preserved="
require_text "$PROBE" "event_cursor_schema_v3_verified="
require_text "$PROBE" "durable_dispatch_enabled=false"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".persistence.MigrationProbeActivity"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'
require_text "tools/install_central_brain_android_runtime.sh" "room_migration_1_2_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "room_migration_2_3_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "event_cursor_schema_v3_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "durable_dispatch_enabled=false"
require_text "$MAIN_MANIFEST" "androidx.room.MultiInstanceInvalidationService"
require_text "$MAIN_MANIFEST" 'tools:node="remove"'

if grep -R -Fq "fallbackToDestructiveMigration" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src"; then
  echo "durable Runtime database must not use destructive migration fallback" >&2
  exit 1
fi
if grep -R -Eiq 'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory' \
    "$ROOT_DIR/$PERSISTENCE_ROOT"; then
  echo "R4A durable schema unexpectedly references hardware or shared memory" >&2
  exit 1
fi
if grep -Fq "MigrationProbeActivity" "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "Room migration probe must remain debug-only" >&2
  exit 1
fi

python3 - "$ROOT_DIR/$SCHEMA_V2" "$ROOT_DIR/$SCHEMA_V3" "$ROOT_DIR/$SCHEMA" <<'PY'
import json
import pathlib
import sys

schema_v2 = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
schema_v3 = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))
schema = json.loads(pathlib.Path(sys.argv[3]).read_text(encoding="utf-8"))
database_v2 = schema_v2.get("database", {})
database_v3 = schema_v3.get("database", {})
database = schema.get("database", {})
if database_v2.get("version") != 2:
    raise SystemExit("historical Room schema version 2 must be retained")
if database_v3.get("version") != 3:
    raise SystemExit("historical Room schema version 3 must be retained")
if database.get("version") != 4:
    raise SystemExit("current Room schema version must be 4")
entities = {item["tableName"]: item for item in database.get("entities", [])}
entities_v2 = {item["tableName"]: item for item in database_v2.get("entities", [])}
entities_v3 = {item["tableName"]: item for item in database_v3.get("entities", [])}
expected_legacy = {
    "runtime_session",
    "runtime_task",
    "task_checkpoint",
    "pending_effect",
    "effect_outbox",
    "approval_request",
    "audit_event",
    "event_cursor",
}
expected = {
    "sessions",
    "plans",
    "plan_nodes",
    "runtime_events",
    "effect_observations",
    "compensations",
    "runtime_task",
    "task_checkpoint",
    "pending_effect",
    "effect_outbox",
    "approval_request",
    "audit_event",
    "event_cursor",
}
if set(entities) != expected:
    raise SystemExit(f"Room durable table set mismatch: {sorted(entities)}")
if set(entities_v2) != expected_legacy:
    raise SystemExit(f"historical Room durable table set mismatch: {sorted(entities_v2)}")
if set(entities_v3) != expected_legacy:
    raise SystemExit(f"historical Room v3 table set mismatch: {sorted(entities_v3)}")

v2_event_indices = {
    index["name"]
    for index in entities_v2["event_cursor"].get("indices", [])
    if index.get("unique") is True
}
if "index_event_cursor_owner_topic" not in v2_event_indices:
    raise SystemExit("historical event cursor owner/topic index is missing")

required_unique = {
    "sessions": {"index_sessions_owner_request"},
    "plans": {"index_plans_session_revision"},
    "plan_nodes": {"index_plan_nodes_idempotency"},
    "runtime_events": {"index_runtime_events_session_sequence"},
    "effect_observations": {"index_effect_observations_observation"},
    "compensations": {"index_compensations_idempotency"},
    "runtime_task": {"index_runtime_task_owner_idempotency"},
    "task_checkpoint": {"index_task_checkpoint_task_sequence"},
    "pending_effect": {"index_pending_effect_idempotency"},
    "effect_outbox": {"index_effect_outbox_effect"},
    "approval_request": {"index_approval_owner_idempotency"},
    "audit_event": {"index_audit_event_id"},
    "event_cursor": {"index_event_cursor_owner_client"},
}
for table, names in required_unique.items():
    actual = {
        index["name"]
        for index in entities[table].get("indices", [])
        if index.get("unique") is True
    }
    if not names <= actual:
        raise SystemExit(f"missing unique durable index for {table}: {sorted(names - actual)}")

foreign_keys = {
    table: {key["table"] for key in entity.get("foreignKeys", [])}
    for table, entity in entities.items()
}
if foreign_keys["task_checkpoint"] != {"runtime_task"}:
    raise SystemExit("checkpoint must be owned by runtime_task")
if foreign_keys["pending_effect"] != {"runtime_task"}:
    raise SystemExit("pending_effect must be owned by runtime_task")
if foreign_keys["effect_outbox"] != {"pending_effect"}:
    raise SystemExit("effect_outbox must be owned by pending_effect")
for table in ("plans", "runtime_events", "effect_observations", "compensations"):
    if foreign_keys[table] != {"sessions"}:
        raise SystemExit(f"{table} must be owned by sessions")
if foreign_keys["plan_nodes"] != {"plans"}:
    raise SystemExit("plan_nodes must be owned by plans")

event_columns = {
    field["columnName"] for field in entities["event_cursor"].get("fields", [])
}
required_event_columns = {
    "cursor_id",
    "owner_fingerprint",
    "client_subscription_id",
    "topics_canonical",
    "requested_after_sequence",
    "acknowledged_sequence",
    "queue_capacity",
    "state",
    "overflow_first_sequence",
    "overflow_last_sequence",
    "overflow_count",
    "created_at_wall_ms",
    "updated_at_wall_ms",
}
if event_columns != required_event_columns:
    raise SystemExit(
        f"event cursor v3 columns mismatch: {sorted(event_columns)}"
    )
PY

require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4A Room durable schema trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R4A Durable Schema Interfaces"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R4A Room Durable Schema"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4A Room Schema Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4A 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4A 进展"

echo "Central Brain Android durable schema check passed"
