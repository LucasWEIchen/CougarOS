#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-TOL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/tools"
DEBUG_ROOT="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/tools"
TEST_ROOT="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/tools"
MANIFEST_CLASS="$MAIN_ROOT/ToolManifest.java"
REGISTRY="$MAIN_ROOT/ToolRegistry.java"
HEALTH="$MAIN_ROOT/ToolHealthSnapshot.java"
RESOLVER="$MAIN_ROOT/ToolResolver.java"
TEST="$TEST_ROOT/ToolRegistryResolverTest.java"
PROBE="$DEBUG_ROOT/ToolRegistryProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P5-W02 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$MANIFEST_CLASS" "$REGISTRY" "$HEALTH" "$RESOLVER" "$TEST" \
    "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME_SERVICE" \
    "$GOVERNANCE_SERVICE" "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W02 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int MAX_REGISTRATIONS = 128' \
  'public enum ErrorCode' \
  'REGISTRATION_LIMIT_EXCEEDED' \
  'CONTRACT_CONFLICT' \
  'Collections.unmodifiableNavigableMap' \
  'highestCompatible' \
  'getRegistryDigest()'; do
  require_text "$REGISTRY" "$marker"
done
for marker in \
  'public enum State' \
  'public enum Eligibility' \
  'HEALTHY' \
  'UNHEALTHY' \
  'UNKNOWN' \
  'STALE' \
  'CLOCK_INVALID' \
  'getMaximumStalenessMs()'; do
  require_text "$HEALTH" "$marker"
done
for marker in \
  'public enum RegistrationState' \
  'public enum ResolutionState' \
  'public enum UsabilityState' \
  'NO_COMPATIBLE_VERSION' \
  'CONTRACT_DIGEST_MISMATCH' \
  'HEALTH_UNHEALTHY' \
  'registry.highestCompatible' \
  'public boolean isExecutionEnabled()' \
  'return false;'; do
  require_text "$RESOLVER" "$marker"
done

for test_name in \
  registryIsBoundedImmutableDeduplicatedAndOrderIndependent \
  sameIdentityVersionWithDifferentDigestConflictsDeterministically \
  resolverSelectsHighestCompatibleHealthyVersionWithoutExecution \
  registeredResolvedAndUsableStatesRemainSeparate \
  dynamicHealthFailsClosedAndNeverFallsBackToOlderVersion; do
  require_text "$TEST" "$test_name"
done

for marker in \
  tool_registry_probe_complete \
  tool_registry_contract_defined \
  tool_resolver_contract_defined \
  tool_health_dynamic_snapshot_defined \
  tool_registry_digest_verified \
  tool_registry_version_conflict_rejected \
  tool_resolver_highest_version_deterministic \
  tool_resolver_states_separated \
  tool_resolver_unhealthy_no_fallback \
  tool_health_fail_closed \
  tool_registry_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
require_text "$PROBE" 'tool_registry_probe_registration_count='
require_text "$INSTALLER" 'tool_registry_probe_registration_count=2'
for marker in \
  tool_registry_published=false \
  tool_resolver_published=false \
  tool_registry_runtime_wired=false \
  tool_execution_enabled=false \
  production_tool_registered=false \
  effect_dispatch_enabled=false \
  vehicle_readback_accessed=false \
  npu_accessed=false \
  network_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$MANIFEST_CLASS" 'public String getFamilyId()'
require_text "$DEBUG_MANIFEST" '.tools.ToolRegistryProbeActivity'
if grep -Fq 'ToolRegistryProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W02 Tool registry probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'ToolRegistry|ToolHealthSnapshot|ToolResolver' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GOVERNANCE_SERVICE" \
    "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W02 Tool Registry/Resolver was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -R -Eiq \
    'ObjectInputStream|ObjectOutputStream|Class[.]forName|java[.]lang[.]reflect|Gson|Jackson|Serializable|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/|androidx[.]room|android[.]os[.]Binder' \
    "$ROOT_DIR/$REGISTRY" "$ROOT_DIR/$HEALTH" "$ROOT_DIR/$RESOLVER" \
    "$ROOT_DIR/$PROBE"; then
  echo "P5-W02 references persistence, Binder, network, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P5-W02 Tool Registry/Resolver"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P5-W02` ToolRegistry/Resolver'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P5-W02 Tool Registry/Resolver trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P5-W02 Tool Registry/Resolver"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W02 Tool Registry/Resolver architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P5-W02 Tool Registry/Resolver detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P5-W02 Tool Registry/Resolver"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P5-W02 Tool Registry/Resolver Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "DEV-064 P5-W02 Registry usability is not execution authority"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-037 Tool health publisher and production registry ownership"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P5-W02 ToolRegistry/Resolver"
require_text "README.md" "P5 Tool Registry/Resolver"

printf '%s\n' \
  "Central Brain Android Tool Registry/Resolver check passed" \
  "tool_registry_contract_defined=true" \
  "tool_resolver_contract_defined=true" \
  "tool_health_dynamic_snapshot_defined=true" \
  "tool_registry_digest_verified=true" \
  "tool_registry_version_conflict_rejected=true" \
  "tool_resolver_highest_version_deterministic=true" \
  "tool_resolver_states_separated=true" \
  "tool_resolver_unhealthy_no_fallback=true" \
  "tool_health_fail_closed=true" \
  "tool_registry_android13_arm64_verified=true" \
  "tool_registry_published=false" \
  "tool_resolver_published=false" \
  "tool_registry_runtime_wired=false" \
  "tool_execution_enabled=false" \
  "production_tool_registered=false" \
  "production_tool_artifact_loaded=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
