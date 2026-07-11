#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AIDL_ROOT="central-brain/android-runtime/central-brain-sdk/src/main/aidl"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android AIDL contract file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android AIDL pattern '$pattern' in $path" >&2
    exit 1
  fi
}

PRODUCTION_FILES=(
  AgentTaskRequest.aidl
  TaskHandle.aidl
  TaskUpdate.aidl
  TaskResult.aidl
  TaskFailure.aidl
  ICentralBrainTaskCallback.aidl
  ICentralBrainRuntime.aidl
)
DIAGNOSTIC_FILES=(
  DiagnosticQuery.aidl
  DiagnosticRecord.aidl
  DiagnosticPage.aidl
  ICentralBrainDiagnostics.aidl
)
GOVERNANCE_FILES=(
  ActionRequest.aidl
  ActionDecision.aidl
  ApprovalHandle.aidl
  ApprovalStatus.aidl
  ICentralBrainGovernance.aidl
)

for name in "${PRODUCTION_FILES[@]}"; do
  require_file "$AIDL_ROOT/com/centralbrain/sdk/production/$name"
done
for name in "${DIAGNOSTIC_FILES[@]}"; do
  require_file "$AIDL_ROOT/com/centralbrain/sdk/diagnostics/$name"
done
for name in "${GOVERNANCE_FILES[@]}"; do
  require_file "$AIDL_ROOT/com/centralbrain/sdk/governance/$name"
done

for name in AgentTaskRequest TaskHandle TaskUpdate TaskResult TaskFailure; do
  require_text "$AIDL_ROOT/com/centralbrain/sdk/production/$name.aidl" "int schemaVersion = 1;"
done
for name in DiagnosticQuery DiagnosticRecord DiagnosticPage; do
  require_text "$AIDL_ROOT/com/centralbrain/sdk/diagnostics/$name.aidl" "int schemaVersion = 1;"
done
for name in ActionRequest ActionDecision ApprovalHandle ApprovalStatus; do
  require_text "$AIDL_ROOT/com/centralbrain/sdk/governance/$name.aidl" "int schemaVersion = 1;"
done

PRODUCTION="$AIDL_ROOT/com/centralbrain/sdk/production/ICentralBrainRuntime.aidl"
CALLBACK="$AIDL_ROOT/com/centralbrain/sdk/production/ICentralBrainTaskCallback.aidl"
DIAGNOSTICS="$AIDL_ROOT/com/centralbrain/sdk/diagnostics/ICentralBrainDiagnostics.aidl"
GOVERNANCE="$AIDL_ROOT/com/centralbrain/sdk/governance/ICentralBrainGovernance.aidl"

require_text "$PRODUCTION" "getProtocolVersion()"
require_text "$PRODUCTION" "getProtocolHash()"
require_text "$PRODUCTION" "submitAgentTask"
require_text "$PRODUCTION" "cancelTask"
require_text "$PRODUCTION" "getTaskStatus"
require_text "$PRODUCTION" "CANCEL_REASON_CLIENT_DIED = 2"
require_text "$PRODUCTION" "ERROR_SERVICE_DIED = 4"
require_text "$CALLBACK" "oneway interface ICentralBrainTaskCallback"
require_text "$CALLBACK" "onTaskUpdate"
require_text "$CALLBACK" "onTaskCompleted"
require_text "$CALLBACK" "onTaskFailed"
require_text "$DIAGNOSTICS" "getProtocolVersion()"
require_text "$DIAGNOSTICS" "getProtocolHash()"
require_text "$DIAGNOSTICS" "MAX_PAGE_SIZE = 100"
require_text "$DIAGNOSTICS" "getPage"
require_text "$AIDL_ROOT/com/centralbrain/sdk/diagnostics/DiagnosticQuery.aidl" "String cursor = \"\";"
require_text "$AIDL_ROOT/com/centralbrain/sdk/diagnostics/DiagnosticQuery.aidl" "int pageSize = 50;"
require_text "$GOVERNANCE" "evaluateAction"
require_text "$GOVERNANCE" "requestApproval"
require_text "$GOVERNANCE" "getApprovalStatus"
require_text "$GOVERNANCE" "cancelApproval"
require_text "$GOVERNANCE" "DECISION_APPROVAL_REQUIRED = 2"
require_text "$GOVERNANCE" "APPROVAL_STATUS_EXPIRED = 3"
require_text "$AIDL_ROOT/com/centralbrain/sdk/governance/ActionRequest.aidl" "String actionId = \"\";"

for interface in "$PRODUCTION" "$DIAGNOSTICS" "$GOVERNANCE"; do
  if ! grep -Eq 'const String INTERFACE_HASH = "[0-9a-f]{64}";' "$ROOT_DIR/$interface"; then
    echo "interface hash must be a 64-character lowercase SHA-256 token: $interface" >&2
    exit 1
  fi
  if grep -Eq 'getInterface(Version|Hash)' "$ROOT_DIR/$interface"; then
    echo "reserved stable-AIDL method name used by Gradle app AIDL: $interface" >&2
    exit 1
  fi
done

if grep -Eiq 'risk|safety|motion|vehicleState|permission|caller|signer|signature' \
    "$ROOT_DIR/$AIDL_ROOT/com/centralbrain/sdk/governance/ActionRequest.aidl"; then
  echo "governance request must not accept caller risk, identity, permission, or state assertions" >&2
  exit 1
fi
if grep -Eiq 'approve|grantApproval|resolveApproval' "$ROOT_DIR/$GOVERNANCE"; then
  echo "R3C Governance AIDL must not expose approval grant authority" >&2
  exit 1
fi

if grep -R -Eiq 'json|Bundle|ParcelFileDescriptor|SharedMemory' "$ROOT_DIR/$AIDL_ROOT/com/centralbrain/sdk/production"; then
  echo "production AIDL must use structured typed fields, not JSON, Bundle, file descriptors, or shared memory" >&2
  exit 1
fi
if grep -v -E '^[[:space:]]*//' "$ROOT_DIR/$PRODUCTION" | grep -Eq 'Diagnostic|diagnostic|getPage'; then
  echo "production AIDL must not expose diagnostic operations" >&2
  exit 1
fi
if grep -v -E '^[[:space:]]*//' "$ROOT_DIR/$DIAGNOSTICS" | grep -Eq 'submit|cancel|TaskHandle|TaskRequest'; then
  echo "diagnostic AIDL must not expose task-control operations" >&2
  exit 1
fi

require_file "docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md"
require_file "central-brain/android-runtime/central-brain-sdk/aidl-api/v1.sha256"
require_file "central-brain/android-runtime/central-brain-sdk/aidl-api/governance-v1.sha256"
require_text "docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md" "Gradle application structured AIDL"
require_text "docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md" "not VINTF stable AIDL"
require_text "docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md" "DEV-018"

(cd "$ROOT_DIR" && sha256sum -c central-brain/android-runtime/central-brain-sdk/aidl-api/v1.sha256 >/dev/null)
(cd "$ROOT_DIR" && sha256sum -c central-brain/android-runtime/central-brain-sdk/aidl-api/governance-v1.sha256 >/dev/null)

echo "Central Brain Android AIDL contract check passed"
