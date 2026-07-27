#!/usr/bin/env bash
set -euo pipefail

# Req IDs: P1 aggregate, APP-004, S2-SES-001, S2-GRF-001, S2-EFF-001,
# S2-EVT-001, XSC-001/005/006, NV-G-003/004/006/007, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_runtime_contract_v2.json"
SDK="central-brain/android-runtime/central-brain-sdk"
RUNTIME="central-brain/android-runtime/runtime-service"
JAVA_CONTRACT="$SDK/src/main/java/com/centralbrain/sdk/RuntimeContractV2.java"
JAVA_TEST="$SDK/src/test/java/com/centralbrain/sdk/RuntimeContractV2Test.java"
SESSION_AIDL="$SDK/src/main/aidl/com/centralbrain/sdk/session/ICentralBrainSessionRuntime.aidl"
EVENT_AIDL="$SDK/src/main/aidl/com/centralbrain/sdk/event/ICentralBrainSessionEvents.aidl"
EVENT_V2_AIDL="$SDK/src/main/aidl/com/centralbrain/sdk/event/ICentralBrainSessionEventsV2.aidl"
EVENT_PAGE="$SDK/src/main/aidl/com/centralbrain/sdk/event/EventPage.aidl"
ROOM_SCHEMA="$RUNTIME/schemas/com.centralbrain.runtime.persistence.CentralBrainDatabase/4.json"
POLICY="$RUNTIME/src/main/res/xml/central_brain_capability_policy.xml"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] || { echo "missing Runtime Contract v2 file: $1" >&2; exit 1; }
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
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing Runtime Contract v2 marker '$2' in $1" >&2; exit 1; }
}

for path in \
  "$CONTRACT" "$JAVA_CONTRACT" "$JAVA_TEST" "$SESSION_AIDL" "$EVENT_AIDL" "$EVENT_V2_AIDL" \
  "$EVENT_PAGE" "$ROOM_SCHEMA" "$POLICY" \
  "$SDK/src/test/java/com/centralbrain/sdk/SessionClientTest.java" \
  "$SDK/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java"; do
  require_file "$path"
done

python3 -B - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$ROOM_SCHEMA" \
  "$ROOT_DIR/$SESSION_AIDL" "$ROOT_DIR/$EVENT_AIDL" "$ROOT_DIR/$EVENT_V2_AIDL" <<'PY'
import json
import pathlib
import re
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
room = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))["database"]
session_aidl = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")
event_aidl = pathlib.Path(sys.argv[4]).read_text(encoding="utf-8")
event_v2_aidl = pathlib.Path(sys.argv[5]).read_text(encoding="utf-8")

if contract.get("schema_version") != "2.0.0":
    raise SystemExit("aggregate Runtime contract must be schema 2.0.0")
if contract.get("contract_id") != "central-brain.runtime.aggregate.v2":
    raise SystemExit("aggregate Runtime contract id drift")
if contract.get("status") != "contract_defined":
    raise SystemExit("aggregate Runtime contract maturity must remain contract_defined")

wires = contract.get("wire_contracts", {})
if set(wires) != {"session", "plan", "event", "event_v2", "effect"}:
    raise SystemExit("aggregate wire contract set drift")
for name in ("session", "plan", "event", "effect"):
    if wires[name].get("version") != 1:
        raise SystemExit(f"{name} V1 wire/DTO version changed")
if wires["event_v2"].get("version") != 2:
    raise SystemExit("Event V2 wire version drift")
if wires["plan"].get("published") or wires["effect"].get("published"):
    raise SystemExit("aggregate contract must not publish Plan or Effect execution")
if (not wires["session"].get("published")
        or not wires["event"].get("published")
        or not wires["event_v2"].get("published")):
    raise SystemExit("published Session/Event surfaces missing from aggregate contract")

def aidl_value(text, name):
    match = re.search(rf'{name}\s*=\s*"?([0-9a-f]+)"?;', text)
    if not match:
        raise SystemExit(f"missing AIDL constant {name}")
    return match.group(1)

if int(aidl_value(session_aidl, "INTERFACE_VERSION")) != wires["session"]["version"]:
    raise SystemExit("Session interface version drift")
if aidl_value(session_aidl, "INTERFACE_HASH") != wires["session"]["interface_hash"]:
    raise SystemExit("Session interface hash drift")
if int(aidl_value(event_aidl, "INTERFACE_VERSION")) != wires["event"]["version"]:
    raise SystemExit("Event interface version drift")
if aidl_value(event_aidl, "INTERFACE_HASH") != wires["event"]["interface_hash"]:
    raise SystemExit("Event interface hash drift")
if int(aidl_value(event_v2_aidl, "INTERFACE_VERSION")) != wires["event_v2"]["version"]:
    raise SystemExit("Event V2 interface version drift")
if aidl_value(event_v2_aidl, "INTERFACE_HASH") != wires["event_v2"]["interface_hash"]:
    raise SystemExit("Event V2 interface hash drift")

capabilities = contract["capabilities"]
expected_capabilities = {
    "runtime.session.protocol.read",
    "runtime.session.open",
    "runtime.session.read.own",
    "runtime.session.cancel.own",
    "runtime.event.protocol.read",
    "runtime.event.read.own",
    "runtime.event.subscribe.own",
}
if set(capabilities.get("published", [])) != expected_capabilities:
    raise SystemExit("aggregate capability set drift")
if capabilities.get("owner_source") != "binder_uid_package_current_signer":
    raise SystemExit("Binder owner source drift")
for key in (
    "caller_identity_in_payload_allowed",
    "plan_runtime_published",
    "effect_runtime_published",
    "approval_response_published",
    "undo_runtime_published",
):
    if capabilities.get(key) is not False:
        raise SystemExit(f"unsafe aggregate capability state: {key}")

errors = contract["error_contract"]
expected_errors = {
    "NOT_CONNECTED", "PROTOCOL_MISMATCH", "TRANSPORT", "SUBSCRIPTION", "CLOSED"
}
if set(errors.get("sdk_failure_codes", [])) != expected_errors:
    raise SystemExit("stable SDK failure-code set drift")
if errors.get("contract_violation_java_type") != "IllegalArgumentException":
    raise SystemExit("contract violation Java type drift")
if errors.get("authorization_failure_java_type") != "SecurityException":
    raise SystemExit("authorization error Java type drift")
if errors.get("raw_remote_exception_public") is not False:
    raise SystemExit("raw RemoteException cannot be public")

bounds = contract["bounds"]
expected_bounds = {
    "session_page_items": 50,
    "event_page_items": 100,
    "cursor_chars": 256,
    "replay_page_limit": 64,
    "callbacks_per_session": 4,
    "callbacks_total": 128,
    "durable_sessions": 64,
    "durable_events_per_session": 8,
    "durable_event_payload_utf8_bytes": 8192,
    "binder_protocol_latency_target_ms": 10,
    "binder_open_latency_target_ms": 50,
    "binder_read_cancel_latency_target_ms": 30,
    "binder_callback_registration_latency_target_ms": 50,
}
if bounds != expected_bounds:
    raise SystemExit("aggregate payload/page/callback/latency bounds drift")

persistence = contract["persistence"]
if room.get("version") != 4 or len(room.get("entities", [])) != 13:
    raise SystemExit("Room schema is not v4/13-table")
if persistence.get("room_schema_version") != 4 or persistence.get("room_table_count") != 13:
    raise SystemExit("aggregate Room identity drift")
if persistence.get("session_event_repository") != "DurableSessionRegistry":
    raise SystemExit("aggregate durable repository identity drift")
if persistence.get("binder_callback_persisted") is not False:
    raise SystemExit("Binder callbacks cannot be durable rows")
if persistence.get("raw_utterance_persisted") is not False:
    raise SystemExit("raw utterance persistence is forbidden")

cursor = contract["event_cursor_evolution"]
if cursor.get("v1_terminal_page_resume_cursor") is not False:
    raise SystemExit("Event V1 terminal cursor limitation was hidden")
if cursor.get("decision") != "separate_event_v2_cursor_ack_contract_implemented":
    raise SystemExit("Event cursor evolution decision drift")
if cursor.get("v2_interface_published") is not True:
    raise SystemExit("Event V2 publication state drift")
required_v2 = {
    "terminal_page_resume_cursor",
    "explicit_monotonic_ack",
    "owner_scoped_cursor",
    "session_bound_cursor",
    "bounded_ack_retention",
    "stale_and_future_cursor_rejection",
}
if set(cursor.get("v2_required_semantics", [])) != required_v2:
    raise SystemExit("Event V2 cursor/ACK semantics drift")
if cursor.get("production_event_broker_ready") is not False:
    raise SystemExit("aggregate contract cannot claim a production Event broker")

compatibility = contract["compatibility"]
if compatibility.get("frozen_v1_hashes_unchanged") is not True:
    raise SystemExit("V1 hash freeze missing")
for key in (
    "aggregate_v2_changes_v1_transaction_order",
    "client_binder_primitives_public",
    "network_or_python_gateway_fallback_allowed",
):
    if compatibility.get(key) is not False:
        raise SystemExit(f"unsafe aggregate compatibility state: {key}")
claims = contract["claim_state"]
if claims.get("aggregate_contract_v2_physical_android13_arm64_verified") is not True:
    raise SystemExit("aggregate Android 13 instrumentation evidence marker missing")
for key in (
    "scenario_execution_enabled", "hardware_accessed", "production_ready",
    "target_hardware_validated",
):
    if claims.get(key) is not False:
        raise SystemExit(f"aggregate contract overclaims {key}")
PY

for manifest in v1.sha256 governance-v1.sha256 session-v1.sha256 plan-v1.sha256 events-v1.sha256 effect-v1.sha256; do
  (cd "$ROOT_DIR" && sha256sum -c "$SDK/aidl-api/$manifest" >/dev/null)
done

for capability in \
  runtime.session.protocol.read runtime.session.open runtime.session.read.own \
  runtime.session.cancel.own runtime.event.protocol.read runtime.event.read.own \
  runtime.event.subscribe.own; do
  require_text "$POLICY" "$capability"
done

for marker in \
  'AGGREGATE_VERSION = 2' \
  'SESSION_WIRE_VERSION = ICentralBrainSessionRuntime.INTERFACE_VERSION' \
  'EVENT_WIRE_VERSION = ICentralBrainSessionEvents.INTERFACE_VERSION' \
  'EVENT_V2_WIRE_VERSION =' \
  'EVENT_V2_CURSOR_ACK_REQUIRED = true' \
  'EVENT_V2_INTERFACE_PUBLISHED = true' \
  'SCENARIO_EXECUTION_ENABLED = false'; do
  require_text "$JAVA_CONTRACT" "$marker"
done
for code in NOT_CONNECTED PROTOCOL_MISMATCH TRANSPORT SUBSCRIPTION CLOSED; do
  require_text "$JAVA_CONTRACT" "ERROR_${code}"
  require_text "$SDK/src/main/java/com/centralbrain/sdk/ScenarioClient.java" \
    "RuntimeContractV2.ERROR_${code}"
done
for marker in \
  'aggregateIdentityPreservesFrozenWireVersionsAndBounds' \
  'stableFacadeErrorsRemainUniqueAndAliased' \
  'eventCursorEvolutionIsFailClosedAndSeparatelyVersioned'; do
  require_text "$JAVA_TEST" "$marker"
done
for marker in \
  'verifyRuntimeContractV2()' \
  'runtime_contract_v2_physical_android13_arm64_verified=true' \
  'event_v2_interface_published=true'; do
  require_text "$SDK/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java" \
    "$marker"
done

require_text "$EVENT_PAGE" 'String nextCursor = "";'
require_text "$SDK/src/main/java/com/centralbrain/sdk/event/EventContract.java" \
  'terminal page must not expose a next cursor'
require_text "$SDK/src/main/java/com/centralbrain/sdk/SessionClient.java" \
  'event replay page limit exceeded'
if grep -R -Eiq \
    'HttpURLConnection|OkHttpClient|http://10\.0\.2\.2|localhost:[0-9]+|127\.0\.0\.1:[0-9]+|python gateway' \
    "$ROOT_DIR/$SDK/src/main"; then
  echo "SDK contains a forbidden network/Python fallback" >&2
  exit 1
fi
if grep -Fq 'android.permission.INTERNET' "$ROOT_DIR/$SDK/src/main/AndroidManifest.xml"; then
  echo "SDK must not request INTERNET" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_android_session_contract.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_plan_contract.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_event_contract.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_event_v2.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_effect_contract.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_sdk_facade.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_room_v4.sh" >/dev/null

for doc in \
  README.md central-brain/android-runtime/README.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md; do
  require_text "$doc" 'P1-W07'
done

printf '%s\n' \
  'Central Brain Runtime Contract v2 aggregate check passed' \
  'runtime_contract_v2_defined=true' \
  'runtime_contract_v2_verified=true' \
  'frozen_v1_hashes_unchanged=true' \
  'room_schema_version=4' \
  'event_v2_cursor_ack_required=true' \
  'event_v2_interface_published=true' \
  'forbidden_network_python_fallback=false' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false'
