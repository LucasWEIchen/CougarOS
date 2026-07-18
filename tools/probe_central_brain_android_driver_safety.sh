#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-002, S2-SAF-001, S2-EFF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage: probe_central_brain_android_driver_safety.sh [--serial SERIAL]

Runs the already-installed debug driver-safety contract probe. It never builds,
installs, uninstalls, reads vehicle state, or prints device/vehicle identities.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  source "$ROOT_DIR/env.sh"
fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
[[ -x "$ADB" ]] || { echo "adb is unavailable" >&2; exit 1; }

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <(
    "$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "driver safety probe requires exactly one online adb transport" >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state | tr -d '\r')" == "device" ]] \
  || { echo "selected adb transport is not online" >&2; exit 1; }

SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$SDK" != "33" || "$ABI" != "arm64-v8a" ]]; then
  echo "driver safety probe requires Android 13 ARM64" >&2
  exit 1
fi

NONCE="$(date +%s%N)"
START_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.governance.DriverSafetyAuditProbeActivity \
  --es nonce "$NONCE")"
grep -Fq "Status: ok" <<<"$START_OUTPUT" \
  || { echo "driver safety debug probe did not start" >&2; exit 1; }

PASSED=false
PROBE_LOG=""
for _ in {1..40}; do
  PROBE_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbSafetyProbe:I '*:S')"
  if grep -Fq "nonce=$NONCE driver_safety_probe_complete=true" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_projection_verified=true" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_action_rule_count=12" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_ux_profile_count=4" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_owner_role_count=3" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_state_maximum_age_ms=500" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_current_owner_approval_count=0" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_moving_blocked_action_count=6" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_vehicle_effect_action_count=4" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_approval_required_action_count=4" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_production_capability_authorized_count=0" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_current_owner_policy_approved=false" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_vehicle_state_provider_wired=false" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_effect_runtime_wired=false" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_effect_dispatch_authorized=false" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_hardware_operation_executed=false" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_vehicle_scalar_read=false" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_owner_approval_reference_logged=false" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_raw_log_persisted=false" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_android_debug_probe_available=true" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_android_debug_probe_executed=true" <<<"$PROBE_LOG" \
      && grep -Fq "driver_safety_android13_arm64_verified=false" <<<"$PROBE_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$PROBE_LOG" \
      && grep -Fq "production_ready=false" <<<"$PROBE_LOG" \
      && grep -Fq "target_hardware_validated=false" <<<"$PROBE_LOG"; then
    PASSED=true
    break
  fi
  sleep 0.25
done

if [[ "$PASSED" != true ]]; then
  echo "driver safety probe returned no conforming redacted evidence" >&2
  exit 1
fi

printf '%s\n' \
  'driver_safety_android_contract_probe_passed=true' \
  'driver_safety_action_rule_count=12' \
  'driver_safety_ux_profile_count=4' \
  'driver_safety_owner_role_count=3' \
  'driver_safety_state_maximum_age_ms=500' \
  'driver_safety_current_owner_approval_count=0' \
  'driver_safety_moving_blocked_action_count=6' \
  'driver_safety_vehicle_effect_action_count=4' \
  'driver_safety_approval_required_action_count=4' \
  'driver_safety_production_capability_authorized_count=0' \
  'driver_safety_android_contract_probe_android13_arm64_verified=true' \
  'driver_safety_target_state_observed=false' \
  'driver_safety_current_owner_policy_approved=false' \
  'driver_safety_vehicle_state_provider_wired=false' \
  'driver_safety_effect_runtime_wired=false' \
  'driver_safety_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
