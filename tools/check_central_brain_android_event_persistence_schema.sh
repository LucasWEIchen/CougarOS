#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-002/004/005, FW-U-003, NV-G-006/007, NV-P-002/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERSISTENCE_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence"
DATABASE="$PERSISTENCE_ROOT/CentralBrainDatabase.java"
ENTITY="$PERSISTENCE_ROOT/EventCursorEntity.java"
DAO="$PERSISTENCE_ROOT/RuntimeStateDao.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/MigrationProbeActivity.java"
SCHEMA="central-brain/android-runtime/runtime-service/schemas/com.centralbrain.runtime.persistence.CentralBrainDatabase/3.json"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Event persistence schema file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android Event persistence schema pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$DATABASE" "$ENTITY" "$DAO" "$PROBE" "$SCHEMA" "$INSTALLER"; do
  require_file "$path"
done

require_text "$DATABASE" "VERSION = 4"
require_text "$DATABASE" "MIGRATION_2_3"
require_text "$DATABASE" "CREATE TABLE event_cursor_v3"
require_text "$DATABASE" "'legacy:' || cursor_id"
require_text "$DATABASE" "index_event_cursor_owner_client"
require_text "$ENTITY" 'name = "index_event_cursor_owner_client"'
require_text "$ENTITY" 'name = "client_subscription_id"'
require_text "$ENTITY" 'name = "topics_canonical"'
require_text "$ENTITY" 'name = "acknowledged_sequence"'
require_text "$ENTITY" 'name = "overflow_count"'
require_text "$DAO" "findEventCursorByOwnerAndClient("
require_text "$DAO" "updateEventCursor("

require_text "$PROBE" "room_migration_2_3_verified="
require_text "$PROBE" "room_schema_version="
require_text "$PROBE" "legacy_event_cursor_preserved="
require_text "$PROBE" "event_cursor_schema_v3_verified="
require_text "$PROBE" "event_cursor_schema_ready="
require_text "$PROBE" "event_cursor_repository_wired=false"
require_text "$INSTALLER" "room_migration_2_3_verified=true"
require_text "$INSTALLER" "room_schema_version=4"
require_text "$INSTALLER" "legacy_event_cursor_preserved=true"
require_text "$INSTALLER" "event_cursor_schema_v3_verified=true"
require_text "$INSTALLER" "event_cursor_schema_ready=true"
require_text "$INSTALLER" "event_cursor_repository_wired=false"

python3 - "$ROOT_DIR/$SCHEMA" <<'PY'
import json
import pathlib
import sys

database = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))["database"]
if database["version"] != 3:
    raise SystemExit("Event persistence schema must be version 3")
if len(database.get("entities", [])) != 8:
    raise SystemExit("R6A2A must preserve the eight-table artifact shape")
event = next(
    (item for item in database["entities"] if item["tableName"] == "event_cursor"),
    None,
)
if event is None:
    raise SystemExit("event_cursor table is missing")
columns = {field["columnName"] for field in event.get("fields", [])}
required = {
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
if columns != required:
    raise SystemExit(f"Event persistence columns mismatch: {sorted(columns)}")
indices = {
    index["name"]
    for index in event.get("indices", [])
    if index.get("unique") is True
}
if indices != {"index_event_cursor_owner_client"}:
    raise SystemExit(f"Event persistence unique index mismatch: {sorted(indices)}")
if any(token in " ".join(columns) for token in ("payload", "utterance", "model_output")):
    raise SystemExit("Event persistence schema must not store raw payload content")
PY

for service in \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java; do
  if grep -Eq 'EventCursorEntity|findEventCursor|insertEventCursor|updateEventCursor' \
      "$ROOT_DIR/$service"; then
    echo "R6A2A Event persistence schema must not be wired into production Services" >&2
    exit 1
  fi
done

require_text "central-brain/android-runtime/README.md" "R6A2A Durable Event Schema"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R6A2A durable Event schema"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R6A2A durable Event schema trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R6A2A Durable Event Schema"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R6A2A Durable Event Schema"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R6A2A Event Schema Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R6A2A 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R6A2A 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R6A2A durable Event schema"

echo "Central Brain Android durable Event schema check passed"
