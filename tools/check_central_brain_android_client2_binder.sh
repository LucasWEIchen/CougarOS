#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-001, S2-HMI-005, APP-004, XSC-001/005/006,
# NV-G-006, NV-P-002, DEL-001/003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="apk-labs/client2-central-brain"
BRIDGE="$PROJECT/bridge/src/com/centralbrain/client2/Client2ScenarioBridge.java"
CALLBACK="$PROJECT/bridge/src/com/centralbrain/client2/ScenarioCallback.java"
CONTROLLER="$PROJECT/patches/smali/com/tuanjie/urasclient2/CentralBrainPanelController.smali"
LAYOUT="$PROJECT/patches/main_layout.central_brain_panel.xml"
PATCHER="$PROJECT/scripts/apply_static_panel_patch.py"
DEX_BUILD="$PROJECT/scripts/build_binder_bridge_dex.sh"
APK_BUILD="$PROJECT/scripts/build_debug_apk.sh"
PROJECT_VERIFY="$PROJECT/scripts/verify_project.sh"
DEVICE_TEST="tools/test_client2_central_brain_binder.sh"
RECOVERY_TEST="tools/test_client2_central_brain_recovery.sh"
POLICY="central-brain/android-runtime/runtime-service/src/main/res/xml/central_brain_capability_policy.xml"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/acceptance/RuntimeAcceptanceSnapshot.java"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Client2 Binder migration file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Client2 Binder migration pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$BRIDGE" "$CALLBACK" "$CONTROLLER" "$LAYOUT" "$PATCHER" "$DEX_BUILD" \
  "$APK_BUILD" "$PROJECT_VERIFY" "$DEVICE_TEST" "$RECOVERY_TEST" \
  "$POLICY" "$SNAPSHOT" \
  "$PROJECT/client2-central-brain.project.json" "$PROJECT/README.md"; do
  require_file "$path"
done

bash -n "$ROOT_DIR/$DEX_BUILD"
bash -n "$ROOT_DIR/$APK_BUILD"
bash -n "$ROOT_DIR/$PROJECT_VERIFY"
bash -n "$ROOT_DIR/$DEVICE_TEST"
bash -n "$ROOT_DIR/$RECOVERY_TEST"
python3 -m py_compile "$ROOT_DIR/$PATCHER"
rm -rf "$ROOT_DIR/$PROJECT/scripts/__pycache__"
python3 -m json.tool "$ROOT_DIR/$PROJECT/client2-central-brain.project.json" >/dev/null

for scenario in \
  care.cold care.fatigue task.home skill.nap state.vehicle memory.preference \
  skills.catalog governance.audit security.denied security.privacy runtime.npu \
  system.overview; do
  require_text "$BRIDGE" "\"$scenario\""
done
for canonical_scenario in \
  scene.comfort.cold.v1 scene.fatigue.assist.v1 scene.navigation.home.v1 \
  scene.rest.nap.v1 scene.diagnostics.vehicle.v1 scene.memory.preference.v1 \
  scene.skills.catalog.v1 scene.governance.audit.v1 scene.security.denied.v1 \
  scene.security.privacy.v1 scene.runtime.npu.v1 scene.system.overview.v1; do
  require_text "$BRIDGE" "\"$canonical_scenario\""
done
require_text "$BRIDGE" "private static Map<String, String> scenarioAliases()"
require_text "$BRIDGE" "request.scenarioId = scenarioId"
require_text "$BRIDGE" "SessionConnection openSession("
require_text "$BRIDGE" "new SessionClient"
require_text "$BRIDGE" "connectedClient.openSession(request(), this)"
require_text "$BRIDGE" "client2_session_snapshot_received=true"
require_text "$BRIDGE" "client2_session_event_received=true"
require_text "$BRIDGE" "client2_session_replay_complete=true"
require_text "$BRIDGE" "client2_legacy_callback_projected=true"
require_text "$BRIDGE" "client2_legacy_callback_reprojected=true"
require_text "$BRIDGE" "client2_legacy_session_replaced=true"
require_text "$BRIDGE" "SessionContract.isTerminalState"
require_text "$BRIDGE" "current.reconnect()"
require_text "$BRIDGE" "Session event overflow; replaying"
require_text "$BRIDGE" "SessionSnapshot accepted = copy(snapshot)"
require_text "$BRIDGE" "RuntimeEvent accepted = copy(event)"
require_text "$BRIDGE" "@Deprecated"
require_text "$CALLBACK" "onSessionOpened(SessionHandle handle, String scenarioId)"
require_text "$CALLBACK" "onSessionSnapshot(SessionSnapshot snapshot)"
require_text "$CALLBACK" "onSessionEvent(RuntimeEvent event)"
require_text "$CALLBACK" "onSessionReplayComplete(SessionHandle handle, long lastSequence)"
require_text "$CALLBACK" "onSessionOverflow(SessionHandle handle, String resumeCursor)"
require_text "$CALLBACK" "onSessionClosed("
require_text "$CALLBACK" "onSessionError(SessionHandle handle, String code, String message)"
require_text "$CALLBACK" "default void onBridgeStatus"
require_text "$CALLBACK" "default void onBridgeReply"
require_text "$CALLBACK" "default void onBridgeFailure"
require_text "$BRIDGE" "session_event_transport_used=true"
require_text "$BRIDGE" "http_transport_used=false"
require_text "$BRIDGE" "service_dispatch_triggered=false"
require_text "$BRIDGE" "hardware_accessed=false"
require_text "$CONTROLLER" ".implements Lcom/centralbrain/client2/ScenarioCallback;"
require_text "$CONTROLLER" "Client2ScenarioBridge;->submit"
require_text "$CONTROLLER" "onBridgeStatus"
require_text "$CONTROLLER" "onBridgeReply"
require_text "$CONTROLLER" "onBridgeFailure"
require_text "$CONTROLLER" "requestInFlight"
require_text "$CONTROLLER" "central_brain_menu_toggle"
require_text "$CONTROLLER" "togglePanel"
require_text "$CONTROLLER" "hidePanel"
require_text "$CONTROLLER" "setVisibility"
require_text "$LAYOUT" "centralBrainNavigationTrigger"
require_text "$LAYOUT" 'android:visibility="gone"'
require_text "$LAYOUT" 'android:background="@android:color/transparent"'

if find "$ROOT_DIR/$PROJECT/patches/smali" -name '*RequestTask.smali' -print -quit \
    | grep -q .; then
  echo "legacy Client2 HTTP RequestTask smali remains tracked" >&2
  exit 1
fi
if grep -R -Eiq \
    'http://10\.0\.2\.2|HttpURLConnection|java\.net|okhttp' \
    "$ROOT_DIR/$PROJECT/bridge" "$ROOT_DIR/$PROJECT/patches/smali"; then
  echo "Client2 Binder sources contain a legacy network transport" >&2
  exit 1
fi
if grep -Eiq \
    'CentralBrainClient|AgentTaskRequest|submitAgentTask|Task(Result|Update|Failure)' \
    "$ROOT_DIR/$BRIDGE"; then
  echo "Client2 Session/Event bridge regressed to the deprecated Task API" >&2
  exit 1
fi
if grep -R -Eiq \
    'System\.loadLibrary|android\.car|CarPropertyManager|ioctl|sysfs|/dev/|SocketCAN|SharedMemory' \
    "$ROOT_DIR/$PROJECT/bridge" "$ROOT_DIR/$PROJECT/patches/smali"; then
  echo "Client2 Binder migration unexpectedly references hardware/native APIs" >&2
  exit 1
fi

require_text "$PATCHER" "com.centralbrain.permission.BIND_RUNTIME"
require_text "$PATCHER" "com.centralbrain.runtime"
require_text "$PATCHER" "must not request INTERNET"
require_text "$PATCHER" "must not opt into cleartext traffic"
require_text "$DEX_BUILD" 'central-brain-sdk-debug.aar'
require_text "$DEX_BUILD" 'd8'
require_text "$DEX_BUILD" 'classes2.dex'
require_text "$APK_BUILD" 'CENTRAL_BRAIN_ANDROID_DEBUG_KEYSTORE'
require_text "$APK_BUILD" 'Client2 and Runtime debug signer mismatch'
require_text "$PROJECT_VERIFY" 'classes2.dex'
require_text "$PROJECT_VERIFY" 'must not request network or cleartext access'

for marker in \
  "client2_signature_permission_granted=true" \
  "runtime_client2_signer_parity=true" \
  "client2_session_transport_connected=true" \
  "client2_session_opened=true" \
  "client2_session_snapshot_received=true" \
  "client2_session_event_received=true" \
  "client2_session_event_sequence_verified=true" \
  "client2_session_replay_complete=true" \
  "client2_legacy_callback_projected=true" \
  "client2_ui_session_projection_verified=true" \
  "client2_panel_initially_hidden=true" \
  "client2_navigation_toggle_show_verified=true" \
  "client2_navigation_toggle_hide_verified=true" \
  "client2_outside_tap_dismiss_verified=true" \
  "client2_identity_resolved=true" \
  "client2_capability_policy_allowed=true" \
  "session_event_transport_used=true" \
  "http_transport_used=false" \
  "service_dispatch_triggered=false" \
  "hardware_accessed=false"; do
  require_text "$DEVICE_TEST" "$marker"
done
require_text "$RECOVERY_TEST" "client2_navigation_menu_reopen_verified=true"
require_text "$RECOVERY_TEST" "client2_session_reconnect_replay_verified=true"
require_text "$RECOVERY_TEST" "client2_session_duplicate_event_suppressed=true"
require_text "$RECOVERY_TEST" "client2_legacy_stream_replacement_verified=true"
require_text "$DEVICE_TEST" "--require-api-33"
require_text "$DEVICE_TEST" "--replace-conflicting-client2"
require_text "$DEVICE_TEST" "SIGNER_MIGRATION_REQUIRED"
require_text "$DEVICE_TEST" "automatic_uninstall_enabled=false"
require_text "$PROJECT/scripts/install_debug_apk.sh" "--replace-conflicting-client2"
require_text "$PROJECT/scripts/install_debug_apk.sh" "SIGNER_MIGRATION_REQUIRED"
require_text "$PROJECT/scripts/install_debug_apk.sh" "automatic_uninstall_enabled=false"

python3 - "$ROOT_DIR/$POLICY" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

root = ET.parse(pathlib.Path(sys.argv[1])).getroot()
client2 = [
    principal
    for principal in root.findall("principal")
    if principal.attrib.get("packageName") == "com.tuanjie.urasclient2"
]
if len(client2) != 1 or client2[0].attrib.get("signer") != "runtime-current":
    raise SystemExit("Client2 must have one runtime-current capability principal")
actual = {entry.attrib.get("name") for entry in client2[0].findall("capability")}
expected = {
    "runtime.protocol.read",
    "runtime.task.submit",
    "runtime.task.status.own",
    "runtime.task.cancel.own",
    "runtime.session.protocol.read",
    "runtime.session.open",
    "runtime.session.read.own",
    "runtime.session.cancel.own",
    "runtime.event.protocol.read",
    "runtime.event.read.own",
    "runtime.event.subscribe.own",
}
if actual != expected:
    raise SystemExit(f"Client2 capability set is not least privilege: {actual}")
PY

require_text "$SNAPSHOT" "isClient2BinderMigrationComplete()"
require_text "$SNAPSHOT" "client2_binder_migration_complete=true"
if grep -Fq "CLIENT2_BINDER_MIGRATION_PENDING" "$ROOT_DIR/$SNAPSHOT"; then
  echo "Client2 Binder migration remains blocked after API 33 evidence" >&2
  exit 1
fi

for doc_pattern in \
  "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md|R7B Client2 SDK/Binder migration" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md|R7B Client2 SDK/Binder migration trace" \
  "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md|Android R7B Client2 SDK/Binder Migration" \
  "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md|Android R7B Client2 SDK/Binder Migration" \
  "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md|R7B Client2 Binder Driver/HAL Boundary" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md|R7B 进展" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md|R7B 进展" \
  "docs/CENTRAL_BRAIN_ROADMAP.md|R7B Client2 SDK/Binder migration"; do
  path="${doc_pattern%%|*}"
  pattern="${doc_pattern#*|}"
  require_text "$path" "$pattern"
done

for doc_pattern in \
  "README.md|12 场景、文本回复、typed Binder" \
  "README.md|cockpit_demo_control_loop_implemented=false" \
  "apk-labs/client2-central-brain/README.md|bottom navigation" \
  "docs/CENTRAL_BRAIN_CLIENT2_APK_REVERSE_DEMO.md|2026-07-15 导航菜单真机验收" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md|Client2 navigation-triggered menu trace" \
  "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md|client2_navigation_menu_acceptance_passed=true" \
  "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md|Client2 Navigation Menu Driver/HAL Result" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md|DEV-051 P4-W01 Client2 UI alias" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md|P4-W01 进展：Client2 已不再通过单次" \
  "docs/CENTRAL_BRAIN_ROADMAP.md|### 2026-07-15" \
  "docs/CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md|client2_navigation_menu_acceptance_passed=true"; do
  path="${doc_pattern%%|*}"
  pattern="${doc_pattern#*|}"
  require_text "$path" "$pattern"
done

bash "$ROOT_DIR/tools/check_central_brain_android_capability_policy.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_runtime_acceptance.sh"

echo "Central Brain Android Client2 Binder migration check passed"
