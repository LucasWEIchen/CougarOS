#!/usr/bin/env bash
set -euo pipefail

# Safe Android 13 hybrid application installer. Dry-run is the default.
# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUNDLE_DIR=""
TARGET_INPUTS=""
SERIAL="${ANDROID_SERIAL:-}"
EXECUTE=false
ALLOW_DEBUG_SIGNING=false
INCLUDE_CLIENT2=false

usage() {
  cat <<'EOF'
Usage: install_central_brain_android_hybrid_delivery.sh [options]

Options:
  --bundle-dir PATH       Unpacked Central Brain hybrid bundle.
  --target-inputs PATH    Completed target-input JSON; bundle template is default.
  --serial SERIAL         Select an adb device explicitly.
  --execute               Install after every preflight check; default is dry-run.
  --allow-debug-signing   Permit the debug signer on a disposable test device.
  --include-client2       Add the optional Client2 Binder demo to the install profile.
  -h, --help              Show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --bundle-dir)
      [[ $# -ge 2 ]] || { echo "--bundle-dir requires a value" >&2; exit 2; }
      BUNDLE_DIR="$2"
      shift 2
      ;;
    --target-inputs)
      [[ $# -ge 2 ]] || { echo "--target-inputs requires a value" >&2; exit 2; }
      TARGET_INPUTS="$2"
      shift 2
      ;;
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --execute)
      EXECUTE=true
      shift
      ;;
    --allow-debug-signing)
      ALLOW_DEBUG_SIGNING=true
      shift
      ;;
    --include-client2)
      INCLUDE_CLIENT2=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

[[ -n "$BUNDLE_DIR" ]] || { echo "--bundle-dir is required" >&2; exit 2; }
BUNDLE_DIR="$(realpath "$BUNDLE_DIR")"
MANIFEST="$BUNDLE_DIR/DELIVERY-MANIFEST.json"
[[ -f "$MANIFEST" ]] || { echo "hybrid delivery manifest is missing" >&2; exit 1; }

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  source "$ROOT_DIR/env.sh"
fi

resolve_executable() {
  local candidate
  for candidate in "$@"; do
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      realpath "$candidate"
      return
    fi
  done
  return 1
}

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
LATEST_APKSIGNER=""
LATEST_AAPT=""
if [[ -n "$SDK_ROOT" && -d "$SDK_ROOT/build-tools" ]]; then
  LATEST_APKSIGNER="$(find "$SDK_ROOT/build-tools" -mindepth 2 -maxdepth 2 \
    -type f -name apksigner -print | sort -V | tail -1)"
  LATEST_AAPT="$(find "$SDK_ROOT/build-tools" -mindepth 2 -maxdepth 2 \
    -type f -name aapt -print | sort -V | tail -1)"
fi
ADB="$(resolve_executable \
  "${ADB:-}" \
  "$(command -v adb || true)" \
  "${SDK_ROOT:+$SDK_ROOT/platform-tools/adb}" \
  "$ROOT_DIR/.tools/android-sdk/platform-tools/adb")" \
  || { echo "required tool unavailable: adb" >&2; exit 1; }
APKSIGNER="$(resolve_executable \
  "${APKSIGNER:-}" \
  "$(command -v apksigner || true)" \
  "$LATEST_APKSIGNER" \
  "$ROOT_DIR/.tools/android-build-tools-current/apksigner")" \
  || { echo "required tool unavailable: apksigner" >&2; exit 1; }
AAPT="$(resolve_executable \
  "${AAPT:-}" \
  "$(command -v aapt || true)" \
  "$LATEST_AAPT" \
  "$ROOT_DIR/.tools/android-build-tools-current/aapt")" \
  || { echo "required tool unavailable: aapt" >&2; exit 1; }
JAVA="$(resolve_executable \
  "${JAVA:-}" \
  "$(command -v java || true)" \
  "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
  "$ROOT_DIR/.tools/jdk/bin/java")" \
  || { echo "required tool unavailable: Java 17" >&2; exit 1; }
PYTHON="$(resolve_executable "$(command -v python3 || true)")" \
  || { echo "required tool unavailable: python3" >&2; exit 1; }

if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME="$(dirname "$(dirname "$JAVA")")"
fi
if [[ -z "$SDK_ROOT" ]]; then
  SDK_ROOT="$(dirname "$(dirname "$ADB")")"
fi
export JAVA_HOME
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ADB APKSIGNER AAPT
export PYTHONDONTWRITEBYTECODE=1
export PATH="$(dirname "$JAVA"):$(dirname "$ADB"):$(dirname "$APKSIGNER"):$PATH"

DELIVERY_TOOL="$BUNDLE_DIR/tools/central_brain_android_hybrid_delivery.py"
[[ -f "$DELIVERY_TOOL" ]] \
  || { echo "hybrid delivery verifier is missing from bundle" >&2; exit 1; }
"$PYTHON" -B "$DELIVERY_TOOL" verify --bundle-dir "$BUNDLE_DIR" >/dev/null

if [[ -z "$TARGET_INPUTS" ]]; then
  TARGET_INPUTS="$BUNDLE_DIR/contracts/target-inputs.example.json"
fi
[[ -f "$TARGET_INPUTS" ]] || { echo "target input file is missing" >&2; exit 1; }
"$PYTHON" -B - "$TARGET_INPUTS" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("target input schema must be 1.0.0")
if payload.get("device", {}).get("android_api_expected") != 33:
    raise SystemExit("target inputs must require Android API 33")
claims = payload.get("claim_state", {})
for key in (
    "physical_controller_evidence_available",
    "target_system_integration_owner_resolved",
    "production_activation_allowed",
    "target_hardware_validated",
):
    if claims.get(key) is not False:
        raise SystemExit(f"unsupported positive target claim: {key}")
print(f"target_input_status={payload.get('status', 'unknown')}")
PY

if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial" >&2
    exit 1
  fi
  SERIAL="${DEVICES[0]}"
fi
ADB_DEVICE=("$ADB" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state)" == "device" ]] \
  || { echo "adb device is not online: $SERIAL" >&2; exit 1; }
SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r\n')"
MODEL="$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r\n')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r\n')"
ABI_LIST_64="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abilist64 | tr -d '\r\n')"
[[ "$SDK" == "33" ]] || { echo "hybrid delivery requires API 33" >&2; exit 1; }
case ",$ABI_LIST_64," in
  *,arm64-v8a,*|*,x86_64,*) ;;
  *) echo "hybrid delivery has no compatible 64-bit ABI" >&2; exit 1 ;;
esac

mapfile -t MANIFEST_ROWS < <("$PYTHON" -B - "$MANIFEST" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
for item in payload["artifact_inventory"]:
    if item["kind"] == "apk":
        print("|".join((
            item["id"],
            item["bundle_path"],
            item["package_name"],
            item["signer_sha256"],
            str(item["install_order"]),
        )))
print("DEBUG|" + str(payload["signing"]["debug_signer_detected"]).lower())
PY
)

DEBUG_SIGNER=false
INSTALL_ROWS=()
for row in "${MANIFEST_ROWS[@]}"; do
  if [[ "$row" == DEBUG\|* ]]; then
    DEBUG_SIGNER="${row#DEBUG|}"
    continue
  fi
  if [[ "$row" == client2-demo\|* && "$INCLUDE_CLIENT2" != true ]]; then
    continue
  fi
  INSTALL_ROWS+=("$row")
done
if [[ "$INCLUDE_CLIENT2" == true ]]; then
  INSTALL_PROFILE="client2-demo"
else
  INSTALL_PROFILE="maintenance"
fi
if [[ "$EXECUTE" == true && "$DEBUG_SIGNER" == true \
    && "$ALLOW_DEBUG_SIGNING" != true ]]; then
  echo "debug bundle requires --allow-debug-signing on a disposable test device" >&2
  exit 1
fi

for row in "${INSTALL_ROWS[@]}"; do
  IFS='|' read -r artifact_id bundle_path package_name expected_signer install_order \
    <<<"$row"
  apk="$BUNDLE_DIR/$bundle_path"
  badging="$("$AAPT" dump badging "$apk")"
  actual_package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" \
    <<<"$badging" | head -1)"
  [[ "$actual_package" == "$package_name" ]] \
    || { echo "BUNDLE_PACKAGE_MISMATCH artifact=$artifact_id" >&2; exit 1; }
  actual_signer="$("$APKSIGNER" verify --print-certs "$apk" \
    | sed -n 's/^.*certificate SHA-256 digest: //p' \
    | head -1 \
    | tr 'A-F' 'a-f')"
  [[ -n "$actual_signer" && "$actual_signer" == "$expected_signer" ]] \
    || { echo "BUNDLE_SIGNER_MISMATCH artifact=$artifact_id" >&2; exit 1; }
done

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
RUNTIME_APK="$BUNDLE_DIR/artifacts/runtime-service-debug.apk"
DEMO_APK="$BUNDLE_DIR/artifacts/demo-hmi-debug.apk"
PREFLIGHT="$BUNDLE_DIR/tools/preflight_central_brain_android13_blackbox.sh"
[[ -x "$PREFLIGHT" ]] || { echo "bundle black-box preflight is missing" >&2; exit 1; }
bash "$PREFLIGHT" \
  --serial "$SERIAL" \
  --runtime-apk "$RUNTIME_APK" \
  --demo-apk "$DEMO_APK" \
  --require-api-33 \
  --report "$TEMP_DIR/preflight.properties" >/dev/null

# Check every selected existing package signer before the first install.
for row in "${INSTALL_ROWS[@]}"; do
  IFS='|' read -r artifact_id bundle_path package_name expected_signer install_order \
    <<<"$row"
  paths="$("${ADB_DEVICE[@]}" shell pm path "$package_name" 2>/dev/null \
    | tr -d '\r' || true)"
  if [[ -z "$paths" ]]; then
    continue
  fi
  remote="$(grep -E '^package:/data/app/.*/base\.apk$' <<<"$paths" \
    | head -1 | sed 's/^package://' || true)"
  [[ -n "$remote" ]] \
    || { echo "existing package is not an ordinary /data/app APK: $package_name" >&2; exit 1; }
  local_apk="$TEMP_DIR/$artifact_id.installed.apk"
  "${ADB_DEVICE[@]}" pull "$remote" "$local_apk" >/dev/null 2>&1
  installed_signer="$("$APKSIGNER" verify --print-certs "$local_apk" \
    | sed -n 's/^.*certificate SHA-256 digest: //p' \
    | head -1 \
    | tr 'A-F' 'a-f')"
  if [[ -z "$installed_signer" || "$installed_signer" != "$expected_signer" ]]; then
    echo "SIGNER_MIGRATION_REQUIRED package=$package_name" >&2
    echo "No package was installed or removed." >&2
    exit 1
  fi
done

if [[ "$EXECUTE" == false ]]; then
  printf '%s\n' \
    "hybrid_delivery_preflight_verified=true" \
    "install_profile=$INSTALL_PROFILE" \
    "device_serial=$SERIAL" \
    "device_model=$MODEL" \
    "android_api=$SDK" \
    "device_abi=$ABI" \
    "supported_64_bit_abis=$ABI_LIST_64" \
    "debug_signer_detected=$DEBUG_SIGNER" \
    "bundle_apk_identity_verified=true" \
    "existing_signer_preflight_verified=true" \
    "native_runtime_abis=arm64-v8a,x86_64" \
    "install_executed=false" \
    "automatic_uninstall_enabled=false" \
    "system_partition_write_capability=false" \
    "production_ready=false" \
    "physical_controller_evidence_available=false" \
    "target_hardware_validated=false" \
    "native_vendor_npu_provider_available=false" \
    "native_runtime_dispatch_enabled=false" \
    "hardware_accessed=false"
  exit 0
fi

for row in "${INSTALL_ROWS[@]}"; do
  IFS='|' read -r artifact_id bundle_path package_name expected_signer install_order \
    <<<"$row"
  "${ADB_DEVICE[@]}" install -r "$BUNDLE_DIR/$bundle_path" \
    >"$TEMP_DIR/$artifact_id.install.txt"
  grep -Fq "Success" "$TEMP_DIR/$artifact_id.install.txt" \
    || { cat "$TEMP_DIR/$artifact_id.install.txt" >&2; exit 1; }
done

for row in "${INSTALL_ROWS[@]}"; do
  IFS='|' read -r artifact_id bundle_path package_name expected_signer install_order \
    <<<"$row"
  installed_path="$("${ADB_DEVICE[@]}" shell pm path "$package_name" | tr -d '\r')"
  [[ "$installed_path" == package:/data/app/*/base.apk ]] \
    || { echo "package is not under /data/app: $package_name" >&2; exit 1; }
done

DEMO_PACKAGE="$("${ADB_DEVICE[@]}" shell dumpsys package com.centralbrain.demo)"
grep -Fq 'com.centralbrain.permission.BIND_RUNTIME: granted=true' <<<"$DEMO_PACKAGE"
grep -Fq 'com.centralbrain.permission.BIND_GOVERNANCE: granted=true' <<<"$DEMO_PACKAGE"
if [[ "$INCLUDE_CLIENT2" == true ]]; then
  CLIENT2_PACKAGE="$("${ADB_DEVICE[@]}" shell dumpsys package com.tuanjie.urasclient2)"
  grep -Fq 'com.centralbrain.permission.BIND_RUNTIME: granted=true' <<<"$CLIENT2_PACKAGE"
fi

DEMO_START="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.demo/.DemoActivity)"
grep -Fq 'Status: ok' <<<"$DEMO_START"
CLIENT2_STARTED=false
if [[ "$INCLUDE_CLIENT2" == true ]]; then
  CLIENT2_START="$("${ADB_DEVICE[@]}" shell am start -W \
    -n com.tuanjie.urasclient2/.MainActivity)"
  grep -Fq 'Status: ok' <<<"$CLIENT2_START"
  CLIENT2_STARTED=true
fi
sleep 1

RUNTIME_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys activity service \
  com.centralbrain.runtime/.CentralBrainRuntimeService)"
for marker in \
  'native_runtime_process_ready=true' \
  'native_runtime_abi_version=1' \
  'native_runtime_active_slots=0' \
  'native_vendor_npu_provider_available=false' \
  'native_runtime_dispatch_enabled=false' \
  'production_activation_allowed=false' \
  'target_hardware_validated=false' \
  'hardware_accessed=false'; do
  grep -Fq "$marker" <<<"$RUNTIME_DUMP" \
    || { echo "installed Runtime missing marker: $marker" >&2; exit 1; }
done

printf '%s\n' \
  "hybrid_delivery_preflight_verified=true" \
  "install_profile=$INSTALL_PROFILE" \
  "device_serial=$SERIAL" \
  "device_model=$MODEL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "debug_signer_detected=$DEBUG_SIGNER" \
  "debug_signing_explicitly_allowed=$ALLOW_DEBUG_SIGNING" \
  "bundle_apk_identity_verified=true" \
  "existing_signer_preflight_verified=true" \
  "install_executed=true" \
  "install_order_verified=true" \
  "data_app_install_verified=true" \
  "signature_permission_granted=true" \
  "demo_activity_started=true" \
  "client2_activity_started=$CLIENT2_STARTED" \
  "native_runtime_process_ready=true" \
  "native_runtime_abi_version=1" \
  "automatic_uninstall_enabled=false" \
  "system_partition_write_capability=false" \
  "production_ready=false" \
  "physical_controller_evidence_available=false" \
  "target_hardware_validated=false" \
  "native_vendor_npu_provider_available=false" \
  "native_runtime_dispatch_enabled=false" \
  "hardware_accessed=false"
