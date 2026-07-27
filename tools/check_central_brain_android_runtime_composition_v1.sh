#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-GRF-001, S2-TOL-001, S2-MEM-001,
# S2-SAF-001, S2-OBS-001, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEBUG_ROOT="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime"
TEST_ROOT="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime"
BOUNDARY="$DEBUG_ROOT/orchestration/DebugRuntimeCompositionBoundary.java"
BACKEND="$DEBUG_ROOT/orchestration/DebugSimulatedOrchestrationBackend.java"
PROBE="$DEBUG_ROOT/orchestration/RuntimeCompositionProbeActivity.java"
TEST="$TEST_ROOT/orchestration/DebugRuntimeCompositionBoundaryTest.java"
CONTRACT="central-brain/contracts/central_brain_android_runtime_composition_v1.json"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] \
    || { echo "missing P5-R1 file: $1" >&2; exit 1; }
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
    || { echo "missing P5-R1 marker '$2' in $1" >&2; exit 1; }
}

for file in "$BOUNDARY" "$BACKEND" "$PROBE" "$TEST" "$CONTRACT"; do
  require_file "$file"
done

for marker in \
  'BoundedBuiltInSkillRuntime.createForContractTest(' \
  'ContextBudgetManager.createForContractTest()' \
  'new WorkingMemoryStore(' \
  'new InProcessBuiltInToolExecutor(' \
  'resolveTool(toolManifest, clock.nowMs())' \
  'workingMemory.terminateSessionOwned(' \
  'isProfileMemoryWritten() { return false; }' \
  'isEpisodicMemoryWritten() { return false; }' \
  'isProductionAuthority() { return false; }' \
  'isHardwareAccessed() { return false; }'; do
  require_text "$BOUNDARY" "$marker"
done

for marker in \
  'runtimeComposition.prepare(' \
  'record.boundEvidenceDigest' \
  'runtimeComposition.complete(' \
  'runtimeComposition.close();'; do
  require_text "$BACKEND" "$marker"
done

for marker in \
  'runtime_composition_probe_complete=' \
  'debug_metadata_tool_boundary_executed=true' \
  'working_memory_digest_only=true' \
  'profile_memory_written=false' \
  'episodic_memory_written=false' \
  'production_tool_execution_enabled=false' \
  'hardware_accessed=false'; do
  require_text "$PROBE" "$marker"
done

for marker in \
  'coldScenarioComposesAndCleansDigestOnlyState' \
  'fatigueScenarioUsesNapSkillAndConflictsFailClosed' \
  'assertFalse(evidence.isProductionAuthority())' \
  'assertEquals(0, boundary.snapshot().getActiveWorkingMemoryItemCount())'; do
  require_text "$TEST" "$marker"
done

require_text \
  'central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml' \
  '.orchestration.RuntimeCompositionProbeActivity'

if grep -R -Fq -- 'DebugRuntimeCompositionBoundary' \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release"; then
  echo 'P5-R1 debug composition leaked into main/release source' >&2
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
assert composition["tool_manifest_registry_resolver_rules_executor_boundary_wired"] is True
assert composition["tool_health_resolved_per_admission"] is True
assert composition["built_in_skill_governance_admission_wired"] is True
assert composition["context_budget_metadata_only"] is True
assert composition["working_memory_digest_only"] is True
assert composition["working_memory_terminal_cleanup_wired"] is True
assert composition["node_and_effect_evidence_bind_composition_digest"] is True
assert composition["profile_memory_written"] is False
assert composition["episodic_memory_written"] is False
authority = contract["authority"]
assert authority["debug_source_set_only"] is True
assert authority["release_backend_unchanged_fail_closed"] is True
assert authority["skill_dispatch_enabled"] is False
assert authority["production_tool_execution_enabled"] is False
assert authority["production_memory_authority_published"] is False
io = contract["privacy_and_io"]
assert io["free_text_accepted"] is False
assert io["network_accessed"] is False
assert io["npu_accessed"] is False
assert io["vehicle_bus_accessed"] is False
state = contract["claim_state"]
assert state["runtime_composition_v1_defined"] is True
assert state["runtime_composition_debug_wired"] is True
assert state["runtime_composition_host_tests_verified"] is True
assert state["runtime_composition_debug_probe_available"] is True
assert state["runtime_composition_debug_probe_executed"] is True
assert state["runtime_composition_android13_arm64_verified"] is False
assert state["production_runtime_composition_wired"] is False
assert state["hardware_accessed"] is False
assert state["production_ready"] is False
assert state["target_hardware_validated"] is False
assert state["implementation_stage"] == "P5-R1"
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
  require_text "$doc" 'P5-R1'
done

printf '%s\n' \
  'Central Brain Android Runtime Composition V1 check passed' \
  'runtime_composition_v1_defined=true' \
  'runtime_composition_debug_wired=true' \
  'runtime_composition_host_tests_verified=true' \
  'runtime_composition_debug_probe_available=true' \
  'runtime_composition_debug_probe_executed=true' \
  'runtime_composition_android13_arm64_verified=false' \
  'production_runtime_composition_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P5-R1'
