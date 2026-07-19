#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-002/004/005, FW-U-003/004, NV-G-004/006/007, NV-P-002/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/EventRuntimeReadinessSnapshot.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/events/EventRuntimeReadinessSnapshotTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/DiagnosticProbeActivity.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Event readiness file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android Event readiness pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$SNAPSHOT" "$TEST" "$RUNTIME" "$DIAGNOSTIC" "$PROBE" "$INSTALLER"; do
  require_file "$path"
done

require_text "$SNAPSHOT" "DURABLE_PUBLISHER_SEQUENCE_MISSING"
require_text "$SNAPSHOT" "EVENT_RUNTIME_NOT_WIRED"
require_text "$SNAPSHOT" "EVENT_REPOSITORY_NOT_WIRED"
require_text "$SNAPSHOT" "CALLBACK_BINDER_NOT_DEFINED"
require_text "$SNAPSHOT" "BROKER_NOT_CONFIGURED"
require_text "$SNAPSHOT" "MIDDLEWARE_CHAIN_NOT_WIRED"
require_text "$SNAPSHOT" "isActivationAllowed()"
require_text "$SNAPSHOT" "isDurableEventSourceAvailable()"
require_text "$SNAPSHOT" "isEventCursorPersistenceWired()"
require_text "$SNAPSHOT" "diagnosticDetail()"
require_text "$TEST" "currentSnapshotIsImmutableAndFailClosed"
require_text "$TEST" "blockersRemainOrderedAndImmutable"
require_text "$TEST" "diagnosticDetailSeparatesAvailabilityFromActivation"
require_text "$RUNTIME" "EventRuntimeReadinessSnapshot.current()"
require_text "$RUNTIME" "event_runtime_readiness_snapshot_wired=true"
require_text "$RUNTIME" "event_runtime_activation_blockers="
require_text "$DIAGNOSTIC" '"event-runtime-readiness"'
require_text "$DIAGNOSTIC" "eventRuntimeReadiness.diagnosticDetail()"
require_text "$PROBE" "hasBlockedEventRuntime("
require_text "$PROBE" "event_runtime_readiness_diagnostic_verified="

for marker in \
  "event_runtime_readiness_diagnostic_verified=true" \
  "event_runtime_readiness_snapshot_wired=true" \
  "event_runtime_readiness_log_verified=true" \
  "event_runtime_readiness_dumpsys_verified=true" \
  "event_runtime_activation_allowed=false" \
  "bounded_event_runtime_implementation_available=true" \
  "event_cursor_schema_ready=true" \
  "event_repository_implementation_available=true" \
  "trusted_event_topic_count=3" \
  "durable_event_source_available=false" \
  "event_runtime_production_wired=false" \
  "event_cursor_repository_production_wired=false" \
  "event_cursor_persistence_wired=false" \
  "event_callback_binder_wired=false" \
  "event_broker_production_wired=false" \
  "event_middleware_chain_wired=false" \
  "raw_event_payload_persisted=false"; do
  require_text "$INSTALLER" "$marker"
done

if grep -Eq 'CentralBrainDatabase|DurableEventCursorRepository|RuntimeStateDao' \
    "$ROOT_DIR/$SNAPSHOT"; then
  echo "Event readiness snapshot must not open or construct durable storage" >&2
  exit 1
fi
if grep -Eq 'import .*BoundedEventRuntime|new BoundedEventRuntime|createForContractTest' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R6A3 production Services must not activate the bounded test Event runtime" >&2
  exit 1
fi
if grep -Fq 'DurableEventCursorRepository' "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R6A3 diagnostics must consume readiness without owning Event V2 storage" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|SocketCAN|SharedMemory' \
    "$ROOT_DIR/$SNAPSHOT" "$ROOT_DIR/$PROBE"; then
  echo "R6A3 Event readiness unexpectedly references transport or hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R6A3 Event Runtime Readiness"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R6A3 Event runtime readiness"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R6A3 Event runtime readiness trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R6A3 Event Runtime Readiness"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R6A3 Event Runtime Readiness"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R6A3 Event Readiness Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R6A3 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R6A3 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R6A3 Event runtime readiness"

echo "Central Brain Android Event runtime readiness check passed"
