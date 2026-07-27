#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-CTX-001, S2-EVT-001, S2-MDL-001, S2-SAF-001,
# S2-OBS-001, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEBUG_ROOT="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime"
TEST_ROOT="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime"
BOUNDARY="$DEBUG_ROOT/orchestration/DebugDecisionCompositionBoundary.java"
BACKEND="$DEBUG_ROOT/orchestration/DebugSimulatedOrchestrationBackend.java"
PROBE="$DEBUG_ROOT/orchestration/DecisionCompositionProbeActivity.java"
TEST="$TEST_ROOT/orchestration/DebugDecisionCompositionBoundaryTest.java"
CONTRACT="central-brain/contracts/central_brain_android_decision_composition_v1.json"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] \
    || { echo "missing P6-P7-R1 file: $1" >&2; exit 1; }
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
    || { echo "missing P6-P7-R1 marker '$2' in $1" >&2; exit 1; }
}

for file in "$BOUNDARY" "$BACKEND" "$PROBE" "$TEST" "$CONTRACT"; do
  require_file "$file"
done

for marker in \
  'new RuntimeHealthContextSourceAdapter()' \
  'new TimeContextSourceAdapter()' \
  'new SimulatedVehicleSignalContextSourceAdapter()' \
  'TriggerEngine.createForContractTest(' \
  'ProactiveConsentPolicy.createForContractTest(' \
  'PolicyAwareModelRouter.decide(' \
  'TestOnlyModelRouter.createForContractTest(' \
  'BoundedEventRuntime.createForContractTest(' \
  'isAutoExecutionAuthorized() { return false; }' \
  'isProductionAuthority() { return false; }' \
  'isNetworkAccessed() { return networkAccessed; }' \
  'isNpuAccessed() { return false; }' \
  'isHardwareAccessed() { return false; }'; do
  require_text "$BOUNDARY" "$marker"
done

for marker in \
  'decisionComposition.prepare(' \
  'DebugDecisionCompositionBoundary.combine(' \
  'record.boundEvidenceDigest' \
  'decisionComposition.complete(' \
  'decisionComposition.close();'; do
  require_text "$BACKEND" "$marker"
done

for marker in \
  'decision_composition_probe_complete=' \
  'context_trigger_chain_verified=' \
  'proactive_consent_fail_closed_verified=' \
  'deterministic_model_stub_verified=' \
  'digest_event_delivery_verified=' \
  'decision_runtime_evidence_bound=' \
  'fatigue_dms_source_stubbed=true' \
  'production_model_invoked=false' \
  'hardware_accessed=false'; do
  require_text "$PROBE" "$marker"
done

for marker in \
  'coldDecisionComposesDigestOnlyEvidenceAndClosesAuthorities' \
  'fatigueUsesExplicitStubSourceAndSessionConflictFailsClosed' \
  'assertFalse(evidence.isAutoExecutionAuthorized())' \
  'assertEquals(0, boundary.snapshot().getActiveEventSubscriptionCount())'; do
  require_text "$TEST" "$marker"
done

require_text \
  'central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml' \
  '.orchestration.DecisionCompositionProbeActivity'

if grep -R -Fq -- 'DebugDecisionCompositionBoundary' \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release"; then
  echo 'P6-P7-R1 debug decision composition leaked into main/release source' >&2
  exit 1
fi

python3 - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    contract = json.load(handle)
assert contract["schema_version"] == 1
composition = contract["composition"]
assert composition["orchestration_debug_backend_wired"] is True
assert composition["context_source_adapter_count"] == 3
assert composition["cold_vehicle_context_simulated"] is True
assert composition["fatigue_dms_source_stubbed"] is True
assert composition["trigger_suggestion_only"] is True
assert composition["proactive_consent_fail_closed"] is True
assert composition["model_registry_policy_route_wired"] is True
assert composition["deterministic_test_model_invoked"] is True
assert composition["digest_event_delivery_wired"] is True
assert composition["event_transport_process_local"] is True
assert composition["decision_and_runtime_evidence_bound"] is True
authority = contract["authority"]
assert authority["debug_source_set_only"] is True
assert authority["release_backend_unchanged_fail_closed"] is True
assert authority["proactive_auto_execution_authorized"] is False
assert authority["production_context_authority_wired"] is False
assert authority["production_consent_authority_wired"] is False
assert authority["production_model_invoked"] is False
io = contract["privacy_and_io"]
assert io["free_text_accepted"] is False
assert io["network_accessed"] is False
assert io["npu_accessed"] is False
assert io["vehicle_bus_accessed"] is False
probe = contract["probe_evidence"]
assert probe["api_level"] == 33
assert probe["abi"] == "x86_64"
assert all(value is True for key, value in probe.items()
           if key not in {"api_level", "abi"})
state = contract["claim_state"]
assert state["decision_composition_v1_defined"] is True
assert state["decision_composition_debug_wired"] is True
assert state["decision_composition_host_tests_verified"] is True
assert state["decision_composition_debug_probe_available"] is True
assert state["decision_composition_debug_probe_executed"] is True
assert state["decision_composition_android13_arm64_verified"] is False
assert state["production_decision_composition_wired"] is False
assert state["hardware_accessed"] is False
assert state["production_ready"] is False
assert state["target_hardware_validated"] is False
assert state["implementation_stage"] == "P6-P7-R1"
PY

for doc in \
  README.md \
  central-brain/android-runtime/README.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md \
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md; do
  require_text "$doc" 'P6-P7-R1'
done

printf '%s\n' \
  'Central Brain Android Decision Composition V1 check passed' \
  'decision_composition_v1_defined=true' \
  'decision_composition_debug_wired=true' \
  'decision_composition_host_tests_verified=true' \
  'decision_composition_debug_probe_executed=true' \
  'decision_composition_android13_arm64_verified=false' \
  'production_decision_composition_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P6-P7-R1'
