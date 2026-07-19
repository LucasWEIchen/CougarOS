#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_callback_replay_device_evidence.json"
GUARD="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/TaskCallbackReplayGuard.java"
CLIENT="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainClient.java"
GUARD_TEST="central-brain/android-runtime/central-brain-sdk/src/test/java/com/centralbrain/sdk/TaskCallbackReplayGuardTest.java"
SDK_INSTRUMENTATION="central-brain/android-runtime/central-brain-sdk/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java"
DEMO_INSTRUMENTATION="central-brain/android-runtime/demo-hmi/src/androidTest/java/com/centralbrain/demo/test/CentralBrainBinderInstrumentation.java"
DEBUG_POLICY="central-brain/android-runtime/runtime-service/src/debug/res/xml/central_brain_capability_policy.xml"
MAIN_POLICY="central-brain/android-runtime/runtime-service/src/main/res/xml/central_brain_capability_policy.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DEVICE_TEST="tools/test_central_brain_android_callback_replay_security.sh"
DOCUMENTS=(
  "README.md"
  "central-brain/README.md"
  "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md"
  "docs/CENTRAL_BRAIN_SECURITY_REVIEW_FUZZ.md"
  "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
  "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md"
  "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
  "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md"
  "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"
  "docs/CENTRAL_BRAIN_ROADMAP.md"
  "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md"
  "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md"
  "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
  "docs/CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md"
)

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W03e marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$GUARD" "$CLIENT" "$GUARD_TEST" "$SDK_INSTRUMENTATION" \
    "$DEMO_INSTRUMENTATION" "$DEBUG_POLICY" "$MAIN_POLICY" "$RUNTIME" "$DEVICE_TEST"; do
  [[ -f "$ROOT_DIR/$file" ]] || { echo "P9-W03e file missing: $file" >&2; exit 1; }
done
bash -n "$ROOT_DIR/$DEVICE_TEST"

python3 -B - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("P9-W03e schema version changed")
if payload.get("profile_id") != "android13-p9-callback-replay-device-evidence-v1":
    raise SystemExit("P9-W03e profile changed")
if payload.get("work_package") != "P9-W03e":
    raise SystemExit("P9-W03e work package changed")
if payload.get("maturity") != "android_application_security_evidence":
    raise SystemExit("P9-W03e maturity changed")
boundary = payload.get("boundary", {})
if boundary != {
    "runtime_interface": "com.centralbrain.sdk.production.ICentralBrainRuntime",
    "callback_interface": "com.centralbrain.sdk.production.ICentralBrainTaskCallback",
    "owner_seed_package": "com.centralbrain.demo",
    "replay_probe_package": "com.centralbrain.sdk.test",
    "authorization_profile": "debug_resource_overlay_only",
    "release_test_principal_present": False,
    "raw_identity_published": False,
}:
    raise SystemExit("P9-W03e callback boundary changed")
expected_cases = [
    ("callback.same_owner_active_replay.v1", "SAME_TASK_STRICT_SEQUENCE_ONE_TERMINAL_PER_CALLBACK"),
    ("callback.idempotency_conflict.v1", "REJECTED_WITHOUT_CALLBACK_DELIVERY"),
    ("callback.same_owner_terminal_replay.v1", "SAME_TASK_ONE_TERMINAL"),
    ("callback.cross_uid_owner_replay.v1", "DISTINCT_TASK_NO_FOREIGN_CALLBACK"),
]
actual_cases = [(item.get("case_id"), item.get("expected")) for item in payload.get("cases", [])]
if actual_cases != expected_cases:
    raise SystemExit("P9-W03e cases changed")
if payload.get("device_requirements") != {
    "android_api": 33,
    "abi": "arm64-v8a",
    "distinct_owner_uids": True,
    "device_identity_redacted": True,
}:
    raise SystemExit("P9-W03e device requirements changed")
state = payload.get("claim_state", {})
required_true = {
    "security_task_callback_replay_android_verified",
    "security_callback_sequence_replay_suppressed",
    "security_callback_terminal_replay_unique",
    "security_idempotency_conflict_callback_silent",
    "security_cross_uid_callback_owner_isolation_verified",
    "security_distinct_callback_owner_uids_verified",
    "security_debug_test_principal_release_excluded",
}
required_false = {
    "security_coverage_guided_fuzz_complete",
    "security_production_signer_verified",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if set(state) != required_true | required_false:
    raise SystemExit("P9-W03e claim set changed")
if any(state[key] is not True for key in required_true):
    raise SystemExit("P9-W03e verified claim is false")
if any(state[key] is not False for key in required_false):
    raise SystemExit("P9-W03e forbidden claim was raised")
PY

for marker in \
  'update.sequence <= lastSequence' \
  'task update belongs to a different task' \
  'task completion was replayed' \
  'task failure was replayed'; do
  require_text "$GUARD" "$marker"
done
for marker in 'TaskCallbackReplayGuard' 'bindTaskId(handle.taskId)' 'MAX_PRE_BIND_CALLBACKS'; do
  require_text "$CLIENT" "$marker"
done
for marker in \
  'dropsDuplicateAndStaleSequencesWithoutRegressingState' \
  'rejectsCrossTaskAndMalformedCallbackPayloads' \
  'permitsOneTerminalAndDropsTerminalReplayOrLateUpdate'; do
  require_text "$GUARD_TEST" "$marker"
done
require_text "$RUNTIME" 'DurableTaskRepository.IdempotencyConflictException'
require_text "$DEBUG_POLICY" 'packageName="com.centralbrain.sdk.test"'
require_text "$DEBUG_POLICY" '<capability name="runtime.session.open" />'
require_text "$DEBUG_POLICY" '<capability name="runtime.event.subscribe.own" />'
require_text "$DEBUG_POLICY" '<capability name="debug.simulation.control" />'
if grep -Fq 'com.centralbrain.sdk.test' "$ROOT_DIR/$MAIN_POLICY"; then
  echo "P9-W03e test principal leaked into the main/release policy" >&2
  exit 1
fi
for marker in \
  'callbackReplayOwnerSeed' \
  'callback_replay_owner_seeded=true' \
  'callback_replay_owner_seed_terminal_unique=true'; do
  require_text "$DEMO_INSTRUMENTATION" "$marker"
done
for marker in \
  'callbackReplaySecurity' \
  'security_task_callback_replay_android_verified=true' \
  'security_callback_sequence_replay_suppressed=true' \
  'security_callback_terminal_replay_unique=true' \
  'security_idempotency_conflict_callback_silent=true' \
  'security_cross_uid_callback_owner_isolation_verified=true' \
  'security_distinct_callback_owner_uids_verified=true'; do
  require_text "$SDK_INSTRUMENTATION" "$marker"
  require_text "$DEVICE_TEST" "$marker"
done
for document in "${DOCUMENTS[@]}"; do
  require_text "$document" 'P9-W03e'
done

printf '%s\n' \
  'Central Brain Android P9-W03e callback replay check passed' \
  'security_task_callback_replay_android_verified=true' \
  'security_callback_sequence_replay_suppressed=true' \
  'security_callback_terminal_replay_unique=true' \
  'security_idempotency_conflict_callback_silent=true' \
  'security_cross_uid_callback_owner_isolation_verified=true' \
  'security_distinct_callback_owner_uids_verified=true' \
  'security_debug_test_principal_release_excluded=true' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_production_signer_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
