#!/usr/bin/env bash
set -euo pipefail

# Req IDs: FW-U-007, XSC-005, XSC-006, NV-F-001, NV-G-005/006/007, NV-P-002.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false

usage() {
  cat <<'EOF'
Usage: test_central_brain_android_capability_policy.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse existing debug artifacts.
  --require-api-33   Fail unless the selected device is exactly Android API 33.
  -h, --help         Show this help.
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
    --require-api-33)
      REQUIRE_API_33=true
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

export ANDROID_HOME
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.tools/gradle-home}"

ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
APKSIGNER="${APKSIGNER:-$ROOT_DIR/.tools/android-build-tools-current/apksigner}"

if [[ "$BUILD" == true ]]; then
  "$RUNTIME_DIR/gradlew" \
    --project-dir "$RUNTIME_DIR" \
    --no-daemon \
    --stacktrace \
    :runtime-service:testDebugUnitTest \
    :runtime-service:assembleDebug \
    :demo-hmi:assembleDebug \
    :policy-probe:assembleDebug
fi

RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
DEMO_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"
PROBE_APK="$RUNTIME_DIR/policy-probe/build/outputs/apk/debug/policy-probe-debug.apk"
for artifact in "$RUNTIME_APK" "$DEMO_APK" "$PROBE_APK"; do
  [[ -f "$artifact" ]] || { echo "missing capability test artifact: $artifact" >&2; exit 1; }
done
[[ -x "$ADB" ]] || { echo "adb not executable: $ADB" >&2; exit 1; }
[[ -x "$APKSIGNER" ]] || { echo "apksigner not executable: $APKSIGNER" >&2; exit 1; }

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
SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ ! "$SDK" =~ ^[0-9]+$ ]] || ((SDK < 33)); then
  echo "Android API 33 or newer is required; device reported '$SDK'" >&2
  exit 1
fi
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "R3 capability API 33 evidence requested, but device reported API $SDK" >&2
  exit 1
fi

signer_set() {
  "$APKSIGNER" verify --print-certs "$1" \
    | sed -n 's/^.*certificate SHA-256 digest: //p' \
    | sort -u \
    | paste -sd, -
}

RUNTIME_SIGNERS="$(signer_set "$RUNTIME_APK")"
DEMO_SIGNERS="$(signer_set "$DEMO_APK")"
PROBE_SIGNERS="$(signer_set "$PROBE_APK")"
if [[ -z "$RUNTIME_SIGNERS" || "$RUNTIME_SIGNERS" != "$DEMO_SIGNERS" \
    || "$RUNTIME_SIGNERS" != "$PROBE_SIGNERS" ]]; then
  echo "Runtime, allowed Demo and denied probe must share the same test signer" >&2
  exit 1
fi

INSTALL_ARGS=(--skip-build --serial "$SERIAL")
if [[ "$REQUIRE_API_33" == true ]]; then
  INSTALL_ARGS+=(--require-api-33)
fi
"$ROOT_DIR/tools/install_central_brain_android_runtime.sh" "${INSTALL_ARGS[@]}"
set +e
NON_TEST_INSTALL_OUTPUT="$("${ADB_DEVICE[@]}" install -r "$PROBE_APK" 2>&1)"
NON_TEST_INSTALL_STATUS=$?
set -e
if [[ $NON_TEST_INSTALL_STATUS -eq 0 ]] \
    || ! grep -Fq "INSTALL_FAILED_TEST_ONLY" <<<"$NON_TEST_INSTALL_OUTPUT"; then
  echo "Policy probe was not enforced as a test-only APK" >&2
  exit 1
fi
"${ADB_DEVICE[@]}" install -r -t "$PROBE_APK"

PROBE_PACKAGE_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys package com.centralbrain.policyprobe)"
if ! grep -Fq "com.centralbrain.permission.BIND_RUNTIME: granted=true" \
    <<<"$PROBE_PACKAGE_DUMP"; then
  echo "Policy probe did not pass the outer signature permission" >&2
  exit 1
fi
if ! grep -Fq "com.centralbrain.permission.ACCESS_DIAGNOSTICS: granted=true" \
    <<<"$PROBE_PACKAGE_DUMP"; then
  echo "Policy probe did not pass the outer diagnostic signature permission" >&2
  exit 1
fi
if ! grep -Fq "com.centralbrain.permission.BIND_GOVERNANCE: granted=true" \
    <<<"$PROBE_PACKAGE_DUMP"; then
  echo "Policy probe did not pass the outer Governance signature permission" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" logcat -c
START_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.policyprobe/.CapabilityPolicyProbeActivity)"
if ! grep -Fq "Status: ok" <<<"$START_OUTPUT"; then
  echo "$START_OUTPUT" >&2
  echo "Capability policy probe did not start" >&2
  exit 1
fi

PROBE_PASSED=false
for _ in {1..40}; do
  PROBE_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    CentralBrainPolicyProbe:I CentralBrainRuntime:W CentralBrainDiagnostic:W \
      CentralBrainGovernance:W '*:S')"
  if grep -Fq "capability_probe_complete=true" <<<"$PROBE_LOG" \
      && grep -Fq "protocol_version_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "protocol_hash_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "submit_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "status_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "cancel_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "diagnostic_version_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "diagnostic_hash_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "diagnostic_page_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "governance_version_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "governance_hash_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "action_evaluate_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "approval_request_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "approval_status_denied=true" <<<"$PROBE_LOG" \
      && grep -Fq "approval_cancel_denied=true" <<<"$PROBE_LOG"; then
    PROBE_PASSED=true
    break
  fi
  sleep 0.1
done
if [[ "$PROBE_PASSED" != true ]]; then
  echo "$PROBE_LOG" >&2
  echo "Unknown package was not denied by every production/diagnostic capability" >&2
  exit 1
fi

for capability in \
  runtime.protocol.read \
  runtime.task.submit \
  runtime.task.status.own \
  runtime.task.cancel.own \
  governance.protocol.read \
  governance.action.evaluate \
  governance.approval.request \
  governance.approval.status.own \
  governance.approval.cancel.own \
  runtime.diagnostics.read; do
  if ! grep -Fq "capability denied capability=$capability reason=PACKAGE_NOT_CONFIGURED" \
      <<<"$PROBE_LOG"; then
    echo "Runtime denial audit missing capability: $capability" >&2
    exit 1
  fi
done
if ! grep -Fq "packages=[com.centralbrain.policyprobe] resolved=true" <<<"$PROBE_LOG"; then
  echo "Runtime did not resolve the denied probe identity" >&2
  exit 1
fi

printf '%s\n' \
  "device_serial=$SERIAL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "outer_signature_permission_passed=true" \
  "outer_diagnostic_signature_permission_passed=true" \
  "outer_governance_signature_permission_passed=true" \
  "test_only_install_enforced=true" \
  "allowed_client_capabilities_verified=true" \
  "unknown_client_default_deny_verified=true" \
  "diagnostic_capability_default_deny_verified=true" \
  "governance_capability_default_deny_verified=true" \
  "package_and_current_signer_mapping_verified=true" \
  "production_capability_denial_audited=true" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false"
