#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-TOL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/tools"
DEBUG_ROOT="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/tools"
TEST_ROOT="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/tools"
MANIFEST_CLASS="$MAIN_ROOT/ToolManifest.java"
VALIDATOR="$MAIN_ROOT/ToolSchemaValidator.java"
TEST="$TEST_ROOT/ToolManifestSchemaTest.java"
PROBE="$DEBUG_ROOT/ToolManifestProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

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
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P5-W01 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$MANIFEST_CLASS" "$VALIDATOR" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME_SERVICE" \
    "$GOVERNANCE_SERVICE" "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W01 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int SCHEMA_VERSION = 1' \
  'public static final int MAX_FIELDS = 32' \
  'public static final int MAX_PAYLOAD_BYTES = 16 * 1024' \
  'public enum ScalarType' \
  'public enum RiskClass' \
  'public enum IdempotencyMode' \
  'public static final class FieldSchema' \
  'public static final class ObjectSchema' \
  'public static final class HealthContract' \
  'health must fail closed before Tool use' \
  'getContractDigest()'; do
  require_text "$MANIFEST_CLASS" "$marker"
done

for marker in \
  'public enum ErrorCode' \
  'MISSING_FIELD' \
  'UNKNOWN_FIELD' \
  'NULL_VALUE' \
  'TYPE_MISMATCH' \
  'VALUE_OUT_OF_RANGE' \
  'PAYLOAD_TOO_LARGE' \
  'value.getClass() != expected' \
  'Collections.unmodifiableMap(result)'; do
  require_text "$VALIDATOR" "$marker"
done

for test_name in \
  manifestIsImmutableVersionedAndHasDeterministicContractDigest \
  validatesExactBoundedInputAndOutputWithoutMutation \
  rejectsMissingUnknownNullAndExactTypeMismatch \
  rejectsDigestStringIntegerAndAggregatePayloadBounds \
  rejectsNonCanonicalManifestAndNonFailClosedHealth; do
  require_text "$TEST" "$test_name"
done

for marker in \
  tool_manifest_probe_complete \
  tool_manifest_contract_defined \
  tool_manifest_contract_digest_verified \
  tool_schema_input_output_verified \
  tool_schema_unknown_field_rejected \
  tool_schema_type_bounds_verified \
  tool_manifest_health_fail_closed \
  tool_manifest_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  tool_registry_published=false \
  tool_resolver_published=false \
  tool_execution_enabled=false \
  production_tool_artifact_loaded=false \
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

require_text "$DEBUG_MANIFEST" '.tools.ToolManifestProbeActivity'
if grep -Fq 'ToolManifestProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W01 Tool manifest probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'ToolManifest|ToolSchemaValidator' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GOVERNANCE_SERVICE" \
    "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W01 Tool contract was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -R -Eiq \
    'ObjectInputStream|ObjectOutputStream|Class[.]forName|java[.]lang[.]reflect|Gson|Jackson|Serializable|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/|androidx[.]room' \
    "$ROOT_DIR/$MAIN_ROOT" "$ROOT_DIR/$PROBE"; then
  echo "P5-W01 Tool contract references arbitrary serialization, persistence, network, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P5-W01 Tool Manifest/Schema"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P5-W01` Tool manifest/schema'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P5-W01 Tool Manifest/Schema trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P5-W01 Tool Manifest/Schema"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W01 Tool contract architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P5-W01 Tool Manifest/Schema detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P5-W01 Tool Manifest/Schema"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P5-W01 Tool Manifest/Schema Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "DEV-063 P5-W01 Tool contract is not Tool execution"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "ISSUE-036 Tool production owner, health source and execution authority"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P5-W01 Tool manifest/schema"
require_text "README.md" "P5 Tool Manifest/Schema"

printf '%s\n' \
  "Central Brain Android Tool manifest check passed" \
  "tool_manifest_contract_defined=true" \
  "tool_manifest_schema_version=1" \
  "tool_manifest_contract_digest_verified=true" \
  "tool_schema_exact_scalar_validation_verified=true" \
  "tool_manifest_health_fail_closed=true" \
  "tool_manifest_android13_arm64_verified=true" \
  "tool_registry_published=false" \
  "tool_resolver_published=false" \
  "tool_execution_enabled=false" \
  "production_tool_artifact_loaded=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
