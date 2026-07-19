#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, XSC-001/005/006,
# DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME="central-brain/android-runtime/runtime-service"
CONTRACT="central-brain/contracts/central_brain_android_openclaw_target_gateway_v1.json"
DESIGN="docs/CENTRAL_BRAIN_OPENCLAW_TARGET_GATEWAY.md"
CODE_GUIDE="docs/CENTRAL_BRAIN_OPENCLAW_INTERFACE_CODE_GUIDE.md"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] || { echo "missing OpenClaw target file: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing OpenClaw target marker '$2' in $1" >&2; exit 1; }
}

for file in \
  "$RUNTIME/src/main/java/com/centralbrain/runtime/model/OpenClawEndpointConfig.java" \
  "$RUNTIME/src/debug/java/com/centralbrain/runtime/model/OpenClawInferenceEngine.java" \
  "$RUNTIME/src/debug/java/com/centralbrain/runtime/orchestration/OpenClawTargetIntegrationProbeActivity.java" \
  "$RUNTIME/src/test/java/com/centralbrain/runtime/model/OpenClawEndpointConfigTest.java" \
  "$RUNTIME/src/testDebug/java/com/centralbrain/runtime/model/OpenClawInferenceEngineTest.java" \
  "$RUNTIME/src/debug/AndroidManifest.xml" "$RUNTIME/build.gradle.kts" \
  "$CONTRACT" "$DESIGN" "$CODE_GUIDE"; do
  require_file "$file"
done

for marker in \
  '## 2. 两类地址的语义' '## 3. 端到端调用关系' \
  '## 11. OpenClaw protocol v3 状态机' 'connect.challenge' \
  'chat.send' 'chat.history' 'chat.abort' \
  '## 13. 回复到 Client2 的 Binder 路径' \
  '## 14. 超时、取消和失败码' \
  'release_routing_enabled=false' 'model_action_authority=false'; do
  require_text "$CODE_GUIDE" "$marker"
done

for removed in \
  "$RUNTIME/src/debug/java/com/centralbrain/runtime/model/OpenClawCredentialStore.java" \
  "$RUNTIME/src/debug/java/com/centralbrain/runtime/model/OpenClawCredentialProvisioningActivity.java" \
  "$RUNTIME/src/testDebug/java/com/centralbrain/runtime/model/OpenClawCredentialStoreTest.java" \
  "tools/provision_central_brain_openclaw_target.sh"; do
  [[ ! -e "$ROOT_DIR/$removed" ]] \
    || { echo "retired OpenClaw provisioning surface still exists: $removed" >&2; exit 1; }
done

CONFIG="$RUNTIME/src/main/java/com/centralbrain/runtime/model/OpenClawEndpointConfig.java"
ENGINE="$RUNTIME/src/debug/java/com/centralbrain/runtime/model/OpenClawInferenceEngine.java"
BOUNDARY="$RUNTIME/src/debug/java/com/centralbrain/runtime/orchestration/DebugDecisionCompositionBoundary.java"
MANIFEST="$RUNTIME/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="$RUNTIME/src/main/AndroidManifest.xml"
BUILD="$RUNTIME/build.gradle.kts"

for marker in \
  'TARGET_HOST = "169.254.208.110"' 'TARGET_PORT = 18_789' \
  'WEBSOCKET_PATH = "/"' 'CONTROL_UI_PATH = "/chat"' \
  'TARGET_TOKEN = "Iluvatar1!"' 'CONTROL_UI_QUERY = "token=" + TARGET_TOKEN' \
  'getEmbeddedToken()' 'PROTOCOL_VERSION = 3' 'getUserInfo() != null'; do
  require_text "$CONFIG" "$marker"
done
for marker in \
  'implements LocalModelProvider.LocalInferenceEngine' 'connect.challenge' \
  'chat.send' 'chat.abort' 'chat.history' 'sec-websocket-accept' \
  'prompt.getAllowedActions().contains(action)' 'history is not bound to the current request' \
  'raw_prompt_logged=false' 'raw_response_logged=false' 'credential_logged=false'; do
  require_text "$ENGINE" "$marker"
done
for marker in \
  'NetworkModelMode.OPENCLAW_TARGET' 'TARGET_OPENCLAW_TRANSITIONAL_ID' \
  'RouteMode.TARGET_INTEGRATION' 'registerScenarioPrompt(inputDigest, scenarioId)'; do
  require_text "$BOUNDARY" "$marker"
done
for marker in \
  'centralBrainTargetOpenClaw' 'target_openclaw_transitional' \
  'OPENCLAW_TARGET_ROUTING_ENABLED' 'ws://169.254.208.110:18789'; do
  require_text "$BUILD" "$marker"
done
require_text "$MANIFEST" '.orchestration.OpenClawTargetIntegrationProbeActivity'
require_text "$MANIFEST" 'android:permission="android.permission.DUMP"'
if grep -Fq 'OpenClawCredentialProvisioningActivity' \
    "$ROOT_DIR/$MANIFEST" "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "retired OpenClaw credential provisioning Activity remains declared" >&2
  exit 1
fi

python3 - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    contract = json.load(handle)
assert contract["schema_version"] == 2
assert contract["implementation_stage"] == "P7-R3-OC2"
target = contract["target_profile"]
assert target["provider_id"] == "external.openclaw.transitional"
assert target["assurance"] == "TARGET_INTEGRATION"
assert target["websocket_uri"] == "ws://169.254.208.110:18789/"
assert target["control_ui_uri"] == "http://169.254.208.110:18789/chat?token=Iluvatar1!"
assert target["protocol_version"] == 3
assert target["credential_embedded"] is True
assert target["credential_extractable_from_apk"] is True
assert target["credential_persisted"] is True
assert target["credential_logged"] is False
assert target["release_routing_enabled"] is False
protocol = contract["gateway_protocol"]
assert protocol["chat_send_method"] == "chat.send"
assert protocol["history_fallback_method"] == "chat.history"
assert protocol["openai_http_endpoint_enabled"] is False
assert protocol["model_action_authority"] is False
evidence = contract["android13_arm64_evidence"]
assert evidence["android_api"] == 33
assert evidence["abi"] == "arm64-v8a"
assert evidence["runtime_probe_verified"] is True
assert evidence["client2_projection_verified"] is True
assert evidence["raw_response_recorded"] is False
latest = contract["latest_target_retest"]
assert latest["target_host_reachable_by_icmp"] is True
assert latest["target_port_18789_listening"] is False
assert latest["transport_result"] == "CONNECTION_REFUSED"
assert latest["protocol_or_token_validation_reached"] is False
assert latest["target_profile_installed"] is True
assert latest["client2_transport_failure_projected"] is True
assert latest["production_model_regression_passed"] is False
claims = contract["claim_state"]
assert claims["openclaw_target_integration_implemented"] is True
assert claims["openclaw_target_android13_arm64_verified"] is True
assert claims["external_compute_accessed"] is True
assert claims["direct_npu_accessed"] is False
assert claims["vehicle_effect_hardware_accessed"] is False
assert claims["fixed_target_credential_active"] is True
assert claims["latest_target_connectivity_verified"] is False
assert claims["production_provider_qualified"] is False
assert claims["production_ready"] is False
assert claims["target_hardware_validated"] is False
PY

for doc in README.md central-brain/android-runtime/README.md \
  docs/CENTRAL_BRAIN_ROADMAP.md docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md \
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md; do
  require_text "$doc" 'P7-R3-OC'
done

printf '%s\n' \
  'openclaw_target_integration_implemented=true' \
  'openclaw_target_android13_arm64_verified=true' \
  'client2_openclaw_projection_verified=true' \
  'fixed_target_credential_active=true' \
  'latest_target_connectivity_verified=false' \
  'external_compute_accessed=true' \
  'direct_npu_accessed=false' \
  'vehicle_effect_hardware_accessed=false' \
  'production_provider_qualified=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P7-R3-OC2'
