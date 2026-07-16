#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AIDL_DIR="central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/plan"
JAVA_CONTRACT="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/plan/PlanContract.java"
UNIT_TEST="central-brain/android-runtime/central-brain-sdk/src/test/java/com/centralbrain/sdk/plan/PlanContractTest.java"
DEVICE_TEST="central-brain/android-runtime/central-brain-sdk/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java"
HASH_MANIFEST="central-brain/android-runtime/central-brain-sdk/aidl-api/plan-v1.sha256"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Plan contract file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Plan contract pattern '$pattern' in $path" >&2
    exit 1
  fi
}

AIDL_TYPES=(ScenarioPlan PlanNode NodeDependency NodePolicy)
for name in "${AIDL_TYPES[@]}"; do
  require_file "$AIDL_DIR/$name.aidl"
  require_text "$AIDL_DIR/$name.aidl" "int schemaVersion = 1;"
done

for field in \
  'long timeoutMs = 0;' \
  'int maxAttempts = 1;' \
  'String idempotencyKey = "";' \
  'boolean required = true;' \
  'String compensationNodeId = "";' \
  'NodePolicy policy;'; do
  require_text "$AIDL_DIR/PlanNode.aidl" "$field"
done
require_text "$AIDL_DIR/ScenarioPlan.aidl" 'PlanNode[] nodes = {};'
require_text "$AIDL_DIR/ScenarioPlan.aidl" 'NodeDependency[] dependencies = {};'
require_text "$AIDL_DIR/NodePolicy.aidl" 'boolean approvalRequired = false;'
require_text "$AIDL_DIR/NodePolicy.aidl" 'boolean verificationRequired = false;'

require_file "$JAVA_CONTRACT"
declared_hash="$(
  sed -nE 's/.*"([0-9a-f]{64})";.*/\1/p' "$ROOT_DIR/$JAVA_CONTRACT" | head -n 1
)"
computed_hash="$({
  for name in "${AIDL_TYPES[@]}"; do
    cat "$ROOT_DIR/$AIDL_DIR/$name.aidl"
  done
} | sha256sum | awk '{print $1}')"
if [[ "$declared_hash" != "$computed_hash" ]]; then
  echo "Plan AIDL contract hash drift: declared=$declared_hash computed=$computed_hash" >&2
  exit 1
fi

for marker in \
  'MAX_NODES = 64' \
  'MAX_DEPENDENCIES = 256' \
  'MAX_GRAPH_DEPTH = 16' \
  'MAX_PARALLEL_NODES = 8' \
  'MAX_ATTEMPTS = 3' \
  'context.capture' \
  'policy.evaluate' \
  'approval.interrupt' \
  'effect.execute' \
  'effect.verify' \
  'tool.invoke' \
  'model.invoke' \
  'memory.query' \
  'memory.write' \
  'summary.render' \
  'compensate' \
  'plan graph contains a cycle' \
  'node.nodeType is not allowlisted' \
  'compensation graph contains a loop' \
  'CB_PLAN_CONTRACT:'; do
  require_text "$JAVA_CONTRACT" "$marker"
done

require_file "$UNIT_TEST"
require_text "$UNIT_TEST" 'acceptsBoundedAcyclicPlan'
require_text "$UNIT_TEST" 'rejectsCycleAndUnknownDependency'
require_text "$UNIT_TEST" 'rejectsUnknownNodeTypeAndVersion'
require_text "$UNIT_TEST" 'rejectsUnsafeRetryAndCompensationMetadata'
require_text "$UNIT_TEST" 'rejectsDepthParallelismDependencyAndDeadlineBounds'
require_file "$DEVICE_TEST"
require_text "$DEVICE_TEST" 'plan_parcel_round_trip_verified=true'
require_text "$DEVICE_TEST" 'plan_cycle_rejected=true'
require_text "$DEVICE_TEST" 'plan_unknown_node_type_rejected=true'
require_text "$DEVICE_TEST" 'plan_runtime_published=false'

if grep -R -Eiq 'Bundle|ParcelFileDescriptor|SharedMemory|FileDescriptor|rawPayload|rawInput' \
    "$ROOT_DIR/$AIDL_DIR"; then
  echo "Plan AIDL must remain structured, bounded, digest-only and handle-free" >&2
  exit 1
fi
if grep -Eiq 'speed|gear|belt|occupancy|caller|signer|signature|permission' \
    "$ROOT_DIR/$AIDL_DIR/ScenarioPlan.aidl"; then
  echo "ScenarioPlan must not accept authority, identity, or vehicle-safety assertions" >&2
  exit 1
fi

require_file "$HASH_MANIFEST"
(cd "$ROOT_DIR" && sha256sum -c "$HASH_MANIFEST" >/dev/null)
for manifest in v1.sha256 governance-v1.sha256 session-v1.sha256; do
  (cd "$ROOT_DIR" && sha256sum -c \
    "central-brain/android-runtime/central-brain-sdk/aidl-api/$manifest" >/dev/null)
done

for doc in \
  README.md \
  central-brain/README.md \
  central-brain/android-runtime/README.md \
  docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md \
  docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md; do
  require_text "$doc" "P1-W02"
done
require_text "README.md" "plan_contract_v1_defined=true"
require_text "README.md" "plan_parcel_physical_android13_arm64_verified=true"
require_text "README.md" "plan_runtime_published=false"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" \
  "plan_contract_v1_defined=true"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" \
  "plan_parcel_physical_android13_arm64_verified=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" \
  "P1-W02 Plan Contract Driver/HAL Boundary"

echo "Central Brain Android Plan/Node contract V1 check passed"
