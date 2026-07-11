#!/usr/bin/env bash
set -euo pipefail

# Req IDs: FW-U-004, FW-U-007, FW-S-005, XSC-005/006, NV-G-005/006/007, NV-P-002.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GOVERNANCE_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance"
GOVERNANCE_AIDL_ROOT="central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/governance"
GOVERNANCE_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
GOVERNANCE_CLIENT="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainGovernanceClient.java"
GOVERNANCE_AIDL="$GOVERNANCE_AIDL_ROOT/ICentralBrainGovernance.aidl"
ACTION_REQUEST_AIDL="$GOVERNANCE_AIDL_ROOT/ActionRequest.aidl"

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
for name in \
  ActionRequest.aidl \
  ActionDecision.aidl \
  ApprovalHandle.aidl \
  ApprovalStatus.aidl \
  ICentralBrainGovernance.aidl; do
  require_file "$GOVERNANCE_AIDL_ROOT/$name"
done
require_file "$GOVERNANCE_SERVICE"
require_file "$GOVERNANCE_CLIENT"
require_file "central-brain/android-runtime/central-brain-sdk/aidl-api/governance-v1.sha256"

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
  require_text "$GOVERNANCE_AIDL" "$action_id"
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
require_text "$GOVERNANCE_AIDL" "evaluateAction"
require_text "$GOVERNANCE_AIDL" "requestApproval"
require_text "$GOVERNANCE_AIDL" "getApprovalStatus"
require_text "$GOVERNANCE_AIDL" "cancelApproval"
require_text "$GOVERNANCE_CLIENT" "CentralBrainGovernanceService"
require_text "$GOVERNANCE_CLIENT" "linkToDeath"
require_text "$GOVERNANCE_SERVICE" "RuntimeOwnedSafetyVehicleStateProvider"
require_text "$GOVERNANCE_SERVICE" "dispatch_allowed=false hardware_accessed=false"
require_text "$GOVERNANCE_SERVICE" "Capability.APPROVAL_REQUEST"
require_text "$GOVERNANCE_SERVICE" "Capability.APPROVAL_STATUS_OWN"
require_text "$GOVERNANCE_SERVICE" "Capability.APPROVAL_CANCEL_OWN"
require_text "central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml" 'android:name=".CentralBrainGovernanceService"'
require_text "central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml" 'android:permission="com.centralbrain.permission.BIND_GOVERNANCE"'
require_text "central-brain/android-runtime/demo-hmi/src/main/AndroidManifest.xml" 'com.centralbrain.permission.BIND_GOVERNANCE'
require_text "central-brain/android-runtime/demo-hmi/src/main/java/com/centralbrain/demo/DemoActivity.java" "R.string.governance_verified"
require_text "central-brain/android-runtime/demo-hmi/src/main/res/values/strings.xml" "Governance: verified v%1\$d"
require_text "tools/install_central_brain_android_runtime.sh" "governance_typed_binder_connected=true"
require_text "tools/install_central_brain_android_runtime.sh" "approval_grant_supported=false"
require_text "tools/test_central_brain_android_capability_policy.sh" "governance_capability_default_deny_verified=true"
require_text "central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java" 'EVOLUTION_STAGE = "R3_TRUSTED_GOVERNANCE"'

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
if grep -v -E '^[[:space:]]*//' "$ROOT_DIR/$GOVERNANCE_AIDL" \
    | grep -Eiq 'approve|grantApproval|resolveApproval'; then
  echo "R3C Governance AIDL must not expose approval grant authority" >&2
  exit 1
fi
if grep -Eiq 'risk|safety|motion|vehicleState|permission|caller|signer|signature' \
    "$ROOT_DIR/$ACTION_REQUEST_AIDL"; then
  echo "R3C ActionRequest must not accept caller risk, state, identity, or permission assertions" >&2
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
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3C2 typed Governance Binder trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R3C2 Typed Governance Binder Interfaces"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R3C2 Typed Governance Binder"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3C2 Governance Binder Driver/HAL Boundary"

bash "$ROOT_DIR/tools/check_central_brain_android_aidl_contract.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_capability_policy.sh"

echo "Central Brain Android action governance check passed"
