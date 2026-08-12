#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001/002, S2-SAF-001, S2-OBS-001/002,
# XSC-001/005/006, DEL-001/003/004/005. Stage: P7-R4-OCDEV.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_APK="$ROOT/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
CLIENT2_APK="$ROOT/builds/client2-central-brain/signed/client2-central-brain.debug.apk"
RUNTIME_PACKAGE="com.centralbrain.runtime"
CLIENT2_PACKAGE="com.tuanjie.urasclient2"
CLIENT2_ACTIVITY="$CLIENT2_PACKAGE/.MainActivity"
TIMEOUT_SECONDS="${CENTRAL_BRAIN_CLIENT2_OPENCLAW_TIMEOUT_SECONDS:-180}"
SCENARIO="${CENTRAL_BRAIN_CLIENT2_SCENARIO:-cold}"
MODEL_ROUTE="${CENTRAL_BRAIN_CLIENT2_MODEL_ROUTE:-development_ty1100_vllm}"

case "$MODEL_ROUTE" in
  development_ty1100_vllm)
    build_profile="development_ty1100_vllm"
    expected_protocol="openai_chat_completions"
    expected_transport="ADB_REVERSE_AND_ETHERNET_SSH_TUNNEL"
    ;;
  development_wsl_openclaw)
    build_profile="development_wsl_openclaw"
    expected_protocol=4
    expected_transport="ADB_REVERSE"
    ;;
  target_openclaw_transitional)
    build_profile="target_openclaw_transitional"
    expected_protocol=3
    expected_transport="TARGET_ETHERNET"
    ;;
  *)
    echo "client2_openclaw_development_test_complete=false reason=INVALID_MODEL_ROUTE" >&2
    exit 2
    ;;
esac

case "$SCENARIO" in
  cold)
    scenario_button="centralBrainColdButton"
    ;;
  fatigue)
    scenario_button="centralBrainTiredButton"
    ;;
  multimodal)
    scenario_button="centralBrainMultimodalButton"
    ;;
  smoking)
    scenario_button="centralBrainSmokingButton"
    ;;
  *)
    echo "client2_openclaw_development_test_complete=false reason=INVALID_SCENARIO" >&2
    exit 2
    ;;
esac

if [[ -n "${ADB_BIN:-}" ]]; then
  adb_base=("$ADB_BIN")
elif [[ -x /mnt/e/platform-tools/adb.exe ]]; then
  adb_base=(/mnt/e/platform-tools/adb.exe)
elif [[ -x "$ROOT/.tools/android-sdk/platform-tools/adb" ]]; then
  adb_base=("$ROOT/.tools/android-sdk/platform-tools/adb")
else
  echo "client2_openclaw_development_test_complete=false reason=ADB_NOT_FOUND" >&2
  exit 3
fi

if [[ -n "${ADB_SERVER_PORT:-}" ]]; then
  [[ "$ADB_SERVER_PORT" =~ ^[0-9]+$ ]] \
    || { echo "client2_openclaw_development_test_complete=false reason=INVALID_ADB_SERVER_PORT" >&2; exit 4; }
  adb_base+=( -P "$ADB_SERVER_PORT" )
fi

adb=("${adb_base[@]}")
if [[ -n "${ANDROID_TRANSPORT_ID:-}" ]]; then
  [[ "$ANDROID_TRANSPORT_ID" =~ ^[0-9]+$ ]] \
    || { echo "client2_openclaw_development_test_complete=false reason=INVALID_TRANSPORT_ID" >&2; exit 4; }
  adb+=( -t "$ANDROID_TRANSPORT_ID" )
elif [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb+=( -s "$ANDROID_SERIAL" )
else
  device_count="$("${adb_base[@]}" devices | awk \
    'NR > 1 {gsub(/\r/, "", $2)} $2 == "device" {count++} END {print count+0}')"
  if [[ "$device_count" != "1" ]]; then
    echo "client2_openclaw_development_test_complete=false reason=ANDROID_DEVICE_COUNT_${device_count}" >&2
    exit 5
  fi
fi

if [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] && ((TIMEOUT_SECONDS >= 30)); then
  :
else
  echo "client2_openclaw_development_test_complete=false reason=INVALID_TIMEOUT" >&2
  exit 6
fi

if [[ "${CENTRAL_BRAIN_SKIP_CLIENT2_BUILD:-false}" != "true" ]]; then
  CENTRAL_BRAIN_MODEL_GATEWAY_PROFILE="$build_profile" \
    "$ROOT/apk-labs/client2-central-brain/scripts/build_debug_apk.sh" >/dev/null
fi
for apk in "$RUNTIME_APK" "$CLIENT2_APK"; do
  [[ -f "$apk" ]] \
    || { echo "client2_openclaw_development_test_complete=false reason=APK_NOT_FOUND" >&2; exit 7; }
done

apk_argument() {
  if [[ "${adb_base[0]}" == *.exe ]] && command -v wslpath >/dev/null; then
    wslpath -w "$1"
  else
    printf '%s\n' "$1"
  fi
}

verify_preinstalled_apk() {
  local package_name="$1"
  local apk_path="$2"
  local installed_path local_hash device_hash
  installed_path="$("${adb[@]}" shell pm path "$package_name" \
    | tr -d '\r' | sed -n 's/^package://p' | head -n 1)"
  [[ -n "$installed_path" ]] \
    || { echo "client2_openclaw_development_test_complete=false reason=PREINSTALLED_PACKAGE_MISSING" >&2; return 1; }
  local_hash="$(sha256sum "$apk_path" | awk '{print $1}')"
  device_hash="$("${adb[@]}" shell sha256sum "$installed_path" \
    | tr -d '\r' | awk '{print $1}')"
  if [[ -z "$device_hash" || "$device_hash" != "$local_hash" ]]; then
    echo "client2_openclaw_development_test_complete=false reason=PREINSTALLED_APK_HASH_MISMATCH" >&2
    return 1
  fi
  echo "preinstalled_apk_verified=true package=$package_name sha256=$local_hash"
}

case "${CENTRAL_BRAIN_SKIP_ANDROID_INSTALL:-false}" in
  false)
    "${adb[@]}" install -r -d -t "$(apk_argument "$RUNTIME_APK")" >/dev/null
    "${adb[@]}" install -r -d -t "$(apk_argument "$CLIENT2_APK")" >/dev/null
    ;;
  true)
    verify_preinstalled_apk "$RUNTIME_PACKAGE" "$RUNTIME_APK"
    verify_preinstalled_apk "$CLIENT2_PACKAGE" "$CLIENT2_APK"
    ;;
  *)
    echo "client2_openclaw_development_test_complete=false reason=INVALID_SKIP_ANDROID_INSTALL" >&2
    exit 20
    ;;
esac

if [[ "$MODEL_ROUTE" == "development_ty1100_vllm" ]]; then
  CENTRAL_BRAIN_ANDROID_SERIAL="${ANDROID_SERIAL:-testboard}" \
    ADB_SERVER_PORT="${ADB_SERVER_PORT:-5038}" \
    "$ROOT/tools/manage_central_brain_ty1100_vllm_bridge.sh" start >/dev/null
elif [[ "$MODEL_ROUTE" == "development_wsl_openclaw" ]]; then
  bridge_env=()
  [[ -n "${ANDROID_TRANSPORT_ID:-}" ]] \
    && bridge_env+=("ANDROID_TRANSPORT_ID=$ANDROID_TRANSPORT_ID")
  [[ -n "${ANDROID_SERIAL:-}" ]] \
    && bridge_env+=("ANDROID_SERIAL=$ANDROID_SERIAL")
  [[ -n "${ADB_BIN:-}" ]] && bridge_env+=("ADB_BIN=$ADB_BIN")
  [[ -n "${ADB_SERVER_PORT:-}" ]] \
    && bridge_env+=("ADB_SERVER_PORT=$ADB_SERVER_PORT")
  env "${bridge_env[@]}" "$ROOT/tools/start_central_brain_wsl_openclaw_bridge.sh" >/dev/null
else
  "${adb[@]}" reverse --remove tcp:18789 >/dev/null 2>&1 || true
  if ! "${adb[@]}" shell curl -sS --fail --max-time 5 \
      "http://169.254.208.110:18789/" >/dev/null; then
    echo "client2_openclaw_development_test_complete=false reason=TARGET_OPENCLAW_NETWORK_UNREACHABLE" >&2
    exit 19
  fi
fi

android_api="$("${adb[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
android_abi="$("${adb[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
display_size="$("${adb[@]}" shell wm size | tr -d '\r' | sed -n 's/.*size: //p' | tail -n 1)"
if [[ "$android_api" != "33" || "$android_abi" != arm64* \
    || "$display_size" != "1920x1080" ]]; then
  echo "client2_openclaw_development_test_complete=false reason=ANDROID_HMI_TARGET_MISMATCH" >&2
  exit 8
fi

dump_ui() {
  "${adb[@]}" shell uiautomator dump /data/local/tmp/central-brain-client2.xml >/dev/null
  "${adb[@]}" exec-out cat /data/local/tmp/central-brain-client2.xml | tr -d '\r'
}

tap_resource() {
  local resource_id="$1"
  local ui node bounds left top right bottom
  ui="$(dump_ui)"
  node="$(printf '%s\n' "$ui" \
    | grep -o "<node[^>]*resource-id=\"${CLIENT2_PACKAGE}:id/${resource_id}\"[^>]*>" \
    | head -n 1 || true)"
  bounds="$(printf '%s\n' "$node" | sed -n \
    's/.*bounds="\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]".*/\1 \2 \3 \4/p')"
  [[ -n "$bounds" ]] || return 1
  read -r left top right bottom <<<"$bounds"
  "${adb[@]}" shell input tap "$(((left + right) / 2))" "$(((top + bottom) / 2))"
}

"${adb[@]}" shell am force-stop "$CLIENT2_PACKAGE"
"${adb[@]}" shell am start -W -n "$CLIENT2_ACTIVITY" >/dev/null
sleep 4
if ! dump_ui | grep -q "${CLIENT2_PACKAGE}:id/${scenario_button}"; then
  tap_resource centralBrainNavigationTrigger \
    || { echo "client2_openclaw_development_test_complete=false reason=NAVIGATION_TRIGGER_NOT_FOUND" >&2; exit 9; }
  sleep 1
fi

"${adb[@]}" logcat -c
tap_resource "$scenario_button" \
  || { echo "client2_openclaw_development_test_complete=false reason=SCENARIO_TRIGGER_NOT_FOUND" >&2; exit 10; }

shopping_consent_approved=false
purchase_commit_approved=false
navigation_start_approved=false
for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  logs="$("${adb[@]}" logcat -d -v brief \
    -s CentralBrainVllm:I CentralBrainOpenClaw:I CbClient2Orchestration:I CbDevModelProjection:I '*:S' \
    | tr -d '\r')"
  if printf '%s\n' "$logs" | grep -Eq \
      'client2_orchestration_command_failed=true|openclaw_inference_failed=true|vllm_inference_completed=false'; then
    printf '%s\n' "$logs" | grep -E \
      'client2_orchestration_(failure_diagnosed|command_failed)=true|openclaw_inference_failed=true|vllm_inference_completed=false' >&2
    echo "client2_openclaw_development_test_complete=false reason=RUNTIME_FAILURE" >&2
    exit 11
  fi
  if [[ "$SCENARIO" == "multimodal" ]]; then
    if [[ "$shopping_consent_approved" == "false" ]] \
        && printf '%s\n' "$logs" | grep -q \
          'pending_node_id=request_shopping_consent'; then
      tap_resource centralBrainApproveButton \
        || { echo "client2_openclaw_development_test_complete=false reason=SHOPPING_CONSENT_BUTTON_MISSING" >&2; exit 15; }
      shopping_consent_approved=true
      sleep 1
      continue
    fi
    if [[ "$purchase_commit_approved" == "false" ]] \
        && printf '%s\n' "$logs" | grep -q \
          'pending_node_id=request_purchase_confirmation'; then
      tap_resource centralBrainApproveButton \
        || { echo "client2_openclaw_development_test_complete=false reason=PURCHASE_CONFIRMATION_BUTTON_MISSING" >&2; exit 16; }
      purchase_commit_approved=true
      sleep 1
      continue
    fi
    if [[ "$navigation_start_approved" == "false" ]] \
        && printf '%s\n' "$logs" | grep -q \
          'pending_node_id=request_navigation_confirmation'; then
      tap_resource centralBrainApproveButton \
        || { echo "client2_openclaw_development_test_complete=false reason=NAVIGATION_CONFIRMATION_BUTTON_MISSING" >&2; exit 17; }
      navigation_start_approved=true
      sleep 1
      continue
    fi
  fi
  model_completed=false
  if [[ "$MODEL_ROUTE" == "development_ty1100_vllm" ]]; then
    printf '%s\n' "$logs" | grep -q \
      'vllm_inference_completed=true endpoint_profile=ty1100_ethernet_via_adb_reverse model=Qwen3.5-9B-AWQ' \
      && model_completed=true
  else
    printf '%s\n' "$logs" | grep -q \
      "openclaw_inference_completed=true endpoint_profile=${MODEL_ROUTE} protocol=${expected_protocol}" \
      && model_completed=true
  fi
  if [[ "$model_completed" == "true" ]] \
      && printf '%s\n' "$logs" | grep -Eq \
      'client2_orchestration_snapshot_projected=true .*model_projection_available=true .*simulated_only=true .*hardware_accessed=false'; then
    ui="$(dump_ui)"
    if [[ "$SCENARIO" == "smoking" ]]; then
      hmi_complete=false
      printf '%s\n' "$ui" | grep -q 'centralBrainPanel' \
        && printf '%s\n' "$ui" | grep -q 'centralBrainLiveTraceText' \
        && hmi_complete=true
    else
      hmi_complete=false
      printf '%s\n' "$ui" | grep -q 'RESULT / COMPLETED' \
        && printf '%s\n' "$ui" | grep -q 'centralBrainActuatorOverlay' \
        && hmi_complete=true
    fi
    if [[ "$hmi_complete" != "true" ]]; then
      echo "client2_openclaw_development_test_complete=false reason=HMI_FEEDBACK_INCOMPLETE" >&2
      exit 12
    fi
    if [[ "$SCENARIO" == "smoking" ]]; then
      if ! printf '%s\n' "$logs" | grep -Eq \
              'vllm_inference_completed=true .*image_present=true image_bytes=206611 image_sha256=fc561d2870d467da910353e6623ab39f056641bf4169083953b9d3c9c82f10a7' \
          || ! printf '%s\n' "$logs" | grep -Eq \
              'development_model_projection_read=true .*projection_available=true image_consumed=true' \
          || ! printf '%s\n' "$logs" | grep -q \
              'client2_orchestration_snapshot_projected=true orchestration_state=5' \
          || ! printf '%s\n' "$logs" | grep -q 'model_projection_available=true'; then
        echo "client2_openclaw_development_test_complete=false reason=SMOKING_PROOF_INCOMPLETE" >&2
        exit 21
      fi
    elif [[ "$SCENARIO" == "multimodal" ]]; then
      if ! { printf '%s\n' "$logs" | grep -Eq \
              'openclaw_inference_started=true .*image_present=true image_bytes=2244206 image_sha256=93441797b96c512a7b87905e4d326fbacdbf3a80e4d336d018a41224a0cd8438' \
            || printf '%s\n' "$logs" | grep -Eq \
              'development_model_projection_read=true .*projection_available=true image_consumed=true'; } \
          || ! printf '%s\n' "$ui" | grep -q 'MODEL OUTPUT / IMAGE_CONSUMED' \
          || ! printf '%s\n' "$ui" | grep -q 'AGENT ACTIONS / ALLOWLISTED' \
          || ! printf '%s\n' "$ui" | grep -q 'MODEL INPUT / BOUND' \
          || ! printf '%s\n' "$ui" | grep -q '购物与路径规划服务' \
          || ! printf '%s\n' "$ui" | grep -q '饮用水候选 3 项' \
          || ! printf '%s\n' "$ui" | grep -q '订单已确认' \
          || ! printf '%s\n' "$ui" | grep -q 'NOT_DISPATCHED' \
          || ! printf '%s\n' "$ui" | grep -q '购物路线已启动' \
          || ! printf '%s\n' "$ui" | grep -q 'UI SIMULATION ONLY' \
          || [[ "$shopping_consent_approved" != "true" ]] \
          || [[ "$purchase_commit_approved" != "true" ]] \
          || [[ "$navigation_start_approved" != "true" ]]; then
        echo "client2_openclaw_development_test_complete=false reason=MULTIMODAL_PROOF_INCOMPLETE" >&2
        exit 14
      fi
    elif ! printf '%s\n' "$ui" | grep -q 'VEHICLE BUS NOT ACCESSED'; then
      echo "client2_openclaw_development_test_complete=false reason=VEHICLE_BOUNDARY_INCOMPLETE" >&2
      exit 18
    fi
    printf '%s\n' "$logs" | grep -E \
      'vllm_inference_completed=|openclaw_(protocol_stage|inference_(started|completed))=|client2_orchestration_snapshot_projected=true'
    if [[ "$MODEL_ROUTE" == "development_ty1100_vllm" ]]; then
      route_claims=(
        'real_ty1100_vllm_accessed=true'
        'direct_android_ethernet_validated=false'
        'production_configuration_changed=false'
      )
    elif [[ "$MODEL_ROUTE" == "development_wsl_openclaw" ]]; then
      route_claims=(
        'real_wsl_openclaw_ollama_accessed=true'
        'target_openclaw_ethernet_validated=false'
        'ethernet_validated=false'
      )
    else
      route_claims=(
        'real_wsl_openclaw_ollama_accessed=false'
        'target_openclaw_ethernet_validated=true'
        'ethernet_validated=true'
      )
    fi
    if [[ "$SCENARIO" == "smoking" ]]; then
      hmi_effect_claims=(
        'response_only_hmi_projection_verified=true'
        'simulated_hmi_effect_verified=false'
      )
    else
      hmi_effect_claims=(
        'response_only_hmi_projection_verified=false'
        'simulated_hmi_effect_verified=true'
      )
    fi
    printf '%s\n' \
      'client2_openclaw_development_test_complete=true' \
      "scenario=$SCENARIO" \
      'sdk_runtime_client2_same_build=true' \
      'android13_arm64_1920x1080_verified=true' \
      "transport=$expected_transport" \
      "endpoint_profile=$MODEL_ROUTE" \
      "model_protocol=$expected_protocol" \
      "${route_claims[@]}" \
      "${hmi_effect_claims[@]}" \
      'vehicle_effect_hardware_accessed=false' \
      'production_ready=false' \
      'target_hardware_validated=false'
    if [[ "$MODEL_ROUTE" == "development_ty1100_vllm" ]]; then
      printf '%s\n' 'client2_ty1100_vllm_test_complete=true'
    fi
    exit 0
  fi
  sleep 1
done

echo "client2_openclaw_development_test_complete=false reason=TEST_TIMEOUT" >&2
exit 13
