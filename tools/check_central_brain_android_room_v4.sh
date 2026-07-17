#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SES-001, S2-GRF-001, S2-EFF-001, S2-EVT-001,
# XSC-001/005/006, NV-G-003/004/006/007, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERSISTENCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence"
SESSION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/session"
DATABASE="$PERSISTENCE/CentralBrainDatabase.java"
DAO="$PERSISTENCE/RuntimeStateDao.java"
REGISTRY="$SESSION/DurableSessionRegistry.java"
SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/MigrationProbeActivity.java"
DEVICE_TEST="central-brain/android-runtime/central-brain-sdk/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java"
SCHEMA="central-brain/android-runtime/runtime-service/schemas/com.centralbrain.runtime.persistence.CentralBrainDatabase/4.json"
DEVICE_SCRIPT="tools/test_central_brain_android_session_durability.sh"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] || { echo "missing P1-W06 file: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing P1-W06 marker '$2' in $1" >&2; exit 1; }
}

for name in \
  SessionEntity.java PlanEntity.java PlanNodeEntity.java RuntimeEventEntity.java \
  EffectObservationEntity.java CompensationEntity.java; do
  require_file "$PERSISTENCE/$name"
done
for path in "$DATABASE" "$DAO" "$REGISTRY" "$SERVICE" "$PROBE" "$DEVICE_TEST" "$SCHEMA" "$DEVICE_SCRIPT"; do
  require_file "$path"
done
require_text "$DEVICE_SCRIPT" "RuntimeFaultProbeReceiver"
require_text "$DEVICE_SCRIPT" "durable_terminal_state_immutable=true"

require_text "$DATABASE" "VERSION = 4"
require_text "$DATABASE" "MIGRATION_3_4"
require_text "$DATABASE" "DROP TABLE runtime_session"
require_text "$DATABASE" "ELSE 9 END"
require_text "$DATABASE" "addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)"
for table in sessions plans plan_nodes runtime_events effect_observations compensations; do
  require_text "$DATABASE" "CREATE TABLE $table"
done

for marker in \
  "findSession(String sessionId)" \
  "findSessionByOwnerAndRequest" \
  "listSessionsOwned" \
  "request_digest != '0000000000000000000000000000000000000000000000000000000000000000'" \
  "listRuntimeEvents" \
  "deleteTerminalSession"; do
  require_text "$DAO" "$marker"
done
for marker in \
  "MAX_CANONICAL_PAYLOAD_BYTES = 8192" \
  "database.runInTransaction" \
  "requestId idempotency conflict" \
  "active session capacity exhausted" \
  "typed payload encoder is not published" \
  "Raw utterances and Binder objects are never persisted"; do
  require_text "$REGISTRY" "$marker"
done

require_text "$SERVICE" "DurableSessionRegistry.create(database)"
require_text "$SERVICE" 'session_runtime_persistence_wired=true'
require_text "$SERVICE" 'session_runtime_process_death_rehydration=true'
require_text "$SERVICE" 'scenario_execution_enabled=false'
for marker in \
  "room_migration_3_4_verified=" \
  "legacy_runtime_session_preserved=" \
  "legacy_session_v1_exposure_blocked=" \
  "room_schema_v4_verified=" \
  "room_v4_foreign_keys_verified=" \
  "room_v4_query_index_verified=" \
  "room_v4_crash_transaction_rollback_verified="; do
  require_text "$PROBE" "$marker"
done
for marker in \
  "durableSeed" \
  "durableVerify" \
  "durable_session_identity_preserved=true" \
  "durable_event_replay_after_process_death=true" \
  "durable_terminal_state_immutable=true" \
  "hardware_accessed=false"; do
  require_text "$DEVICE_TEST" "$marker"
done

python3 - "$ROOT_DIR/$SCHEMA" <<'PY'
import json
import pathlib
import sys

database = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))["database"]
if database.get("version") != 4:
    raise SystemExit("Room current schema must be version 4")
entities = {entity["tableName"]: entity for entity in database.get("entities", [])}
expected = {
    "sessions", "plans", "plan_nodes", "runtime_events", "effect_observations",
    "compensations", "runtime_task", "task_checkpoint", "pending_effect",
    "effect_outbox", "approval_request", "audit_event", "event_cursor",
}
if set(entities) != expected:
    raise SystemExit(f"Room v4 table set mismatch: {sorted(entities)}")

foreign_keys = {
    table: {key["table"] for key in entity.get("foreignKeys", [])}
    for table, entity in entities.items()
}
for table in ("plans", "runtime_events", "effect_observations", "compensations"):
    if foreign_keys[table] != {"sessions"}:
        raise SystemExit(f"{table} must cascade from sessions")
if foreign_keys["plan_nodes"] != {"plans"}:
    raise SystemExit("plan_nodes must cascade from plans")

unique_indices = {
    table: {
        index["name"]
        for index in entity.get("indices", [])
        if index.get("unique") is True
    }
    for table, entity in entities.items()
}
required_unique = {
    "sessions": "index_sessions_owner_request",
    "plans": "index_plans_session_revision",
    "plan_nodes": "index_plan_nodes_idempotency",
    "runtime_events": "index_runtime_events_session_sequence",
    "effect_observations": "index_effect_observations_observation",
    "compensations": "index_compensations_idempotency",
}
for table, name in required_unique.items():
    if name not in unique_indices[table]:
        raise SystemExit(f"missing Room v4 unique index {name}")

forbidden = ("utterance", "token_stream", "raw_model", "binder", "native_pointer", "signing_material")
for table, entity in entities.items():
    columns = {field["columnName"] for field in entity.get("fields", [])}
    for column in columns:
        if any(token in column for token in forbidden):
            raise SystemExit(f"forbidden durable field {table}.{column}")
PY

for manifest in v1.sha256 session-v1.sha256 plan-v1.sha256 events-v1.sha256 effect-v1.sha256; do
  (cd "$ROOT_DIR" && sha256sum -c \
    "central-brain/android-runtime/central-brain-sdk/aidl-api/$manifest" >/dev/null)
done

for doc in \
  README.md \
  central-brain/android-runtime/README.md \
  docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md \
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md; do
  require_text "$doc" "P1-W06"
done
require_text README.md "implementation_stage=P2-W01"
require_text docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md \
  '### `P1-W06` Room v4 schema'
require_text docs/CENTRAL_BRAIN_ROADMAP.md \
  '`P1-W07 Contract v2 aggregate check` 已完成'
require_text docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md \
  'P1-W06 Room v4 Driver/HAL Boundary'

printf '%s\n' \
  'Central Brain Android Room v4 check passed' \
  'room_schema_version=4' \
  'room_migration_3_4_verified=true' \
  'legacy_session_v1_exposure_blocked=true' \
  'session_runtime_persistence_wired=true' \
  'session_runtime_process_death_rehydration=true' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false'
