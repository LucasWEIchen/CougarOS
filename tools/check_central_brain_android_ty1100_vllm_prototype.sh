#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/VllmEndpointConfig.java"
ENGINE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/VllmInferenceEngine.java"
BOUNDARY="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/orchestration/DebugDecisionCompositionBoundary.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/orchestration/VllmPrototypeIntegrationProbeActivity.java"
BUILD="central-brain/android-runtime/runtime-service/build.gradle.kts"
CONTRACT="central-brain/contracts/central_brain_android_ty1100_vllm_prototype_v1.json"

require_text() {
  grep -Fq -- "$2" "$ROOT/$1" \
    || { echo "missing TY1100 vLLM marker '$2' in $1" >&2; exit 1; }
}

for file in "$CONFIG" "$ENGINE" "$BOUNDARY" "$PROBE" "$BUILD" "$CONTRACT" \
  tools/manage_central_brain_ty1100_vllm_bridge.sh \
  tools/run_central_brain_android_ty1100_vllm_probe.sh; do
  [[ -f "$ROOT/$file" ]] || { echo "missing TY1100 vLLM file: $file" >&2; exit 1; }
done

for marker in \
  'VLLM_PORT = 10_030' 'DEVELOPMENT_HOST = "127.0.0.1"' \
  'CHAT_COMPLETIONS_PATH = "/v1/chat/completions"' \
  'EXPECTED_MODEL = "Qwen3.5-9B-AWQ"' \
  'TY1100_ETHERNET_VIA_ADB_REVERSE'; do
  require_text "$CONFIG" "$marker"
done
for marker in \
  '"image_url"' '"data:" + imageAttachment.mimeType + ";base64,"' \
  '"response_format"' '"json_schema"' '"strict", true' \
  'duplicate actions are rejected locally' \
  'raw_prompt_logged=false' 'raw_response_logged=false'; do
  require_text "$ENGINE" "$marker"
done
for marker in \
  'VLLM_DEVELOPMENT' 'ty1100EthernetViaAdbReverse()' \
  'ANDROID_LOCAL_DEVELOPMENT_ID' 'RouteMode.DEVELOPMENT'; do
  require_text "$BOUNDARY" "$marker"
done
for marker in \
  'development_ty1100_vllm' \
  'buildConfigField("boolean", "VLLM_DEVELOPMENT_ENABLED", "false")' \
  'ws://169.254.208.110:18789' \
  'buildConfigField("int", "OPENCLAW_PROTOCOL_VERSION", "3")'; do
  require_text "$BUILD" "$marker"
done
for marker in \
  'vllm_prototype_probe_complete=' 'android_transport=ADB_REVERSE' \
  'ai_transport=ETHERNET_SSH_TUNNEL' 'model_action_authority=false' \
  'production_ready=false'; do
  require_text "$PROBE" "$marker"
done

python3 -B - "$ROOT/$CONTRACT" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["schema_version"] == 1
assert contract["implementation_stage"] == "P4-R10-VLLM"
provider = contract["prototype_provider"]
assert provider["provider_id"] == "android.vllm.prototype"
assert provider["model"] == "Qwen3.5-9B-AWQ"
assert provider["android_base_url"] == "http://127.0.0.1:10030"
assert provider["only_selectable_prototype_network_provider"] is True
topology = contract["validation_topology"]
assert topology["wsl_to_ai_device"] == "SSH_LOCAL_FORWARD_OVER_ETHERNET"
assert topology["ai_device_ipv4"] == "192.168.250.100"
assert topology["ai_device_service_modified"] is False
protocol = contract["protocol"]
assert protocol["api"] == "OPENAI_CHAT_COMPLETIONS_COMPATIBLE"
assert protocol["text_supported"] is True
assert protocol["one_png_or_jpeg_image_supported"] is True
production = contract["production_separation"]
assert production["release_openclaw_base_url"] == "ws://169.254.208.110:18789"
assert production["release_openclaw_protocol_version"] == 3
assert production["production_configuration_changed"] is False
claims = contract["claim_state"]
assert claims["android13_arm64_text_verified"] is True
assert claims["android13_arm64_image_verified"] is True
for key in (
    "direct_android_ethernet_validated", "direct_npu_api_accessed",
    "vehicle_effect_hardware_accessed", "production_ready",
    "target_hardware_validated",
):
    assert claims[key] is False
PY

printf '%s\n' \
  'ty1100_vllm_prototype_contract_verified=true' \
  'prototype_provider=android.vllm.prototype' \
  'model=Qwen3.5-9B-AWQ' \
  'production_configuration_changed=false' \
  'production_ready=false'
