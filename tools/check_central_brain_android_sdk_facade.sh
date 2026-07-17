#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SES-001, S2-UX-001..003, S2-EVT-001, APP-004,
# XSC-001, XSC-006, NV-G-003, NV-G-004, DEL-001, DEL-003..005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime"
POLICY="central-brain/android-runtime/runtime-service/src/main/res/xml/central_brain_capability_policy.xml"
DEBUG_POLICY="central-brain/android-runtime/runtime-service/src/debug/res/xml/central_brain_capability_policy.xml"
MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] || { echo "missing P1-W05 file: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing P1-W05 marker '$2' in $1" >&2; exit 1; }
}

for file in \
  "$SDK/ScenarioClient.java" \
  "$SDK/SessionClient.java" \
  "$SDK/RuntimeEventListener.java" \
  "$SDK/ScenarioTransport.java" \
  "$SDK/AndroidScenarioTransport.java" \
  "$RUNTIME/session/TransientSessionRegistry.java" \
  "$RUNTIME/session/TransientSessionEndpoint.java" \
  "central-brain/android-runtime/central-brain-sdk/src/test/java/com/centralbrain/sdk/SessionClientTest.java" \
  "central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/session/TransientSessionRegistryTest.java" \
  "central-brain/android-runtime/central-brain-sdk/src/androidTest/AndroidManifest.xml" \
  "$DEBUG_POLICY"; do
  require_file "$file"
done

for marker in \
  'boolean reconnect();' \
  'SessionHandle openSession(SessionRequest request, RuntimeEventListener listener);' \
  'void observeSession(' \
  'void stopObserving(SessionHandle handle);' \
  'final class Failure extends RuntimeException'; do
  require_text "$SDK/ScenarioClient.java" "$marker"
done

if grep -Eq 'android\.os\.(IBinder|RemoteException)|ICentralBrain(Session|Runtime|Event)' \
    "$ROOT_DIR/$SDK/ScenarioClient.java" \
    "$ROOT_DIR/$SDK/RuntimeEventListener.java"; then
  echo "public Stage 2 facade leaks Binder primitives" >&2
  exit 1
fi

require_text "$SDK/CentralBrainSdk.java" 'ACTION_SESSION_RUNTIME'
require_text "$SDK/CentralBrainSdk.java" 'ACTION_SESSION_EVENTS'
for marker in \
  'active_session_reconnect_resubscribe_verified=true' \
  'callback_replay_deduplicated=true' \
  'session_runtime_process_death_rehydration=true'; do
  require_text \
    "central-brain/android-runtime/central-brain-sdk/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java" \
    "$marker"
done

require_text "$RUNTIME/CentralBrainRuntimeService.java" \
  'CentralBrainSdk.ACTION_SESSION_RUNTIME.equals(action)'
require_text "$RUNTIME/CentralBrainRuntimeService.java" \
  'CentralBrainSdk.ACTION_SESSION_EVENTS.equals(action)'
require_text "$RUNTIME/CentralBrainRuntimeService.java" \
  'session_runtime_persistence_wired=true'
require_text "$RUNTIME/session/TransientSessionEndpoint.java" \
  'SessionRegistry registry'
require_text "$RUNTIME/session/TransientSessionEndpoint.java" \
  'MAX_CALLBACKS_PER_SESSION = 4'
require_text "$RUNTIME/session/TransientSessionEndpoint.java" \
  'MAX_CALLBACKS_TOTAL = 128'
require_text "$RUNTIME/session/TransientSessionRegistry.java" \
  'It never retains raw utterances.'

for capability in \
  runtime.session.protocol.read runtime.session.open runtime.session.read.own \
  runtime.session.cancel.own runtime.event.protocol.read runtime.event.read.own \
  runtime.event.subscribe.own; do
  require_text "$POLICY" "$capability"
  require_text "$DEBUG_POLICY" "$capability"
done
if grep -Fq 'com.centralbrain.sdk.test' "$ROOT_DIR/$POLICY"; then
  echo "production capability policy must not grant the instrumentation package" >&2
  exit 1
fi
require_text "$DEBUG_POLICY" 'packageName="com.centralbrain.sdk.test"'

service_count="$(grep -c '<service' "$ROOT_DIR/$MANIFEST")"
[[ "$service_count" == 4 ]] \
  || { echo "manifest source must retain three app services plus removed Room service" >&2; exit 1; }
require_text "$MANIFEST" 'android:name=".CentralBrainRuntimeService"'

(cd "$ROOT_DIR" && sha256sum -c \
  central-brain/android-runtime/central-brain-sdk/aidl-api/v1.sha256 >/dev/null)
(cd "$ROOT_DIR" && sha256sum -c \
  central-brain/android-runtime/central-brain-sdk/aidl-api/session-v1.sha256 >/dev/null)
(cd "$ROOT_DIR" && sha256sum -c \
  central-brain/android-runtime/central-brain-sdk/aidl-api/events-v1.sha256 >/dev/null)
(cd "$ROOT_DIR" && sha256sum -c \
  central-brain/android-runtime/central-brain-sdk/aidl-api/plan-v1.sha256 >/dev/null)
(cd "$ROOT_DIR" && sha256sum -c \
  central-brain/android-runtime/central-brain-sdk/aidl-api/effect-v1.sha256 >/dev/null)

for doc in \
  README.md \
  docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md; do
  require_text "$doc" 'P1-W05'
done
require_text README.md 'sdk_facade_v2_available=true'
require_text docs/CENTRAL_BRAIN_ROADMAP.md '`P1-W07 Contract v2 aggregate check` 已完成'
require_text docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md \
  'session_runtime_process_death_rehydration=true'
require_text docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  'terminal page 不提供可前移的 resume cursor'
require_text docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  'active_session_reconnect_resubscribe_verified=true'
require_text docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md \
  'P1-W05 SDK Facade Driver/HAL Boundary'

printf '%s\n' \
  'Central Brain Android SDK facade v2 check passed' \
  'sdk_facade_v2_available=true' \
  'session_runtime_service_published=true' \
  'event_runtime_service_published=true' \
  'event_callback_service_published=true' \
  'session_runtime_persistence_wired=true' \
  'session_runtime_process_death_rehydration=true' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false'
