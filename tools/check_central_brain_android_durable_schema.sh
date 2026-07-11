#!/usr/bin/env bash
set -euo pipefail

# Req IDs: FW-U-004, NV-F-001, NV-G-006, NV-G-007, XSC-005, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERSISTENCE_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence"
SCHEMA="central-brain/android-runtime/runtime-service/schemas/com.centralbrain.runtime.persistence.CentralBrainDatabase/2.json"
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
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android durable schema pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for name in \
  RuntimeSessionEntity.java \
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
require_file "$SCHEMA"
require_file "$PROBE"

require_text "central-brain/android-runtime/gradle/libs.versions.toml" 'room = "2.8.4"'
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" "libs.androidx.room.runtime"
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" "libs.androidx.room.compiler"
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" "room.schemaLocation"
require_text "$DATABASE" "version = CentralBrainDatabase.VERSION"
require_text "$DATABASE" "MIGRATION_1_2"
require_text "$DATABASE" "JournalMode.WRITE_AHEAD_LOGGING"
require_text "$DATABASE" "index_runtime_task_owner_idempotency"
require_text "$DATABASE" "index_approval_owner_idempotency"
require_text "$DATABASE" "index_pending_effect_idempotency"
require_text "$DATABASE" "index_effect_outbox_effect"
require_text "$PROBE" "room_migration_1_2_verified="
require_text "$PROBE" "durable_dispatch_enabled=false"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".persistence.MigrationProbeActivity"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'
require_text "tools/install_central_brain_android_runtime.sh" "room_migration_1_2_verified=true"
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

python3 - "$ROOT_DIR/$SCHEMA" <<'PY'
import json
import pathlib
import sys

schema = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
database = schema.get("database", {})
if database.get("version") != 2:
    raise SystemExit("Room schema version must be 2")
entities = {item["tableName"]: item for item in database.get("entities", [])}
expected = {
    "runtime_session",
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

required_unique = {
    "runtime_session": {"index_runtime_session_owner_key"},
    "runtime_task": {"index_runtime_task_owner_idempotency"},
    "task_checkpoint": {"index_task_checkpoint_task_sequence"},
    "pending_effect": {"index_pending_effect_idempotency"},
    "effect_outbox": {"index_effect_outbox_effect"},
    "approval_request": {"index_approval_owner_idempotency"},
    "audit_event": {"index_audit_event_id"},
    "event_cursor": {"index_event_cursor_owner_topic"},
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
PY

require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4A Room durable schema trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R4A Durable Schema Interfaces"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4A Room Durable Schema"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4A Room Schema Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R4A 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R4A 进展"

echo "Central Brain Android durable schema check passed"
