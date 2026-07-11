#!/usr/bin/env bash
set -euo pipefail

# Req IDs: FW-U-004, FW-U-007, FW-S-005, XSC-005/006, NV-G-005/006/007, NV-P-002.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GOVERNANCE_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android action governance file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android action governance pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for name in \
  SafetyVehicleStateSnapshot.java \
  SafetyVehicleStateProvider.java \
  RuntimeOwnedSafetyVehicleStateProvider.java \
  ActionGovernancePolicy.java \
  InMemoryApprovalRegistry.java; do
  require_file "$GOVERNANCE_ROOT/$name"
done

POLICY="$GOVERNANCE_ROOT/ActionGovernancePolicy.java"
STATE="$GOVERNANCE_ROOT/SafetyVehicleStateSnapshot.java"
PROVIDER="$GOVERNANCE_ROOT/RuntimeOwnedSafetyVehicleStateProvider.java"
APPROVAL="$GOVERNANCE_ROOT/InMemoryApprovalRegistry.java"

for action_id in \
  vehicle.state.read \
  cabin.temperature.set \
  driver.display.video.play \
  vehicle.diagnostics.write \
  system.ota.install; do
  require_text "$POLICY" "$action_id"
done

for risk_class in \
  READ_ONLY \
  COMFORT_CONTROL \
  DRIVER_DISTRACTION \
  DIAGNOSTIC_WRITE \
  OTA \
  UNKNOWN; do
  require_text "$POLICY" "$risk_class"
done

require_text "$POLICY" "APPROVAL_REQUIRED"
require_text "$POLICY" "VEHICLE_MOVING_DENIED"
require_text "$POLICY" "isDispatchAllowed"
require_text "$STATE" "RUNTIME_OWNED_STUB"
require_text "$STATE" "PLATFORM_TRUSTED_ADAPTER"
require_text "$STATE" "isCallerControlled"
require_text "$PROVIDER" "runtime-owned-state-stub"
require_text "$APPROVAL" "supportsApprovalGrant"
require_text "$APPROVAL" "isDurable"
require_text "$APPROVAL" "pending approval capacity exhausted"

if grep -R -Eq '^import android\.|android\.(os|hardware|car)' "$ROOT_DIR/$GOVERNANCE_ROOT"; then
  echo "R3C1 governance core must remain pure Java and hardware-free" >&2
  exit 1
fi
if grep -R -Eiq 'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory' \
    "$ROOT_DIR/$GOVERNANCE_ROOT"; then
  echo "R3C1 governance core unexpectedly references hardware or shared memory" >&2
  exit 1
fi
if grep -Fq "APPROVED" "$ROOT_DIR/$APPROVAL"; then
  echo "R3C1 approval registry must not claim grant authority" >&2
  exit 1
fi

require_file "central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/governance/ActionGovernancePolicyTest.java"
require_file "central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/governance/InMemoryApprovalRegistryTest.java"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3C1 action governance core trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R3C1 Action Governance Core Interfaces"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R3C1 Action Governance Core"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3C1 Action Governance Core Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R3C1 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R3C1 进展"

echo "Central Brain Android action governance check passed"
