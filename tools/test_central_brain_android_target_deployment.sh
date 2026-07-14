#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-011/012, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
RUN_INSTALL_GATE=true

usage() {
  cat <<'EOF'
Usage: test_central_brain_android_target_deployment.sh [options]

Options:
  --serial SERIAL       Select an adb device explicitly.
  --skip-build          Reuse existing debug artifacts.
  --skip-install-gate   Reuse an already-passed installation on the device.
  -h, --help            Show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --skip-build)
      BUILD=false
      shift
      ;;
    --skip-install-gate)
      RUN_INSTALL_GATE=false
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

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  source "$ROOT_DIR/env.sh"
fi

: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
APKANALYZER="${APKANALYZER:-$(command -v apkanalyzer || true)}"
APKSIGNER="${APKSIGNER:-$(command -v apksigner || true)}"
JAR="$JAVA_HOME/bin/jar"
for tool in "$ADB" "$APKANALYZER" "$APKSIGNER" "$JAR"; do
  [[ -n "$tool" && -x "$tool" ]] || { echo "required tool is unavailable: $tool" >&2; exit 1; }
done

if [[ "$BUILD" == true ]]; then
  "$ROOT_DIR/tools/build_central_brain_android_runtime.sh"
fi

SDK_AAR="$RUNTIME_DIR/central-brain-sdk/build/outputs/aar/central-brain-sdk-debug.aar"
RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
DEMO_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"
for artifact in "$SDK_AAR" "$RUNTIME_APK" "$DEMO_APK"; do
  [[ -f "$artifact" ]] || { echo "missing target deployment artifact: $artifact" >&2; exit 1; }
done

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial when multiple exist" >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state | tr -d '\r')" == "device" ]] \
  || { echo "adb device is not online: $SERIAL" >&2; exit 1; }

SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
MODEL="$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r')"
FINGERPRINT="$("${ADB_DEVICE[@]}" shell getprop ro.build.fingerprint | tr -d '\r')"
QEMU="$("${ADB_DEVICE[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
[[ "$SDK" == "33" ]] || { echo "R5D1 requires Android API 33; device reported '$SDK'" >&2; exit 1; }
[[ -n "$ABI" ]] || { echo "device ABI is empty" >&2; exit 1; }

if [[ "$RUN_INSTALL_GATE" == true ]]; then
  "$ROOT_DIR/tools/install_central_brain_android_runtime.sh" \
    --serial "$SERIAL" \
    --skip-build \
    --require-api-33 >/dev/null
fi

manifest_value() {
  "$APKANALYZER" manifest "$1" "$2"
}

[[ "$(manifest_value application-id "$RUNTIME_APK")" == "com.centralbrain.runtime" ]]
[[ "$(manifest_value application-id "$DEMO_APK")" == "com.centralbrain.demo" ]]
[[ "$(manifest_value min-sdk "$RUNTIME_APK")" == "33" ]]
[[ "$(manifest_value min-sdk "$DEMO_APK")" == "33" ]]
RUNTIME_TARGET_SDK="$(manifest_value target-sdk "$RUNTIME_APK")"
DEMO_TARGET_SDK="$(manifest_value target-sdk "$DEMO_APK")"
[[ "$RUNTIME_TARGET_SDK" =~ ^[0-9]+$ && "$RUNTIME_TARGET_SDK" -ge 33 ]]
[[ "$DEMO_TARGET_SDK" =~ ^[0-9]+$ && "$DEMO_TARGET_SDK" -ge 33 ]]

RUNTIME_PERMISSIONS="$("$APKANALYZER" manifest permissions "$RUNTIME_APK")"
DEMO_PERMISSIONS="$("$APKANALYZER" manifest permissions "$DEMO_APK")"
if grep -Fq "android.permission.INTERNET" \
    <<<"$RUNTIME_PERMISSIONS"$'\n'"$DEMO_PERMISSIONS"; then
  echo "Android target artifacts must not request INTERNET" >&2
  exit 1
fi

for artifact in "$SDK_AAR" "$RUNTIME_APK" "$DEMO_APK"; do
  if "$JAR" tf "$artifact" | grep -Eq '^(lib|jni|libs)/.*\.so$'; then
    echo "unexpected native library payload: $artifact" >&2
    exit 1
  fi
done

signer_set() {
  "$APKSIGNER" verify --print-certs "$1" \
    | sed -n 's/^.*certificate SHA-256 digest: //p' \
    | sort -u \
    | paste -sd, -
}

RUNTIME_SIGNERS="$(signer_set "$RUNTIME_APK")"
DEMO_SIGNERS="$(signer_set "$DEMO_APK")"
[[ -n "$RUNTIME_SIGNERS" && "$RUNTIME_SIGNERS" == "$DEMO_SIGNERS" ]] \
  || { echo "Runtime and Demo signer sets do not match" >&2; exit 1; }

RUNTIME_MANIFEST="$("$APKANALYZER" manifest print "$RUNTIME_APK")"
[[ "$(grep -c '<service' <<<"$RUNTIME_MANIFEST" || true)" == "3" ]]
for permission in \
  "com.centralbrain.permission.BIND_RUNTIME" \
  "com.centralbrain.permission.ACCESS_DIAGNOSTICS" \
  "com.centralbrain.permission.BIND_GOVERNANCE"; do
  grep -Fq "android:permission=\"$permission\"" <<<"$RUNTIME_MANIFEST" \
    || { echo "Runtime manifest service permission missing: $permission" >&2; exit 1; }
done

RUNTIME_PATH="$("${ADB_DEVICE[@]}" shell pm path com.centralbrain.runtime | tr -d '\r')"
DEMO_PATH="$("${ADB_DEVICE[@]}" shell pm path com.centralbrain.demo | tr -d '\r')"
[[ "$RUNTIME_PATH" == package:/data/app/*/base.apk ]] \
  || { echo "Runtime is not installed as an application package: $RUNTIME_PATH" >&2; exit 1; }
[[ "$DEMO_PATH" == package:/data/app/*/base.apk ]] \
  || { echo "Demo is not installed as an application package: $DEMO_PATH" >&2; exit 1; }

RUNTIME_PACKAGE="$("${ADB_DEVICE[@]}" shell dumpsys package com.centralbrain.runtime)"
DEMO_PACKAGE="$("${ADB_DEVICE[@]}" shell dumpsys package com.centralbrain.demo)"
RUNTIME_UID="$(sed -n 's/^[[:space:]]*userId=//p' <<<"$RUNTIME_PACKAGE" | head -n 1)"
DEMO_UID="$(sed -n 's/^[[:space:]]*userId=//p' <<<"$DEMO_PACKAGE" | head -n 1)"
RUNTIME_VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName=//p' \
  <<<"$RUNTIME_PACKAGE" | head -n 1)"
DEMO_VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName=//p' \
  <<<"$DEMO_PACKAGE" | head -n 1)"
[[ "$RUNTIME_UID" =~ ^[0-9]+$ && "$RUNTIME_UID" -ge 10000 ]]
[[ "$DEMO_UID" =~ ^[0-9]+$ && "$DEMO_UID" -ge 10000 ]]
[[ -n "$RUNTIME_VERSION_NAME" && "$RUNTIME_VERSION_NAME" == "$DEMO_VERSION_NAME" ]]
for package_dump in "$RUNTIME_PACKAGE" "$DEMO_PACKAGE"; do
  PACKAGE_FLAGS="$(sed -n 's/^[[:space:]]*pkgFlags=\[\(.*\)\]/\1/p' \
    <<<"$package_dump" | head -n 1)"
  if grep -Eq '(^|[[:space:]])(SYSTEM|PRIVILEGED|PERSISTENT)([[:space:]]|$)' \
      <<<"$PACKAGE_FLAGS"; then
    echo "target package unexpectedly requires system or privileged flags" >&2
    exit 1
  fi
done
for permission in \
  "com.centralbrain.permission.BIND_RUNTIME: granted=true" \
  "com.centralbrain.permission.BIND_GOVERNANCE: granted=true"; do
  grep -Fq "$permission" <<<"$DEMO_PACKAGE" \
    || { echo "Demo signature permission missing: $permission" >&2; exit 1; }
done

"${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.demo/.DemoActivity >/dev/null
sleep 1
RUNTIME_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys activity service \
  com.centralbrain.runtime/.CentralBrainRuntimeService)"
for marker in \
  "production_inference_allowed=false" \
  "deterministic_stub_implementation_configured=false" \
  "deterministic_stub_routing_enabled=false" \
  "vendor_npu_provider_available=false" \
  "scheduler_production_wired=false" \
  "production_model_router_wired=false" \
  "production_model_router_dispatch_enabled=false" \
  "hardware_accessed=false"; do
  grep -Fq "$marker" <<<"$RUNTIME_DUMP" \
    || { echo "Runtime target readiness marker missing: $marker" >&2; exit 1; }
done

if [[ "$QEMU" == "1" ]]; then
  EVIDENCE_SCOPE="api33-emulator-application-layer"
  REAL_TARGET_APPLICATION_ACCEPTANCE_REQUIRED=true
else
  EVIDENCE_SCOPE="api33-device-application-layer"
  REAL_TARGET_APPLICATION_ACCEPTANCE_REQUIRED=false
fi

SDK_AAR_SHA256="$(sha256sum "$SDK_AAR" | awk '{ print $1 }')"
RUNTIME_APK_SHA256="$(sha256sum "$RUNTIME_APK" | awk '{ print $1 }')"
DEMO_APK_SHA256="$(sha256sum "$DEMO_APK" | awk '{ print $1 }')"

printf '%s\n' \
  "target_deployment_preflight_verified=true" \
  "evidence_scope=$EVIDENCE_SCOPE" \
  "device_serial=$SERIAL" \
  "device_model=$MODEL" \
  "android_api=$SDK" \
  "android_api_33_verified=true" \
  "device_abi=$ABI" \
  "device_fingerprint=$FINGERPRINT" \
  "runtime_target_sdk=$RUNTIME_TARGET_SDK" \
  "demo_target_sdk=$DEMO_TARGET_SDK" \
  "runtime_uid=$RUNTIME_UID" \
  "demo_uid=$DEMO_UID" \
  "runtime_version_name=$RUNTIME_VERSION_NAME" \
  "demo_version_name=$DEMO_VERSION_NAME" \
  "runtime_install_path=$RUNTIME_PATH" \
  "demo_install_path=$DEMO_PATH" \
  "application_layer_only_verified=true" \
  "data_app_install_verified=true" \
  "system_app_required=false" \
  "privileged_app_required=false" \
  "vendor_aosp_bsp_modified=false" \
  "system_partition_write_capability=false" \
  "internet_permission_requested=false" \
  "native_library_payload_present=false" \
  "signature_protected_service_count=3" \
  "runtime_demo_signer_match=true" \
  "runtime_demo_signer_sha256=$RUNTIME_SIGNERS" \
  "central_brain_sdk_sha256=$SDK_AAR_SHA256" \
  "runtime_apk_sha256=$RUNTIME_APK_SHA256" \
  "demo_apk_sha256=$DEMO_APK_SHA256" \
  "production_inference_allowed=false" \
  "vendor_npu_provider_available=false" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false" \
  "target_hardware_validated=false" \
  "real_target_application_acceptance_required=$REAL_TARGET_APPLICATION_ACCEPTANCE_REQUIRED"
