#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001/002, S2-SAF-001, S2-OBS-001/002,
# XSC-001/005/006, DEL-001/003/004/005. Stage: P7-R4-OCDEV.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME="central-brain/android-runtime/runtime-service"
CONFIG="$RUNTIME/src/main/java/com/centralbrain/runtime/model/OpenClawEndpointConfig.java"
ENGINE="$RUNTIME/src/debug/java/com/centralbrain/runtime/model/OpenClawInferenceEngine.java"
BOUNDARY="$RUNTIME/src/debug/java/com/centralbrain/runtime/orchestration/DebugDecisionCompositionBoundary.java"
PROBE="$RUNTIME/src/debug/java/com/centralbrain/runtime/orchestration/OpenClawDevelopmentIntegrationProbeActivity.java"
BUILD="$RUNTIME/build.gradle.kts"
BRIDGE="tools/start_central_brain_wsl_openclaw_bridge.sh"
RUNNER="tools/run_central_brain_android_openclaw_development_probe.sh"
CLIENT2_RUNNER="tools/run_client2_central_brain_openclaw_development_test.sh"
CONTRACT="central-brain/contracts/central_brain_android_openclaw_development_gateway_v1.json"
DESIGN="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] \
    || { echo "missing OpenClaw development file: $1" >&2; exit 1; }
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
    || { echo "missing OpenClaw development marker '$2' in $1" >&2; exit 1; }
}

for file in "$CONFIG" "$ENGINE" "$BOUNDARY" "$PROBE" "$BUILD" \
  "$BRIDGE" "$RUNNER" "$CLIENT2_RUNNER" "$CONTRACT" "$DESIGN"; do
  require_file "$file"
done

for marker in \
  'DEVELOPMENT_PROFILE = "development_wsl_openclaw"' \
  'DEVELOPMENT_HOST = "127.0.0.1"' \
  'DEVELOPMENT_PROTOCOL_VERSION = 4' \
  'TARGET_PROTOCOL_VERSION = 3' \
  'developmentWslAdbReverse()'; do
  require_text "$CONFIG" "$marker"
done
for marker in \
  'client.addProperty("id", "gateway-client")' \
  'client.addProperty("mode", "backend")' \
  'developmentProfile ? "" : "Origin: http://"' \
  'connect.challenge' 'chat.send' 'chat.history' 'chat.abort' \
  'endpoint_profile=' 'raw_prompt_logged=false' \
  'raw_response_logged=false' 'credential_logged=false'; do
  require_text "$ENGINE" "$marker"
done
for marker in \
  'OPENCLAW_DEVELOPMENT' 'developmentWslAdbReverse()' \
  'ANDROID_LOCAL_DEVELOPMENT_ID' 'RouteMode.DEVELOPMENT'; do
  require_text "$BOUNDARY" "$marker"
done
for marker in \
  'development_wsl_openclaw' 'ws://127.0.0.1:18789' \
  'if (targetOpenClaw || developmentOllama) "3" else "4"'; do
  require_text "$BUILD" "$marker"
done
for marker in \
  'transport=ADB_REVERSE' 'websocket_protocol=' \
  'model_action_authority=false' 'vehicle_effect_dispatch_authorized=false' \
  'ethernet_validated=false' 'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$PROBE" "$marker"
done
for marker in \
  'reverse "tcp:${DEVICE_PORT}" "tcp:${HOST_PORT}"' \
  'model_available=true' 'ethernet_validated=false'; do
  require_text "$BRIDGE" "$marker"
done
require_text "$RUNNER" 'OpenClawDevelopmentIntegrationProbeActivity'
require_text "$RUNNER" 'CENTRAL_BRAIN_MODEL_GATEWAY_PROFILE=development_wsl_openclaw'
for marker in \
  'scripts/build_debug_apk.sh' 'sdk_runtime_client2_same_build=true' \
  'centralBrainNavigationTrigger' 'centralBrainColdButton' \
  'client2_orchestration_snapshot_projected=true' \
  'model_projection_available=true' 'centralBrainActuatorOverlay' \
  'CENTRAL_BRAIN_SKIP_ANDROID_INSTALL' 'PREINSTALLED_APK_HASH_MISMATCH' \
  'real_wsl_openclaw_ollama_accessed=true' \
  'vehicle_effect_hardware_accessed=false'; do
  require_text "$CLIENT2_RUNNER" "$marker"
done

python3 -B - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["schema_version"] == 1
assert contract["implementation_stage"] == "P7-R4-OCDEV"
development = contract["development_profile"]
assert development["profile_id"] == "development_wsl_openclaw"
assert development["android_websocket_uri"] == "ws://127.0.0.1:18789/"
assert development["transport"] == "ADB_REVERSE"
assert development["protocol_version"] == 4
assert development["gateway_client_id"] == "gateway-client"
assert development["gateway_client_mode"] == "backend"
assert development["browser_origin_header_sent"] is False
assert development["debug_build_only"] is True
assert development["release_routing_enabled"] is False
compute = contract["wsl_compute_profile"]
assert compute["model_provider"] == "ollama"
assert compute["model_base_url"] == "http://127.0.0.1:11435"
assert compute["model"] == "qwen3.6:27b"
assert compute["real_model_required"] is True
evidence = contract["android13_arm64_evidence"]
assert evidence["android_api"] == 33
assert evidence["abi"] == "arm64-v8a"
assert evidence["protocol_v4_authentication_verified"] is True
assert evidence["real_model_terminal_response_verified"] is True
assert evidence["client2_full_path_verified"] is True
assert evidence["client2_model_projection_verified"] is True
assert evidence["client2_simulated_hmi_effect_verified"] is True
assert evidence["sdk_runtime_client2_same_build"] is True
assert evidence["raw_prompt_recorded"] is False
production = contract["production_separation"]
assert production["target_websocket_uri"] == "ws://169.254.208.110:18789/"
assert production["target_protocol_version"] == 3
assert production["development_transport_is_production_evidence"] is False
assert production["ethernet_validated"] is False
claims = contract["claim_state"]
assert claims["development_wsl_openclaw_android13_arm64_verified"] is True
assert claims["external_compute_accessed"] is True
for key in (
    "direct_npu_accessed", "vehicle_effect_hardware_accessed",
    "driver_hal_accessed", "production_ready", "target_hardware_validated",
):
    assert claims[key] is False
PY

for doc in README.md central-brain/android-runtime/README.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md; do
  require_text "$doc" 'P7-R4-OCDEV'
done

printf '%s\n' \
  'development_wsl_openclaw_implemented=true' \
  'development_wsl_openclaw_android13_arm64_verified=true' \
  'external_compute_accessed=true' \
  'direct_npu_accessed=false' \
  'ethernet_validated=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P7-R4-OCDEV'
