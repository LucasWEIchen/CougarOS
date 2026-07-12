#!/usr/bin/env bash
set -euo pipefail

# Safe Android 13 application-bundle installer. Dry-run is the default.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUNDLE_DIR=""
TARGET_INPUTS=""
SERIAL="${ANDROID_SERIAL:-}"
EXECUTE=false
ALLOW_DEBUG_SIGNING=false

usage() {
  cat <<'EOF'
Usage: install_central_brain_android_delivery.sh [options]

Options:
  --bundle-dir PATH       Unpacked Central Brain Android delivery bundle.
  --target-inputs PATH    Completed target-input JSON; template is used by default.
  --serial SERIAL         Select an adb device explicitly.
  --execute               Install after all preflight checks; default is dry-run.
  --allow-debug-signing   Explicitly permit the debug signer on a disposable test device.
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
[[ -f "$MANIFEST" ]] || { echo "delivery manifest is missing: $MANIFEST" >&2; exit 1; }

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

resolve_executable() {
  local candidate
  for candidate in "$@"; do
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      realpath "$candidate"
      return 0
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

if ! ADB="$(resolve_executable \
    "${ADB:-}" \
    "$(command -v adb || true)" \
    "${SDK_ROOT:+$SDK_ROOT/platform-tools/adb}" \
    "$ROOT_DIR/.tools/android-sdk/platform-tools/adb")"; then
  echo "required Android delivery tool is unavailable: adb" >&2
  exit 1
fi
if ! APKSIGNER="$(resolve_executable \
    "${APKSIGNER:-}" \
    "$(command -v apksigner || true)" \
    "$LATEST_APKSIGNER" \
    "$ROOT_DIR/.tools/android-build-tools-current/apksigner")"; then
  echo "required Android delivery tool is unavailable: apksigner" >&2
  exit 1
fi
if ! AAPT="$(resolve_executable \
    "${AAPT:-}" \
    "$(command -v aapt || true)" \
    "$LATEST_AAPT" \
    "$ROOT_DIR/.tools/android-build-tools-current/aapt")"; then
  echo "required Android delivery tool is unavailable: aapt" >&2
  exit 1
fi
if ! JAVA="$(resolve_executable \
    "${JAVA:-}" \
    "$(command -v java || true)" \
    "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
    "$ROOT_DIR/.tools/jdk/bin/java" \
    "$ROOT_DIR/.tools/bin/java")"; then
  echo "required Java runtime is unavailable; set JAVA_HOME or add java to PATH" >&2
  exit 1
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME="$(dirname "$(dirname "$JAVA")")"
fi
export JAVA_HOME
export PATH="$(dirname "$JAVA"):$PATH"

DELIVERY_TOOL="$BUNDLE_DIR/tools/central_brain_android_delivery.py"
if [[ ! -f "$DELIVERY_TOOL" ]]; then
  DELIVERY_TOOL="$ROOT_DIR/tools/central_brain_android_delivery.py"
fi
python3 "$DELIVERY_TOOL" verify --bundle-dir "$BUNDLE_DIR" >/dev/null

if [[ -z "$TARGET_INPUTS" ]]; then
  TARGET_INPUTS="$BUNDLE_DIR/contracts/target-inputs.example.json"
fi
[[ -f "$TARGET_INPUTS" ]] \
  || { echo "target input file is missing: $TARGET_INPUTS" >&2; exit 1; }
python3 - "$TARGET_INPUTS" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
payload = json.loads(path.read_text(encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("target input schema must be 1.0.0")
if payload.get("device", {}).get("android_api_expected") != 33:
    raise SystemExit("target inputs must require Android API 33")
claims = payload.get("claim_state", {})
for key in (
    "target_system_integration_owner_resolved",
    "production_activation_allowed",
    "target_hardware_validated",
):
    if claims.get(key) is not False:
        raise SystemExit(f"unsupported target input claim: {key}")
unresolved = 0
for section in ("owners", "deployment_decisions", "evidence_references"):
    unresolved += sum(value is None for value in payload.get(section, {}).values())
print(f"target_input_status={payload.get('status', 'unknown')}")
print(f"unresolved_target_input_count={unresolved}")
PY

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial when multiple exist" >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi
ADB_DEVICE=("$ADB" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state)" == "device" ]] \
  || { echo "adb device is not online: $SERIAL" >&2; exit 1; }
SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
MODEL="$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r')"
[[ "$SDK" == "33" ]] || { echo "delivery requires API 33; device reported $SDK" >&2; exit 1; }

mapfile -t APK_ROWS < <(python3 - "$MANIFEST" <<'PY'
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
for row in "${APK_ROWS[@]}"; do
  if [[ "$row" == DEBUG\|* ]]; then
    DEBUG_SIGNER="${row#DEBUG|}"
  else
    INSTALL_ROWS+=("$row")
  fi
done
if [[ "$EXECUTE" == true && "$DEBUG_SIGNER" == true \
    && "$ALLOW_DEBUG_SIGNING" != true ]]; then
  echo "bundle is debug-signed; --allow-debug-signing is required for a test install" >&2
  exit 1
fi

# Re-read every delivered APK before comparing it with installed packages.
for row in "${INSTALL_ROWS[@]}"; do
  IFS='|' read -r artifact_id bundle_path package_name expected_signer install_order \
    <<<"$row"
  apk_path="$BUNDLE_DIR/$bundle_path"
  badging="$("$AAPT" dump badging "$apk_path")"
  actual_package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging" \
    | head -1)"
  if [[ -z "$actual_package" || "$actual_package" != "$package_name" ]]; then
    echo "BUNDLE_PACKAGE_MISMATCH artifact=$artifact_id" >&2
    exit 1
  fi
  if ! signer_output="$("$APKSIGNER" verify --print-certs "$apk_path" 2>&1)"; then
    echo "BUNDLE_SIGNATURE_INVALID artifact=$artifact_id" >&2
    echo "$signer_output" >&2
    exit 1
  fi
  actual_signer="$(sed -n 's/^.*certificate SHA-256 digest: //p' \
    <<<"$signer_output" | head -1 | tr '[:upper:]' '[:lower:]')"
  if [[ -z "$actual_signer" || "$actual_signer" != "$expected_signer" ]]; then
    echo "BUNDLE_SIGNER_MISMATCH artifact=$artifact_id" >&2
    exit 1
  fi
done

TEMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

# Resolve all existing signer conflicts before installing the first package.
for row in "${INSTALL_ROWS[@]}"; do
  IFS='|' read -r artifact_id bundle_path package_name expected_signer install_order \
    <<<"$row"
  remote_path="$("${ADB_DEVICE[@]}" shell pm path "$package_name" \
    | tr -d '\r' | sed -n '1s/^package://p')"
  if [[ -z "$remote_path" ]]; then
    continue
  fi
  local_apk="$TEMP_DIR/$artifact_id.installed.apk"
  if ! "${ADB_DEVICE[@]}" pull "$remote_path" "$local_apk" \
      >"$TEMP_DIR/$artifact_id.pull.txt" 2>&1; then
    cat "$TEMP_DIR/$artifact_id.pull.txt" >&2
    exit 1
  fi
  installed_signer="$("$APKSIGNER" verify --print-certs "$local_apk" \
    | sed -n 's/^.*certificate SHA-256 digest: //p' \
    | head -1 \
    | tr '[:upper:]' '[:lower:]')"
  if [[ -z "$installed_signer" || "$installed_signer" != "$expected_signer" ]]; then
    echo "SIGNER_MIGRATION_REQUIRED package=$package_name" >&2
    echo "No package was installed or uninstalled." >&2
    exit 1
  fi
done

if [[ "$EXECUTE" == false ]]; then
  printf '%s\n' \
    "target_delivery_preflight_verified=true" \
    "device_serial=$SERIAL" \
    "device_model=$MODEL" \
    "android_api=$SDK" \
    "device_abi=$ABI" \
    "debug_signer_detected=$DEBUG_SIGNER" \
    "bundle_apk_identity_verified=true" \
    "install_executed=false" \
    "automatic_uninstall_enabled=false" \
    "system_partition_write_capability=false" \
    "production_ready=false" \
    "target_hardware_validated=false"
  exit 0
fi

for row in "${INSTALL_ROWS[@]}"; do
  IFS='|' read -r artifact_id bundle_path package_name expected_signer install_order \
    <<<"$row"
  "${ADB_DEVICE[@]}" install -r "$BUNDLE_DIR/$bundle_path" \
    >"$TEMP_DIR/$artifact_id.install.txt"
  grep -Fq 'Success' "$TEMP_DIR/$artifact_id.install.txt" \
    || { cat "$TEMP_DIR/$artifact_id.install.txt" >&2; exit 1; }
done

for package_name in \
  com.centralbrain.runtime com.centralbrain.demo com.tuanjie.urasclient2; do
  installed_path="$("${ADB_DEVICE[@]}" shell pm path "$package_name" | tr -d '\r')"
  [[ "$installed_path" == package:/data/app/*/base.apk ]] \
    || { echo "package is not installed under /data/app: $package_name" >&2; exit 1; }
done

DEMO_PACKAGE="$("${ADB_DEVICE[@]}" shell dumpsys package com.centralbrain.demo)"
CLIENT2_PACKAGE="$("${ADB_DEVICE[@]}" shell dumpsys package com.tuanjie.urasclient2)"
grep -Fq 'com.centralbrain.permission.BIND_RUNTIME: granted=true' <<<"$DEMO_PACKAGE"
grep -Fq 'com.centralbrain.permission.BIND_RUNTIME: granted=true' <<<"$CLIENT2_PACKAGE"

DEMO_START="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.demo/.DemoActivity)"
CLIENT2_START="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.tuanjie.urasclient2/.MainActivity)"
grep -Fq 'Status: ok' <<<"$DEMO_START"
grep -Fq 'Status: ok' <<<"$CLIENT2_START"

RUNTIME_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys activity service \
  com.centralbrain.runtime/.CentralBrainRuntimeService)"
for marker in \
  'r7_application_integration_complete=true' \
  'api33_end_to_end_acceptance_complete=true' \
  'production_activation_allowed=false' \
  'target_system_integration_owner_resolved=false' \
  'target_hardware_validated=false' \
  'hardware_accessed=false'; do
  grep -Fq "$marker" <<<"$RUNTIME_DUMP"
done

printf '%s\n' \
  "target_delivery_preflight_verified=true" \
  "device_serial=$SERIAL" \
  "device_model=$MODEL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "debug_signer_detected=$DEBUG_SIGNER" \
  "debug_signing_explicitly_allowed=$ALLOW_DEBUG_SIGNING" \
  "bundle_apk_identity_verified=true" \
  "install_executed=true" \
  "install_order_verified=true" \
  "data_app_install_verified=true" \
  "signature_permission_granted=true" \
  "demo_activity_started=true" \
  "client2_activity_started=true" \
  "automatic_uninstall_enabled=false" \
  "system_partition_write_capability=false" \
  "production_ready=false" \
  "target_hardware_validated=false" \
  "hardware_accessed=false"
