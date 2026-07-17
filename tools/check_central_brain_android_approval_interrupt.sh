#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-UX-003, S2-GRF-001, NV-G-005/006/007, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRAPH_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph"
RECORD="$GRAPH_ROOT/ApprovalInterruptRecord.java"
EXECUTOR="$GRAPH_ROOT/ApprovalInterruptExecutor.java"
VALIDATOR="$GRAPH_ROOT/ApprovalResumeValidator.java"
SERIALIZER="$GRAPH_ROOT/JsonPrimitiveCheckpointSerializer.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/graph/ApprovalInterruptExecutorTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/graph/ApprovalInterruptProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
GRAPH_RUNTIME="$GRAPH_ROOT/AgentGraphRuntime.java"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DATABASE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/CentralBrainDatabase.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P3-W05 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$RECORD" "$EXECUTOR" "$VALIDATOR" "$SERIALIZER" "$TEST" "$PROBE" \
    "$MANIFEST" "$MAIN_MANIFEST" "$GRAPH_RUNTIME" "$RUNTIME_SERVICE" "$DATABASE" \
    "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P3-W05 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class ApprovalInterruptRecord' \
  'PENDING' \
  'APPROVED' \
  'REJECTED' \
  'CANCELLED' \
  'EXPIRED' \
  'graph.approval.record.v1' \
  'ownerFingerprint' \
  'planDigest' \
  'contextDigest' \
  'policyDigest' \
  'safetyStateDigest' \
  'recordDigest'; do
  require_text "$RECORD" "$marker"
done

for marker in \
  'public final class ApprovalInterruptExecutor' \
  'MAX_APPROVAL_TTL_MS = 5L * 60L * 1_000L' \
  'graph.approval.interrupt' \
  'createPending(' \
  'recordDecision(' \
  'expire(' \
  'checkpointRegistration()' \
  'Long.toString(value.getCreatedAtEpochMs())' \
  'restored approval record digest does not match' \
  'approval decision is terminal and cannot be replayed'; do
  require_text "$EXECUTOR" "$marker"
done

for marker in \
  'public final class ApprovalResumeValidator' \
  'OWNER_MISMATCH' \
  'BINDING_MISMATCH' \
  'CONTEXT_STALE' \
  'CONTEXT_CHANGED' \
  'POLICY_DENIED' \
  'POLICY_CHANGED' \
  'CAPABILITY_DENIED' \
  'SAFETY_UNTRUSTED' \
  'SAFETY_UNSAFE' \
  'SAFETY_CHANGED' \
  'graph.approval.resume.v1'; do
  require_text "$VALIDATOR" "$marker"
done

require_text "$SERIALIZER" 'canonicalPositiveLong('
require_text "$SERIALIZER" 'field(builder, "createdAt", quote(Long.toString(createdAtEpochMs)))'

for test_name in \
  pendingInterruptBindsCallerPlanContextPolicyAndClampsExpiry \
  checkpointCodecRoundTripsCanonicalEpochStringsAndDigest \
  trustedApprovalResumesOnlyWithCurrentValidBindings \
  ownerPlanAndActionBindingMismatchFailClosed \
  contextAndPolicyMustRemainFreshAuthorizedAndDigestBound \
  resumeRevalidatesTrustedSafeAndUnchangedSafetyState \
  pendingRejectedAndExpiredApprovalCannotResume \
  untrustedAuthorityReplayAndMalformedRequestsFailClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  approval_interrupt_probe_complete \
  approval_interrupt_record_defined \
  approval_interrupt_binding_verified \
  approval_interrupt_checkpoint_roundtrip_verified \
  approval_interrupt_trusted_decision_verified \
  approval_resume_owner_plan_context_policy_verified \
  approval_resume_safety_revalidation_verified \
  approval_resume_expiry_verified \
  approval_interrupt_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  approval_interrupt_persistence_wired=false \
  approval_grant_service_published=false \
  agent_graph_executor_dispatch_enabled=false \
  effect_dispatch_enabled=false \
  model_invoked=false \
  network_accessed=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$MANIFEST" '.graph.ApprovalInterruptProbeActivity'
if grep -Fq 'ApprovalInterruptProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P3-W05 debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'ApprovalInterruptExecutor|ApprovalResumeValidator|ApprovalInterruptRecord' \
    "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$DATABASE"; then
  echo "P3-W05 approval interrupt was wired into Graph, Service, or Room" >&2
  exit 1
fi
if grep -R -Eiq \
    'System[.](currentTimeMillis|nanoTime)|Thread[.]sleep|new Thread|java[.]util[.]Random|SecureRandom|Executors[.]|java[.]util[.]concurrent|android[.]os|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$RECORD" "$ROOT_DIR/$EXECUTOR" "$ROOT_DIR/$VALIDATOR"; then
  echo "P3-W05 contract owns a clock/thread/dispatcher or references network, vehicle, or hardware APIs" >&2
  exit 1
fi
if grep -Fq '.toList()' "$ROOT_DIR/$PROBE"; then
  echo "P3-W05 debug probe uses Stream.toList(), which is unavailable on the API 33 target" >&2
  exit 1
fi

require_text "README.md" "P3 Durable approval interrupt"
require_text "central-brain/android-runtime/README.md" "P3-W05 Durable approval interrupt"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P3-W05` Durable approval interrupt'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P3-W05 Durable approval interrupt trace"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P3-W05 implemented approval interrupt contract"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P3-W05 Durable Approval Interrupt"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P3-W05 Durable Approval Interrupt"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P3-W05 Approval Interrupt Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P3-W05 approval interrupt"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P3-W05 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P3-W05 Durable approval interrupt"

printf '%s\n' \
  "Central Brain Android approval interrupt check passed" \
  "approval_interrupt_record_defined=true" \
  "approval_interrupt_binding_verified=true" \
  "approval_interrupt_checkpoint_roundtrip_verified=true" \
  "approval_interrupt_trusted_decision_verified=true" \
  "approval_resume_owner_plan_context_policy_verified=true" \
  "approval_resume_safety_revalidation_verified=true" \
  "approval_resume_expiry_verified=true" \
  "approval_interrupt_persistence_wired=false" \
  "approval_grant_service_published=false" \
  "agent_graph_executor_dispatch_enabled=false" \
  "effect_dispatch_enabled=false" \
  "model_invoked=false" \
  "hardware_accessed=false"
